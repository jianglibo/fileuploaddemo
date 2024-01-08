package me.resp.fileuploaddemo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

import reactor.core.publisher.Flux;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureWebTestClient
public class UploadServiceTest {

	@Autowired
	WebTestClient webClient;

	@Autowired
	ResourceLoader resourceLoader;

	@Test
	public void testUnlimitLengthWithDelimiterCharactersCount() {
		webClient.post()
				.uri(ub -> ub.path("/upload/lines")
						.queryParam("delimiter", "处处")
						.queryParam("action", "countCharacters")
						.build())
				.contentType(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromPublisher(Flux.range(0, 1000)
						.map(l -> "春眠不觉晓".repeat(2) + "处处"), String.class))
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectBody()
				.jsonPath("$.characterCount").isEqualTo(12000);
	}

	private List<String> splitIntoRandomLengthLines(String str, int maxLength) {
		Random rand = new Random();
		List<String> lines = new ArrayList<>();
		int index = 0;
		while (index < str.length()) {
			int end = Math.min(index + rand.nextInt(maxLength) + 1, str.length());
			lines.add(str.substring(index, end));
			index = end;
		}
		return lines;
	}

	@Test
	public void testUnlimitLengthWithDelimiterLinesCount() {
		String oneline = Stream.generate(() -> "春眠不觉晓".repeat(2) + "处处").limit(10000)
				.collect(Collectors.joining());
		List<String> lines = splitIntoRandomLengthLines(oneline, 1024);
		webClient.post()
				.uri(ub -> ub.path("/upload/lines")
						.queryParam("delimiter", "处处")
						.build())
				.contentType(MediaType.APPLICATION_JSON)
				.body(BodyInserters.fromPublisher(Flux.fromIterable(lines), String.class))
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectBody()
				.jsonPath("$.lineCount").isEqualTo(10000);
	}

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
					Assertions.assertThat(lv).isNotEqualTo(10000);
				});
	}

	@Test
	public void testUrlencodedBig() {
		MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
		formData.add("a", "b".repeat(2048));
		webClient.post()
				.uri("/upload/urlencoded")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.bodyValue(formData)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectBody()
				.jsonPath("$.msg").isEqualTo("Exceeded limit on max bytes to buffer : 1024");
	}

	@Test
	public void testUrlencodedSmall() {
		MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
		formData.add("a", "b".repeat(512));
		webClient.post()
				.uri("/upload/urlencoded")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.bodyValue(formData)
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectBody()
				.jsonPath("$.formdata.a").isEqualTo("b".repeat(512));
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
