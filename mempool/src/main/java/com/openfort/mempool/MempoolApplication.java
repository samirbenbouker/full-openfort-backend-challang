package com.openfort.mempool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MempoolApplication {

	public static void main(String[] args) {
		SpringApplication.run(MempoolApplication.class, args);
	}

}
