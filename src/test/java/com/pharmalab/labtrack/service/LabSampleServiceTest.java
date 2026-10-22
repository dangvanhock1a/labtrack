package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pharmalab.labtrack.config.LabtrackProperties;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleStatusChange;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.persistence.LabSampleMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
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

	@Nested
	class StatusChange {

		// 09:00 ngày 07/10/2026 giờ VN.
		static final Instant CHANGED_AT = Instant.parse("2026-10-07T02:00:00Z");
		static final long SAMPLE_ID = 1L;

		@BeforeEach
		void useStatusClock() {
			service = new LabSampleService(sampleMapper, codeGenerator,
					Clock.fixed(CHANGED_AT, ZoneId.of("Asia/Ho_Chi_Minh")),
					new LabtrackProperties(ZoneId.of("Asia/Ho_Chi_Minh"), 20));
		}

		@Test
		@DisplayName("TC-BR03-03 Nhận kiểm nghiệm: Đã tiếp nhận → Đang kiểm nghiệm, UPDATE có điều kiện trạng thái cũ")
		void startTestingFromReceived() {
			givenSampleWithStatus(SampleStatus.RECEIVED);
			when(sampleMapper.updateStatus(any())).thenReturn(1);

			service.startTesting(SAMPLE_ID);

			SampleStatusChange change = capturedStatusChange();
			assertThat(change.sampleId()).isEqualTo(SAMPLE_ID);
			assertThat(change.fromStatus()).isEqualTo(SampleStatus.RECEIVED);
			assertThat(change.toStatus()).isEqualTo(SampleStatus.IN_TESTING);
			assertThat(change.reason()).isNull();
			assertThat(change.changedAt().toInstant()).isEqualTo(CHANGED_AT);
		}

		@Test
		@DisplayName("TC-BR03-04 Nhận kiểm nghiệm mẫu đã Hoàn thành bị chặn, không ghi CSDL")
		void startTestingFromCompletedIsRejected() {
			givenSampleWithStatus(SampleStatus.COMPLETED);

			assertThatThrownBy(() -> service.startTesting(SAMPLE_ID))
				.isInstanceOf(InvalidStatusTransitionException.class)
				.hasMessage("Không thể chuyển mẫu từ \"Hoàn thành\" sang \"Đang kiểm nghiệm\"");
			verify(sampleMapper, never()).updateStatus(any());
		}

		@Test
		@DisplayName("TC-BR03-05 Người khác vừa đổi trạng thái (UPDATE trả 0 dòng) → CONCURRENT_UPDATE")
		void concurrentUpdateIsReported() {
			givenSampleWithStatus(SampleStatus.RECEIVED);
			when(sampleMapper.updateStatus(any())).thenReturn(0);

			assertThatThrownBy(() -> service.startTesting(SAMPLE_ID)).isInstanceOf(BusinessRuleException.class)
				.hasMessage("Mẫu vừa được người khác cập nhật, hãy tải lại trang")
				.extracting("code").isEqualTo("CONCURRENT_UPDATE");
		}

		@Test
		@DisplayName("TC-BR03-06 Đổi trạng thái mẫu không tồn tại → SAMPLE_NOT_FOUND, không ghi CSDL")
		void missingSampleIsReported() {
			when(sampleMapper.findById(SAMPLE_ID)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.startTesting(SAMPLE_ID)).isInstanceOf(BusinessRuleException.class)
				.hasMessage("Không tìm thấy mẫu")
				.extracting("code").isEqualTo("SAMPLE_NOT_FOUND");
			verify(sampleMapper, never()).updateStatus(any());
		}

		@ParameterizedTest(name = "lý do = [{0}]")
		@NullSource
		@ValueSource(strings = { "", "    ", "abcd", "  abc  " })
		@DisplayName("TC-BR05-01 Hủy mẫu thiếu lý do hoặc lý do dưới 5 ký tự (sau khi cắt khoảng trắng) → REASON_REQUIRED")
		void cancelWithMissingOrShortReasonIsRejected(String reason) {
			assertThatThrownBy(() -> service.cancel(SAMPLE_ID, reason)).isInstanceOf(BusinessRuleException.class)
				.hasMessage("Lý do phải dài từ 5 đến 500 ký tự")
				.extracting("code", "field").containsExactly("REASON_REQUIRED", "reason");
			verifyNoInteractions(sampleMapper);
		}

		@Test
		@DisplayName("TC-BR05-02 Hủy mẫu hợp lệ → Đã hủy, lý do được cắt khoảng trắng đầu/cuối")
		void cancelWithValidReason() {
			givenSampleWithStatus(SampleStatus.RECEIVED);
			when(sampleMapper.updateStatus(any())).thenReturn(1);

			service.cancel(SAMPLE_ID, "   Gửi nhầm mẫu   ");

			SampleStatusChange change = capturedStatusChange();
			assertThat(change.fromStatus()).isEqualTo(SampleStatus.RECEIVED);
			assertThat(change.toStatus()).isEqualTo(SampleStatus.CANCELLED);
			assertThat(change.reason()).isEqualTo("Gửi nhầm mẫu");
			assertThat(change.changedAt().toInstant()).isEqualTo(CHANGED_AT);
		}

		@ParameterizedTest(name = "{0} ký tự")
		@ValueSource(ints = { 5, 500 })
		@DisplayName("TC-BR05-03 Lý do đúng 5 hoặc 500 ký tự thì hợp lệ")
		void cancelAcceptsReasonLengthBoundaries(int length) {
			givenSampleWithStatus(SampleStatus.IN_TESTING);
			when(sampleMapper.updateStatus(any())).thenReturn(1);

			service.cancel(SAMPLE_ID, "x".repeat(length));

			assertThat(capturedStatusChange().reason()).hasSize(length);
		}

		@Test
		@DisplayName("TC-BR05-04 Lý do 501 ký tự → REASON_REQUIRED")
		void cancelRejectsTooLongReason() {
			assertThatThrownBy(() -> service.cancel(SAMPLE_ID, "x".repeat(501)))
				.isInstanceOf(BusinessRuleException.class)
				.extracting("code").isEqualTo("REASON_REQUIRED");
			verifyNoInteractions(sampleMapper);
		}

		private void givenSampleWithStatus(SampleStatus status) {
			LabSample sample = new LabSample();
			sample.setId(SAMPLE_ID);
			sample.setCode("LS-2026-00001");
			sample.setStatus(status);
			when(sampleMapper.findById(SAMPLE_ID)).thenReturn(Optional.of(sample));
		}

		private SampleStatusChange capturedStatusChange() {
			ArgumentCaptor<SampleStatusChange> change = ArgumentCaptor.forClass(SampleStatusChange.class);
			verify(sampleMapper).updateStatus(change.capture());
			return change.getValue();
		}

	}

	private static RegisterSampleCommand command(LocalDateTime receivedAt, LocalDate dueDate) {
		return new RegisterSampleCommand("Paracetamol 500 mg", "PA-2601", SampleType.FINISHED_PRODUCT, receivedAt, dueDate);
	}

}
