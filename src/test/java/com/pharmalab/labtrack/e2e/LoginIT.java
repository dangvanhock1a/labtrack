package com.pharmalab.labtrack.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/** UC-01 đăng nhập, đăng xuất (BR-12). Kịch bản ở {@code docs/kich-ban-e2e.md}. */
class LoginIT extends E2ETestBase {

	private static final String WRONG_CREDENTIALS = "Tên đăng nhập hoặc mật khẩu không đúng";

	@Test
	@DisplayName("TC-E2E-01 Sai mật khẩu hoặc tên không tồn tại: cùng một câu báo lỗi, không vào được hệ thống (BR-12)")
	void wrongCredentialsStayOnLoginPage() {
		page.navigate(url("/login"));
		submitLogin("kiemnghiem1", "SaiMatKhau1");

		assertThat(page).hasURL(url("/login?error"));
		assertThat(page.getByRole(AriaRole.ALERT)).hasText(WRONG_CREDENTIALS);
		assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Đăng xuất"))).hasCount(0);
		screenshot("01-sai-mat-khau");

		submitLogin("khongco", "Passw0rd!");

		// Trang đăng nhập không điền lại tên, nên ô trống nghĩa là trang đã tải lại sau lần gửi thứ hai.
		assertThat(page.getByLabel("Tên đăng nhập")).isEmpty();
		assertThat(page.getByRole(AriaRole.ALERT)).hasText(WRONG_CREDENTIALS);
		screenshot("02-ten-khong-ton-tai");
	}

	@Test
	@DisplayName("TC-E2E-02 Đăng nhập đúng: vào danh sách mẫu, navbar có họ tên và vai trò; đăng xuất xong phải đăng nhập lại (BR-12)")
	void signInShowsUserThenSignOutEndsSession() {
		login("tiepnhan");

		assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Danh sách mẫu"))).isVisible();
		Locator navbar = page.getByRole(AriaRole.NAVIGATION);
		assertThat(navbar).containsText("Trần Thị Thu");
		assertThat(navbar).containsText("Tiếp nhận mẫu");
		assertThat(navbar.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Đăng ký mẫu"))).isVisible();
		assertThat(page.getByRole(AriaRole.MAIN)).containsText("Chưa có mẫu nào.");
		screenshot("01-da-dang-nhap");

		logout();

		assertThat(page.getByRole(AriaRole.STATUS)).hasText("Bạn đã đăng xuất");
		screenshot("02-da-dang-xuat");

		page.navigate(url("/samples"));

		assertThat(page).hasURL(url("/login"));
	}

}
