package com.pharmalab.labtrack.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình {@code labtrack.*}.
 *
 * @param timeZone múi giờ nghiệp vụ dùng để tính "hôm nay", năm của mã mẫu và để hiển thị (NFR-04)
 * @param pageSize số dòng mỗi trang danh sách (BR-13)
 */
@ConfigurationProperties("labtrack")
public record LabtrackProperties(ZoneId timeZone, int pageSize) {
}
