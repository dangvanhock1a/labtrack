package com.pharmalab.labtrack.domain;

import java.time.OffsetDateTime;

/**
 * Một lần duyệt hoặc từ chối kết quả (BR-08, BR-09): chỉ áp dụng khi kết quả vẫn Chờ duyệt và thuộc đúng
 * {@code sampleId}. Là lệnh ghi như {@link StatusChange}; dòng đọc ra là {@link TestResult}.
 * {@code rejectReason} chỉ có khi {@code toStatus} là Đã từ chối.
 */
public record ResultReview(long resultId, long sampleId, ResultStatus toStatus, String actor,
		OffsetDateTime reviewedAt, String rejectReason) {
}
