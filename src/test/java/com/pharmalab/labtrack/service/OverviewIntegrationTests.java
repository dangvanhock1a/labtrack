package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.StatusChange;
import com.pharmalab.labtrack.domain.StorageCondition;
import com.pharmalab.labtrack.domain.Verdict;
import com.pharmalab.labtrack.persistence.LabSampleMapper;

/**
 * Trang Tổng quan (BR-15) trên PostgreSQL thật. {@code labtrack_test} có thể còn mẫu do E2E và test đồng thời commit,
 * mang ngày thật (năm 2026), nên test không so số tuyệt đối trên toàn bảng:
 * <ul>
 * <li>Clock cố định ở 00:30 ngày 31/03/2099 giờ VN (UTC còn 30/03). Cửa sổ 30 ngày bắt đầu 00:00 ngày 02/03/2099,
 * nên chỉ chứa mẫu do chính test tạo.</li>
 * <li>Số theo trạng thái và số quá hạn tính trên toàn bảng: so chênh lệch trước và sau khi chèn.</li>
 * <li>Mẫu cần chú ý gọi thẳng mapper với "hôm nay" năm 2000: mẫu của test có hạn trả năm 2000, đứng trước mọi mẫu
 * ngày thật trong thứ tự theo hạn trả.</li>
 * </ul>
 */
@SpringBootTest
@Import(OverviewIntegrationTests.FixedClockConfig.class)
@Transactional
class OverviewIntegrationTests {

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfig {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2099-03-30T17:30:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
		}

	}

	@Autowired
	private LabSampleMapper sampleMapper;

	@Autowired
	private LabSampleService sampleService;

	/**
	 * Ghi một mẫu thẳng qua mapper. Mẫu Hoàn thành được ghi ở Đang kiểm nghiệm rồi kết luận bằng {@code conclude} lúc
	 * {@code completedAt}, như {@code OverdueSearchIntegrationTests}: {@code insert} không ghi kết luận, mà
	 * {@code ck_lab_sample_completed} (V5) không cho mẫu Hoàn thành thiếu kết luận.
	 */
	private LabSample insert(String code, SampleStatus status, SamplePriority priority, String receivedAt,
			String dueDate, String completedAt) {
		boolean completed = status == SampleStatus.COMPLETED;
		LabSample sample = new LabSample();
		sample.setCode(code);
		sample.setProductName("Mẫu tổng quan BR15");
		sample.setBatchNo("PA-2601");
		sample.setSampleType(SampleType.FINISHED_PRODUCT);
		sample.setPriority(priority);
		sample.setStorageCondition(StorageCondition.ROOM_TEMPERATURE);
		sample.setStatus(completed ? SampleStatus.IN_TESTING : status);
		sample.setReceivedAt(OffsetDateTime.parse(receivedAt));
		sample.setDueDate(LocalDate.parse(dueDate));
		sample.setCreatedBy("tiepnhan");
		sampleMapper.insert(sample);
		if (completed) {
			StatusChange change = new StatusChange(sample.getId(), SampleStatus.IN_TESTING, SampleStatus.COMPLETED,
					"Kết luận: Đạt", "duyet", OffsetDateTime.parse(completedAt));
			assertThat(sampleMapper.conclude(change, Verdict.PASS)).isEqualTo(1);
		}
		return sample;
	}

	/** Mẫu Hoàn thành, hạn trả đúng ngày nhận (qua {@code ck_lab_sample_due_date}). */
	private void insertCompleted(String code, String receivedAt, String completedAt) {
		insert(code, SampleStatus.COMPLETED, SamplePriority.NORMAL, receivedAt, receivedAt.substring(0, 10),
				completedAt);
	}

	private void insertOpen(String code, SampleStatus status, SamplePriority priority, String receivedAt,
			String dueDate) {
		insert(code, status, priority, receivedAt, dueDate, null);
	}

	private static List<String> codes(List<LabSample> samples) {
		return samples.stream().map(LabSample::getCode).toList();
	}

	@Test
	@DisplayName("TC-BR15-06 Biên cửa sổ 30 ngày giờ VN: hoàn thành 23:59 ngày 01/03 bị loại; 00:00 ngày 02/03 và 00:10 hôm nay (31/03) được tính")
	void completedWindowStartsAtVietnamMidnight() {
		insertCompleted("LS-2099-15001", "2099-03-01T08:00+07:00", "2099-03-01T23:59+07:00");
		insertCompleted("LS-2099-15002", "2099-03-01T08:00+07:00", "2099-03-02T00:00+07:00");
		insertCompleted("LS-2099-15003", "2099-03-30T08:00+07:00", "2099-03-31T00:10+07:00");

		Overview overview = sampleService.overview();

		assertThat(overview.completedCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("TC-BR15-07 TAT trung bình tính từ Thời điểm nhận tới lúc kết luận, chỉ trên mẫu hoàn thành trong cửa sổ: 10 giờ và 15 giờ ra 12.5 giờ")
	void averageTatCoversOnlyCompletedSamplesInWindow() {
		insertCompleted("LS-2099-15011", "2099-03-20T08:00+07:00", "2099-03-20T18:00+07:00");
		insertCompleted("LS-2099-15012", "2099-03-21T08:00+07:00", "2099-03-21T23:00+07:00");
		// Ngoài cửa sổ: TAT 100 giờ, kéo trung bình lên nếu bị tính.
		insertCompleted("LS-2099-15013", "2099-02-01T08:00+07:00", "2099-02-05T12:00+07:00");
		insertOpen("LS-2099-15014", SampleStatus.CANCELLED, SamplePriority.NORMAL, "2099-03-10T08:00+07:00",
				"2099-03-17");

		Overview overview = sampleService.overview();

		assertThat(overview.completedCount()).isEqualTo(2);
		assertThat(overview.avgTatHours()).isEqualTo(new BigDecimal("12.5"));
	}

	@Test
	@DisplayName("TC-BR15-08 Chưa có mẫu hoàn thành trong cửa sổ 30 ngày: số hoàn thành là 0 và TAT trung bình để trống")
	void noCompletedSampleInWindowLeavesAverageTatEmpty() {
		insertCompleted("LS-2099-15021", "2099-02-01T08:00+07:00", "2099-02-05T12:00+07:00");

		Overview overview = sampleService.overview();

		assertThat(overview.completedCount()).isZero();
		assertThat(overview.avgTatHours()).isNull();
	}

	@Test
	@DisplayName("TC-BR15-09 Số theo trạng thái đủ 4 trạng thái; quá hạn chỉ tính mẫu chưa kết thúc có hạn trước hôm nay 31/03 (hạn 30/03 tính, hạn 31/03 không)")
	void statusAndOverdueCountsComeFromWholeTable() {
		Overview before = sampleService.overview();
		insertOpen("LS-2099-15031", SampleStatus.RECEIVED, SamplePriority.NORMAL, "2099-03-20T08:00+07:00",
				"2099-03-30");
		insertOpen("LS-2099-15032", SampleStatus.IN_TESTING, SamplePriority.NORMAL, "2099-03-20T08:00+07:00",
				"2099-03-31");
		insertOpen("LS-2099-15033", SampleStatus.IN_TESTING, SamplePriority.URGENT, "2099-03-20T08:00+07:00",
				"2099-04-01");
		insertCompleted("LS-2099-15034", "2099-02-01T08:00+07:00", "2099-02-10T08:00+07:00");
		insertOpen("LS-2099-15035", SampleStatus.CANCELLED, SamplePriority.NORMAL, "2099-03-01T08:00+07:00",
				"2099-03-01");

		Overview after = sampleService.overview();

		assertThat(after.statusCounts()).containsOnlyKeys(SampleStatus.values());
		Map<SampleStatus, Long> added = Map.of(SampleStatus.RECEIVED, 1L, SampleStatus.IN_TESTING, 2L,
				SampleStatus.COMPLETED, 1L, SampleStatus.CANCELLED, 1L);
		added.forEach((status, count) -> assertThat(after.statusCounts().get(status))
			.as(status.name())
			.isEqualTo(before.statusCounts().get(status) + count));
		assertThat(after.overdueCount()).isEqualTo(before.overdueCount() + 1);
	}

	@Test
	@DisplayName("TC-BR15-10 Mẫu cần chú ý: chỉ mẫu chưa kết thúc mà Khẩn hoặc quá hạn; hạn trả sớm trước, cùng hạn thì Khẩn trước rồi nhận sớm trước")
	void attentionHoldsOpenUrgentOrOverdueSamplesByDueDate() {
		insertOpen("LS-2000-15101", SampleStatus.RECEIVED, SamplePriority.NORMAL, "2000-03-10T08:00+07:00",
				"2000-03-20");
		insertOpen("LS-2000-15102", SampleStatus.IN_TESTING, SamplePriority.NORMAL, "2000-03-05T08:00+07:00",
				"2000-03-25");
		insertOpen("LS-2000-15103", SampleStatus.RECEIVED, SamplePriority.URGENT, "2000-03-15T08:00+07:00",
				"2000-03-25");
		insertOpen("LS-2000-15104", SampleStatus.RECEIVED, SamplePriority.NORMAL, "2000-03-02T08:00+07:00",
				"2000-03-28");
		insertOpen("LS-2000-15105", SampleStatus.RECEIVED, SamplePriority.NORMAL, "2000-03-01T08:00+07:00",
				"2000-03-28");
		// Khẩn nhưng chưa tới hạn: vẫn cần chú ý.
		insertOpen("LS-2000-15106", SampleStatus.IN_TESTING, SamplePriority.URGENT, "2000-03-20T08:00+07:00",
				"2000-04-10");
		// Không cần chú ý: hạn đúng hôm nay; Thấp chưa tới hạn; Khẩn quá hạn nhưng đã kết thúc.
		insertOpen("LS-2000-15107", SampleStatus.RECEIVED, SamplePriority.NORMAL, "2000-03-20T08:00+07:00",
				"2000-03-31");
		insertOpen("LS-2000-15108", SampleStatus.RECEIVED, SamplePriority.LOW, "2000-03-20T08:00+07:00",
				"2000-04-05");
		insert("LS-2000-15109", SampleStatus.COMPLETED, SamplePriority.URGENT, "2000-03-01T08:00+07:00",
				"2000-03-10", "2000-03-09T08:00+07:00");
		insertOpen("LS-2000-15110", SampleStatus.CANCELLED, SamplePriority.URGENT, "2000-03-01T08:00+07:00",
				"2000-03-10");

		List<String> codes = codes(sampleMapper.findAttention(LocalDate.parse("2000-03-31"), 10));

		assertThat(codes.stream().filter(code -> code.startsWith("LS-2000-151")))
			.containsExactly("LS-2000-15101", "LS-2000-15103", "LS-2000-15102", "LS-2000-15105", "LS-2000-15104",
					"LS-2000-15106");
	}

	@Test
	@DisplayName("TC-BR15-11 Mẫu cần chú ý tối đa 10 dòng: có 12 mẫu quá hạn thì lấy 10 mẫu hạn trả sớm nhất")
	void attentionIsLimitedToTenEarliestDueSamples() {
		IntStream.rangeClosed(1, 12)
			.forEach(day -> insertOpen("LS-2000-152%02d".formatted(day), SampleStatus.RECEIVED,
					SamplePriority.NORMAL, "2000-03-01T08:00+07:00", "2000-03-%02d".formatted(day)));

		List<String> codes = codes(sampleMapper.findAttention(LocalDate.parse("2000-03-31"), 10));

		assertThat(codes).containsExactlyElementsOf(
				IntStream.rangeClosed(1, 10).mapToObj("LS-2000-152%02d"::formatted).toList());
	}

}
