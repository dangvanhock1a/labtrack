package com.pharmalab.labtrack.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.service.LabSampleService;
import com.pharmalab.labtrack.service.NewSample;

/**
 * Trang Tổng quan (UC-08, BR-15) và nhãn quá hạn trên đó (BR-14). "Hôm qua" của tiền điều kiện tính bằng
 * {@link Clock} của ứng dụng chứ không theo múi giờ JVM, để trùng "hôm nay" giờ VN mà trang dùng để đếm quá hạn.
 */
class DashboardIT extends E2ETestBase {

	private static final String URGENT_OVERDUE_BATCH = "AM-2601";

	private static final String NORMAL_NOT_DUE_BATCH = "IB-2601";

	@Autowired
	private LabSampleService sampleService;

	@Autowired
	private Clock clock;

	@Test
	@DisplayName("TC-E2E-08 Tổng quan: số theo trạng thái, quá hạn, TAT trống; mẫu Khẩn quá hạn nằm trong Mẫu cần chú ý; ô Quá hạn mở danh sách đã lọc; trang chi tiết mẫu đó có nhãn Quá hạn (BR-15, BR-14)")
	void overviewShowsCountsAndAttention() {
		LocalDate today = LocalDate.now(clock);
		LabSample overdue = sampleService.register(new NewSample("Amoxicillin 250 mg", URGENT_OVERDUE_BATCH,
				SampleType.FINISHED_PRODUCT, SamplePriority.URGENT, today.minusDays(2).atTime(8, 0), today.minusDays(1)),
				"tiepnhan");
		sampleService.register(new NewSample("Ibuprofen 400 mg", NORMAL_NOT_DUE_BATCH, SampleType.FINISHED_PRODUCT,
				SamplePriority.NORMAL, null, null), "tiepnhan");

		// 1. Đăng nhập: vào thẳng trang Tổng quan.
		login("quanly");

		assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Tổng quan"))).isVisible();
		assertThat(page.getByTestId("status-count-RECEIVED")).hasText("2");
		assertThat(page.getByTestId("status-count-IN_TESTING")).hasText("0");
		assertThat(page.getByTestId("status-count-COMPLETED")).hasText("0");
		assertThat(page.getByTestId("status-count-CANCELLED")).hasText("0");
		assertThat(page.getByTestId("overdue-count")).hasText("1");
		assertThat(page.getByTestId("completed-count")).hasText("0");
		assertThat(page.getByTestId("avg-tat")).hasText("—");
		Locator main = page.getByRole(AriaRole.MAIN);
		assertThat(main).containsText("Chưa có mẫu hoàn thành trong 30 ngày");
		Locator attention = page.getByTestId("attention-row");
		assertThat(attention).hasCount(1);
		assertThat(attention).containsText(URGENT_OVERDUE_BATCH);
		assertThat(attention).containsText("Khẩn");
		assertThat(attention.getByTestId("overdue-badge")).hasText("Quá hạn");
		assertThat(main).not().containsText(NORMAL_NOT_DUE_BATCH);
		screenshot("01-tong-quan");

		// 2. Ô Quá hạn mở danh sách đã bật bộ lọc Quá hạn (BR-14).
		page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Quá hạn")).click();

		assertThat(page).hasURL(url("/samples?overdueOnly=true"));
		assertThat(page.getByLabel("Quá hạn")).isChecked();
		assertThat(main).containsText("Hiển thị 1–1 / tổng 1 mẫu");
		Locator overdueRow = page.getByRole(AriaRole.ROW)
			.filter(new Locator.FilterOptions().setHasText(URGENT_OVERDUE_BATCH));
		assertThat(overdueRow.getByTestId("overdue-badge")).hasText("Quá hạn");
		assertThat(main).not().containsText(NORMAL_NOT_DUE_BATCH);
		screenshot("02-danh-sach-qua-han");

		// 3. Màn hình thứ ba của BR-14: trang chi tiết cũng mang nhãn Quá hạn.
		overdueRow.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(overdue.getCode()).setExact(true))
			.click();

		assertThat(page).hasURL(url("/samples/" + overdue.getId()));
		assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Mẫu " + overdue.getCode())))
			.isVisible();
		assertThat(page.getByTestId("status-badge")).hasText("Đã tiếp nhận");
		assertThat(page.getByTestId("overdue-badge")).hasText("Quá hạn");
		screenshot("03-chi-tiet-qua-han");
	}

}
