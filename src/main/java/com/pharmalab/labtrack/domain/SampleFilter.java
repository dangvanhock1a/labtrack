package com.pharmalab.labtrack.domain;

/**
 * Bộ lọc danh sách mẫu (BR-13). Trường {@code null} nghĩa là không lọc theo trường đó; các trường còn lại kết hợp
 * bằng AND. {@code keyword} là chữ người dùng gõ, chưa escape: {@code LabSampleService.search} mới đổi nó thành mẫu
 * LIKE, để mapper không phải biết quy tắc {@code %} và {@code _}.
 */
public record SampleFilter(String keyword, SampleStatus status, SamplePriority priority, SampleType sampleType) {

	/** Không lọc gì: mọi mẫu. */
	public static final SampleFilter NONE = new SampleFilter(null, null, null, null);

}
