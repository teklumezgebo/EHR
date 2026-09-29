package com.teklumezgebo.ehr;

import org.springframework.boot.SpringApplication;

public class TestEhrApplication {

	public static void main(String[] args) {
		SpringApplication.from(EhrApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
