package com.pharmalab.labtrack.service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.pharmalab.labtrack.domain.ResultStatus;
import com.pharmalab.labtrack.domain.TestResult;
import com.pharmalab.labtrack.domain.Verdict;

/**
 * Kết quả của mẫu đã đủ để kết luận chưa, và nếu đủ thì kết luận gì (BR-11). Đây là nơi duy nhất tính điều kiện
 * này: {@link LabSampleService#conclude} dùng nó để chặn, trang chi tiết dùng nó để bật/tắt nút Kết luận mẫu và
 * hiện lý do, nên nút và tầng nghiệp vụ không lệch nhau về điều kiện hay câu chữ.
 * <p>
 * Trạng thái mẫu không xét ở đây: nó thuộc bảng chuyển của {@code SampleStatus} (BR-03). {@code blockCode} và
 * {@code blockReason} để trống khi đủ điều kiện; {@code verdict} để trống khi chưa đủ.
 */
public record ConclusionReadiness(String blockCode, String blockReason, Verdict verdict) {

	/**
	 * {@code results}: kết quả của mẫu, kể cả bản bị từ chối. Chỉ kết quả hiệu lực được tính (LT-BRD mục 5), nên mẫu
	 * chỉ có bản bị từ chối cũng là chưa có kết quả nào.
	 */
	static ConclusionReadiness of(Collection<TestResult> results) {
		List<TestResult> effective = results.stream().filter(r -> r.getStatus().isEffective()).toList();
		if (effective.isEmpty()) {
			return new ConclusionReadiness("NO_RESULTS", "Mẫu chưa có kết quả nào nên chưa thể kết luận", null);
		}
		if (effective.stream().anyMatch(r -> r.getStatus() != ResultStatus.APPROVED)) {
			return new ConclusionReadiness("RESULTS_PENDING", "Còn kết quả chưa được duyệt nên chưa thể kết luận", null);
		}
		boolean allPass = effective.stream().allMatch(r -> r.getVerdict() == Verdict.PASS);
		return new ConclusionReadiness(null, null, allPass ? Verdict.PASS : Verdict.FAIL);
	}

	/**
	 * Cho trang chi tiết, tính trên bảng kết quả đã nạp để không đọc lại CSDL. Ra cùng tập kết quả hiệu lực với
	 * {@link #of} trên mọi kết quả của mẫu, vì {@link TestResultService#findSheet} giữ mọi chỉ tiêu đã có kết quả,
	 * kể cả chỉ tiêu đã ngừng dùng (SPEC-004 R2).
	 */
	public static ConclusionReadiness ofSheet(List<ResultSheetRow> sheet) {
		return of(sheet.stream().map(ResultSheetRow::effective).filter(Objects::nonNull).toList());
	}

	public boolean ready() {
		return blockCode == null;
	}

	/** Chỉ gọi khi chưa đủ điều kiện. Không có field vì lỗi không thuộc ô nhập nào. */
	BusinessException toException() {
		return new BusinessException(blockCode, null, blockReason);
	}

}
