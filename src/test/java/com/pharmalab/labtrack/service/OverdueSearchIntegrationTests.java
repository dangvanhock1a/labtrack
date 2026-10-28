package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import com.pharmalab.labtrack.TestcontainersConfiguration;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleFilter;
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.Verdict;
import com.pharmalab.labtrack.persistence.LabSampleMapper;

/**
 * Lọc "chỉ mẫu quá hạn" (BR-14) trên PostgreSQL thật. Clock cố định ở 2026-10-28T17:30Z = 00:30 ngày 29/10 giờ VN, khi
 * ngày UTC (và {@code CURRENT_DATE} của CSDL nếu múi giờ phiên là UTC) vẫn là 28/10: SQL tự lấy ngày hay JVM lấy ngày
 * theo UTC sẽ lệch một ngày so với "hôm nay" giờ VN = 2026-10-29.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, OverdueSearchIntegrationTests.FixedClockConfig.class })
@Transactional
class OverdueSearchIntegrationTests {

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfig {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-10-28T17:30:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
		}

	}

	private static final String KEYWORD = "BR14S";

	@Autowired
	private LabSampleMapper sampleMapper;

	@Autowired
	private LabSampleService sampleService;

	/** Mẫu có từ khóa BR14S, nhận 01/03/2000 (qua {@code ck_lab_sample_due_date}); {@code code} cũng là mã ghi vào CSDL. */
	private void insert(String code, SampleStatus status, String dueDate, Consumer<LabSample> customizer) {
		LabSample sample = new LabSample();
		sample.setCode(code);
		sample.setProductName("Mẫu quá hạn " + KEYWORD);
		sample.setBatchNo("PA-2601");
		sample.setSampleType(SampleType.FINISHED_PRODUCT);
		sample.setPriority(SamplePriority.NORMAL);
		sample.setStatus(status);
		sample.setReceivedAt(OffsetDateTime.parse("2000-03-01T09:00+07:00"));
		sample.setDueDate(LocalDate.parse(dueDate));
		sample.setCreatedBy("tiepnhan");
		customizer.accept(sample);
		sampleMapper.insert(sample);
	}

	private void insert(String code, SampleStatus status, String dueDate) {
		insert(code, status, dueDate, sample -> {
			if (status == SampleStatus.COMPLETED) {
				sample.setConclusion(Verdict.PASS);
				sample.setCompletedAt(OffsetDateTime.parse("2000-03-05T09:00+07:00"));
			}
		});
	}

	private SamplePage overdueOnly() {
		return sampleService.search(new SampleFilter(KEYWORD, null, null, null, true), 1);
	}

	private static List<String> codes(SamplePage page) {
		return page.samples().stream().map(LabSample::getCode).toList();
	}

	@Test
	@DisplayName("TC-BR14-08 overdueOnly lúc 00:30 giờ VN (UTC còn hôm trước): hạn hôm qua (28/10) quá hạn, hạn hôm nay (29/10) và ngày mai thì không")
	void overdueBoundaryUsesVietnamToday() {
		insert("LS-2000-00101", SampleStatus.RECEIVED, "2026-10-27");
		insert("LS-2000-00102", SampleStatus.IN_TESTING, "2026-10-28");
		insert("LS-2000-00103", SampleStatus.RECEIVED, "2026-10-29");
		insert("LS-2000-00104", SampleStatus.IN_TESTING, "2026-10-30");

		SamplePage page = overdueOnly();

		assertThat(codes(page)).containsExactlyInAnyOrder("LS-2000-00101", "LS-2000-00102");
		assertThat(page.total()).isEqualTo(2);
	}

	@Test
	@DisplayName("TC-BR14-09 overdueOnly không trả mẫu Hoàn thành hoặc Đã hủy dù hạn trả đã qua")
	void overdueOnlyExcludesFinishedSamples() {
		insert("LS-2000-00111", SampleStatus.RECEIVED, "2026-10-01");
		insert("LS-2000-00112", SampleStatus.COMPLETED, "2026-10-01");
		insert("LS-2000-00113", SampleStatus.CANCELLED, "2026-10-01");

		SamplePage page = overdueOnly();

		assertThat(codes(page)).containsExactly("LS-2000-00111");
		assertThat(page.total()).isEqualTo(1);
	}

	@Test
	@DisplayName("TC-BR14-10 Không bật overdueOnly thì mọi mẫu khớp đều được trả, kể cả chưa tới hạn và đã kết thúc")
	void withoutOverdueOnlyAllSamplesAreReturned() {
		insert("LS-2000-00121", SampleStatus.RECEIVED, "2026-10-01");
		insert("LS-2000-00122", SampleStatus.RECEIVED, "2026-10-29");
		insert("LS-2000-00123", SampleStatus.COMPLETED, "2026-10-01");
		insert("LS-2000-00124", SampleStatus.CANCELLED, "2026-10-01");

		SamplePage page = sampleService.search(new SampleFilter(KEYWORD, null, null, null, false), 1);

		assertThat(page.total()).isEqualTo(4);
		assertThat(codes(page)).hasSize(4);
	}

	@Test
	@DisplayName("TC-BR14-11 overdueOnly kết hợp AND với trạng thái và từ khóa; countSearch khớp số dòng của search")
	void overdueOnlyCombinesWithOtherFiltersAndCountMatches() {
		insert("LS-2000-00131", SampleStatus.RECEIVED, "2026-10-01");
		insert("LS-2000-00132", SampleStatus.IN_TESTING, "2026-10-01");
		insert("LS-2000-00133", SampleStatus.IN_TESTING, "2026-10-29");
		insert("LS-2000-00134", SampleStatus.IN_TESTING, "2026-10-01", sample -> sample.setProductName("Khác"));

		SamplePage inTesting = sampleService.search(
				new SampleFilter(KEYWORD, SampleStatus.IN_TESTING, null, null, true), 1);

		assertThat(codes(inTesting)).containsExactly("LS-2000-00132");
		assertThat(inTesting.total()).isEqualTo(1);
		SamplePage both = overdueOnly();
		assertThat(both.total()).isEqualTo(codes(both).size()).isEqualTo(2);
	}

	@Test
	@DisplayName("TC-BR14-12 Mẫu trong kết quả tra cứu mang cờ quá hạn theo ngày VN: hạn 28/10 quá hạn, hạn 29/10 thì chưa")
	void searchResultCarriesOverdueFlag() {
		insert("LS-2000-00141", SampleStatus.RECEIVED, "2026-10-28");
		insert("LS-2000-00142", SampleStatus.RECEIVED, "2026-10-29");

		List<LabSample> samples = sampleService.search(new SampleFilter(KEYWORD, null, null, null, false), 1).samples();

		assertThat(samples).extracting(LabSample::getCode, LabSample::isOverdue)
			.containsExactlyInAnyOrder(org.assertj.core.api.Assertions.tuple("LS-2000-00141", true),
					org.assertj.core.api.Assertions.tuple("LS-2000-00142", false));
	}

}
