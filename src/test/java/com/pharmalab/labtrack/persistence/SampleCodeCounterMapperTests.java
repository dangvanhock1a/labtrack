package com.pharmalab.labtrack.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SampleCodeCounterMapperTests {

	@Autowired
	private SampleCodeCounterMapper counterMapper;

	@Test
	@DisplayName("TC-BR01-06 Gọi nextNumber 2 lần trong cùng transaction ra 2 số liên tiếp")
	void nextNumberTwiceInSameTransactionReturnsConsecutiveNumbers() {
		int first = counterMapper.nextNumber(2026);
		int second = counterMapper.nextNumber(2026);

		assertThat(second).isEqualTo(first + 1);
	}

}
