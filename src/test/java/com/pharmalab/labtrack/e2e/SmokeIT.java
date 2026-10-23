package com.pharmalab.labtrack.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/** Kiểm tra hạ tầng E2E chạy được: Tomcat, CSDL, trình duyệt, ảnh chụp và trace. Chưa có kịch bản nghiệp vụ. */
class SmokeIT extends E2ETestBase {

	@Test
	@DisplayName("TC-E2E-00 Khói: mở /login bằng trình duyệt thật, thấy nút Đăng nhập")
	void loginPageShowsSignInButton() {
		page.navigate(url("/login"));

		assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Đăng nhập"))).isVisible();
	}

}
