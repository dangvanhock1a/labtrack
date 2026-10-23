package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pharmalab.labtrack.TestcontainersConfiguration;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.ResultAction;
import com.pharmalab.labtrack.domain.ResultAudit;
import com.pharmalab.labtrack.domain.ResultStatus;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.TestResult;
import com.pharmalab.labtrack.domain.Verdict;
import com.pharmalab.labtrack.persistence.ResultAuditMapper;
import com.pharmalab.labtrack.persistence.TestParameterMapper;

/**
 * Kết quả kiểm nghiệm qua tầng nghiệp vụ trên PostgreSQL thật (BR-06..BR-10): bản chụp tiêu chuẩn, nhập lại
 * sau khi bị từ chối, audit trail và rollback. Đếm audit luôn theo mẫu do chính test tạo, vì dữ liệu mà test
 * khác đã commit không xóa được (SPEC-004 L1).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TestResultIntegrationTests {

	@Autowired
	private TestResultService resultService;

	@Autowired
	private LabSampleService sampleService;

	@Autowired
	private TestParameterMapper parameterMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/** Gọi thật xuống mapper; chỉ TC-BR10-03 cho nó ném lỗi. */
	@MockitoSpyBean
	private ResultAuditMapper auditMapper;

	private long sampleInTesting() {
		LabSample sample = sampleService.register(
				new NewSample("Paracetamol 500 mg", "PA-2601", SampleType.FINISHED_PRODUCT, null, null), "tiepnhan");
		sampleService.startTesting(sample.getId(), "kiemnghiem1");
		return sample.getId();
	}

	private long parameterId(String code) {
		return parameterMapper.findByCode(code).orElseThrow().getId();
	}

	private TestResult enter(long sampleId, String code, String value, String actor) {
		return resultService.enter(sampleId, new NewResult(parameterId(code), new BigDecimal(value)), actor);
	}

	private int auditRows(long sampleId) {
		return jdbcTemplate.queryForObject("""
				SELECT count(*) FROM result_audit a JOIN test_result r ON r.id = a.result_id
				WHERE r.sample_id = ?
				""", Integer.class, sampleId);
	}

	private ResultSheetRow sheetRow(long sampleId, String code) {
		return resultService.findSheet(sampleId).stream()
			.filter(row -> row.parameter().getCode().equals(code))
			.findFirst()
			.orElseThrow();
	}

	private static Consumer<Throwable> businessError(String code) {
		return ex -> assertThat(ex).isInstanceOfSatisfying(BusinessException.class,
				business -> assertThat(business.getCode()).isEqualTo(code));
	}

	@Test
	@DisplayName("TC-BR06-09 Nhập ASSAY 98.50: đọc lại đúng 98.50, Chờ duyệt, Đạt, bản chụp % 95–105, người nhập, giờ +07:00")
	void enteredResultIsStoredAsTyped() {
		long sampleId = sampleInTesting();

		enter(sampleId, "ASSAY", "98.50", "kiemnghiem1");

		TestResult stored = sheetRow(sampleId, "ASSAY").effective();
		assertThat(stored.getValue().toPlainString()).isEqualTo("98.50");
		assertThat(stored.getStatus()).isEqualTo(ResultStatus.PENDING);
		assertThat(stored.getVerdict()).isEqualTo(Verdict.PASS);
		assertThat(stored.getUnit()).isEqualTo("%");
		assertThat(stored.getSpecMin().toPlainString()).isEqualTo("95");
		assertThat(stored.getSpecMax().toPlainString()).isEqualTo("105");
		assertThat(stored.getEnteredBy()).isEqualTo("kiemnghiem1");
		assertThat(stored.getEnteredAt().getOffset()).isEqualTo(ZoneOffset.ofHours(7));
		assertThat(stored.getParameterName()).isEqualTo("Định lượng hoạt chất");
		assertThat(stored.getAttemptNo()).isEqualTo(1);
	}

	@Test
	@DisplayName("TC-BR06-11 Trang dữ liệu có đủ 5 chỉ tiêu của V4 theo thứ tự hiển thị, kèm tiêu chuẩn hiện hành cho dòng chưa nhập")
	void sheetListsActiveParametersInOrder() {
		long sampleId = sampleInTesting();

		assertThat(resultService.findSheet(sampleId))
			.extracting(row -> row.parameter().getCode(), ResultSheetRow::specLabel, ResultSheetRow::awaitingEntry)
			.containsExactly(tuple("ASSAY", "95 – 105", true), tuple("PH", "4.5 – 7", true),
					tuple("LOD", "≤ 0.5", true), tuple("DISSOLUTION", "≥ 80", true),
					tuple("IMPURITY_TOTAL", "≤ 1.0", true));
	}

	@Test
	@DisplayName("TC-BR06-11 Chỉ tiêu ngừng dùng: không nhập được, không có dòng trên trang; đã có kết quả từ trước thì vẫn hiện, không có ô nhập")
	void inactiveParameterIsOnlyShownWithExistingResult() {
		jdbcTemplate.update("""
				INSERT INTO test_parameter (code, name, unit, spec_max, active, display_order)
				VALUES ('WATER', 'Hàm ẩm', '%', 5, TRUE, 6)
				""");
		long sampleWithResult = sampleInTesting();
		enter(sampleWithResult, "WATER", "3.2", "kiemnghiem1");
		jdbcTemplate.update("UPDATE test_parameter SET active = FALSE WHERE code = 'WATER'");
		long freshSample = sampleInTesting();

		assertThatThrownBy(() -> enter(freshSample, "WATER", "3.2", "kiemnghiem1"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("PARAMETER_INACTIVE");
				assertThat(ex).hasMessage("Chỉ tiêu \"Hàm ẩm\" đã ngừng sử dụng");
			});
		assertThat(resultService.findSheet(freshSample)).extracting(row -> row.parameter().getCode())
			.containsExactly("ASSAY", "PH", "LOD", "DISSOLUTION", "IMPURITY_TOTAL");

		ResultSheetRow water = sheetRow(sampleWithResult, "WATER");
		assertThat(water.effective().getValue().toPlainString()).isEqualTo("3.2");
		assertThat(water.awaitingEntry()).isFalse();
	}

	@Test
	@DisplayName("TC-BR07-04 Đổi tiêu chuẩn sau khi nhập: kết quả cũ giữ tiêu chuẩn và đánh giá cũ; lần nhập sau dùng tiêu chuẩn mới")
	void specChangeDoesNotAffectExistingResult() {
		long before = sampleInTesting();
		enter(before, "ASSAY", "96", "kiemnghiem1");

		jdbcTemplate.update("UPDATE test_parameter SET spec_min = 97, spec_max = 103 WHERE code = 'ASSAY'");
		long after = sampleInTesting();
		enter(after, "ASSAY", "96", "kiemnghiem1");

		TestResult old = sheetRow(before, "ASSAY").effective();
		assertThat(old.getSpecMin().toPlainString()).isEqualTo("95");
		assertThat(old.getSpecMax().toPlainString()).isEqualTo("105");
		assertThat(old.getVerdict()).isEqualTo(Verdict.PASS);
		assertThat(sheetRow(before, "ASSAY").specLabel()).isEqualTo("95 – 105");

		TestResult fresh = sheetRow(after, "ASSAY").effective();
		assertThat(fresh.getSpecMin().toPlainString()).isEqualTo("97");
		assertThat(fresh.getSpecMax().toPlainString()).isEqualTo("103");
		assertThat(fresh.getVerdict()).isEqualTo(Verdict.FAIL);
	}

	@Test
	@DisplayName("TC-BR08-08 Hủy mẫu còn kết quả Chờ duyệt: kết quả giữ Chờ duyệt, không duyệt được nữa, số dòng audit không đổi")
	void cancellingSampleLeavesPendingResultUntouched() {
		long sampleId = sampleInTesting();
		TestResult ph = enter(sampleId, "PH", "6.2", "kiemnghiem1");
		int auditBefore = auditRows(sampleId);

		sampleService.cancel(sampleId, "Gửi nhầm mẫu", "tiepnhan");

		assertThatThrownBy(() -> resultService.approve(sampleId, ph.getId(), "duyet"))
			.satisfies(businessError("SAMPLE_NOT_IN_TESTING"));
		assertThat(sheetRow(sampleId, "PH").effective().getStatus()).isEqualTo(ResultStatus.PENDING);
		assertThat(auditRows(sampleId)).isEqualTo(auditBefore);
	}

	@Test
	@DisplayName("TC-BR09-02 Nhập → từ chối → nhập lại: còn 2 dòng, bản cũ Đã từ chối kèm lý do, bản mới trỏ về bản cũ và là bản hiệu lực duy nhất")
	void reEntryAfterRejectionKeepsBothRows() {
		long sampleId = sampleInTesting();
		TestResult first = enter(sampleId, "PH", "6.2", "kiemnghiem1");

		resultService.reject(sampleId, first.getId(), "  Sai độ pha loãng  ", "duyet");
		TestResult second = enter(sampleId, "PH", "6.30", "kiemnghiem1");

		ResultSheetRow ph = sheetRow(sampleId, "PH");
		assertThat(ph.effective().getId()).isEqualTo(second.getId());
		assertThat(ph.effective().getStatus()).isEqualTo(ResultStatus.PENDING);
		assertThat(ph.effective().getReplacesId()).isEqualTo(first.getId());
		assertThat(ph.effective().getValue().toPlainString()).isEqualTo("6.30");
		assertThat(ph.effective().getAttemptNo()).isEqualTo(2);
		assertThat(ph.rejected()).singleElement().satisfies(old -> {
			assertThat(old.getId()).isEqualTo(first.getId());
			assertThat(old.getStatus()).isEqualTo(ResultStatus.REJECTED);
			assertThat(old.getRejectReason()).isEqualTo("Sai độ pha loãng");
			assertThat(old.getReviewedBy()).isEqualTo("duyet");
			assertThat(old.getReviewedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(7));
			assertThat(old.getAttemptNo()).isEqualTo(1);
		});
	}

	@Test
	@DisplayName("TC-BR10-01 Nhập, duyệt, từ chối: mỗi thao tác đúng 1 dòng audit, đúng người, giờ +07:00, chi tiết là dòng kết quả sau thao tác")
	void eachSuccessfulOperationWritesOneAuditRow() {
		long sampleId = sampleInTesting();
		TestResult assay = enter(sampleId, "ASSAY", "98.50", "kiemnghiem1");
		assertThat(auditRows(sampleId)).isEqualTo(1);
		resultService.approve(sampleId, assay.getId(), "duyet");
		assertThat(auditRows(sampleId)).isEqualTo(2);
		TestResult ph = enter(sampleId, "PH", "6.2", "kiemnghiem2");
		assertThat(auditRows(sampleId)).isEqualTo(3);
		resultService.reject(sampleId, ph.getId(), "Sai độ pha loãng", "duyet2");
		assertThat(auditRows(sampleId)).isEqualTo(4);

		assertThat(jdbcTemplate.queryForList("""
				SELECT a.action, a.actor, a.detail ->> 'value' AS value, a.detail ->> 'status' AS status,
				       a.detail ->> 'rejectReason' AS reason,
				       a.acted_at = CASE a.action WHEN 'ENTER' THEN r.entered_at ELSE r.reviewed_at END AS same_time
				FROM result_audit a JOIN test_result r ON r.id = a.result_id
				WHERE r.sample_id = ?
				ORDER BY a.id
				""", sampleId))
			.extracting(row -> tuple(row.get("action"), row.get("actor"), row.get("value"), row.get("status"),
					row.get("reason"), row.get("same_time")))
			.containsExactly(
					tuple("ENTER", "kiemnghiem1", "98.50", "PENDING", null, true),
					tuple("APPROVE", "duyet", "98.50", "APPROVED", null, true),
					tuple("ENTER", "kiemnghiem2", "6.2", "PENDING", null, true),
					tuple("REJECT", "duyet2", "6.2", "REJECTED", "Sai độ pha loãng", true));

		List<ResultAudit> audit = resultService.findAudit(sampleId);
		assertThat(audit).extracting(ResultAudit::getAction, ResultAudit::getActor, ResultAudit::getValue)
			.containsExactly(tuple(ResultAction.ENTER, "kiemnghiem1", "98.50"),
					tuple(ResultAction.APPROVE, "duyet", "98.50"), tuple(ResultAction.ENTER, "kiemnghiem2", "6.2"),
					tuple(ResultAction.REJECT, "duyet2", "6.2"));
		assertThat(audit).allSatisfy(row -> assertThat(row.getActedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(7)));
	}

	@Test
	@DisplayName("TC-BR10-02 Thao tác bị chặn (4 mắt, đã xử lý, trùng hiệu lực, mẫu không Đang kiểm nghiệm) không thêm dòng audit nào")
	void blockedOperationsWriteNoAuditRow() {
		long sampleId = sampleInTesting();
		TestResult own = enter(sampleId, "ASSAY", "98.50", "duyet");
		TestResult ph = enter(sampleId, "PH", "6.2", "kiemnghiem1");
		resultService.approve(sampleId, ph.getId(), "duyet");
		int auditBefore = auditRows(sampleId);

		assertThatThrownBy(() -> resultService.approve(sampleId, own.getId(), "duyet"))
			.satisfies(businessError("FOUR_EYES"));
		assertThatThrownBy(() -> resultService.reject(sampleId, ph.getId(), "Sai độ pha loãng", "duyet2"))
			.satisfies(businessError("RESULT_ALREADY_PROCESSED"));
		assertThatThrownBy(() -> enter(sampleId, "PH", "6.3", "kiemnghiem2"))
			.satisfies(businessError("RESULT_ALREADY_EFFECTIVE"));
		assertThat(auditRows(sampleId)).isEqualTo(auditBefore);

		sampleService.cancel(sampleId, "Gửi nhầm mẫu", "tiepnhan");
		assertThatThrownBy(() -> enter(sampleId, "LOD", "0.2", "kiemnghiem1"))
			.satisfies(businessError("SAMPLE_NOT_IN_TESTING"));
		assertThat(auditRows(sampleId)).isEqualTo(auditBefore);
	}

	/**
	 * Không có transaction của test bao ngoài, để mỗi lời gọi service tự commit hoặc rollback như khi chạy thật.
	 * Mẫu và kết quả đầu tiên được commit và ở lại trong CSDL (SPEC-004 L1); hai thao tác lỗi thì rollback nên
	 * không để lại gì.
	 */
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@DisplayName("TC-BR10-03 Ghi audit lỗi thì thao tác rollback theo: không có kết quả mới, kết quả đang duyệt vẫn Chờ duyệt")
	void auditFailureRollsBackOperation() {
		long sampleId = sampleInTesting();
		TestResult ph = enter(sampleId, "PH", "6.2", "kiemnghiem1");
		doThrow(new IllegalStateException("Mô phỏng lỗi ghi audit")).when(auditMapper)
			.insert(anyLong(), any(), any(), any());

		assertThatThrownBy(() -> enter(sampleId, "ASSAY", "98.50", "kiemnghiem1"))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> resultService.approve(sampleId, ph.getId(), "duyet"))
			.isInstanceOf(IllegalStateException.class);

		assertThat(jdbcTemplate.queryForList("SELECT status FROM test_result WHERE sample_id = ?", String.class,
				sampleId))
			.containsExactly("PENDING");
		assertThat(auditRows(sampleId)).isEqualTo(1);
		assertThat(sampleService.findById(sampleId).orElseThrow().getStatus()).isEqualTo(SampleStatus.IN_TESTING);
	}

}
