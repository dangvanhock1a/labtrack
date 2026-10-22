package com.pharmalab.labtrack.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

	/** Nguồn thời gian duy nhất để test cố định được giờ. Múi giờ nghiệp vụ lấy từ {@link LabtrackProperties}, không lấy từ Clock. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
