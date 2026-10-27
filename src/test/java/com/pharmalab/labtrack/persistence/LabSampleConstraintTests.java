package com.pharmalab.labtrack.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;

import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.pharmalab.labtrack.TestcontainersConfiguration;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.service.LabSampleService;
import com.pharmalab.labtrack.service.NewSample;

/**
 * Ràng buộc kết luận của V5 (BR-11) và mức ưu tiên của V6 (BR-13), thử bằng SQL ghi thẳng qua {@link JdbcTemplate}:
 * CSDL vẫn phải chặn khi tầng nghiệp vụ bị bỏ qua. Mỗi câu vi phạm chạy trong một savepoint và lỗi được nhận diện bằng
 * tên ràng buộc, cùng lý do như {@link TestResultConstraintTests}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class LabSampleConstraintTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private LabSampleService sampleService;

	private long newSampleInTesting() {
		LabSample sample = sampleService.register(
				new NewSample("Paracetamol 500 mg", "PA-2601", SampleType.FINISHED_PRODUCT, null, null), "tiepnhan");
		sampleService.startTesting(sample.getId(), "kiemnghiem1");
		return sample.getId();
	}

	/** Câu lệnh bị CSDL chặn. Chạy trong savepoint, xem Javadoc lớp. */
	private AbstractThrowableAssert<?, ? extends Throwable> assertRejected(String sql, Object... args) {
		TransactionTemplate savepoint = new TransactionTemplate(transactionManager);
		savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
		return assertThatThrownBy(() -> savepoint.executeWithoutResult(status -> jdbcTemplate.update(sql, args)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	/** Câu lệnh bị CSDL chặn, và lỗi nêu đúng tên {@code constraint}. */
	private void assertViolates(String constraint, String sql, Object... args) {
		assertRejected(sql, args).hasMessageContaining(constraint);
	}

	@Test
	@DisplayName("TC-BR11-08 Ghi thẳng: Hoàn thành thiếu kết luận hay thời điểm hoàn thành, hoặc có kết luận mà chưa Hoàn thành, bị ck_lab_sample_completed chặn; kết luận ngoài PASS/FAIL bị ck_lab_sample_conclusion chặn")
	void conclusionColumnsFollowCompletedStatus() {
		long sampleId = newSampleInTesting();

		assertViolates("ck_lab_sample_completed", "UPDATE lab_sample SET status = 'COMPLETED' WHERE id = ?", sampleId);
		assertViolates("ck_lab_sample_completed",
				"UPDATE lab_sample SET status = 'COMPLETED', conclusion = 'PASS' WHERE id = ?", sampleId);
		assertViolates("ck_lab_sample_completed",
				"UPDATE lab_sample SET status = 'COMPLETED', completed_at = now() WHERE id = ?", sampleId);
		assertViolates("ck_lab_sample_completed",
				"UPDATE lab_sample SET conclusion = 'PASS', completed_at = now() WHERE id = ?", sampleId);
		assertViolates("ck_lab_sample_completed",
				"UPDATE lab_sample SET status = 'CANCELLED', conclusion = 'FAIL', completed_at = now() WHERE id = ?",
				sampleId);
		// 'pass' vừa cột VARCHAR(4): giá trị dài hơn sẽ báo lỗi độ dài, không tới được ràng buộc.
		assertViolates("ck_lab_sample_conclusion",
				"UPDATE lab_sample SET status = 'COMPLETED', conclusion = 'pass', completed_at = now() WHERE id = ?",
				sampleId);

		// Đủ cả ba cột thì ghi được.
		assertThat(jdbcTemplate.update(
				"UPDATE lab_sample SET status = 'COMPLETED', conclusion = 'FAIL', completed_at = now() WHERE id = ?",
				sampleId))
			.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT conclusion FROM lab_sample WHERE id = ?", String.class,
				sampleId))
			.isEqualTo("FAIL");
	}

	@Test
	@DisplayName("TC-BR13-02 Ghi thẳng: mức ưu tiên ngoài LOW/NORMAL/URGENT bị ck_lab_sample_priority chặn, NULL bị NOT NULL chặn; URGENT ghi được")
	void priorityColumnIsConstrained() {
		long sampleId = newSampleInTesting();

		assertViolates("ck_lab_sample_priority", "UPDATE lab_sample SET priority = 'HIGH' WHERE id = ?", sampleId);
		assertViolates("ck_lab_sample_priority", "UPDATE lab_sample SET priority = 'urgent' WHERE id = ?", sampleId);
		assertViolates("ck_lab_sample_priority", "UPDATE lab_sample SET priority = '' WHERE id = ?", sampleId);
		// NOT NULL không mang tên tự đặt, còn câu báo lỗi đổi theo lc_messages của máy chủ (CSDL localdb có thể
		// báo tiếng Nhật): nhận diện bằng SQLSTATE 23502 not_null_violation.
		assertRejected("UPDATE lab_sample SET priority = NULL WHERE id = ?", sampleId)
			.rootCause()
			.isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23502"));

		assertThat(jdbcTemplate.update("UPDATE lab_sample SET priority = 'URGENT' WHERE id = ?", sampleId))
			.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT priority FROM lab_sample WHERE id = ?", String.class, sampleId))
			.isEqualTo("URGENT");
	}

	@Test
	@DisplayName("TC-BR13-03 V6: priority là VARCHAR(10) NOT NULL DEFAULT 'NORMAL'; danh sách có index ix_lab_sample_status_received (status, received_at DESC)")
	void priorityColumnAndListIndexFollowV6() {
		assertThat(jdbcTemplate.queryForMap("""
				SELECT data_type, character_maximum_length, column_default, is_nullable
				FROM information_schema.columns
				WHERE table_schema = current_schema() AND table_name = 'lab_sample' AND column_name = 'priority'"""))
			.containsEntry("data_type", "character varying")
			.containsEntry("character_maximum_length", 10)
			.containsEntry("column_default", "'NORMAL'::character varying")
			.containsEntry("is_nullable", "NO");
		assertThat(jdbcTemplate.queryForObject("""
				SELECT indexdef FROM pg_indexes
				WHERE schemaname = current_schema() AND indexname = 'ix_lab_sample_status_received'""", String.class))
			.endsWith("USING btree (status, received_at DESC)");
	}

}
