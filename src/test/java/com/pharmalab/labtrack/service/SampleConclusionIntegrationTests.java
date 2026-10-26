package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.pharmalab.labtrack.TestcontainersConfiguration;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.StatusHistory;
import com.pharmalab.labtrack.domain.TestResult;
import com.pharmalab.labtrack.domain.Verdict;
import com.pharmalab.labtrack.persistence.TestParameterMapper;

/**
 * Kết luận mẫu qua tầng nghiệp vụ trên PostgreSQL thật (BR-11): đi từ đăng ký tới Hoàn thành, rồi đọc lại từ CSDL
 * để thấy hai cột của V5 và dòng lịch sử ghi cùng transaction (BR-04).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SampleConclusionIntegrationTests {

	@Autowired
	private LabSampleService sampleService;

	@Autowired
	private TestResultService resultService;

	@Autowired
	private TestParameterMapper parameterMapper;

	private TestResult enter(long sampleId, String parameterCode, String value) {
		long parameterId = parameterMapper.findByCode(parameterCode).orElseThrow().getId();
		return resultService.enter(sampleId, new NewResult(parameterId, new BigDecimal(value)), "kiemnghiem1");
	}

	@Test
	@DisplayName("TC-BR11-07 Luồng đầy đủ: đăng ký, nhận kiểm nghiệm, nhập ASSAY Đạt và pH Không đạt, duyệt cả 2, kết luận Không đạt; lưu kết luận, thời điểm hoàn thành và đúng 1 dòng lịch sử mới")
	void fullFlowEndsCompletedWithConclusionAndHistory() {
		long id = sampleService.register(
				new NewSample("Paracetamol 500 mg", "PA-2601", SampleType.FINISHED_PRODUCT, null, null), "tiepnhan")
			.getId();
		sampleService.startTesting(id, "kiemnghiem1");
		TestResult assay = enter(id, "ASSAY", "98.5");
		// pH 7.5 vượt tiêu chuẩn 4.5–7 nên Không đạt (BR-07): một kết quả Không đạt là đủ để kết luận Không đạt.
		TestResult ph = enter(id, "PH", "7.5");
		resultService.approve(id, assay.getId(), "duyet");
		resultService.approve(id, ph.getId(), "duyet");

		LabSample concluded = sampleService.conclude(id, Verdict.FAIL, "duyet");

		assertThat(concluded.getStatus()).isEqualTo(SampleStatus.COMPLETED);
		assertThat(concluded.getConclusion()).isEqualTo(Verdict.FAIL);
		LabSample stored = sampleService.findById(id).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(SampleStatus.COMPLETED);
		assertThat(stored.getConclusion()).isEqualTo(Verdict.FAIL);
		// So với giá trị cũng đọc từ CSDL: PostgreSQL làm tròn tới micro giây, Clock của JVM thì không.
		assertThat(stored.getCompletedAt()).isNotNull().isEqualTo(stored.getUpdatedAt());
		assertThat(stored.getCompletedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(7));

		List<StatusHistory> history = sampleService.findStatusHistory(id);
		assertThat(history)
			.extracting(StatusHistory::getFromStatus, StatusHistory::getToStatus, StatusHistory::getReason,
					StatusHistory::getChangedBy)
			.containsExactly(tuple(null, SampleStatus.RECEIVED, "Đăng ký mẫu", "tiepnhan"),
					tuple(SampleStatus.RECEIVED, SampleStatus.IN_TESTING, null, "kiemnghiem1"),
					tuple(SampleStatus.IN_TESTING, SampleStatus.COMPLETED, "Kết luận: Không đạt", "duyet"));
		assertThat(history.get(2).getChangedAt()).isEqualTo(stored.getCompletedAt());
	}

	@Test
	@DisplayName("TC-BR11-10 Hộp thoại hiện Đạt; trong lúc nó mở, pH Không đạt được nhập và được duyet2 duyệt: kết luận Đạt bị từ chối, mẫu vẫn Đang kiểm nghiệm, không thêm dòng lịch sử")
	void concludeRejectsVerdictThatChangedWhileDialogWasOpen() {
		long id = sampleService.register(
				new NewSample("Paracetamol 500 mg", "PA-2601", SampleType.FINISHED_PRODUCT, null, null), "tiepnhan")
			.getId();
		sampleService.startTesting(id, "kiemnghiem1");
		TestResult assay = enter(id, "ASSAY", "98.5");
		resultService.approve(id, assay.getId(), "duyet");
		// Kết luận trang chi tiết tính sẵn cho hộp thoại, đúng như SampleController.detail.
		Verdict shown = ConclusionReadiness.ofSheet(resultService.findSheet(id)).verdict();
		assertThat(shown).isEqualTo(Verdict.PASS);

		TestResult ph = enter(id, "PH", "7.5");
		resultService.approve(id, ph.getId(), "duyet2");

		assertThatThrownBy(() -> sampleService.conclude(id, shown, "duyet"))
			.isInstanceOfSatisfying(BusinessException.class,
					ex -> assertThat(ex.getCode()).isEqualTo("CONCLUSION_CHANGED"));
		LabSample stored = sampleService.findById(id).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(SampleStatus.IN_TESTING);
		assertThat(stored.getConclusion()).isNull();
		assertThat(stored.getCompletedAt()).isNull();
		assertThat(sampleService.findStatusHistory(id)).hasSize(2);
	}

}
