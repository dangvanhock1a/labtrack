package com.pharmalab.labtrack.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pharmalab.labtrack.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

// @Transactional là bắt buộc: hai lần gọi phải dùng chung một SqlSession thì mới lộ lỗi cache của MyBatis.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SampleCodeCounterMapperTest {

	@Autowired
	SampleCodeCounterMapper mapper;

	@Test
	@DisplayName("TC-BR01-05 Gọi nextNumber 2 lần trong cùng transaction ra 2 số liên tiếp")
	void nextNumberTwiceInSameTransactionReturnsDistinctNumbers() {
		int first = mapper.nextNumber(2026);
		int second = mapper.nextNumber(2026);

		assertThat(second).isEqualTo(first + 1);
	}

	@Test
	@DisplayName("TC-BR01-06 Mỗi năm đánh số lại từ 1, bộ đếm các năm độc lập")
	void eachYearStartsFromOne() {
		assertThat(mapper.nextNumber(2999)).isEqualTo(1);
		assertThat(mapper.nextNumber(2998)).isEqualTo(1);
		assertThat(mapper.nextNumber(2999)).isEqualTo(2);
	}

}
