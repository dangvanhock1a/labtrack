package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.StatusChange;
import com.pharmalab.labtrack.persistence.LabSampleMapper;
import com.pharmalab.labtrack.persistence.StatusHistoryMapper;

/** Đổi trạng thái mẫu: nhận kiểm nghiệm (BR-03) và hủy mẫu (BR-05). */
class LabSampleServiceTest {

	/** 09:00 ngày 07/10/2026 giờ VN. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T02:00:00Z"),
			ZoneId.of("Asia/Ho_Chi_Minh"));

	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-07T09:00+07:00");

	private static final long SAMPLE_ID = 42L;

	private final LabSampleMapper sampleMapper = mock(LabSampleMapper.class);

	private final StatusHistoryMapper historyMapper = mock(StatusHistoryMapper.class);

	private final LabSampleService service = new LabSampleService(sampleMapper, historyMapper,
			mock(SampleCodeGenerator.class), CLOCK);

	private void givenSampleIn(SampleStatus status) {
		LabSample sample = new LabSample();
		sample.setId(SAMPLE_ID);
		sample.setCode("LS-2026-00042");
		sample.setStatus(status);
		when(sampleMapper.findById(SAMPLE_ID)).thenReturn(Optional.of(sample));
	}

	@Test
	@DisplayName("TC-BR03-03 Nhận kiểm nghiệm: Đã tiếp nhận → Đang kiểm nghiệm, UPDATE kèm trạng thái cũ")
	void startTestingMovesReceivedSampleToInTesting() {
		givenSampleIn(SampleStatus.RECEIVED);
		when(sampleMapper.updateStatus(any())).thenReturn(1);

		LabSample result = service.startTesting(SAMPLE_ID, "kiemnghiem1");

		assertThat(result.getStatus()).isEqualTo(SampleStatus.IN_TESTING);
		verify(sampleMapper).updateStatus(new StatusChange(SAMPLE_ID, SampleStatus.RECEIVED, SampleStatus.IN_TESTING,
				null, "kiemnghiem1", NOW));
	}

	@Test
	@DisplayName("TC-BR03-04 Nhận kiểm nghiệm mẫu đã Hoàn thành bị chặn và không chạy câu UPDATE")
	void startTestingRejectsCompletedSample() {
		givenSampleIn(SampleStatus.COMPLETED);

		assertThatThrownBy(() -> service.startTesting(SAMPLE_ID, "kiemnghiem1"))
			.isInstanceOf(InvalidStatusTransitionException.class)
			.hasMessage("Không thể chuyển mẫu từ \"Hoàn thành\" sang \"Đang kiểm nghiệm\"");
		verify(sampleMapper, never()).updateStatus(any());
	}

	@Test
	@DisplayName("TC-BR03-05 Người khác vừa đổi trạng thái (UPDATE không khớp dòng nào) thì yêu cầu tải lại trang")
	void reportsConcurrentStatusChange() {
		givenSampleIn(SampleStatus.RECEIVED);
		when(sampleMapper.updateStatus(any())).thenReturn(0);

		assertThatThrownBy(() -> service.startTesting(SAMPLE_ID, "kiemnghiem1"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("CONCURRENT_UPDATE");
				assertThat(ex).hasMessage("Mẫu vừa được người khác cập nhật, hãy tải lại trang");
			});
	}

	@ParameterizedTest(name = "[{index}] lý do = [{0}]")
	@NullSource
	@ValueSource(strings = { "", "     ", "abcd", "  abcd  " })
	@DisplayName("TC-BR05-01 Lý do hủy để trống hoặc dưới 5 ký tự (sau khi cắt khoảng trắng) bị từ chối")
	void cancelRequiresReasonOfAtLeastFiveCharacters(String reason) {
		givenSampleIn(SampleStatus.RECEIVED);

		assertThatThrownBy(() -> service.cancel(SAMPLE_ID, reason, "tiepnhan"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("REASON_REQUIRED");
				assertThat(ex.getField()).isEqualTo("reason");
				assertThat(ex).hasMessage("Lý do phải dài từ 5 đến 500 ký tự");
			});
		verify(sampleMapper, never()).updateStatus(any());
	}

	@Test
	@DisplayName("TC-BR05-02 Hủy mẫu với lý do hợp lệ: chuyển sang Đã hủy, lý do được cắt khoảng trắng hai đầu")
	void cancelMovesSampleToCancelledWithTrimmedReason() {
		givenSampleIn(SampleStatus.RECEIVED);
		when(sampleMapper.updateStatus(any())).thenReturn(1);

		LabSample result = service.cancel(SAMPLE_ID, "  Gửi nhầm mẫu  ", "tiepnhan");

		assertThat(result.getStatus()).isEqualTo(SampleStatus.CANCELLED);
		verify(sampleMapper).updateStatus(new StatusChange(SAMPLE_ID, SampleStatus.RECEIVED, SampleStatus.CANCELLED,
				"Gửi nhầm mẫu", "tiepnhan", NOW));
	}

	@Test
	@DisplayName("TC-BR04-01 Nhận kiểm nghiệm ghi 1 dòng lịch sử cùng người và thời điểm với câu UPDATE")
	void startTestingWritesOneHistoryRow() {
		givenSampleIn(SampleStatus.RECEIVED);
		when(sampleMapper.updateStatus(any())).thenReturn(1);

		service.startTesting(SAMPLE_ID, "kiemnghiem1");

		verify(historyMapper).insert(new StatusChange(SAMPLE_ID, SampleStatus.RECEIVED, SampleStatus.IN_TESTING,
				null, "kiemnghiem1", NOW));
		verifyNoMoreInteractions(historyMapper);
	}

	@Test
	@DisplayName("TC-BR04-02 Hủy mẫu ghi dòng lịch sử kèm lý do đã cắt khoảng trắng hai đầu")
	void cancelWritesHistoryRowWithTrimmedReason() {
		givenSampleIn(SampleStatus.RECEIVED);
		when(sampleMapper.updateStatus(any())).thenReturn(1);

		service.cancel(SAMPLE_ID, "  Gửi nhầm mẫu  ", "tiepnhan");

		verify(historyMapper).insert(new StatusChange(SAMPLE_ID, SampleStatus.RECEIVED, SampleStatus.CANCELLED,
				"Gửi nhầm mẫu", "tiepnhan", NOW));
		verifyNoMoreInteractions(historyMapper);
	}

	@Test
	@DisplayName("TC-BR04-03 Chuyển trạng thái sai bị chặn thì không ghi dòng lịch sử nào")
	void invalidTransitionWritesNoHistoryRow() {
		givenSampleIn(SampleStatus.COMPLETED);

		assertThatThrownBy(() -> service.startTesting(SAMPLE_ID, "kiemnghiem1"))
			.isInstanceOf(InvalidStatusTransitionException.class);
		verifyNoInteractions(historyMapper);
	}

	@Test
	@DisplayName("TC-BR04-04 Người khác vừa đổi trạng thái (UPDATE 0 dòng) thì không ghi dòng lịch sử")
	void concurrentUpdateWritesNoHistoryRow() {
		givenSampleIn(SampleStatus.RECEIVED);
		when(sampleMapper.updateStatus(any())).thenReturn(0);

		assertThatThrownBy(() -> service.startTesting(SAMPLE_ID, "kiemnghiem1"))
			.isInstanceOf(BusinessException.class);
		// Chứng minh câu insert nằm SAU cửa kiểm tra số dòng, không chỉ dựa vào rollback.
		verifyNoInteractions(historyMapper);
	}

}
