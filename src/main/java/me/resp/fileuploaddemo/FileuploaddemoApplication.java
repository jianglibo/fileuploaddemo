package me.resp.fileuploaddemo;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class FileuploaddemoApplication {

	public static void main(String[] args) {
		new SpringApplicationBuilder()
				.profiles("dev") // and so does this
				.sources(FileuploaddemoApplication.class)
				.run(args);
	}
}
