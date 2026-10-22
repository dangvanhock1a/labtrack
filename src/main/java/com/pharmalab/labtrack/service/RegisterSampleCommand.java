package com.pharmalab.labtrack.service;

import com.pharmalab.labtrack.domain.SampleType;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Dữ liệu đăng ký mẫu (BR-02).
 *
 * @param receivedAt thời điểm nhận theo giờ treo tường của múi giờ nghiệp vụ (giờ VN)
 */
public record RegisterSampleCommand(String productName, String batchNo, SampleType sampleType,
		LocalDateTime receivedAt, LocalDate dueDate) {
}
