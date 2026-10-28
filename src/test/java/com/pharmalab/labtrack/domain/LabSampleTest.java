package com.pharmalab.labtrack.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Quá hạn (BR-14) ở mức domain: không mock, "hôm nay" là tham số nên không phụ thuộc Clock hay múi giờ JVM. */
class LabSampleTest {

	private static final LocalDate TODAY = LocalDate.parse("2026-10-29");

	private static LabSample sample(SampleStatus status, String dueDate) {
		LabSample sample = new LabSample();
		sample.setStatus(status);
		sample.setDueDate(LocalDate.parse(dueDate));
		return sample;
	}

	@ParameterizedTest
	@EnumSource(value = SampleStatus.class, names = { "RECEIVED", "IN_TESTING" })
	@DisplayName("TC-BR14-01 Chưa kết thúc và hạn trả là hôm qua thì quá hạn")
	void dueYesterdayIsOverdue(SampleStatus status) {
		assertThat(sample(status, "2026-10-28").isOverdue(TODAY)).isTrue();
	}

	@ParameterizedTest
	@EnumSource(value = SampleStatus.class, names = { "RECEIVED", "IN_TESTING" })
	@DisplayName("TC-BR14-02 Hạn trả là hôm nay thì chưa quá hạn")
	void dueTodayIsNotOverdue(SampleStatus status) {
		assertThat(sample(status, "2026-10-29").isOverdue(TODAY)).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = SampleStatus.class, names = { "RECEIVED", "IN_TESTING" })
	@DisplayName("TC-BR14-03 Hạn trả là ngày mai thì chưa quá hạn")
	void dueTomorrowIsNotOverdue(SampleStatus status) {
		assertThat(sample(status, "2026-10-30").isOverdue(TODAY)).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = SampleStatus.class, names = { "COMPLETED", "CANCELLED" })
	@DisplayName("TC-BR14-04 Mẫu Hoàn thành hoặc Đã hủy không bao giờ quá hạn, dù hạn trả đã qua rất lâu")
	void finishedSampleIsNeverOverdue(SampleStatus status) {
		assertThat(sample(status, "2026-10-28").isOverdue(TODAY)).isFalse();
		assertThat(sample(status, "2000-01-01").isOverdue(TODAY)).isFalse();
	}

	@Test
	@DisplayName("TC-BR14-05 Ranh giới đổi ngày: cùng hạn trả 2026-10-28, hôm nay 28 thì chưa quá hạn, sang 29 thì quá hạn")
	void overdueFlipsWhenTodayAdvances() {
		LabSample sample = sample(SampleStatus.RECEIVED, "2026-10-28");

		assertThat(sample.isOverdue(LocalDate.parse("2026-10-28"))).isFalse();
		assertThat(sample.isOverdue(LocalDate.parse("2026-10-29"))).isTrue();
	}

}
