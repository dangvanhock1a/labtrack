package com.pharmalab.labtrack.service;

import java.util.List;

import com.pharmalab.labtrack.domain.LabSample;

/**
 * Một trang danh sách mẫu (BR-13). {@code page} đánh số từ 1 và luôn là trang có thật (xem
 * {@link LabSampleService#search}); không có mẫu nào thì trang 1 rỗng và {@link #from()} = {@link #to()} = 0.
 */
public record SamplePage(List<LabSample> samples, int page, int pageSize, long total) {

	/** Số trang, ít nhất 1 kể cả khi không có mẫu nào: trang 1 rỗng vẫn là trang hợp lệ. */
	static int pageCount(long total, int pageSize) {
		return (int) Math.max(1, (total + pageSize - 1) / pageSize);
	}

	/** Số thứ tự (từ 1) của mẫu đầu trang, cho nhãn "Hiển thị a–b / tổng N mẫu". */
	public long from() {
		return (total == 0) ? 0 : (long) (page - 1) * pageSize + 1;
	}

	public long to() {
		return Math.min((long) page * pageSize, total);
	}

	public int totalPages() {
		return pageCount(total, pageSize);
	}

	public boolean hasPrevious() {
		return page > 1;
	}

	public boolean hasNext() {
		return page < totalPages();
	}

}
