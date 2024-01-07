package me.resp.fileuploaddemo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import reactor.core.publisher.Flux;

@SpringBootTest
@ActiveProfiles("test")
// @WebFluxTest
@AutoConfigureWebTestClient
public class UploadServiceTest {

	@Autowired
	WebTestClient webClient;

	@Autowired
	ResourceLoader resourceLoader;

	@Test
	public void testUnlimitLength() {
		// 1024 bytes * 10000 = 10M
		webClient.post()
				.uri("/upload/lines")
				.contentType(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromPublisher(Flux.range(0, 10000)
						.map(l -> "a".repeat(1024)), String.class))
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectBody()
				.jsonPath("$.bufferCount")
				.value(v -> {
					Long lv = ((Integer) v).longValue();
					Assertions.assertThat(lv).isGreaterThan(10);
				});
	}

	@Test
	public void testDataSmall() {
		List<Map<String, Object>> body = List.of(Map.of("a", "b"));
		webClient.post()
				.uri("/upload/json")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectBody()
				.jsonPath("$.[0].a").isEqualTo("b");
	}

	@Test
	public void testDataLarge() throws IOException {
		String body = resourceLoader.getResource("classpath:large.json")
				.getContentAsString(StandardCharsets.UTF_8);

		webClient.post()
				.uri("/upload/json")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(body)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectBody()
				.jsonPath("$.msg").isEqualTo("Exceeded limit on max bytes to buffer : 1024");
	}

	@Test
	public void testUploadSmall() {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("file", resourceLoader.getResource("classpath:upload.txt"));
		webClient.post()
				.uri("/upload/multipart")
				.body(BodyInserters.fromMultipartData(builder.build()))
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectBody()
				.jsonPath("$.filePartFileName").isNotEmpty();
	}

	@Test
	public void testUploadLarge() {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("file", resourceLoader.getResource("classpath:upload2k.txt"));
		webClient.post()
				.uri("/upload/multipart")
				.body(BodyInserters.fromMultipartData(builder.build()))
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectBody()
				.jsonPath("$.msg")
				.isEqualTo("Part exceeded the disk usage limit of 1024 bytes");
	}

}
