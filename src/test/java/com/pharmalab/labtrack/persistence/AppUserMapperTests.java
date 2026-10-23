package com.pharmalab.labtrack.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.pharmalab.labtrack.TestcontainersConfiguration;
import com.pharmalab.labtrack.domain.AppUser;
import com.pharmalab.labtrack.domain.UserRole;

/**
 * Tài khoản demo do {@code db/devdata/R__dev_seed.sql} nạp, đọc qua MyBatis trên PostgreSQL thật.
 * Hash mật khẩu được dán tay vào file seed, nên chỉ test này phát hiện được hash sai: khi đó không
 * ai đăng nhập được, mà ứng dụng vẫn khởi động bình thường.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AppUserMapperTests {

	@Autowired
	private AppUserMapper userMapper;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@ParameterizedTest(name = "[{index}] {0}: {1}")
	@DisplayName("TC-BR12-05 Dữ liệu demo có đủ 5 tài khoản của LT-BRD mục 4, đúng vai trò, mật khẩu Passw0rd! khớp hash")
	@CsvSource(textBlock = """
			# tên đăng nhập  vai trò
			tiepnhan,        RECEPTIONIST
			kiemnghiem1,     ANALYST
			kiemnghiem2,     ANALYST
			duyet,           REVIEWER
			quanly,          MANAGER
			""")
	void devSeedAccount(String username, UserRole role) {
		AppUser user = userMapper.findByUsername(username).orElseThrow();

		assertThat(user.getRole()).isEqualTo(role);
		assertThat(user.isEnabled()).isTrue();
		assertThat(user.getFullName()).isNotBlank();
		assertThat(user.getPasswordHash()).startsWith("{bcrypt}$2a$10$");
		assertThat(passwordEncoder.matches("Passw0rd!", user.getPasswordHash())).isTrue();
	}

}
