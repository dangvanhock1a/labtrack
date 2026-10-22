package com.pharmalab.labtrack.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SampleStatusTest {

	@DisplayName("TC-BR03-01 Bảng chuyển trạng thái mẫu (16 tổ hợp)")
	@ParameterizedTest(name = "{0} → {1}: {2}")
	@CsvSource({
			"RECEIVED,   RECEIVED,   false",
			"RECEIVED,   IN_TESTING, true",
			"RECEIVED,   COMPLETED,  false",
			"RECEIVED,   CANCELLED,  true",
			"IN_TESTING, RECEIVED,   false",
			"IN_TESTING, IN_TESTING, false",
			"IN_TESTING, COMPLETED,  true",
			"IN_TESTING, CANCELLED,  true",
			"COMPLETED,  RECEIVED,   false",
			"COMPLETED,  IN_TESTING, false",
			"COMPLETED,  COMPLETED,  false",
			"COMPLETED,  CANCELLED,  false",
			"CANCELLED,  RECEIVED,   false",
			"CANCELLED,  IN_TESTING, false",
			"CANCELLED,  COMPLETED,  false",
			"CANCELLED,  CANCELLED,  false"
	})
	void canTransitionTo(SampleStatus from, SampleStatus to, boolean allowed) {
		assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
	}

	@DisplayName("TC-BR03-02 Hoàn thành và Đã hủy là trạng thái kết thúc")
	@ParameterizedTest(name = "{0}: kết thúc = {1}")
	@CsvSource({
			"RECEIVED,   false",
			"IN_TESTING, false",
			"COMPLETED,  true",
			"CANCELLED,  true"
	})
	void isTerminal(SampleStatus status, boolean terminal) {
		assertThat(status.isTerminal()).isEqualTo(terminal);
	}

}
