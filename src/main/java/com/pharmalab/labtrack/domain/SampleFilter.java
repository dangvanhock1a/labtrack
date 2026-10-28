package com.pharmalab.labtrack.domain;

/**
 * Bộ lọc danh sách mẫu (BR-13). Trường {@code null} nghĩa là không lọc theo trường đó; các trường còn lại kết hợp
 * bằng AND. {@code keyword} là chữ người dùng gõ, chưa escape: {@code LabSampleService.search} mới đổi nó thành mẫu
 * LIKE, để mapper không phải biết quy tắc {@code %} và {@code _}. {@code overdueOnly} (BR-14) chỉ giữ mẫu quá hạn
 * theo ngày {@code LabSampleService} truyền xuống mapper.
 */
public record SampleFilter(String keyword, SampleStatus status, SamplePriority priority, SampleType sampleType,
		boolean overdueOnly) {

	/** Không lọc gì: mọi mẫu. */
	public static final SampleFilter NONE = new SampleFilter(null, null, null, null);

	/** Bộ lọc của BR-13, không lọc quá hạn. */
	public SampleFilter(String keyword, SampleStatus status, SamplePriority priority, SampleType sampleType) {
		this(keyword, status, priority, sampleType, false);
	}

}
