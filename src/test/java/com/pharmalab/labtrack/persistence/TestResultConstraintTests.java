package com.pharmalab.labtrack.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.service.LabSampleService;
import com.pharmalab.labtrack.service.NewSample;

/**
 * Ràng buộc và trigger của V4 (BR-06..BR-10), thử bằng SQL ghi thẳng qua {@link JdbcTemplate}, không qua
 * service hay mapper: CSDL vẫn phải chặn khi mọi lớp phía trên bị bỏ qua.
 * <p>
 * PostgreSQL hủy cả transaction sau câu lỗi đầu tiên, nên mỗi câu vi phạm chạy trong một savepoint
 * ({@code PROPAGATION_NESTED}) để câu sau vẫn chạy được. Lỗi được nhận diện bằng tên ràng buộc hoặc trigger,
 * không bằng câu chữ, vì máy chủ có thể trả thông báo theo ngôn ngữ hệ điều hành. Trigger mức dòng chỉ chạy
 * khi có dòng khớp, nên lệnh UPDATE/DELETE luôn nhắm vào dòng vừa tạo.
 */
@SpringBootTest
@Transactional
class TestResultConstraintTests {

	/** Kết quả ASSAY (tiêu chuẩn 95–105 %) Chờ duyệt. Giá trị truyền dạng chuỗi để giữ đúng cách viết. */
	private static final String INSERT_ASSAY = """
			INSERT INTO test_result (sample_id, parameter_id, value, unit, spec_min, spec_max, verdict, entered_by)
			VALUES (?, ?, ?::numeric, '%', 95, 105, ?, 'kiemnghiem1')""";

	/** Bản nhập lại của kết quả ASSAY bị từ chối; các giá trị dùng ở đây đều nằm trong 95–105. */
	private static final String INSERT_ASSAY_REPLACEMENT = """
			INSERT INTO test_result (sample_id, parameter_id, value, unit, spec_min, spec_max, verdict, entered_by,
			                         replaces_id)
			VALUES (?, ?, ?::numeric, '%', 95, 105, 'PASS', 'kiemnghiem1', ?)""";

	private static final String REVIEW = """
			UPDATE test_result SET status = ?, reviewed_by = ?, reviewed_at = now(), reject_reason = ?
			WHERE id = ?""";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private LabSampleService sampleService;

	private long sampleId;

	private long assayId;

	@BeforeEach
	void sampleInTesting() {
		sampleId = newSampleInTesting();
		assayId = parameterId("ASSAY");
	}

	private long newSampleInTesting() {
		LabSample sample = sampleService.register(
				new NewSample("Paracetamol 500 mg", "PA-2601", SampleType.FINISHED_PRODUCT, null, null), "tiepnhan");
		sampleService.startTesting(sample.getId(), "kiemnghiem1");
		return sample.getId();
	}

	private long parameterId(String code) {
		return jdbcTemplate.queryForObject("SELECT id FROM test_parameter WHERE code = ?", Long.class, code);
	}

	private long insertAssay(long sample, String value, String verdict) {
		return jdbcTemplate.queryForObject(INSERT_ASSAY + " RETURNING id", Long.class, sample, assayId, value,
				verdict);
	}

	private void review(long resultId, String status, String reviewer, String reason) {
		jdbcTemplate.update(REVIEW, status, reviewer, reason, resultId);
	}

	private String status(long resultId) {
		return jdbcTemplate.queryForObject("SELECT status FROM test_result WHERE id = ?", String.class, resultId);
	}

	/** Câu lệnh bị CSDL chặn, và lỗi nêu đúng tên {@code constraint}. Chạy trong savepoint, xem Javadoc lớp. */
	private void assertViolates(String constraint, String sql, Object... args) {
		TransactionTemplate savepoint = new TransactionTemplate(transactionManager);
		savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
		assertThatThrownBy(() -> savepoint.executeWithoutResult(status -> jdbcTemplate.update(sql, args)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining(constraint);
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@DisplayName("TC-BR06-05 Ghi thẳng giá trị âm, quá 4 chữ số thập phân (kể cả số 0 ở cuối) hoặc từ 100000000 trở lên thì bị ck_test_result_value chặn")
	@CsvSource(textBlock = """
			# giá trị,  đánh giá khớp tiêu chuẩn, để chỉ vi phạm ck_test_result_value
			-0.0001,    FAIL
			1.23456,    FAIL
			98.50000,   PASS
			100000000,  FAIL
			1e8,        FAIL
			""")
	void invalidValueIsRejected(String value, String verdict) {
		assertViolates("ck_test_result_value", INSERT_ASSAY, sampleId, assayId, value, verdict);
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@DisplayName("TC-BR06-05 Giá trị hợp lệ ở biên ghi được và đọc lại đúng cách viết, không bị làm tròn hay thêm số 0")
	@CsvSource(textBlock = """
			0,              FAIL
			0.0000,         FAIL
			98.50,          PASS
			99999999.9999,  FAIL
			""")
	void validValueIsStoredAsWritten(String value, String verdict) {
		long resultId = insertAssay(sampleId, value, verdict);

		assertThat(jdbcTemplate.queryForObject("SELECT value::text FROM test_result WHERE id = ?", String.class,
				resultId))
			.isEqualTo(value);
	}

	@Test
	@DisplayName("TC-BR06-07 Hai kết quả hiệu lực cho cùng mẫu và chỉ tiêu bị uq_test_result_effective chặn; bản Đã từ chối không tính nên nhập lại được")
	void onlyOneEffectiveResultPerSampleAndParameter() {
		long first = insertAssay(sampleId, "98.50", "PASS");
		assertViolates("uq_test_result_effective", INSERT_ASSAY, sampleId, assayId, "99.10", "PASS");

		review(first, "REJECTED", "duyet", "Sai độ pha loãng");
		long second = insertAssay(sampleId, "99.10", "PASS");

		review(second, "APPROVED", "duyet", null);
		assertViolates("uq_test_result_effective", INSERT_ASSAY, sampleId, assayId, "99.20", "PASS");

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM test_result WHERE sample_id = ?", Integer.class,
				sampleId))
			.isEqualTo(2);
	}

	/** Cờ {@code active} không chặn được bằng ràng buộc trên một bảng; phần đó do service lo (TC-BR06-02). */
	@Test
	@DisplayName("TC-BR06-12 Kết quả trỏ tới chỉ tiêu không tồn tại, hoặc xóa chỉ tiêu đã có kết quả, đều bị fk_test_result_parameter chặn")
	void resultMustReferenceExistingParameter() {
		assertViolates("fk_test_result_parameter", INSERT_ASSAY, sampleId, -1L, "98.50", "PASS");

		insertAssay(sampleId, "98.50", "PASS");
		assertViolates("fk_test_result_parameter", "DELETE FROM test_parameter WHERE id = ?", assayId);
	}

	@Test
	@DisplayName("TC-BR07-03 Verdict ghi thẳng không khớp giá trị và tiêu chuẩn đã chụp bị ck_test_result_verdict_spec chặn: 105.04 Đạt với 95–105, 80 Không đạt với ≥ 80")
	void verdictMustMatchValueAndSpec() {
		String insertDissolution = """
				INSERT INTO test_result (sample_id, parameter_id, value, unit, spec_min, spec_max, verdict, entered_by)
				VALUES (?, ?, 80, '%', 80, NULL, ?, 'kiemnghiem1')""";
		long dissolutionId = parameterId("DISSOLUTION");

		assertViolates("ck_test_result_verdict_spec", INSERT_ASSAY, sampleId, assayId, "105.04", "PASS");
		assertViolates("ck_test_result_verdict_spec", insertDissolution, sampleId, dissolutionId, "FAIL");

		// Cùng giá trị và tiêu chuẩn, verdict đúng thì ghi được.
		insertAssay(sampleId, "105.04", "FAIL");
		assertThat(jdbcTemplate.update(insertDissolution, sampleId, dissolutionId, "PASS")).isEqualTo(1);
	}

	@Test
	@DisplayName("TC-BR08-05 Ghi thẳng người duyệt trùng người nhập bị ck_four_eyes chặn; người khác duyệt thì ghi được")
	void reviewerMustDifferFromEnterer() {
		long resultId = insertAssay(sampleId, "98.50", "PASS");

		assertViolates("ck_four_eyes", REVIEW, "APPROVED", "kiemnghiem1", null, resultId);
		assertViolates("ck_four_eyes", REVIEW, "REJECTED", "kiemnghiem1", "Sai độ pha loãng", resultId);

		review(resultId, "APPROVED", "duyet", null);
		assertThat(status(resultId)).isEqualTo("APPROVED");
	}

	@Test
	@DisplayName("TC-BR09-03 Từ chối thiếu lý do, lý do dưới 5 ký tự sau khi cắt khoảng trắng, hoặc duyệt mà có lý do, đều bị ck_test_result_reject_reason chặn")
	void rejectReasonRequiredOnlyWhenRejected() {
		long resultId = insertAssay(sampleId, "98.50", "PASS");

		assertViolates("ck_test_result_reject_reason", REVIEW, "REJECTED", "duyet", null, resultId);
		assertViolates("ck_test_result_reject_reason", REVIEW, "REJECTED", "duyet", "  sai  ", resultId);
		assertViolates("ck_test_result_reject_reason", REVIEW, "APPROVED", "duyet", "Sai độ pha loãng", resultId);

		review(resultId, "REJECTED", "duyet", "Sai độ pha loãng");
		assertThat(status(resultId)).isEqualTo("REJECTED");
	}

	@Test
	@DisplayName("TC-BR09-03 Bản bị từ chối chỉ được thay một lần (uq_test_result_replaces); bản thay phải cùng mẫu, cùng chỉ tiêu (fk_test_result_replaces)")
	void replacementIsUniqueAndMatchesSampleAndParameter() {
		long rejected = insertAssay(sampleId, "98.50", "PASS");
		review(rejected, "REJECTED", "duyet", "Sai độ pha loãng");
		long replacement = jdbcTemplate.queryForObject(INSERT_ASSAY_REPLACEMENT + " RETURNING id", Long.class,
				sampleId, assayId, "98.70", rejected);
		review(replacement, "REJECTED", "duyet", "Vẫn sai độ pha loãng");

		assertViolates("uq_test_result_replaces", INSERT_ASSAY_REPLACEMENT, sampleId, assayId, "98.90", rejected);
		// replacement đã bị từ chối và chưa ai thay, nên chỉ còn FK ghép bắt được sai mẫu, sai chỉ tiêu.
		assertViolates("fk_test_result_replaces", INSERT_ASSAY_REPLACEMENT, newSampleInTesting(), assayId, "98.90",
				replacement);
		assertViolates("fk_test_result_replaces", INSERT_ASSAY_REPLACEMENT, sampleId, parameterId("PH"), "98.90",
				replacement);
	}

	@Test
	@DisplayName("TC-BR10-04 result_audit chỉ thêm: UPDATE, DELETE, TRUNCATE ghi thẳng đều bị trigger chặn")
	void resultAuditIsAppendOnly() {
		long resultId = insertAssay(sampleId, "98.50", "PASS");
		long auditId = jdbcTemplate.queryForObject("""
				INSERT INTO result_audit (result_id, action, actor, detail)
				VALUES (?, 'ENTER', 'kiemnghiem1', '{"value": 98.50}'::jsonb)
				RETURNING id""", Long.class, resultId);

		assertViolates("tg_result_audit_append_only", "UPDATE result_audit SET actor = 'x' WHERE id = ?", auditId);
		assertViolates("tg_result_audit_append_only", "DELETE FROM result_audit WHERE id = ?", auditId);
		assertViolates("tg_result_audit_no_truncate", "TRUNCATE result_audit");
	}

	@Test
	@DisplayName("TC-BR10-05 test_result không xóa được, và chỉ được UPDATE đúng một lần từ PENDING sang APPROVED/REJECTED ở 4 cột xử lý")
	void testResultChangesOnlyOnceFromPending() {
		long resultId = insertAssay(sampleId, "98.50", "PASS");

		assertViolates("tg_test_result_no_delete", "DELETE FROM test_result WHERE id = ?", resultId);
		assertViolates("tg_test_result_guard_update", "UPDATE test_result SET unit = '%' WHERE id = ?", resultId);
		// Bước duyệt hợp lệ nhưng sửa kèm cột khác: 98.5 bằng 98.50 về giá trị, chỉ khác cách viết, vẫn bị chặn.
		assertViolates("tg_test_result_guard_update", """
				UPDATE test_result SET status = 'APPROVED', reviewed_by = 'duyet', reviewed_at = now(), value = 98.5
				WHERE id = ?""", resultId);
		assertViolates("tg_test_result_guard_update", """
				UPDATE test_result SET status = 'APPROVED', reviewed_by = 'duyet', reviewed_at = now(),
				       entered_by = 'kiemnghiem2'
				WHERE id = ?""", resultId);

		review(resultId, "APPROVED", "duyet", null);
		assertThat(status(resultId)).isEqualTo("APPROVED");
		assertViolates("tg_test_result_guard_update",
				"UPDATE test_result SET status = 'PENDING', reviewed_by = NULL, reviewed_at = NULL WHERE id = ?",
				resultId);

		long rejected = insertAssay(newSampleInTesting(), "98.50", "PASS");
		review(rejected, "REJECTED", "duyet", "Sai độ pha loãng");
		assertViolates("tg_test_result_guard_update",
				"UPDATE test_result SET status = 'APPROVED', reject_reason = NULL WHERE id = ?", rejected);
	}

}
