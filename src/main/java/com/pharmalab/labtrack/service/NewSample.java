package com.pharmalab.labtrack.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.pharmalab.labtrack.domain.SampleType;

/**
 * Dữ liệu đăng ký một mẫu mới (BR-02). {@code receivedAt} là giờ địa phương theo múi giờ
 * của ứng dụng; {@code receivedAt} và {@code dueDate} để {@code null} thì dùng mặc định.
 */
public record NewSample(String productName, String batchNo, SampleType sampleType, LocalDateTime receivedAt,
		LocalDate dueDate) {
}
