package com.pharmalab.labtrack.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.StorageCondition;

/**
 * Dữ liệu đăng ký một mẫu mới (BR-02, BR-13, BR-18). {@code receivedAt} là giờ địa phương theo múi giờ
 * của ứng dụng; {@code priority}, {@code storageCondition}, {@code receivedAt} và {@code dueDate} để {@code null}
 * thì dùng mặc định.
 */
public record NewSample(String productName, String batchNo, SampleType sampleType, SamplePriority priority,
		StorageCondition storageCondition, LocalDateTime receivedAt, LocalDate dueDate) {

	/**
	 * Không chọn điều kiện bảo quản, nên mẫu nhận Nhiệt độ phòng. Có từ trước BR-18; giữ lại để test và E2E gọi thẳng
	 * tầng nghiệp vụ không phải ghi thêm tham số không liên quan tới điều chúng kiểm.
	 */
	public NewSample(String productName, String batchNo, SampleType sampleType, SamplePriority priority,
			LocalDateTime receivedAt, LocalDate dueDate) {
		this(productName, batchNo, sampleType, priority, null, receivedAt, dueDate);
	}

	/** Không chọn mức ưu tiên, nên mẫu nhận Bình thường. Dành cho test và E2E gọi thẳng tầng nghiệp vụ. */
	public NewSample(String productName, String batchNo, SampleType sampleType, LocalDateTime receivedAt,
			LocalDate dueDate) {
		this(productName, batchNo, sampleType, null, null, receivedAt, dueDate);
	}

}
