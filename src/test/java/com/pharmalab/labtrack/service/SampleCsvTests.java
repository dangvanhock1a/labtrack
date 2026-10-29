package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.StorageCondition;
import com.pharmalab.labtrack.domain.Verdict;

/** Nội dung file CSV (BR-16): BOM, tiêu đề, quy tắc RFC 4180, chống CSV injection (NFR-02), ngày giờ theo giờ VN. */
class SampleCsvTests {

	private final SampleCsv csv = new SampleCsv(ZoneId.of("Asia/Ho_Chi_Minh"));

	/** Mẫu Khẩn, Mát, đang kiểm nghiệm; nhận 00:30 ngày 10/10 giờ VN, mang offset UTC như pgjdbc trả về. */
	private static LabSample sample() {
		LabSample sample = new LabSample();
		sample.setCode("LS-2026-00042");
		sample.setProductName("Paracetamol 500 mg");
		sample.setBatchNo("PA-2601");
		sample.setSampleType(SampleType.FINISHED_PRODUCT);
		sample.setPriority(SamplePriority.URGENT);
		sample.setStorageCondition(StorageCondition.COOL);
		sample.setStatus(SampleStatus.IN_TESTING);
		sample.setReceivedAt(OffsetDateTime.parse("2026-10-09T17:30Z"));
		sample.setDueDate(LocalDate.parse("2026-10-17"));
		return sample;
	}

	private static String text(byte[] content) {
		return new String(content, StandardCharsets.UTF_8);
	}

	@Test
	@DisplayName("TC-BR16-01 File mở đầu bằng BOM UTF-8, dòng đầu là đúng 11 tiêu đề theo thứ tự BR-16, mỗi dòng kết thúc bằng CRLF; không có mẫu thì chỉ có dòng tiêu đề")
	void emptyExportHasBomAndHeaderOnly() {
		byte[] content = csv.write(List.of());

		assertThat(content).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
		assertThat(text(content)).isEqualTo("﻿Mã mẫu,Sản phẩm,Số lô,Loại mẫu,Ưu tiên,Bảo quản,Trạng thái,"
				+ "Ngày nhận,Hạn trả,Quá hạn,Kết luận\r\n");
	}

	static Stream<Arguments> formulaCells() {
		return Stream.of(
				Arguments.of("=1+1", "'=1+1"),
				Arguments.of("+84 912 345 678", "'+84 912 345 678"),
				// Số lô hợp lệ theo BR-02; câu 10 docs/cau-hoi-lam-ro.md chấp nhận nó thành '-2601.
				Arguments.of("-2601", "'-2601"),
				Arguments.of("@SUM(A1:A9)", "'@SUM(A1:A9)"),
				Arguments.of("\tcmd", "'\tcmd"),
				Arguments.of("PA-2601", "PA-2601"),
				Arguments.of("Paracetamol 500 mg", "Paracetamol 500 mg"),
				Arguments.of("", ""),
				Arguments.of(null, ""));
	}

	@ParameterizedTest(name = "[{index}] [{0}] → [{1}]")
	@MethodSource("formulaCells")
	@DisplayName("TC-BR16-02 Ô bắt đầu bằng = + - @ hoặc Tab được thêm ' phía trước; ký tự đó ở giữa ô thì giữ nguyên")
	void formulaCellsArePrefixed(String value, String expected) {
		assertThat(SampleCsv.cell(value)).isEqualTo(expected);
	}

	static Stream<Arguments> quotedCells() {
		return Stream.of(
				Arguments.of("Vitamin C, 500 mg", "\"Vitamin C, 500 mg\""),
				Arguments.of("Viên \"bao phim\"", "\"Viên \"\"bao phim\"\"\""),
				Arguments.of("Dòng 1\nDòng 2", "\"Dòng 1\nDòng 2\""),
				Arguments.of("Dòng 1\r\nDòng 2", "\"Dòng 1\r\nDòng 2\""),
				Arguments.of("=A1,B1", "\"'=A1,B1\""),
				Arguments.of("\r=1", "\"'\r=1\""));
	}

	@ParameterizedTest(name = "[{index}] [{0}] → [{1}]")
	@MethodSource("quotedCells")
	@DisplayName("TC-BR16-03 Ô có dấu phẩy, ngoặc kép, CR hoặc LF được bọc ngoặc kép, ngoặc kép bên trong nhân đôi; ô vừa là công thức vừa cần bọc thì thêm ' rồi mới bọc")
	void specialCellsAreQuoted(String value, String expected) {
		assertThat(SampleCsv.cell(value)).isEqualTo(expected);
	}

	@Test
	@DisplayName("TC-BR16-04 Mỗi mẫu một dòng theo thứ tự đưa vào: nhãn tiếng Việt, Ngày nhận theo giờ VN (17:30 UTC → 00:30 hôm sau), Hạn trả dd/MM/yyyy, Quá hạn Có/Không, Kết luận trống hoặc Đạt")
	void rowsUseLabelsAndVietnamTime() {
		LabSample overdue = sample();
		overdue.setOverdue(true);
		LabSample completed = sample();
		completed.setCode("LS-2026-00043");
		completed.setPriority(SamplePriority.NORMAL);
		completed.setStorageCondition(StorageCondition.FROZEN);
		completed.setStatus(SampleStatus.COMPLETED);
		completed.setConclusion(Verdict.PASS);

		String[] lines = text(csv.write(List.of(overdue, completed))).split("\r\n");

		assertThat(lines).hasSize(3);
		assertThat(lines[1]).isEqualTo("LS-2026-00042,Paracetamol 500 mg,PA-2601,Thành phẩm,Khẩn,Mát (2–8 °C),"
				+ "Đang kiểm nghiệm,10/10/2026 00:30,17/10/2026,Có,");
		assertThat(lines[2]).isEqualTo("LS-2026-00043,Paracetamol 500 mg,PA-2601,Thành phẩm,Bình thường,"
				+ "Đông lạnh (≤ −20 °C),Hoàn thành,10/10/2026 00:30,17/10/2026,Không,Đạt");
	}

}
