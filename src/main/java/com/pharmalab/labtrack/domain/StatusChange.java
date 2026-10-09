package com.pharmalab.labtrack.domain;

import java.time.OffsetDateTime;

/**
 * Một lần chuyển trạng thái mẫu (BR-03): chỉ áp dụng khi mẫu vẫn còn ở {@code fromStatus}.
 * {@code reason} chỉ có khi hủy mẫu (BR-05); cùng người và thời điểm, nó sẽ được ghi vào lịch sử
 * trạng thái ở BR-04.
 */
public record StatusChange(long sampleId, SampleStatus fromStatus, SampleStatus toStatus, String reason,
		String changedBy, OffsetDateTime changedAt) {
}
