package com.pharmalab.labtrack.service;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import com.pharmalab.labtrack.domain.LabSample;

/**
 * Nội dung file CSV danh sách mẫu (BR-16), theo RFC 4180: dấu phẩy, xuống dòng CRLF, ô có dấu phẩy, ngoặc kép hay
 * xuống dòng thì bọc ngoặc kép. BOM ở đầu để Excel nhận ra UTF-8 và hiện đúng tiếng Việt.
 * <p>
 * Chống CSV injection (NFR-02): ô bắt đầu bằng ký tự mà Excel hiểu là công thức được thêm {@code '} phía trước, kể cả
 * số lô như {@code -2601} (câu 10 {@code docs/cau-hoi-lam-ro.md}). Ngoài {@code = + - @} của BR-16 còn có Tab và CR,
 * theo danh sách của OWASP. Thêm {@code '} trước rồi mới bọc ngoặc kép, để ô có cả hai vẫn mở ra đúng chữ.
 * <p>
 * Ngày giờ in cùng định dạng với giao diện ({@code DisplayFormat}, NFR-04) nhưng formatter dựng lại ở đây, vì tầng
 * nghiệp vụ không phụ thuộc tầng web.
 */
final class SampleCsv {

	static final List<String> HEADER = List.of("Mã mẫu", "Sản phẩm", "Số lô", "Loại mẫu", "Ưu tiên", "Bảo quản",
			"Trạng thái", "Ngày nhận", "Hạn trả", "Quá hạn", "Kết luận");

	private static final String BOM = "﻿";

	private static final String CRLF = "\r\n";

	private static final String FORMULA_START = "=+-@\t\r";

	private final DateTimeFormatter dateTime;

	private final DateTimeFormatter date = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	SampleCsv(ZoneId zone) {
		this.dateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zone);
	}

	byte[] write(List<LabSample> samples) {
		StringBuilder csv = new StringBuilder(BOM);
		appendRow(csv, HEADER);
		samples.forEach(sample -> appendRow(csv, row(sample)));
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	/** Một dòng theo thứ tự {@link #HEADER}. Mẫu chưa kết luận để trống ô Kết luận (BR-11). */
	List<String> row(LabSample sample) {
		return Arrays.asList(sample.getCode(), sample.getProductName(), sample.getBatchNo(),
				sample.getSampleType().getLabel(), sample.getPriority().getLabel(),
				sample.getStorageCondition().getLabel(), sample.getStatus().getLabel(),
				dateTime.format(sample.getReceivedAt()), date.format(sample.getDueDate()),
				sample.isOverdue() ? "Có" : "Không",
				(sample.getConclusion() != null) ? sample.getConclusion().getLabel() : "");
	}

	static String cell(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		String safe = (FORMULA_START.indexOf(value.charAt(0)) >= 0) ? "'" + value : value;
		if (safe.contains(",") || safe.contains("\"") || safe.contains("\r") || safe.contains("\n")) {
			return "\"" + safe.replace("\"", "\"\"") + "\"";
		}
		return safe;
	}

	private static void appendRow(StringBuilder csv, List<String> values) {
		csv.append(String.join(",", values.stream().map(SampleCsv::cell).toList())).append(CRLF);
	}

}
