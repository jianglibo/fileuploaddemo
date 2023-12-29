package me.resp.fileuploaddemo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class InfoBean {

	@Autowired
	Environment environment;

	@PostConstruct
	void post() {
		log.debug("TOBF_SERVER_PORT: {}", environment.getProperty("TOBF_SERVER_PORT"));
		log.debug("TOBF_LIVERELOAD_PORT: {}", environment.getProperty("TOBF_LIVERELOAD_PORT"));
	}
}
