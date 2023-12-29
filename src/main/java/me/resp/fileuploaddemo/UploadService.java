package me.resp.fileuploaddemo;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@Component
public class UploadService {

	@Autowired
	ObjectMapper objectMapper;

	private Path dst = Path.of("uploaded.txt");

	public Mono<ServerResponse> uploadGet(ServerRequest req) {
		return ServerResponse.ok().render("upload");
	}

	public Mono<ServerResponse> uploadPartExceeded1(ServerRequest req) {
		return req.body(BodyExtractors.toParts())
				.flatMap(part -> {
					if (part instanceof FilePart) {
						FilePart filePart = (FilePart) part;
						return filePart.transferTo(dst);
					}
					return Mono.empty();
				}).then(Mono.defer(() -> {
					return ServerResponse.ok().bodyValue("ok");
				})).onErrorResume(t -> {
					return ServerResponse.ok().bodyValue(t.getMessage());
				});

	}

	public Mono<ServerResponse> uploadPartExceeded2(ServerRequest req) {
		return req.body(BodyExtractors.toMultipartData()) // will consume whole body in memory.
				.flatMap(parts -> {
					FilePart filePart = (FilePart) parts.toSingleValueMap()
							.get("file");
					return filePart.transferTo(dst);
				}).then(Mono.defer(() -> {
					return ServerResponse.ok().bodyValue("ok");
				})).onErrorResume(t -> {
					return ServerResponse.ok().bodyValue(t.getMessage());
				});
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
				return ServerResponse.ok().render("upload",
						Map.of(
								"infos", reqInfo(req),
								"msg",
								String.format("'%s' as name '%s' uploaded.", filePart.filename(), filePart.name())));
			}));
		});
	}

	public Mono<ServerResponse> urlencoded(ServerRequest req) {
		return req.bodyToFlux(DataBuffer.class).flatMap(db -> {
			String dbs = db.toString(StandardCharsets.UTF_8);
			return Mono.just(dbs);
		}).collectList().flatMap((lines) -> {
			// the data will be like: data=urlencoded value.
			String data = String.join("", lines);
			data = data.substring(data.indexOf("=") + 1);
			data = URLDecoder.decode(data, StandardCharsets.UTF_8);
			try {
				Map<String, String> obdata = objectMapper.readValue(data, new TypeReference<>() {
				});
				Map<String, String> newmap = new HashMap<>();
				obdata.forEach((k, v) -> {
					newmap.put(k, v.length() + "");
				});
				return ServerResponse.ok().render("upload",
						Map.of("infos", reqInfo(req), "msg", "ok", "lines", lines.size(), "json",
								objectMapper.writeValueAsString(newmap)));
			} catch (JsonProcessingException e) {
				return ServerResponse.ok().bodyValue(e.getMessage());
			}
		}).onErrorResume(t -> {
			return ServerResponse.ok().bodyValue(t.getMessage());
		});
	}

	public Mono<ServerResponse> urlencodedBufferExceeded(ServerRequest req) {
		return req.formData().flatMap(mdata -> {
			String data = mdata.getFirst("data");
			try {
				Files.writeString(dst, data);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return ServerResponse.ok().render("upload", Map.of("infos", reqInfo(req), "msg", "ok"));
		}).onErrorResume(t -> {
			return ServerResponse.ok().bodyValue(t.getMessage());
		});
	}

	public Mono<ServerResponse> postJson(ServerRequest req) {
		String fluxType = req.headers().firstHeader("Flux-Type");
		if ("String".equals(fluxType)) {
			return req.bodyToFlux(String.class)
					.collectList()
					.flatMap(lines -> {
						String data = String.join("", lines);
						return ServerResponse.ok().bodyValue("ok");
					}).onErrorResume(t -> {
						return ServerResponse.ok().bodyValue(t.getMessage());
					});
		} else {
			return req.bodyToFlux(DataBuffer.class)
					.map(df -> {
						return df.toString(StandardCharsets.UTF_8);
					})
					.collectList()
					.flatMap(lines -> {
						String data = String.join("", lines);
						try {
							Map<String, String> obdata = objectMapper.readValue(data, new TypeReference<>() {
							});
							Map<String, String> newmap = new HashMap<>();
							obdata.forEach((k, v) -> {
								newmap.put(k, v.length() + "");
							});
							return ServerResponse.ok().bodyValue(newmap);
						} catch (JsonProcessingException e) {
							return ServerResponse.ok().bodyValue(e.getMessage());
						}
					}).onErrorResume(t -> {
						return ServerResponse.ok().bodyValue(t.getMessage());
					});

		}
	}
}
