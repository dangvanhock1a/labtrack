package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleFilter;
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.StorageCondition;
import com.pharmalab.labtrack.persistence.LabSampleMapper;
import com.pharmalab.labtrack.persistence.StatusHistoryMapper;
import com.pharmalab.labtrack.persistence.TestResultMapper;

/** Xuất CSV (BR-16) ở tầng nghiệp vụ: đúng bộ lọc, giới hạn 10.000 dòng, ngày "hôm nay" theo giờ VN. */
class LabSampleExportTests {

	/** 00:30 ngày 29/10/2026 giờ VN, khi UTC vẫn là ngày 28/10. */
	private static final Clock AFTER_MIDNIGHT = Clock.fixed(Instant.parse("2026-10-28T17:30:00Z"),
			ZoneId.of("Asia/Ho_Chi_Minh"));

	/** Ngày VN của {@link #AFTER_MIDNIGHT}; ngày UTC cùng lúc là 2026-10-28. */
	private static final LocalDate TODAY = LocalDate.parse("2026-10-29");

	private final LabSampleMapper sampleMapper = mock(LabSampleMapper.class);

	private final LabSampleService service = new LabSampleService(sampleMapper, mock(StatusHistoryMapper.class),
			mock(TestResultMapper.class), mock(SampleCodeGenerator.class), AFTER_MIDNIGHT, 20);

	private static LabSample sampleDue(SampleStatus status, String dueDate) {
		LabSample sample = new LabSample();
		sample.setCode("LS-2026-00042");
		sample.setProductName("Paracetamol 500 mg");
		sample.setBatchNo("PA-2601");
		sample.setSampleType(SampleType.FINISHED_PRODUCT);
		sample.setPriority(SamplePriority.NORMAL);
		sample.setStorageCondition(StorageCondition.ROOM_TEMPERATURE);
		sample.setStatus(status);
		sample.setReceivedAt(OffsetDateTime.parse("2026-10-20T02:00Z"));
		sample.setDueDate(LocalDate.parse(dueDate));
		return sample;
	}

	private void givenSearchReturns(List<LabSample> samples) {
		when(sampleMapper.search(any(), any(), any(), anyInt(), anyInt())).thenReturn(samples);
	}

	private static String[] lines(CsvFile file) {
		return new String(file.content(), StandardCharsets.UTF_8).split("\r\n");
	}

	@Test
	@DisplayName("TC-BR16-05 Xuất đúng bộ lọc đang xem: mapper nhận cùng bộ lọc, từ khóa đã escape, hôm nay giờ VN, lấy dư 1 dòng (10.001) từ dòng đầu, một câu SQL; tên file theo ngày VN lúc 00:30")
	void exportUsesListFilterAndVietnamDate() {
		SampleFilter filter = new SampleFilter(" 50% ", SampleStatus.RECEIVED, SamplePriority.URGENT,
				SampleType.RAW_MATERIAL, true);
		givenSearchReturns(List.of());

		CsvFile file = service.exportCsv(filter);

		verify(sampleMapper).search(eq(filter), eq("%50\\%%"), eq(TODAY), eq(10_001), eq(0));
		verifyNoMoreInteractions(sampleMapper);
		assertThat(file.fileName()).isEqualTo("labtrack-mau-2026-10-29.csv");
		assertThat(lines(file)).hasSize(1);
	}

	@Test
	@DisplayName("TC-BR16-06 Đúng 10.000 mẫu thì file có đủ 10.000 dòng; 10.001 mẫu thì từ chối với EXPORT_LIMIT, không cắt bớt")
	void exportRejectsMoreThanTenThousandRows() {
		givenSearchReturns(Collections.nCopies(10_000, sampleDue(SampleStatus.RECEIVED, "2026-11-05")));
		assertThat(lines(service.exportCsv(SampleFilter.NONE))).hasSize(1 + 10_000);

		givenSearchReturns(Collections.nCopies(10_001, sampleDue(SampleStatus.RECEIVED, "2026-11-05")));
		assertThatThrownBy(() -> service.exportCsv(SampleFilter.NONE))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("EXPORT_LIMIT");
				assertThat(ex.getField()).isNull();
				assertThat(ex).hasMessage("Bộ lọc khớp hơn 10.000 mẫu. Hãy lọc hẹp hơn để xuất CSV.");
			});
	}

	@Test
	@DisplayName("TC-BR16-07 Cột Quá hạn theo ngày VN lúc 00:30: hạn 28/10 là Có, hạn 29/10 là Không, mẫu Hoàn thành hạn 28/10 là Không (BR-14)")
	void exportMarksOverdueByVietnamDate() {
		givenSearchReturns(List.of(sampleDue(SampleStatus.RECEIVED, "2026-10-28"),
				sampleDue(SampleStatus.IN_TESTING, "2026-10-29"), sampleDue(SampleStatus.COMPLETED, "2026-10-28")));

		String[] lines = lines(service.exportCsv(SampleFilter.NONE));

		assertThat(lines).hasSize(4);
		assertThat(lines[1]).endsWith(",28/10/2026,Có,");
		assertThat(lines[2]).endsWith(",29/10/2026,Không,");
		assertThat(lines[3]).endsWith(",28/10/2026,Không,");
	}

}
