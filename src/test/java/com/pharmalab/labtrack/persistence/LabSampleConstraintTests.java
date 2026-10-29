package com.pharmalab.labtrack.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;

import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * Ràng buộc kết luận của V5 (BR-11) và mức ưu tiên của V6 (BR-13), thử bằng SQL ghi thẳng qua {@link JdbcTemplate}:
 * CSDL vẫn phải chặn khi tầng nghiệp vụ bị bỏ qua. Mỗi câu vi phạm chạy trong một savepoint và lỗi được nhận diện bằng
 * tên ràng buộc, cùng lý do như {@link TestResultConstraintTests}.
 */
@SpringBootTest
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

	private int codeSeq = 0;

	private static final String INSERT_SAMPLE = """
			INSERT INTO lab_sample (code, product_name, batch_no, sample_type, received_at, due_date, created_by)
			VALUES (?, ?, ?, ?, ?::timestamptz, ?::date, 'tiepnhan')""";

	/** Mã LS-2001-9nnnn duy nhất trong test; mỗi câu INSERT thành công cần một mã khác. */
	private String nextCode() {
		return "LS-2001-9%04d".formatted(++codeSeq);
	}

	private Object[] row(String productName, String batchNo, String sampleType, String receivedAt, String dueDate) {
		return new Object[] { nextCode(), productName, batchNo, sampleType, receivedAt, dueDate };
	}

	private Object[] validRow() {
		return row("Paracetamol 500 mg", "PA-2601", "FINISHED_PRODUCT", "2026-10-09T10:00+07:00", "2026-10-16");
	}

	private Object[] withValue(int index, Object value) {
		Object[] row = validRow();
		row[index] = value;
		return row;
	}

	private void assertSqlState(String sqlState, Object[] row) {
		assertRejected(INSERT_SAMPLE, row).rootCause()
			.isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(sqlState));
	}

	@Test
	@DisplayName("TC-BR02-25 Ghi thẳng: hạn trả trước ngày nhận theo giờ VN bị ck_lab_sample_due_date chặn, kể cả lúc 00:10 VN khi UTC còn hôm trước")
	void dueDateIsComparedWithVietnamReceivedDate() {
		// 2026-10-09T17:10Z = 00:10 ngày 10/10 giờ VN.
		String receivedAt = "2026-10-09T17:10:00Z";
		assertViolates("ck_lab_sample_due_date", INSERT_SAMPLE,
				row("Paracetamol", "PA-2601", "FINISHED_PRODUCT", receivedAt, "2026-10-09"));

		assertThat(jdbcTemplate.update(INSERT_SAMPLE,
				row("Paracetamol", "PA-2601", "FINISHED_PRODUCT", receivedAt, "2026-10-10")))
			.isEqualTo(1);
	}

	@Test
	@DisplayName("TC-BR02-26 Ghi thẳng: số lô có khoảng trắng, chữ có dấu, _ hoặc rỗng bị ck_lab_sample_batch chặn; A-1/2 ghi được")
	void batchNoIsConstrained() {
		for (String bad : new String[] { "PA 2601", "LÔ-01", "PA_01", "" }) {
			assertViolates("ck_lab_sample_batch", INSERT_SAMPLE, withValue(2, bad));
		}
		assertThat(jdbcTemplate.update(INSERT_SAMPLE, withValue(2, "A-1/2"))).isEqualTo(1);
	}

	@Test
	@DisplayName("TC-BR02-27 Ghi thẳng: tên sản phẩm rỗng hoặc chỉ khoảng trắng bị ck_lab_sample_product chặn")
	void productNameMustNotBeBlank() {
		assertViolates("ck_lab_sample_product", INSERT_SAMPLE, withValue(1, ""));
		assertViolates("ck_lab_sample_product", INSERT_SAMPLE, withValue(1, "   "));
	}

	@Test
	@DisplayName("TC-BR02-28 Ghi thẳng: tên sản phẩm chỉ gồm tab, xuống dòng hoặc ký tự điều khiển U+0001..U+0020 phải bị ck_lab_sample_product chặn")
	void productNameOfOnlyWhitespaceControlCharsIsRejected() {
		for (String bad : new String[] { "\t", "\n", " \t\r\n ", "\f", "\u000B", "\u001F", "\u0001" }) {
			assertViolates("ck_lab_sample_product", INSERT_SAMPLE, withValue(1, bad));
		}
	}

	@Test
	@DisplayName("TC-BR02-39 Ghi thẳng: tên có tab ở giữa (\"Para\\tcetamol\") hoặc một ký tự giữa các dấu cách (\" A \") ghi được, ck_lab_sample_product không chặn quá tay")
	void productNameWithContentIsAccepted() {
		assertThat(jdbcTemplate.update(INSERT_SAMPLE, withValue(1, "Para\tcetamol"))).isEqualTo(1);
		assertThat(jdbcTemplate.update(INSERT_SAMPLE, withValue(1, " A "))).isEqualTo(1);
	}

	@Test
	@DisplayName("TC-BR02-29 Ghi thẳng: loại mẫu ngoài 4 hằng (TABLET, raw_material) bị ck_lab_sample_type chặn")
	void sampleTypeIsConstrained() {
		assertViolates("ck_lab_sample_type", INSERT_SAMPLE, withValue(3, "TABLET"));
		assertViolates("ck_lab_sample_type", INSERT_SAMPLE, withValue(3, "raw_material"));
	}

	@Test
	@DisplayName("TC-BR02-30 Ghi thẳng: NULL ở tên sản phẩm, số lô, loại mẫu, thời điểm nhận, hạn trả bị chặn (SQLSTATE 23502)")
	void requiredColumnsRejectNull() {
		for (int index = 1; index <= 5; index++) {
			assertSqlState("23502", withValue(index, null));
		}
	}

	@Test
	@DisplayName("TC-BR02-31 Ghi thẳng: tên 201 ký tự và số lô 51 ký tự bị chặn (SQLSTATE 22001); 200 và 50 ký tự ghi được")
	void columnLengthsAreLimited() {
		assertSqlState("22001", withValue(1, "Đ".repeat(201)));
		assertSqlState("22001", withValue(2, "A".repeat(51)));

		assertThat(jdbcTemplate.update(INSERT_SAMPLE, withValue(1, "Đ".repeat(200)))).isEqualTo(1);
		assertThat(jdbcTemplate.update(INSERT_SAMPLE, withValue(2, "A".repeat(50)))).isEqualTo(1);
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
		// NOT NULL không mang tên tự đặt, còn câu báo lỗi đổi theo lc_messages của máy chủ (CSDL labtrack_test trên
		// máy này báo tiếng Nhật): nhận diện bằng SQLSTATE 23502 not_null_violation.
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
