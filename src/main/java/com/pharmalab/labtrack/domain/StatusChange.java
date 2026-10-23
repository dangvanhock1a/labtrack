package com.pharmalab.labtrack.domain;

import java.time.OffsetDateTime;

/**
 * Một lần chuyển trạng thái mẫu (BR-03): chỉ áp dụng khi mẫu vẫn còn ở {@code fromStatus}.
 * Cùng một đối tượng vừa là tham số của câu UPDATE trạng thái, vừa là dòng lịch sử được ghi
 * (BR-04), nên hai bên không thể lệch nhau về người thực hiện hay thời điểm. {@code fromStatus}
 * để trống chỉ ở dòng lúc đăng ký; {@code reason} có khi hủy mẫu (BR-05) và khi đăng ký.
 */
public record StatusChange(long sampleId, SampleStatus fromStatus, SampleStatus toStatus, String reason,
		String changedBy, OffsetDateTime changedAt) {
}
