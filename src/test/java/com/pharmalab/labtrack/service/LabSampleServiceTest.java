package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pharmalab.labtrack.config.LabtrackProperties;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.persistence.LabSampleMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabSampleServiceTest {

	// 01:00 ngày 22/10/2026 giờ VN, trong khi theo UTC vẫn là ngày 21/10. Clock để zone UTC
	// để lộ ra code nào lỡ dùng zone của Clock thay vì zone nghiệp vụ.
	private static final Instant NOW = Instant.parse("2026-10-21T18:00:00Z");

	@Mock
	LabSampleMapper sampleMapper;

	@Mock
	SampleCodeGenerator codeGenerator;

	LabSampleService service;

	@BeforeEach
	void setUp() {
		service = new LabSampleService(sampleMapper, codeGenerator, Clock.fixed(NOW, ZoneOffset.UTC),
				new LabtrackProperties(ZoneId.of("Asia/Ho_Chi_Minh"), 20));
	}

	@Test
	@DisplayName("TC-BR02-01 Đăng ký hợp lệ: mã từ generator, trạng thái Đã tiếp nhận, ghi người và thời điểm tạo")
	void registersValidSample() {
		when(codeGenerator.nextCode(any())).thenReturn("LS-2026-00001");

		LabSample result = service.register(command(LocalDateTime.parse("2026-10-22T00:30"), LocalDate.parse("2026-10-29")),
				"demo");

		ArgumentCaptor<LabSample> inserted = ArgumentCaptor.forClass(LabSample.class);
		verify(sampleMapper).insert(inserted.capture());
		LabSample sample = inserted.getValue();
		assertThat(result).isSameAs(sample);
		assertThat(sample.getCode()).isEqualTo("LS-2026-00001");
		assertThat(sample.getStatus()).isEqualTo(SampleStatus.RECEIVED);
		assertThat(sample.getProductName()).isEqualTo("Paracetamol 500 mg");
		assertThat(sample.getBatchNo()).isEqualTo("PA-2601");
		assertThat(sample.getSampleType()).isEqualTo(SampleType.FINISHED_PRODUCT);
		assertThat(sample.getDueDate()).isEqualTo(LocalDate.parse("2026-10-29"));
		assertThat(sample.getCreatedBy()).isEqualTo("demo");
		assertThat(sample.getCreatedAt().toInstant()).isEqualTo(NOW);
		assertThat(sample.getUpdatedAt().toInstant()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("TC-BR02-02 Giờ nhận hiểu theo giờ VN: 00:30 ngày 22/10 là quá khứ, generator nhận đúng thời điểm")
	void interpretsReceivedAtInBusinessZone() {
		when(codeGenerator.nextCode(any())).thenReturn("LS-2026-00001");

		service.register(command(LocalDateTime.parse("2026-10-22T00:30"), LocalDate.parse("2026-10-29")), "demo");

		ArgumentCaptor<OffsetDateTime> receivedAt = ArgumentCaptor.forClass(OffsetDateTime.class);
		verify(codeGenerator).nextCode(receivedAt.capture());
		assertThat(receivedAt.getValue().toInstant()).isEqualTo(Instant.parse("2026-10-21T17:30:00Z"));
	}

	@Test
	@DisplayName("TC-BR02-03 Nhận đúng thời điểm hiện tại thì hợp lệ")
	void acceptsReceivedAtEqualToNow() {
		when(codeGenerator.nextCode(any())).thenReturn("LS-2026-00001");

		service.register(command(LocalDateTime.parse("2026-10-22T01:00"), LocalDate.parse("2026-10-22")), "demo");

		verify(sampleMapper).insert(any());
	}

	@Test
	@DisplayName("TC-BR02-04 Thời điểm nhận ở tương lai bị từ chối, không cấp mã")
	void rejectsReceivedAtInFuture() {
		RegisterSampleCommand command = command(LocalDateTime.parse("2026-10-22T01:01"), LocalDate.parse("2026-10-29"));

		assertThatThrownBy(() -> service.register(command, "demo")).isInstanceOf(BusinessRuleException.class)
			.hasMessage("Thời điểm nhận mẫu không được ở tương lai")
			.extracting("field").isEqualTo("receivedAt");
		verifyNoInteractions(codeGenerator, sampleMapper);
	}

	@Test
	@DisplayName("TC-BR02-05 Hạn trả trùng ngày nhận thì hợp lệ")
	void acceptsDueDateOnReceivedDate() {
		when(codeGenerator.nextCode(any())).thenReturn("LS-2026-00001");

		service.register(command(LocalDateTime.parse("2026-10-22T00:30"), LocalDate.parse("2026-10-22")), "demo");

		verify(sampleMapper).insert(any());
	}

	@Test
	@DisplayName("TC-BR02-06 Hạn trả trước ngày nhận (giờ VN) bị từ chối, không cấp mã")
	void rejectsDueDateBeforeReceivedDate() {
		// Ngày nhận theo giờ VN là 22/10; theo UTC là 21/10 nên so sai zone sẽ để lọt.
		RegisterSampleCommand command = command(LocalDateTime.parse("2026-10-22T00:30"), LocalDate.parse("2026-10-21"));

		assertThatThrownBy(() -> service.register(command, "demo")).isInstanceOf(BusinessRuleException.class)
			.hasMessage("Hạn trả kết quả phải từ ngày nhận mẫu trở đi")
			.extracting("field").isEqualTo("dueDate");
		verifyNoInteractions(codeGenerator, sampleMapper);
	}

	@Test
	@DisplayName("TC-BR02-07 Mặc định: nhận lúc bây giờ theo giờ VN (tròn phút), hạn trả sau 7 ngày")
	void defaultsUseBusinessZone() {
		Clock clock = Clock.fixed(Instant.parse("2026-10-21T18:05:42Z"), ZoneOffset.UTC);
		service = new LabSampleService(sampleMapper, codeGenerator, clock,
				new LabtrackProperties(ZoneId.of("Asia/Ho_Chi_Minh"), 20));

		RegisterSampleCommand defaults = service.newRegistrationDefaults();

		assertThat(defaults.receivedAt()).isEqualTo(LocalDateTime.parse("2026-10-22T01:05"));
		assertThat(defaults.dueDate()).isEqualTo(LocalDate.parse("2026-10-29"));
	}

	private static RegisterSampleCommand command(LocalDateTime receivedAt, LocalDate dueDate) {
		return new RegisterSampleCommand("Paracetamol 500 mg", "PA-2601", SampleType.FINISHED_PRODUCT, receivedAt, dueDate);
	}

}
