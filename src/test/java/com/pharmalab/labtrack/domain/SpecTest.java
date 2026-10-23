package com.pharmalab.labtrack.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpecTest {

	@ParameterizedTest(name = "[{index}] {0} ({1} – {2}), đo {3}: {4}")
	@DisplayName("TC-BR07-01 Đánh giá tại biên của 5 chỉ tiêu V4: tính cả biên, so nguyên văn không làm tròn, 105.04 với 95–105 là Không đạt")
	@CsvSource(textBlock = """
			# chỉ tiêu       min,  max,  giá trị,        đánh giá
			ASSAY,           95,   105,  94.9999,        FAIL
			ASSAY,           95,   105,  95,             PASS
			ASSAY,           95,   105,  95.00,          PASS
			ASSAY,           95,   105,  105,            PASS
			ASSAY,           95,   105,  105.00,         PASS
			ASSAY,           95,   105,  105.0001,       FAIL
			ASSAY,           95,   105,  105.04,         FAIL
			PH,              4.5,  7,    4.4999,         FAIL
			PH,              4.5,  7,    4.5,            PASS
			PH,              4.5,  7,    7,              PASS
			PH,              4.5,  7,    7.0001,         FAIL
			LOD,             ,     0.5,  0,              PASS
			LOD,             ,     0.5,  0.5,            PASS
			LOD,             ,     0.5,  0.5001,         FAIL
			DISSOLUTION,     80,   ,     79.9999,        FAIL
			DISSOLUTION,     80,   ,     80,             PASS
			DISSOLUTION,     80,   ,     99999999.9999,  PASS
			IMPURITY_TOTAL,  ,     1.0,  1.0,            PASS
			IMPURITY_TOTAL,  ,     1.0,  1.0001,         FAIL
			""")
	void evaluateAtBoundaries(String parameter, BigDecimal min, BigDecimal max, BigDecimal value, Verdict expected) {
		assertThat(new Spec(min, max).evaluate(value)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "[{index}] {0} – {1}: \"{2}\"")
	@DisplayName("TC-BR07-01 Nhãn tiêu chuẩn: hai biên, chỉ cận trên, chỉ cận dưới; số giữ nguyên cách viết (1.0 không thành 1)")
	@CsvSource(textBlock = """
			95,   105,  95 – 105
			4.5,  7,    4.5 – 7
			,     0.5,  ≤ 0.5
			80,   ,     ≥ 80
			,     1.0,  ≤ 1.0
			""")
	void label(BigDecimal min, BigDecimal max, String expected) {
		assertThat(new Spec(min, max).label()).isEqualTo(expected);
	}

	@Test
	@DisplayName("TC-BR07-01 Tiêu chuẩn không có biên nào bị từ chối, như ck_test_parameter_spec_bound")
	void specWithoutBoundIsRejected() {
		assertThatThrownBy(() -> new Spec(null, null)).isInstanceOf(IllegalArgumentException.class);
	}

}
