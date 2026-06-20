package com.beemer.coinservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CoinserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CoinserviceApplication.class, args);
	}

}
