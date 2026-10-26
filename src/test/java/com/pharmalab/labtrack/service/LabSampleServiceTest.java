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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.ResultStatus;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.StatusChange;
import com.pharmalab.labtrack.domain.TestResult;
import com.pharmalab.labtrack.domain.Verdict;
import com.pharmalab.labtrack.persistence.LabSampleMapper;
import com.pharmalab.labtrack.persistence.StatusHistoryMapper;
import com.pharmalab.labtrack.persistence.TestResultMapper;

/** Đổi trạng thái mẫu: nhận kiểm nghiệm (BR-03), hủy mẫu (BR-05) và kết luận mẫu (BR-11). */
class LabSampleServiceTest {

	/** 09:00 ngày 07/10/2026 giờ VN. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T02:00:00Z"),
			ZoneId.of("Asia/Ho_Chi_Minh"));

	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-07T09:00+07:00");

	private static final long SAMPLE_ID = 42L;

	private final LabSampleMapper sampleMapper = mock(LabSampleMapper.class);

	private final StatusHistoryMapper historyMapper = mock(StatusHistoryMapper.class);

	private final TestResultMapper resultMapper = mock(TestResultMapper.class);

	private final LabSampleService service = new LabSampleService(sampleMapper, historyMapper, resultMapper,
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

	// ---- Kết luận mẫu (BR-11) ----

	/** Kết luận khóa dòng mẫu bằng lockById (SPEC-004 R14), nên mock findById không đủ để có mẫu. */
	private void givenLockedSampleIn(SampleStatus status) {
		LabSample sample = new LabSample();
		sample.setId(SAMPLE_ID);
		sample.setCode("LS-2026-00042");
		sample.setStatus(status);
		when(sampleMapper.lockById(SAMPLE_ID)).thenReturn(Optional.of(sample));
	}

	private void givenResults(List<TestResult> results) {
		when(resultMapper.findBySample(SAMPLE_ID)).thenReturn(results);
	}

	private static TestResult result(ResultStatus status, Verdict verdict) {
		TestResult result = new TestResult();
		result.setSampleId(SAMPLE_ID);
		result.setStatus(status);
		result.setVerdict(verdict);
		return result;
	}

	private static StatusChange conclusionChange(String reason) {
		return new StatusChange(SAMPLE_ID, SampleStatus.IN_TESTING, SampleStatus.COMPLETED, reason, "duyet", NOW);
	}

	@ParameterizedTest(name = "[{index}] số bản bị từ chối = {0}")
	@ValueSource(ints = { 0, 2 })
	@DisplayName("TC-BR11-01 Chưa có kết quả hiệu lực nào (không có kết quả, hoặc chỉ có bản bị từ chối) thì chưa kết luận được")
	void concludeRequiresAnEffectiveResult(int rejectedCount) {
		givenLockedSampleIn(SampleStatus.IN_TESTING);
		givenResults(Collections.nCopies(rejectedCount, result(ResultStatus.REJECTED, Verdict.PASS)));

		assertThatThrownBy(() -> service.conclude(SAMPLE_ID, "duyet"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("NO_RESULTS");
				assertThat(ex.getField()).isNull();
				assertThat(ex).hasMessage("Mẫu chưa có kết quả nào nên chưa thể kết luận");
			});
		verify(sampleMapper, never()).conclude(any(), any());
		verifyNoInteractions(historyMapper);
	}

	@Test
	@DisplayName("TC-BR11-02 Còn kết quả hiệu lực Chờ duyệt thì chưa kết luận được, dù các kết quả khác đã duyệt")
	void concludeRequiresEveryEffectiveResultApproved() {
		givenLockedSampleIn(SampleStatus.IN_TESTING);
		givenResults(List.of(result(ResultStatus.APPROVED, Verdict.PASS), result(ResultStatus.PENDING, Verdict.PASS)));

		assertThatThrownBy(() -> service.conclude(SAMPLE_ID, "duyet"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("RESULTS_PENDING");
				assertThat(ex).hasMessage("Còn kết quả chưa được duyệt nên chưa thể kết luận");
			});
		verify(sampleMapper, never()).conclude(any(), any());
		verifyNoInteractions(historyMapper);
	}

	@Test
	@DisplayName("TC-BR11-03 Mọi kết quả hiệu lực Đạt thì kết luận Đạt, bản bị từ chối Không đạt không tính; UPDATE và dòng lịch sử dùng cùng một lần chuyển")
	void concludePassIgnoringRejectedResults() {
		givenLockedSampleIn(SampleStatus.IN_TESTING);
		givenResults(List.of(result(ResultStatus.REJECTED, Verdict.FAIL), result(ResultStatus.APPROVED, Verdict.PASS),
				result(ResultStatus.APPROVED, Verdict.PASS)));
		when(sampleMapper.conclude(any(), any())).thenReturn(1);

		LabSample concluded = service.conclude(SAMPLE_ID, "duyet");

		StatusChange change = conclusionChange("Kết luận: Đạt");
		verify(sampleMapper).conclude(change, Verdict.PASS);
		verify(historyMapper).insert(change);
		verifyNoMoreInteractions(historyMapper);
		assertThat(concluded.getStatus()).isEqualTo(SampleStatus.COMPLETED);
		assertThat(concluded.getConclusion()).isEqualTo(Verdict.PASS);
		assertThat(concluded.getCompletedAt()).isEqualTo(NOW);
		assertThat(concluded.getUpdatedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("TC-BR11-04 Có một kết quả hiệu lực Không đạt thì kết luận Không đạt")
	void concludeFailWhenAnyEffectiveResultFails() {
		givenLockedSampleIn(SampleStatus.IN_TESTING);
		givenResults(List.of(result(ResultStatus.APPROVED, Verdict.PASS), result(ResultStatus.APPROVED, Verdict.FAIL)));
		when(sampleMapper.conclude(any(), any())).thenReturn(1);

		LabSample concluded = service.conclude(SAMPLE_ID, "duyet");

		StatusChange change = conclusionChange("Kết luận: Không đạt");
		verify(sampleMapper).conclude(change, Verdict.FAIL);
		verify(historyMapper).insert(change);
		assertThat(concluded.getConclusion()).isEqualTo(Verdict.FAIL);
	}

	@Test
	@DisplayName("TC-BR11-05 Mẫu chưa nhận kiểm nghiệm thì không kết luận được: báo theo bảng chuyển BR-03, không đọc kết quả")
	void concludeRejectsSampleNotInTesting() {
		givenLockedSampleIn(SampleStatus.RECEIVED);

		assertThatThrownBy(() -> service.conclude(SAMPLE_ID, "duyet"))
			.isInstanceOf(InvalidStatusTransitionException.class)
			.hasMessage("Không thể chuyển mẫu từ \"Đã tiếp nhận\" sang \"Hoàn thành\"");
		verifyNoInteractions(resultMapper);
		verify(sampleMapper, never()).conclude(any(), any());
		verifyNoInteractions(historyMapper);
	}

	@Test
	@DisplayName("TC-BR11-06 UPDATE kết luận khớp 0 dòng thì yêu cầu tải lại trang và không ghi dòng lịch sử")
	void concludeReportsConcurrentUpdate() {
		givenLockedSampleIn(SampleStatus.IN_TESTING);
		givenResults(List.of(result(ResultStatus.APPROVED, Verdict.PASS)));
		when(sampleMapper.conclude(any(), any())).thenReturn(0);

		assertThatThrownBy(() -> service.conclude(SAMPLE_ID, "duyet"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("CONCURRENT_UPDATE");
				assertThat(ex).hasMessage("Mẫu vừa được người khác cập nhật, hãy tải lại trang");
			});
		verifyNoInteractions(historyMapper);
	}

}
