package me.resp.fileuploaddemo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class UploadService {

	@Autowired
	ObjectMapper objectMapper;

	private boolean acceptJson(ServerRequest req) {
		return req.headers().accept().stream().filter(p -> p.includes(MediaType.APPLICATION_JSON)).findAny()
				.isPresent();
	}

	private Path dst = Path.of("uploaded.txt");

	public Mono<ServerResponse> uploadGet(ServerRequest req) {
		return ServerResponse.ok().render("upload");
	}

	private Map<String, Object> reqInfo(ServerRequest req) {
		Map<String, Object> envs = new HashMap<>();
		envs.put("contentType", req.headers().contentType());
		envs.put("contentLength", req.headers().contentLength());
		return envs;
	}

	public Mono<ServerResponse> uploadMultipart(ServerRequest req) {
		return req.multipartData().flatMap(parts -> {
			FilePart filePart = (FilePart) parts.toSingleValueMap()
					.get("file");
			return filePart.transferTo(dst).then(Mono.defer(() -> {
				if (acceptJson(req)) {
					return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(
							Map.of(
									"infos", reqInfo(req),
									"filePartFileName", filePart.filename(),
									"filePartName", filePart.name()));

				}
				return ServerResponse.ok().render("upload",
						Map.of(
								"infos", reqInfo(req),
								"msg",
								String.format("'%s' as name '%s' uploaded.", filePart.filename(), filePart.name())));
			}));
		}).onErrorResume(t -> {
			if (acceptJson(req)) {
				return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(
						Map.of(
								"infos", reqInfo(req),
								"msg", t.getMessage()));
			} else {
				return ServerResponse.ok().render("upload",
						Map.of(
								"infos", reqInfo(req),
								"msg", t.getMessage()));
			}
		});
	}

	private Mono<ServerResponse> countCharacters(ServerRequest req, String delimiter) {
		return req.bodyToFlux(DataBuffer.class)
				.map(dataBuffer -> {
					byte[] bytes = new byte[dataBuffer.readableByteCount()];
					dataBuffer.read(bytes);
					DataBufferUtils.release(dataBuffer);
					return new String(bytes, StandardCharsets.UTF_8);
				})
				.map(line -> {
					log.debug(line);
					return line.length();
				})
				.collect(Collectors.summingInt(Integer::intValue))
				.flatMap(c -> {
					return ServerResponse.ok()
							.contentType(MediaType.APPLICATION_JSON)
							.bodyValue(Map.of("characterCount", c));
				});
	}

	@Data
	static class Partline {
		String fullpart;
		String remains;

		static Partline of(String fullpart, String remains) {
			Partline pl = new Partline();
			pl.setFullpart(fullpart);
			pl.setRemains(remains);
			return pl;
		}
	}

	private Mono<ServerResponse> countLines(ServerRequest req, String delimiter) {
		int dlen = delimiter.length();
		return req.bodyToFlux(DataBuffer.class)
				.map(dataBuffer -> {
					byte[] bytes = new byte[dataBuffer.readableByteCount()];
					dataBuffer.read(bytes);
					DataBufferUtils.release(dataBuffer);
					return new String(bytes, StandardCharsets.UTF_8);
				})
				.scan(new Partline(), (buffer, item) -> {
					int p = item.lastIndexOf(delimiter);
					if (p == -1) {
						buffer.setFullpart(null);
						if (buffer.getRemains() != null) {
							buffer.setRemains(buffer.getRemains() + item);
						} else {
							buffer.setRemains(item);
						}
						return buffer;
					}
					if (buffer.getRemains() != null) {
						buffer.setFullpart(buffer.getRemains() + item.substring(0, p));
						buffer.setRemains(item.substring(p + dlen));
					} else {
						buffer.setFullpart(item.substring(0, p));
						buffer.setRemains(item.substring(p + dlen));
					}
					return buffer;
				})
				.filter(line -> line.getFullpart() != null)
				.map(oneline -> {
					log.debug("{}", oneline);
					String[] split = oneline.getFullpart().split(delimiter);
					return List.of(split);
				})
				.flatMapIterable(l -> l)
				.count()
				.flatMap(c -> {
					return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
							.bodyValue(Map.of("lineCount", c));
				});
	}

	public Mono<ServerResponse> lines(ServerRequest req) {
		String delimiter = req.queryParam("delimiter").orElse(null);
		String action = req.queryParam("action").orElse(null);
		if (delimiter != null) {
			if ("countCharacters".equals(action)) {
				return countCharacters(req, delimiter);
			} else {
				return countLines(req, delimiter);
			}
		}
		return req.bodyToFlux(DataBuffer.class)
				.map(df -> {
					return df.toString(StandardCharsets.UTF_8);
				})
				.buffer(3, 2)
				.map(lines -> {
					return lines;
				})
				.count()
				.flatMap(c -> {
					return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
							.bodyValue(Map.of("bufferCount", c));
				});
	}

	public Mono<ServerResponse> postJson(ServerRequest req) {
		return req.bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {
		}).flatMap(m -> {
			return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(m);
		}).onErrorResume(t -> {
			log.debug("", t);
			return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(
					Map.of("msg",
							t.getMessage()));
		});
	}
	// public Mono<ServerResponse> postJson(ServerRequest req) {
	// String fluxType = req.headers().firstHeader("Flux-Type");
	// if ("String".equals(fluxType)) {
	// return req.bodyToFlux(String.class)
	// .collectList()
	// .flatMap(lines -> {
	// String data = String.join("", lines);
	// return ServerResponse.ok().bodyValue("ok");
	// }).onErrorResume(t -> {
	// return ServerResponse.ok().bodyValue(t.getMessage());
	// });
	// } else {
	// return req.bodyToFlux(DataBuffer.class)
	// .map(df -> {
	// return df.toString(StandardCharsets.UTF_8);
	// })
	// .collectList()
	// .flatMap(lines -> {
	// String data = String.join("", lines);
	// try {
	// Map<String, String> obdata = objectMapper.readValue(data, new
	// TypeReference<>() {
	// });
	// Map<String, String> newmap = new HashMap<>();
	// obdata.forEach((k, v) -> {
	// newmap.put(k, v.length() + "");
	// });
	// return ServerResponse.ok().bodyValue(newmap);
	// } catch (JsonProcessingException e) {
	// return ServerResponse.ok().bodyValue(e.getMessage());
	// }
	// }).onErrorResume(t -> {
	// return ServerResponse.ok().bodyValue(t.getMessage());
	// });
	// }
	// }
}
