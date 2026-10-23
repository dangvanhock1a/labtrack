package com.pharmalab.labtrack.service;

import java.util.List;

import com.pharmalab.labtrack.domain.ResultStatus;
import com.pharmalab.labtrack.domain.TestParameter;
import com.pharmalab.labtrack.domain.TestResult;

/**
 * Một dòng chỉ tiêu trên trang chi tiết mẫu (UC-12): kết quả hiệu lực nếu có, và các bản đã bị từ chối theo
 * thứ tự nhập. Bản bị từ chối vẫn hiện, gạch ngang, vì chúng không bị xóa (BR-09, NFR-03).
 */
public record ResultSheetRow(TestParameter parameter, TestResult effective, List<TestResult> rejected) {

	/** {@code results}: mọi kết quả của mẫu cho chỉ tiêu này, cũ trước mới sau. */
	static ResultSheetRow of(TestParameter parameter, List<TestResult> results) {
		TestResult effective = results.stream().filter(r -> r.getStatus().isEffective()).findFirst().orElse(null);
		List<TestResult> rejected = results.stream().filter(r -> !r.getStatus().isEffective()).toList();
		return new ResultSheetRow(parameter, effective, rejected);
	}

	/**
	 * Tiêu chuẩn để hiển thị: có kết quả hiệu lực thì là bản chụp lúc nhập (BR-07), chưa có thì là tiêu chuẩn
	 * hiện hành, tức tiêu chuẩn mà lần nhập tới sẽ dùng.
	 */
	public String specLabel() {
		return (effective != null) ? effective.spec().label() : parameter.spec().label();
	}

	/**
	 * Còn chờ nhập: chưa có kết quả hiệu lực và chỉ tiêu còn được dùng (BR-06, SPEC-004 R2). Có được nhập hay
	 * không còn tùy trạng thái mẫu và vai trò, do trang chi tiết xét tiếp.
	 */
	public boolean awaitingEntry() {
		return effective == null && parameter.isActive();
	}

	/** Kết quả hiệu lực đang Chờ duyệt. */
	public boolean pending() {
		return effective != null && effective.getStatus() == ResultStatus.PENDING;
	}

}
