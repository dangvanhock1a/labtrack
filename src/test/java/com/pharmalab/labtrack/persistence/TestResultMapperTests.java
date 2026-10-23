package com.pharmalab.labtrack.persistence;

import static com.pharmalab.labtrack.domain.ResultStatus.APPROVED;
import static com.pharmalab.labtrack.domain.ResultStatus.PENDING;
import static com.pharmalab.labtrack.domain.ResultStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.pharmalab.labtrack.TestcontainersConfiguration;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.ResultAction;
import com.pharmalab.labtrack.domain.ResultAudit;
import com.pharmalab.labtrack.domain.ResultReview;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.TestParameter;
import com.pharmalab.labtrack.domain.TestResult;
import com.pharmalab.labtrack.domain.Verdict;
import com.pharmalab.labtrack.service.LabSampleService;
import com.pharmalab.labtrack.service.NewSample;

/**
 * SQL trong {@code TestParameterMapper.xml}, {@code TestResultMapper.xml}, {@code ResultAuditMapper.xml} và
 * {@code LabSampleMapper.lockById} trên PostgreSQL thật: lỗi trong XML chỉ lộ ra khi câu lệnh chạy. Kết quả
 * được ghi thẳng qua mapper, không qua tầng nghiệp vụ, nên test tự chụp tiêu chuẩn và tự tính verdict.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TestResultMapperTests {

	private static final String ASSAY_NAME = "Định lượng hoạt chất";

	@Autowired
	private TestParameterMapper parameterMapper;

	@Autowired
	private TestResultMapper resultMapper;

	@Autowired
	private ResultAuditMapper auditMapper;

	@Autowired
	private LabSampleMapper sampleMapper;

	@Autowired
	private LabSampleService sampleService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Clock clock;

	private long sampleId;

	private TestParameter assay;

	@BeforeEach
	void sampleInTesting() {
		sampleId = newSampleInTesting();
		assay = parameterMapper.findByCode("ASSAY").orElseThrow();
	}

	private long newSampleInTesting() {
		LabSample sample = sampleService.register(
				new NewSample("Paracetamol 500 mg", "PA-2601", SampleType.FINISHED_PRODUCT, null, null), "tiepnhan");
		sampleService.startTesting(sample.getId(), "kiemnghiem1");
		return sample.getId();
	}

	/** Kết quả Chờ duyệt dựng như tầng nghiệp vụ sẽ dựng: chụp tiêu chuẩn của chỉ tiêu, tự tính verdict. */
	private TestResult insertResult(TestParameter parameter, String value, String enteredBy, Long replacesId) {
		TestResult result = new TestResult();
		result.setSampleId(sampleId);
		result.setParameterId(parameter.getId());
		result.setValue(new BigDecimal(value));
		result.setUnit(parameter.getUnit());
		result.setSpecMin(parameter.getSpecMin());
		result.setSpecMax(parameter.getSpecMax());
		result.setVerdict(parameter.spec().evaluate(result.getValue()));
		result.setStatus(PENDING);
		result.setEnteredBy(enteredBy);
		result.setEnteredAt(OffsetDateTime.now(clock));
		result.setReplacesId(replacesId);
		resultMapper.insert(result);
		return result;
	}

	@Test
	@DisplayName("TC-BR06-13 Danh mục chỉ tiêu đọc qua mapper: 5 chỉ tiêu của V4 theo thứ tự hiển thị, giữ nguyên cách viết tiêu chuẩn, có cả chỉ tiêu đã ngừng dùng")
	void readsParameterCatalog() {
		jdbcTemplate.update("""
				INSERT INTO test_parameter (code, name, unit, spec_min, spec_max, active, display_order)
				VALUES ('OLD_TEST', 'Chỉ tiêu đã ngừng dùng', '%', NULL, 2.0, FALSE, 999)""");

		List<TestParameter> parameters = parameterMapper.findAll();

		assertThat(parameters).extracting(TestParameter::getCode)
			.startsWith("ASSAY", "PH", "LOD", "DISSOLUTION", "IMPURITY_TOTAL")
			.endsWith("OLD_TEST");
		assertThat(parameters.getLast().isActive()).isFalse();
		TestParameter impurity = parameterMapper.findByCode("IMPURITY_TOTAL").orElseThrow();
		assertThat(impurity.isActive()).isTrue();
		assertThat(impurity.getSpecMin()).isNull();
		assertThat(impurity.getSpecMax().toPlainString()).isEqualTo("1.0");
		assertThat(parameterMapper.findById(assay.getId())).get()
			.extracting(TestParameter::getName, TestParameter::getUnit, p -> p.spec().label())
			.containsExactly(ASSAY_NAME, "%", "95 – 105");
		assertThat(parameterMapper.findById(-1)).isEmpty();
	}

	@Test
	@DisplayName("TC-BR06-14 Ghi và đọc kết quả qua mapper: giữ nguyên 98.50 và tiêu chuẩn đã chụp, nhận ra kết quả hiệu lực, bản bị từ chối cần thay và số lần nhập")
	void insertsAndReadsResults() {
		TestResult first = insertResult(assay, "98.50", "kiemnghiem1", null);

		assertThat(first.getId()).isNotNull();
		TestResult stored = resultMapper.findById(first.getId()).orElseThrow();
		assertThat(stored.getValue().toPlainString()).isEqualTo("98.50");
		assertThat(stored.getUnit()).isEqualTo("%");
		assertThat(stored.spec().label()).isEqualTo("95 – 105");
		assertThat(stored.getVerdict()).isEqualTo(Verdict.PASS);
		assertThat(stored.getStatus()).isEqualTo(PENDING);
		assertThat(stored.getEnteredBy()).isEqualTo("kiemnghiem1");
		assertThat(stored.getEnteredAt()).isNotNull();
		assertThat(stored.getReplacesId()).isNull();
		assertThat(stored.getParameterCode()).isEqualTo("ASSAY");
		assertThat(stored.isParameterActive()).isTrue();
		assertThat(resultMapper.existsEffective(sampleId, assay.getId())).isTrue();
		assertThat(resultMapper.findReplaceableRejectedId(sampleId, assay.getId())).isEmpty();

		resultMapper.review(new ResultReview(first.getId(), sampleId, REJECTED, "duyet", OffsetDateTime.now(clock),
				"Sai độ pha loãng"));
		assertThat(resultMapper.existsEffective(sampleId, assay.getId())).isFalse();
		assertThat(resultMapper.findReplaceableRejectedId(sampleId, assay.getId())).contains(first.getId());

		// pH nhập xen giữa: id lớn hơn bản ASSAY đầu nhưng vẫn đứng sau ASSAY vì xếp theo thứ tự hiển thị.
		TestResult ph = insertResult(parameterMapper.findByCode("PH").orElseThrow(), "6.2", "kiemnghiem1", null);
		TestResult second = insertResult(assay, "98.7", "kiemnghiem2", first.getId());
		assertThat(resultMapper.existsEffective(sampleId, assay.getId())).isTrue();
		assertThat(resultMapper.findReplaceableRejectedId(sampleId, assay.getId())).isEmpty();

		assertThat(resultMapper.findBySample(sampleId))
			.extracting(TestResult::getId, TestResult::getParameterName, TestResult::getAttemptNo,
					TestResult::getStatus, TestResult::getReplacesId)
			.containsExactly(tuple(first.getId(), ASSAY_NAME, 1, REJECTED, null),
					tuple(second.getId(), ASSAY_NAME, 2, PENDING, first.getId()),
					tuple(ph.getId(), "pH", 1, PENDING, null));
	}

	@Test
	@DisplayName("TC-BR08-09 lockById đọc được mẫu; review chỉ cập nhật kết quả Chờ duyệt thuộc đúng mẫu, sai mẫu hoặc lần thứ hai thì 0 dòng")
	void locksSampleAndReviewsPendingResultOnce() {
		assertThat(sampleMapper.lockById(sampleId)).get()
			.extracting(LabSample::getStatus)
			.isEqualTo(SampleStatus.IN_TESTING);
		assertThat(sampleMapper.lockById(-1)).isEmpty();

		TestResult result = insertResult(assay, "98.50", "kiemnghiem1", null);
		long otherSampleId = newSampleInTesting();
		OffsetDateTime reviewedAt = OffsetDateTime.now(clock);

		assertThat(resultMapper.review(new ResultReview(result.getId(), otherSampleId, APPROVED, "duyet",
				reviewedAt, null))).isZero();
		assertThat(resultMapper.review(new ResultReview(result.getId(), sampleId, APPROVED, "duyet", reviewedAt,
				null))).isEqualTo(1);
		assertThat(resultMapper.review(new ResultReview(result.getId(), sampleId, REJECTED, "duyet2", reviewedAt,
				"Sai độ pha loãng"))).isZero();

		TestResult stored = resultMapper.findById(result.getId()).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(APPROVED);
		assertThat(stored.getReviewedBy()).isEqualTo("duyet");
		assertThat(stored.getReviewedAt()).isNotNull();
		assertThat(stored.getRejectReason()).isNull();
	}

	@Test
	@DisplayName("TC-BR10-08 Ghi audit qua mapper: đúng 1 dòng, detail chụp đúng dòng kết quả (98.50 giữ nguyên); kết quả không tồn tại thì 0 dòng; đọc ra cũ trước mới sau")
	void writesAndReadsAudit() {
		TestResult result = insertResult(assay, "98.50", "kiemnghiem1", null);
		// Cắt tới giây: PostgreSQL chỉ giữ tới micro giây, Clock của JVM có thể chi tiết hơn.
		OffsetDateTime enteredAt = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
		OffsetDateTime rejectedAt = enteredAt.plusMinutes(1);

		assertThat(auditMapper.insert(result.getId(), ResultAction.ENTER, "kiemnghiem1", enteredAt)).isEqualTo(1);
		assertThat(auditMapper.insert(-1, ResultAction.ENTER, "kiemnghiem1", enteredAt)).isZero();
		resultMapper.review(new ResultReview(result.getId(), sampleId, REJECTED, "duyet", rejectedAt,
				"Sai độ pha loãng"));
		assertThat(auditMapper.insert(result.getId(), ResultAction.REJECT, "duyet", rejectedAt)).isEqualTo(1);

		Map<String, Object> enterDetail = jdbcTemplate.queryForMap("""
				SELECT detail ->> 'parameter' AS parameter, detail ->> 'value' AS value,
				       detail ->> 'specMin' AS spec_min, detail ->> 'status' AS status
				FROM result_audit
				WHERE result_id = ? AND action = 'ENTER'""", result.getId());
		assertThat(enterDetail).containsEntry("parameter", "ASSAY")
			.containsEntry("value", "98.50")
			.containsEntry("spec_min", "95")
			.containsEntry("status", "PENDING");

		List<ResultAudit> audit = auditMapper.findBySample(sampleId);
		assertThat(audit)
			.extracting(ResultAudit::getAction, ResultAudit::getActor, ResultAudit::getParameterName,
					ResultAudit::getValue, ResultAudit::getUnit, ResultAudit::getVerdict, ResultAudit::getRejectReason)
			.containsExactly(
					tuple(ResultAction.ENTER, "kiemnghiem1", ASSAY_NAME, "98.50", "%", Verdict.PASS, null),
					tuple(ResultAction.REJECT, "duyet", ASSAY_NAME, "98.50", "%", Verdict.PASS, "Sai độ pha loãng"));
		assertThat(audit.getFirst().getActedAt()).isAtSameInstantAs(enteredAt);
		assertThat(audit.getLast().getActedAt()).isAtSameInstantAs(rejectedAt);
	}

}
