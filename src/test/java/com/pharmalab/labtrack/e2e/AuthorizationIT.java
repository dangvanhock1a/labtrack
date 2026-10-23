package com.pharmalab.labtrack.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;

import com.pharmalab.labtrack.domain.LabSample;

/** Ai được làm gì trên giao diện: nguyên tắc 4 mắt (BR-08) và phân quyền (BR-12). */
class AuthorizationIT extends E2ETestBase {

	private static final String FOUR_EYES_NOTE = "Bạn đã nhập kết quả này; cần người duyệt khác (nguyên tắc 4 mắt)";

	@Test
	@DisplayName("TC-E2E-04 Người duyệt tự nhập kết quả: nút Duyệt, Từ chối bị vô hiệu kèm lời giải thích; người duyệt khác duyệt được (BR-08)")
	void reviewerCannotReviewOwnResult() {
		LabSample sample = seedSampleInTesting();
		login("duyet");
		openSample(sample.getCode());

		enterResult("pH", "6.2");

		assertThat(page.getByTestId("flash-success")).hasText("Đã nhập kết quả pH: 6.2 pH (Đạt)");
		assertThat(resultRow("pH").getByTestId("result-entered")).containsText(actorAt("duyet"));
		Locator approve = resultRow("pH").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Duyệt"));
		Locator reject = resultRow("pH").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Từ chối"));
		assertThat(approve).isDisabled();
		assertThat(reject).isDisabled();
		assertThat(approve).hasAccessibleDescription(FOUR_EYES_NOTE);
		assertThat(reject).hasAccessibleDescription(FOUR_EYES_NOTE);
		// Nút disabled không nhận sự kiện chuột (pointer-events: none), tooltip gắn ở thẻ bọc. force đưa con trỏ
		// tới nút như người dùng, thay vì để Playwright chờ nút nhận được sự kiện.
		approve.hover(new Locator.HoverOptions().setForce(true));
		assertThat(page.getByRole(AriaRole.TOOLTIP)).hasText(FOUR_EYES_NOTE);
		screenshot("01-nut-duyet-bi-vo-hieu");

		logout();
		login("duyet2");
		openSample(sample.getCode());
		resultRow("pH").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Duyệt")).click();

		assertThat(page.getByTestId("flash-success")).hasText("Đã duyệt kết quả pH");
		Locator status = resultRow("pH").getByTestId("result-status");
		assertThat(status).containsText("Đã duyệt");
		assertThat(status).containsText(actorAt("duyet2"));
		screenshot("02-nguoi-duyet-khac-da-duyet");
	}

	@Test
	@DisplayName("TC-E2E-06 Kiểm nghiệm viên gõ thẳng /samples/new: trang 403 nêu vai trò, vẫn đăng nhập (BR-12)")
	void analystTypingRegisterUrlGetsForbiddenPage() {
		login("kiemnghiem1");

		assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Đăng ký mẫu"))).hasCount(0);
		screenshot("01-khong-co-dang-ky-mau");

		Response response = page.navigate(url("/samples/new"));

		assertEquals(403, response.status());
		assertThat(page.getByRole(AriaRole.HEADING,
				new Page.GetByRoleOptions().setName("Bạn không có quyền thực hiện thao tác này"))).isVisible();
		assertThat(page.getByRole(AriaRole.MAIN)).containsText("Kiểm nghiệm viên");
		assertThat(page.getByRole(AriaRole.NAVIGATION)).containsText("Lê Văn Minh");
		screenshot("02-trang-403");

		page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Về danh sách mẫu")).click();

		assertThat(page).hasURL(url("/samples"));
		assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Danh sách mẫu"))).isVisible();
	}

}
