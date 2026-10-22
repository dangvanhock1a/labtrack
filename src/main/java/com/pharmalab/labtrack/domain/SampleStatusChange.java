package com.pharmalab.labtrack.domain;

import java.time.OffsetDateTime;

/**
 * Một lần đổi trạng thái mẫu: tham số cho {@code UPDATE ... WHERE status = fromStatus} (BR-03),
 * sau này cũng là nội dung một dòng lịch sử trạng thái (BR-04).
 *
 * @param reason lý do đã cắt khoảng trắng, chỉ có khi hủy mẫu (BR-05)
 */
public record SampleStatusChange(long sampleId, SampleStatus fromStatus, SampleStatus toStatus, String reason,
		OffsetDateTime changedAt) {
}
