package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.StatusChange;
import com.pharmalab.labtrack.domain.StatusHistory;
import com.pharmalab.labtrack.persistence.LabSampleMapper;
import com.pharmalab.labtrack.persistence.StatusHistoryMapper;
import com.pharmalab.labtrack.persistence.TestResultMapper;

class LabSampleServiceTests {

	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

	/** 10:00 ngày 09/10/2026 giờ VN. */
	private static final Clock MORNING = Clock.fixed(Instant.parse("2026-10-09T03:00:00Z"), VN);

	/** 00:30 ngày 10/10/2026 giờ VN, khi UTC vẫn là ngày 09/10. */
	private static final Clock AFTER_MIDNIGHT = Clock.fixed(Instant.parse("2026-10-09T17:30:00Z"), VN);

	private static final long NEW_SAMPLE_ID = 1L;

	private final LabSampleMapper sampleMapper = mock(LabSampleMapper.class);

	private final StatusHistoryMapper historyMapper = mock(StatusHistoryMapper.class);

	private final SampleCodeGenerator codeGenerator = mock(SampleCodeGenerator.class);

	private LabSampleService serviceAt(Clock clock) {
		when(codeGenerator.nextCode(any())).thenReturn("LS-2026-00001");
		// MyBatis gán khóa do CSDL sinh vào sample.id; mock phải làm thay, nếu không dòng lịch sử
		// lúc đăng ký không có sample_id để tham chiếu (BR-04).
		doAnswer(call -> {
			call.getArgument(0, LabSample.class).setId(NEW_SAMPLE_ID);
			return null;
		}).when(sampleMapper).insert(any());
		return new LabSampleService(sampleMapper, historyMapper, mock(TestResultMapper.class), codeGenerator, clock);
	}

	private static NewSample newSample(LocalDateTime receivedAt, LocalDate dueDate) {
		return new NewSample("Paracetamol 500 mg", "PA-2601", SampleType.FINISHED_PRODUCT, receivedAt, dueDate);
	}

	private LabSample savedSample() {
		ArgumentCaptor<LabSample> captor = ArgumentCaptor.forClass(LabSample.class);
		verify(sampleMapper).insert(captor.capture());
		return captor.getValue();
	}

	@Test
	@DisplayName("TC-BR02-01 Form mới điền sẵn thời điểm nhận là bây giờ, hạn trả là ngày nhận + 7")
	void defaultsAreNowAndSevenDaysLater() {
		NewSample defaults = serviceAt(MORNING).newSampleDefaults();

		assertThat(defaults.receivedAt()).isEqualTo(LocalDateTime.parse("2026-10-09T10:00"));
		assertThat(defaults.dueDate()).isEqualTo(LocalDate.parse("2026-10-16"));
	}

	@Test
	@DisplayName("TC-BR02-02 Để trống thời điểm nhận và hạn trả thì dùng giá trị mặc định")
	void blankTimesFallBackToDefaults() {
		serviceAt(MORNING).register(newSample(null, null), "demo");

		LabSample saved = savedSample();
		assertThat(saved.getReceivedAt()).isEqualTo(OffsetDateTime.parse("2026-10-09T10:00+07:00"));
		assertThat(saved.getDueDate()).isEqualTo(LocalDate.parse("2026-10-16"));
	}

	@Test
	@DisplayName("TC-BR02-03 Thời điểm nhận ở tương lai bị từ chối và không cấp mã")
	void rejectsReceivedAtInFuture() {
		LabSampleService service = serviceAt(MORNING);

		assertThatThrownBy(() -> service.register(newSample(LocalDateTime.parse("2026-10-09T10:01"), null), "demo"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getField()).isEqualTo("receivedAt");
				assertThat(ex).hasMessage("Thời điểm nhận mẫu không được ở tương lai");
			});
		verifyNoInteractions(codeGenerator, sampleMapper, historyMapper);
	}

	@Test
	@DisplayName("TC-BR02-04 Hạn trả trùng ngày nhận là hợp lệ")
	void acceptsDueDateOnReceivedDate() {
		serviceAt(MORNING).register(
				newSample(LocalDateTime.parse("2026-10-09T08:00"), LocalDate.parse("2026-10-09")), "demo");

		assertThat(savedSample().getDueDate()).isEqualTo(LocalDate.parse("2026-10-09"));
	}

	@Test
	@DisplayName("TC-BR02-05 Hạn trả trước ngày nhận bị từ chối")
	void rejectsDueDateBeforeReceivedDate() {
		LabSampleService service = serviceAt(MORNING);

		assertThatThrownBy(() -> service.register(
				newSample(LocalDateTime.parse("2026-10-09T08:00"), LocalDate.parse("2026-10-08")), "demo"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getField()).isEqualTo("dueDate");
				assertThat(ex).hasMessage("Hạn trả kết quả phải từ ngày nhận mẫu trở đi");
			});
		verifyNoInteractions(codeGenerator, sampleMapper, historyMapper);
	}

	@Test
	@DisplayName("TC-BR02-06 Sau nửa đêm giờ VN (UTC còn hôm trước): nhận 00:10, hạn trả hôm nay đều hợp lệ")
	void usesVietnamDateAfterMidnight() {
		serviceAt(AFTER_MIDNIGHT).register(
				newSample(LocalDateTime.parse("2026-10-10T00:10"), LocalDate.parse("2026-10-10")), "demo");

		LabSample saved = savedSample();
		assertThat(saved.getReceivedAt().toInstant()).isEqualTo(Instant.parse("2026-10-09T17:10:00Z"));
		assertThat(saved.getDueDate()).isEqualTo(LocalDate.parse("2026-10-10"));
	}

	@Test
	@DisplayName("TC-BR02-07 Mẫu mới ở trạng thái Đã tiếp nhận, ghi người tạo và mã được cấp")
	void savesReceivedSampleWithGeneratedCode() {
		LabSample result = serviceAt(MORNING).register(
				newSample(LocalDateTime.parse("2026-10-09T09:30"), LocalDate.parse("2026-10-12")), "demo");

		LabSample saved = savedSample();
		assertThat(result).isSameAs(saved);
		assertThat(saved.getCode()).isEqualTo("LS-2026-00001");
		assertThat(saved.getStatus()).isEqualTo(SampleStatus.RECEIVED);
		assertThat(saved.getCreatedBy()).isEqualTo("demo");
		assertThat(saved.getProductName()).isEqualTo("Paracetamol 500 mg");
		assertThat(saved.getBatchNo()).isEqualTo("PA-2601");
		assertThat(saved.getSampleType()).isEqualTo(SampleType.FINISHED_PRODUCT);
		verify(codeGenerator).nextCode(OffsetDateTime.parse("2026-10-09T09:30+07:00"));
	}

	@Test
	@DisplayName("TC-BR13-04 Form mới chọn sẵn Bình thường; đăng ký không chọn mức ưu tiên thì lưu Bình thường")
	void priorityDefaultsToNormal() {
		LabSampleService service = serviceAt(MORNING);

		assertThat(service.newSampleDefaults().priority()).isEqualTo(SamplePriority.NORMAL);
		service.register(newSample(null, null), "demo");
		assertThat(savedSample().getPriority()).isEqualTo(SamplePriority.NORMAL);
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@EnumSource(SamplePriority.class)
	@DisplayName("TC-BR13-05 Mức ưu tiên đã chọn được lưu nguyên")
	void savesChosenPriority(SamplePriority priority) {
		serviceAt(MORNING).register(new NewSample("Paracetamol 500 mg", "PA-2601", SampleType.FINISHED_PRODUCT,
				priority, null, null), "demo");

		assertThat(savedSample().getPriority()).isEqualTo(priority);
	}

	@Test
	@DisplayName("TC-BR04-05 Đăng ký mẫu ghi đúng 1 dòng lịch sử Tạo mới → Đã tiếp nhận, lý do \"Đăng ký mẫu\"")
	void registerWritesCreationHistoryRow() {
		serviceAt(MORNING).register(
				newSample(LocalDateTime.parse("2026-10-09T09:30"), LocalDate.parse("2026-10-12")), "demo");

		// Thời điểm lấy từ Clock (10:00), không phải thời điểm nhận mẫu nhập lùi (09:30): lần đổi
		// trạng thái này xảy ra đúng lúc bấm đăng ký.
		verify(historyMapper).insert(new StatusChange(NEW_SAMPLE_ID, null, SampleStatus.RECEIVED, "Đăng ký mẫu",
				"demo", OffsetDateTime.parse("2026-10-09T10:00+07:00")));
		verifyNoMoreInteractions(historyMapper);
	}

	@Test
	@DisplayName("TC-BR04-06 Lịch sử đọc ra được đổi về múi giờ ứng dụng")
	void statusHistoryIsReturnedInClockZone() {
		StatusHistory row = new StatusHistory();
		row.setChangedAt(OffsetDateTime.parse("2026-10-09T03:00Z"));
		when(historyMapper.findBySampleId(NEW_SAMPLE_ID)).thenReturn(new ArrayList<>(List.of(row)));

		List<StatusHistory> history = serviceAt(MORNING).findStatusHistory(NEW_SAMPLE_ID);

		assertThat(history).singleElement()
			.extracting(StatusHistory::getChangedAt)
			.isEqualTo(OffsetDateTime.parse("2026-10-09T10:00+07:00"));
	}

}
