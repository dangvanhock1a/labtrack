package com.pharmalab.labtrack.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleType;

/**
 * Dữ liệu đăng ký một mẫu mới (BR-02, BR-13). {@code receivedAt} là giờ địa phương theo múi giờ
 * của ứng dụng; {@code priority}, {@code receivedAt} và {@code dueDate} để {@code null} thì dùng mặc định.
 */
public record NewSample(String productName, String batchNo, SampleType sampleType, SamplePriority priority,
		LocalDateTime receivedAt, LocalDate dueDate) {

	/** Không chọn mức ưu tiên, nên mẫu nhận Bình thường. Dành cho test và E2E gọi thẳng tầng nghiệp vụ. */
	public NewSample(String productName, String batchNo, SampleType sampleType, LocalDateTime receivedAt,
			LocalDate dueDate) {
		this(productName, batchNo, sampleType, null, receivedAt, dueDate);
	}

}
