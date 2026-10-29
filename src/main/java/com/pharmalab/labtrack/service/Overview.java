package com.pharmalab.labtrack.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleStatus;

/**
 * Số liệu trang Tổng quan (BR-15). {@code statusCounts} có đủ mọi {@link SampleStatus} theo thứ tự khai báo, trạng thái
 * không có mẫu mang 0, nên template không phải tự lấp. {@code avgTatHours} đã làm tròn 1 chữ số thập phân, là
 * {@code null} khi không có mẫu hoàn thành trong cửa sổ 30 ngày. Mẫu trong {@code attention} mang cờ quá hạn (BR-14).
 */
public record Overview(Map<SampleStatus, Long> statusCounts, long overdueCount, long completedCount,
		BigDecimal avgTatHours, List<LabSample> attention) {
}
