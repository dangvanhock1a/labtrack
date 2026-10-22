package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pharmalab.labtrack.config.LabtrackProperties;
import com.pharmalab.labtrack.persistence.SampleCodeCounterMapper;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SampleCodeGeneratorTest {

	private static final OffsetDateTime RECEIVED_2026 = OffsetDateTime.parse("2026-10-22T03:00:00Z");

	@Mock
	SampleCodeCounterMapper counterMapper;

	SampleCodeGenerator generator;

	@BeforeEach
	void setUp() {
		generator = new SampleCodeGenerator(counterMapper, new LabtrackProperties(ZoneId.of("Asia/Ho_Chi_Minh"), 20));
	}

	@Test
	@DisplayName("TC-BR01-01 Số đầu tiên của năm được đệm 0 thành LS-2026-00001")
	void padsNumberWithZeros() {
		when(counterMapper.nextNumber(2026)).thenReturn(1);

		assertThat(generator.nextCode(RECEIVED_2026)).isEqualTo("LS-2026-00001");
	}

	@Test
	@DisplayName("TC-BR01-02 Số 99999 vẫn hợp lệ")
	void acceptsLastNumberOfRange() {
		when(counterMapper.nextNumber(2026)).thenReturn(99_999);

		assertThat(generator.nextCode(RECEIVED_2026)).isEqualTo("LS-2026-99999");
	}

	@Test
	@DisplayName("TC-BR01-03 Vượt 99.999 mẫu/năm thì báo hết dải mã")
	void rejectsNumberBeyondRange() {
		when(counterMapper.nextNumber(2026)).thenReturn(100_000);

		assertThatThrownBy(() -> generator.nextCode(RECEIVED_2026)).isInstanceOf(BusinessRuleException.class)
			.hasMessage("Đã hết dải mã mẫu của năm 2026");
	}

	@Test
	@DisplayName("TC-BR01-04a Nhận lúc 23:59:59 ngày 31/12 giờ VN vẫn thuộc năm cũ")
	void lastSecondOfYearInVietnam() {
		when(counterMapper.nextNumber(2026)).thenReturn(7);

		assertThat(generator.nextCode(OffsetDateTime.parse("2026-12-31T16:59:59Z"))).isEqualTo("LS-2026-00007");
		verify(counterMapper).nextNumber(2026);
	}

	@Test
	@DisplayName("TC-BR01-04b Nhận lúc 00:00 ngày 01/01 giờ VN (17:00Z hôm trước) đã sang năm mới")
	void firstSecondOfYearInVietnam() {
		when(counterMapper.nextNumber(2027)).thenReturn(1);

		assertThat(generator.nextCode(OffsetDateTime.parse("2026-12-31T17:00:00Z"))).isEqualTo("LS-2027-00001");
		verify(counterMapper).nextNumber(2027);
	}

}
