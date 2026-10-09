package com.pharmalab.labtrack;

import org.springframework.boot.SpringApplication;

public class TestLabtrackApplication {

	public static void main(String[] args) {
		SpringApplication.from(LabtrackApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
