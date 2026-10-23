package com.pharmalab.labtrack.config;

import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.pharmalab.labtrack.web.DisplayFormat;

/**
 * Bean dùng trong template. Lớp implements {@link WebMvcConfigurer} dù không tùy biến gì, vì
 * {@code @WebMvcTest} chỉ nạp các cấu hình MVC như vậy: nếu {@link DisplayFormat} là một
 * {@code @Component} thường, test controller không có bean {@code fmt} và mọi trang có ngày giờ lỗi khi
 * render. Múi giờ đọc cùng thuộc tính với {@link ClockConfig}, không theo JVM (NFR-04).
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	@Bean
	DisplayFormat fmt(@Value("${labtrack.time-zone}") ZoneId zone) {
		return new DisplayFormat(zone);
	}

}
