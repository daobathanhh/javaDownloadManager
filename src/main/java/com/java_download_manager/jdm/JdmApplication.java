package com.java_download_manager.jdm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class JdmApplication {

	public static void main(String[] args) {
		SpringApplication.run(JdmApplication.class, args);
	}

}
