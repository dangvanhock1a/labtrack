package com.pharmalab.labtrack.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Nguồn thời gian duy nhất của ứng dụng. Múi giờ lấy từ {@code labtrack.time-zone}, không
 * phụ thuộc múi giờ của JVM (NFR-04). Test thay bằng {@link Clock#fixed}.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	@Bean
	Clock clock(@Value("${labtrack.time-zone}") ZoneId zone) {
		return Clock.system(zone);
	}

}
