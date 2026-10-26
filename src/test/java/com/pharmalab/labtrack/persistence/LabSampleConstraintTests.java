package com.pharmalab.labtrack.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Ràng buộc kết luận của V5 (BR-11), thử bằng SQL ghi thẳng qua {@link JdbcTemplate}: CSDL vẫn phải chặn khi
 * {@code LabSampleService.conclude} bị bỏ qua. Mỗi câu vi phạm chạy trong một savepoint và lỗi được nhận diện bằng
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

	/** Câu lệnh bị CSDL chặn, và lỗi nêu đúng tên {@code constraint}. Chạy trong savepoint, xem Javadoc lớp. */
	private void assertViolates(String constraint, String sql, Object... args) {
		TransactionTemplate savepoint = new TransactionTemplate(transactionManager);
		savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
		assertThatThrownBy(() -> savepoint.executeWithoutResult(status -> jdbcTemplate.update(sql, args)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining(constraint);
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

}
