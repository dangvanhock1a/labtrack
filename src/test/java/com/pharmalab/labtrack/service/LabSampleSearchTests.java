package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleFilter;
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.persistence.LabSampleMapper;
import com.pharmalab.labtrack.persistence.StatusHistoryMapper;
import com.pharmalab.labtrack.persistence.TestResultMapper;

/** Tra cứu danh sách mẫu (BR-13): mẫu LIKE của từ khóa, số trang ngoài khoảng, múi giờ của mẫu đọc ra. */
class LabSampleSearchTests {

	/** 00:30 ngày 10/10/2026 giờ VN, khi UTC vẫn là ngày 09/10. */
	private static final Clock AFTER_MIDNIGHT = Clock.fixed(Instant.parse("2026-10-09T17:30:00Z"),
			ZoneId.of("Asia/Ho_Chi_Minh"));

	/** Ngày VN của {@link #AFTER_MIDNIGHT}; ngày UTC cùng lúc là 2026-10-09. */
	private static final LocalDate TODAY = LocalDate.parse("2026-10-10");

	private static final int PAGE_SIZE = 20;

	private final LabSampleMapper sampleMapper = mock(LabSampleMapper.class);

	private final LabSampleService service = new LabSampleService(sampleMapper, mock(StatusHistoryMapper.class),
			mock(TestResultMapper.class), mock(SampleCodeGenerator.class), AFTER_MIDNIGHT, PAGE_SIZE);

	@Test
	@DisplayName("TC-BR13-15 Từ khóa: cắt khoảng trắng, escape \\ % _ để chúng là ký tự thường, ô trống thì không lọc theo từ khóa")
	void keywordBecomesEscapedContainsPattern() {
		assertThat(LabSampleService.likePattern("  pa-2601 ")).isEqualTo("%pa-2601%");
		assertThat(LabSampleService.likePattern("50%")).isEqualTo("%50\\%%");
		assertThat(LabSampleService.likePattern("A_1")).isEqualTo("%A\\_1%");
		// Người dùng gõ \% (hai ký tự): \ được escape trước thành \\, rồi % thành \%, nên không có \ nào bị nuốt.
		assertThat(LabSampleService.likePattern("\\%")).isEqualTo("%\\\\\\%%");
		assertThat(LabSampleService.likePattern(null)).isNull();
		assertThat(LabSampleService.likePattern("")).isNull();
		assertThat(LabSampleService.likePattern("   ")).isNull();
	}

	@Test
	@DisplayName("TC-BR13-15 Một trang đúng hai câu SQL (NFR-01): đếm và lấy trang với cùng bộ lọc và cùng mẫu LIKE đã escape")
	void searchRunsCountThenPageWithSamePattern() {
		SampleFilter filter = new SampleFilter(" 50% ", SampleStatus.RECEIVED, SamplePriority.URGENT,
				SampleType.RAW_MATERIAL);
		when(sampleMapper.countSearch(filter, "%50\\%%", TODAY)).thenReturn(1L);

		service.search(filter, 1);

		verify(sampleMapper).countSearch(filter, "%50\\%%", TODAY);
		verify(sampleMapper).search(filter, "%50\\%%", TODAY, PAGE_SIZE, 0);
		verifyNoMoreInteractions(sampleMapper);
	}

	@Test
	@DisplayName("TC-BR14-06 Service truyền \"hôm nay\" theo giờ VN (2026-10-10, không phải ngày UTC 2026-10-09) cho cả đếm và lấy trang, kể cả khi bật overdueOnly")
	void searchPassesTodayInClockZoneToMapper() {
		SampleFilter overdue = new SampleFilter(null, null, null, null, true);
		when(sampleMapper.countSearch(overdue, null, LocalDate.parse("2026-10-10"))).thenReturn(1L);

		service.search(overdue, 1);

		verify(sampleMapper).countSearch(overdue, null, LocalDate.parse("2026-10-10"));
		verify(sampleMapper).search(overdue, null, LocalDate.parse("2026-10-10"), PAGE_SIZE, 0);
		verifyNoMoreInteractions(sampleMapper);
	}

	@Test
	@DisplayName("TC-BR14-07 Mẫu trong trang được đánh dấu quá hạn theo ngày VN lúc 00:30: hạn 09/10 quá hạn, hạn 10/10 thì chưa, mẫu Hoàn thành hạn 09/10 thì không")
	void searchMarksOverdueByVietnamDate() {
		LabSample yesterday = overdueCandidate(SampleStatus.RECEIVED, "2026-10-09");
		LabSample today = overdueCandidate(SampleStatus.IN_TESTING, "2026-10-10");
		LabSample finished = overdueCandidate(SampleStatus.COMPLETED, "2026-10-09");
		when(sampleMapper.countSearch(SampleFilter.NONE, null, TODAY)).thenReturn(3L);
		when(sampleMapper.search(SampleFilter.NONE, null, TODAY, PAGE_SIZE, 0))
			.thenReturn(List.of(yesterday, today, finished));

		List<LabSample> samples = service.search(SampleFilter.NONE, 1).samples();

		assertThat(samples).extracting(LabSample::isOverdue).containsExactly(true, false, false);
	}

	private static LabSample overdueCandidate(SampleStatus status, String dueDate) {
		LabSample sample = new LabSample();
		sample.setStatus(status);
		sample.setDueDate(LocalDate.parse(dueDate));
		sample.setReceivedAt(OffsetDateTime.parse("2026-10-01T09:00+07:00"));
		return sample;
	}

	@ParameterizedTest(name = "[{index}] N = {0}, xin trang {1} → trang {2}/{6}, OFFSET {3}, hiển thị {4}–{5}")
	@DisplayName("TC-BR13-16 Số trang ngoài khoảng được kéo về trang có thật gần nhất; nhãn a–b đúng ở trang cuối và khi không có mẫu")
	@CsvSource(textBlock = """
			# N, xin, trang, offset, a, b, số trang
			45,   1,  1,  0,  1, 20, 3
			45,   3,  3, 40, 41, 45, 3
			45,  99,  3, 40, 41, 45, 3
			45,   0,  1,  0,  1, 20, 3
			45,  -5,  1,  0,  1, 20, 3
			40,   3,  2, 20, 21, 40, 2
			20,   2,  1,  0,  1, 20, 1
			 0,   1,  1,  0,  0,  0, 1
			 0,   7,  1,  0,  0,  0, 1
			""")
	void pageIsClampedToExistingPage(long total, int requested, int page, int offset, long from, long to, int pages) {
		when(sampleMapper.countSearch(SampleFilter.NONE, null, TODAY)).thenReturn(total);

		SamplePage result = service.search(SampleFilter.NONE, requested);

		verify(sampleMapper).search(SampleFilter.NONE, null, TODAY, PAGE_SIZE, offset);
		assertThat(result.page()).isEqualTo(page);
		assertThat(result.total()).isEqualTo(total);
		assertThat(result.from()).isEqualTo(from);
		assertThat(result.to()).isEqualTo(to);
		assertThat(result.totalPages()).isEqualTo(pages);
		assertThat(result.hasPrevious()).isEqualTo(page > 1);
		assertThat(result.hasNext()).isEqualTo(page < pages);
	}

	@Test
	@DisplayName("TC-BR13-16 Mẫu đọc ra mang giờ VN: nhận lúc 17:30 UTC ngày 09/10 hiện là 00:30 ngày 10/10")
	void samplesAreReturnedInClockZone() {
		LabSample sample = new LabSample();
		sample.setReceivedAt(OffsetDateTime.parse("2026-10-09T17:30Z"));
		when(sampleMapper.countSearch(SampleFilter.NONE, null, TODAY)).thenReturn(1L);
		when(sampleMapper.search(SampleFilter.NONE, null, TODAY, PAGE_SIZE, 0)).thenReturn(List.of(sample));

		SamplePage result = service.search(SampleFilter.NONE, 1);

		OffsetDateTime receivedAt = result.samples().getFirst().getReceivedAt();
		assertThat(receivedAt.getOffset()).isEqualTo(ZoneOffset.ofHours(7));
		assertThat(receivedAt.toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-10-10T00:30"));
	}

}
