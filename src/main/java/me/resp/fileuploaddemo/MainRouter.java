package me.resp.fileuploaddemo;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class MainRouter {

	@Bean
	RouterFunction<ServerResponse> myroute(UploadService uploadService) {
		return route().path("/",
				b -> {
					b.path("/upload", b1 -> {
						b1.GET("", uploadService::uploadGet);
						b1.POST("/multipart", uploadService::uploadMultipart);
						b1.POST("/urlencodedBufferExceeded", uploadService::urlencodedBufferExceeded);
						b1.POST("/urlencoded", uploadService::urlencoded);
						b1.POST("/json", uploadService::postJson);
					});
				}).build();
	}
}
