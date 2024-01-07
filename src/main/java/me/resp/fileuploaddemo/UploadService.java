package me.resp.fileuploaddemo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

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

	public Mono<ServerResponse> lines(ServerRequest req) {
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
