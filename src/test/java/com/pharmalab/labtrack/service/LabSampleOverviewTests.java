package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.OverviewStats;
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.StatusCount;
import com.pharmalab.labtrack.persistence.LabSampleMapper;
import com.pharmalab.labtrack.persistence.StatusHistoryMapper;
import com.pharmalab.labtrack.persistence.TestResultMapper;

/** Số liệu trang Tổng quan (BR-15): lấp 0, cửa sổ 30 ngày theo giờ VN, làm tròn TAT, mẫu cần chú ý. */
class LabSampleOverviewTests {

	/** 00:30 ngày 29/10/2026 giờ VN, khi UTC vẫn là ngày 28/10. */
	private static final Clock AFTER_MIDNIGHT = Clock.fixed(Instant.parse("2026-10-28T17:30:00Z"),
			ZoneId.of("Asia/Ho_Chi_Minh"));

	/** Ngày VN của {@link #AFTER_MIDNIGHT}; ngày UTC cùng lúc là 2026-10-28. */
	private static final LocalDate TODAY = LocalDate.parse("2026-10-29");

	private final LabSampleMapper sampleMapper = mock(LabSampleMapper.class);

	private final LabSampleService service = new LabSampleService(sampleMapper, mock(StatusHistoryMapper.class),
			mock(TestResultMapper.class), mock(SampleCodeGenerator.class), AFTER_MIDNIGHT, 20);

	/** {@code overviewStats} chưa stub sẽ trả null; mọi test đều cần một dòng số liệu. */
	private void givenStats(long overdue, long completed, String avgTatSeconds) {
		OverviewStats stats = new OverviewStats();
		stats.setOverdueCount(overdue);
		stats.setCompletedCount(completed);
		stats.setAvgTatSeconds((avgTatSeconds != null) ? new BigDecimal(avgTatSeconds) : null);
		when(sampleMapper.overviewStats(any(), any())).thenReturn(stats);
	}

	private static StatusCount count(SampleStatus status, long count) {
		StatusCount row = new StatusCount();
		row.setStatus(status);
		row.setCount(count);
		return row;
	}

	private static LabSample sample(String code, SamplePriority priority, String dueDate) {
		LabSample sample = new LabSample();
		sample.setCode(code);
		sample.setStatus(SampleStatus.RECEIVED);
		sample.setPriority(priority);
		sample.setDueDate(LocalDate.parse(dueDate));
		// pgjdbc trả TIMESTAMPTZ theo UTC: 02:00Z là 09:00 giờ VN.
		sample.setReceivedAt(OffsetDateTime.parse("2026-10-20T02:00:00Z"));
		return sample;
	}

	@Test
	@DisplayName("TC-BR15-01 Trạng thái không có mẫu nào vẫn có số 0; đủ 4 trạng thái theo thứ tự SampleStatus")
	void statusWithoutSamplesShowsZero() {
		givenStats(0, 0, null);
		when(sampleMapper.countByStatus()).thenReturn(List.of(count(SampleStatus.IN_TESTING, 3)));

		Overview overview = service.overview();

		assertThat(overview.statusCounts()).containsExactly(entry(SampleStatus.RECEIVED, 0L),
				entry(SampleStatus.IN_TESTING, 3L), entry(SampleStatus.COMPLETED, 0L),
				entry(SampleStatus.CANCELLED, 0L));
	}

	@Test
	@DisplayName("TC-BR15-02 Lúc 00:30 giờ VN (UTC còn hôm trước): hôm nay là 29/10, cửa sổ 30 ngày bắt đầu 00:00 ngày 30/09 giờ VN, lấy tối đa 10 mẫu cần chú ý")
	void windowAndTodayFollowVietnamCalendar() {
		givenStats(0, 0, null);

		service.overview();

		verify(sampleMapper).overviewStats(TODAY, OffsetDateTime.parse("2026-09-30T00:00+07:00"));
		verify(sampleMapper).findAttention(TODAY, 10);
	}

	@ParameterizedTest(name = "{0} giây → {1} giờ")
	@CsvSource({ "45180, 12.6", "45179.9, 12.5", "3600, 1.0", "180, 0.1", "36000.000000000000, 10.0" })
	@DisplayName("TC-BR15-03 TAT trung bình đổi từ giây sang giờ, làm tròn 1 chữ số thập phân (HALF_UP)")
	void averageTatIsRoundedToOneDecimalHour(String seconds, String hours) {
		givenStats(0, 2, seconds);

		Overview overview = service.overview();

		// isEqualTo của BigDecimal so cả scale: "10.0" khác "10", nên kiểm luôn việc giữ đúng 1 chữ số thập phân.
		assertThat(overview.avgTatHours()).isEqualTo(new BigDecimal(hours));
		assertThat(overview.completedCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("TC-BR15-04 Chưa có mẫu hoàn thành trong 30 ngày: TAT trung bình để trống (null) chứ không phải 0")
	void noCompletedSampleLeavesAverageTatEmpty() {
		givenStats(4, 0, null);

		Overview overview = service.overview();

		assertThat(overview.avgTatHours()).isNull();
		assertThat(overview.completedCount()).isZero();
		assertThat(overview.overdueCount()).isEqualTo(4);
	}

	@Test
	@DisplayName("TC-BR15-05 Mẫu cần chú ý mang cờ quá hạn theo ngày VN (hạn 28/10 quá hạn, hạn 29/10 thì chưa) và thời điểm nhận theo giờ VN")
	void attentionSamplesCarryOverdueFlagAndClockZone() {
		givenStats(1, 0, null);
		when(sampleMapper.findAttention(TODAY, 10)).thenReturn(List.of(
				sample("LS-2026-00001", SamplePriority.NORMAL, "2026-10-28"),
				sample("LS-2026-00002", SamplePriority.URGENT, "2026-10-29")));

		List<LabSample> attention = service.overview().attention();

		assertThat(attention).extracting(LabSample::getCode, LabSample::isOverdue)
			.containsExactly(tuple("LS-2026-00001", true), tuple("LS-2026-00002", false));
		assertThat(attention.get(0).getReceivedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(7));
	}

}
