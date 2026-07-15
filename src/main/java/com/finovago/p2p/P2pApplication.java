package com.finovago.p2p;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.finovago.p2p")
public class P2pApplication {

	public static void main(String[] args) {
		SpringApplication.run(P2pApplication.class, args);
	}

}
