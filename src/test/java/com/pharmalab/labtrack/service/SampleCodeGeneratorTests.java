package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pharmalab.labtrack.persistence.SampleCodeCounterMapper;

class SampleCodeGeneratorTests {

	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

	private final SampleCodeCounterMapper counterMapper = mock(SampleCodeCounterMapper.class);

	private final SampleCodeGenerator generator = new SampleCodeGenerator(counterMapper,
			Clock.fixed(Instant.parse("2026-10-09T03:00:00Z"), VN));

	@Test
	@DisplayName("TC-BR01-01 Mã có dạng LS-YYYY-NNNNN, số thứ tự đệm số 0")
	void padsNumberWithZeros() {
		when(counterMapper.nextNumber(2026)).thenReturn(7);

		assertThat(generator.nextCode(OffsetDateTime.parse("2026-10-09T10:00:00+07:00"))).isEqualTo("LS-2026-00007");
	}

	@Test
	@DisplayName("TC-BR01-02 Số 99999 vẫn hợp lệ")
	void acceptsLastNumberOfYear() {
		when(counterMapper.nextNumber(2026)).thenReturn(99_999);

		assertThat(generator.nextCode(OffsetDateTime.parse("2026-10-09T10:00:00+07:00"))).isEqualTo("LS-2026-99999");
	}

	@Test
	@DisplayName("TC-BR01-03 Vượt 99999 mẫu/năm thì báo hết dải mã")
	void rejectsNumberBeyondRange() {
		when(counterMapper.nextNumber(2026)).thenReturn(100_000);

		assertThatThrownBy(() -> generator.nextCode(OffsetDateTime.parse("2026-10-09T10:00:00+07:00")))
			.isInstanceOf(BusinessException.class)
			.hasMessage("Đã hết dải mã mẫu của năm 2026");
	}

	@Test
	@DisplayName("TC-BR01-04 Năm lấy theo giờ VN: 17:30 UTC ngày 31/12 đã là năm sau")
	void usesVietnamYearAfterMidnight() {
		when(counterMapper.nextNumber(2027)).thenReturn(1);

		assertThat(generator.nextCode(OffsetDateTime.parse("2026-12-31T17:30:00Z"))).isEqualTo("LS-2027-00001");
		verify(counterMapper).nextNumber(2027);
	}

	@Test
	@DisplayName("TC-BR01-05 Năm lấy theo giờ VN: 16:59 UTC ngày 31/12 vẫn là năm cũ")
	void usesVietnamYearBeforeMidnight() {
		when(counterMapper.nextNumber(2026)).thenReturn(1);

		assertThat(generator.nextCode(OffsetDateTime.parse("2026-12-31T16:59:00Z"))).isEqualTo("LS-2026-00001");
		verify(counterMapper).nextNumber(2026);
	}

}
