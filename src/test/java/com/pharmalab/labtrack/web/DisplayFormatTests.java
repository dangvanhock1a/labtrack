package com.pharmalab.labtrack.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DisplayFormatTests {

	private final DisplayFormat fmt = new DisplayFormat(ZoneId.of("Asia/Ho_Chi_Minh"));

	@Test
	@DisplayName("TC-BR02-11 Thời điểm mang offset UTC (dạng pgjdbc trả) hay Instant đều in theo giờ VN, có và không có giây")
	void instantIsPrintedInAppZone() {
		OffsetDateTime utc = OffsetDateTime.parse("2026-10-07T02:00:05Z");

		assertThat(fmt.dateTime(utc)).isEqualTo("07/10/2026 09:00");
		assertThat(fmt.dateTimeSeconds(utc)).isEqualTo("07/10/2026 09:00:05");
		assertThat(fmt.dateTime(Instant.parse("2026-10-07T02:00:05Z"))).isEqualTo("07/10/2026 09:00");
	}

	@Test
	@DisplayName("TC-BR02-12 Sau nửa đêm giờ VN khi UTC còn hôm trước: ngày và giờ in ra đã sang năm mới")
	void afterMidnightInVietnamIsNextDay() {
		OffsetDateTime utc = OffsetDateTime.parse("2025-12-31T17:30Z");

		assertThat(fmt.dateTime(utc)).isEqualTo("01/01/2026 00:30");
		assertThat(fmt.date(utc)).isEqualTo("01/01/2026");
	}

	@Test
	@DisplayName("TC-BR02-13 Hạn trả kiểu LocalDate in nguyên ngày; giá trị null in ra null")
	void localDateIsPrintedAsIsAndNullStaysNull() {
		assertThat(fmt.date(LocalDate.parse("2026-10-14"))).isEqualTo("14/10/2026");
		assertThat(fmt.dateTime(null)).isNull();
		assertThat(fmt.dateTimeSeconds(null)).isNull();
		assertThat(fmt.date(null)).isNull();
	}

}
