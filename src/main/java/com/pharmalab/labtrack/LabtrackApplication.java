package com.pharmalab.labtrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Không đặt @MapperScan ở đây: mapper dùng @Mapper để @WebMvcTest không phải tạo SqlSessionFactory.
@SpringBootApplication
@ConfigurationPropertiesScan
public class LabtrackApplication {

	public static void main(String[] args) {
		SpringApplication.run(LabtrackApplication.class, args);
	}

}
