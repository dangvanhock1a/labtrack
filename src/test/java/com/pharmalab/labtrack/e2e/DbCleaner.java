package com.pharmalab.labtrack.e2e;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Xóa dữ liệu nghiệp vụ trước mỗi test E2E, giữ tài khoản demo ({@code R__dev_seed.sql}) và danh mục chỉ tiêu
 * (V4). Trình duyệt gọi Tomcat thật nên test không bọc được trong transaction để rollback: dữ liệu được commit.
 * <p>
 * Bảng cần xóa lấy từ catalog chứ không liệt kê tay, để bảng nghiệp vụ do migration sau thêm vào cũng được dọn.
 * Không dùng {@code CASCADE}: nếu bảng được giữ lại có khóa ngoại trỏ tới bảng bị xóa thì TRUNCATE báo lỗi,
 * thay vì âm thầm xóa luôn tài khoản hay chỉ tiêu.
 * <p>
 * V4 chặn TRUNCATE {@code test_result} và {@code result_audit} bằng trigger (NFR-03). Ở đây tắt trigger do người
 * viết (không đụng trigger nội bộ của khóa ngoại) rồi bật lại trong cùng một transaction, nên lỗi giữa chừng thì
 * rollback trả trigger về như cũ. Cách này chỉ cần quyền chủ bảng, không cần superuser như
 * {@code session_replication_role}. Vì nó vượt qua kiểm soát chỉ-thêm, lớp này từ chối chạy trên CSDL không
 * mang tên của CSDL test.
 */
final class DbCleaner {

	private final JdbcTemplate jdbcTemplate;

	private final TransactionTemplate transactionTemplate;

	DbCleaner(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	void clean() {
		// Hỏi chính server đang kết nối thay vì đọc TEST_DB_URL: biến có thể chưa đặt, và URL thật còn qua tầng
		// cấu hình của Spring. CSDL test theo CLAUDE.md là labtrack_test.
		String database = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
		if (!database.endsWith("_test")) {
			throw new IllegalStateException("Từ chối dọn CSDL \"" + database
					+ "\": chỉ dọn CSDL tên kết thúc bằng \"_test\"");
		}
		List<String> tables = jdbcTemplate.queryForList("""
				SELECT quote_ident(tablename)
				FROM pg_tables
				WHERE schemaname = current_schema()
				  AND tablename NOT IN ('flyway_schema_history', 'app_user', 'test_parameter')
				ORDER BY tablename""", String.class);
		if (tables.isEmpty()) {
			return;
		}
		transactionTemplate.executeWithoutResult(status -> {
			tables.forEach(table -> jdbcTemplate.execute("ALTER TABLE " + table + " DISABLE TRIGGER USER"));
			// RESTART IDENTITY cùng với việc xóa sample_code_counter: mỗi test bắt đầu từ id 1 và LS-YYYY-00001.
			jdbcTemplate.execute("TRUNCATE " + String.join(", ", tables) + " RESTART IDENTITY");
			tables.forEach(table -> jdbcTemplate.execute("ALTER TABLE " + table + " ENABLE TRIGGER USER"));
		});
	}

}
