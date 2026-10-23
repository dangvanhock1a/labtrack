package com.pharmalab.labtrack.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.persistence.TestParameterMapper;
import com.pharmalab.labtrack.service.NewResult;
import com.pharmalab.labtrack.service.TestResultService;

/** Vòng đời mẫu và kết quả qua giao diện, UC-02..UC-06. Kịch bản ở {@code docs/kich-ban-e2e.md}. */
class SampleLifecycleIT extends E2ETestBase {

	@Autowired
	private Clock clock;

	@Autowired
	private TestResultService resultService;

	@Autowired
	private TestParameterMapper parameterMapper;

	@Test
	@DisplayName("TC-E2E-03 Đăng ký (thử bỏ trống trước), nhận kiểm nghiệm, nhập 2 kết quả, người duyệt duyệt cả 2 (BR-01..BR-08, BR-10)")
	void fullLifecycle() {
		// DbCleaner đặt lại bộ đếm, nên mẫu đầu tiên trong năm theo giờ VN (BR-01) là số 00001.
		String code = "LS-" + Year.now(clock) + "-00001";
		Locator flash = page.getByTestId("flash-success");
		Locator history = page.getByTestId("history").getByRole(AriaRole.ROW);
		Locator audit = page.getByTestId("audit").getByRole(AriaRole.ROW);

		login("tiepnhan");
		page.getByRole(AriaRole.MAIN)
			.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Đăng ký mẫu"))
			.click();
		Locator receivedAt = page.getByLabel("Thời điểm nhận");
		Locator dueDate = page.getByLabel("Hạn trả kết quả");
		String defaultReceivedAt = receivedAt.inputValue();
		String defaultDueDate = dueDate.inputValue();
		Locator register = page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName("Đăng ký").setExact(true));
		register.click();

		assertThat(page.getByTestId("productName-error")).hasText("Vui lòng nhập tên sản phẩm");
		assertThat(page.getByTestId("batchNo-error")).hasText("Số lô chỉ gồm chữ không dấu, số, dấu - và /");
		assertThat(page.getByTestId("sampleType-error")).hasText("Vui lòng chọn loại mẫu");
		assertThat(receivedAt).hasValue(defaultReceivedAt);
		assertThat(dueDate).hasValue(defaultDueDate);
		screenshot("01-dang-ky-bo-trong");

		page.getByLabel("Tên sản phẩm").fill("Paracetamol 500 mg");
		page.getByLabel("Số lô").fill("PA-2601");
		page.getByLabel("Loại mẫu").selectOption(new SelectOption().setLabel("Thành phẩm"));
		register.click();

		assertThat(flash).hasText("Đã đăng ký mẫu " + code);
		assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Mẫu " + code))).isVisible();
		assertThat(page.getByTestId("status-badge")).hasText("Đã tiếp nhận");
		assertThat(history).hasCount(1);
		assertThat(history).containsText("Tạo mới → Đã tiếp nhận");
		assertThat(history).containsText("tiepnhan");
		assertThat(history).containsText("Đăng ký mẫu");
		assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Nhận kiểm nghiệm"))).hasCount(0);
		screenshot("02-da-dang-ky");

		logout();
		login("kiemnghiem1");
		openSample(code);
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Nhận kiểm nghiệm")).click();

		assertThat(flash).hasText("Mẫu " + code + " đã chuyển sang Đang kiểm nghiệm");
		assertThat(page.getByTestId("status-badge")).hasText("Đang kiểm nghiệm");
		assertThat(history).hasCount(2);
		assertThat(history.filter(new Locator.FilterOptions().setHasText("Đã tiếp nhận → Đang kiểm nghiệm")))
			.containsText("kiemnghiem1");
		assertThat(page.getByTestId("result-table")
			.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Lưu"))).hasCount(5);
		screenshot("03-da-nhan-kiem-nghiem");

		enterResult("Định lượng hoạt chất", "98.5");

		assertThat(flash).hasText("Đã nhập kết quả Định lượng hoạt chất: 98.5 % (Đạt)");
		Locator assay = resultRow("Định lượng hoạt chất");
		assertThat(assay.getByTestId("result-verdict")).hasText("Đạt");
		assertThat(assay.getByTestId("result-status")).hasText("Chờ duyệt");
		assertThat(assay.getByTestId("result-entered")).containsText(actorAt("kiemnghiem1"));

		// pH 7.5 vượt tiêu chuẩn 4.5–7: hệ thống tự đánh giá Không đạt (BR-07).
		enterResult("pH", "7.5");

		assertThat(flash).hasText("Đã nhập kết quả pH: 7.5 pH (Không đạt)");
		assertThat(resultRow("pH").getByTestId("result-verdict")).hasText("Không đạt");
		assertThat(audit.filter(new Locator.FilterOptions().setHasText("Nhập"))).hasCount(2);
		// Kiểm nghiệm viên không có quyền duyệt (BR-12), nên không thấy nút nào.
		assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Duyệt").setExact(true)))
			.hasCount(0);
		assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Từ chối").setExact(true)))
			.hasCount(0);
		screenshot("04-da-nhap-2-ket-qua");

		logout();
		login("duyet");
		openSample(code);
		approve("Định lượng hoạt chất");
		assertThat(flash).hasText("Đã duyệt kết quả Định lượng hoạt chất");
		approve("pH");
		assertThat(flash).hasText("Đã duyệt kết quả pH");

		for (String parameter : List.of("Định lượng hoạt chất", "pH")) {
			Locator status = resultRow(parameter).getByTestId("result-status");
			assertThat(status).containsText("Đã duyệt");
			assertThat(status).containsText(actorAt("duyet"));
		}
		assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Duyệt").setExact(true)))
			.hasCount(0);
		assertThat(audit).hasCount(4);
		assertThat(audit.filter(new Locator.FilterOptions().setHasText("Duyệt"))).hasCount(2);
		// Chưa có chức năng kết luận mẫu (BR-11, I-8), nên duyệt hết kết quả không đổi trạng thái mẫu.
		assertThat(page.getByTestId("status-badge")).hasText("Đang kiểm nghiệm");
		screenshot("05-da-duyet-2-ket-qua");
	}

	@Test
	@DisplayName("TC-E2E-05 Từ chối kết quả: lý do trống hay toàn dấu cách bị chặn; bản bị từ chối gạch ngang kèm lý do, nhập lại thành bản mới (BR-09, BR-10)")
	void rejectWithReasonThenReenter() {
		LabSample sample = seedSampleInTesting();
		seedResult(sample, "PH", "6.2", "kiemnghiem1");
		Locator flash = page.getByTestId("flash-success");
		Locator dialog = page.getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions().setName("Từ chối kết quả pH"));
		Locator reason = dialog.getByLabel("Lý do từ chối (5–500 ký tự)");
		Locator submit = dialog.getByRole(AriaRole.BUTTON,
				new Locator.GetByRoleOptions().setName("Từ chối").setExact(true));

		login("duyet");
		openSample(sample.getCode());
		openRejectDialog("pH");

		assertThat(dialog).containsText("6.2 pH, do kiemnghiem1 nhập");
		assertThat(reason).isFocused();
		screenshot("01-hop-thoai-tu-choi");

		submit.click();

		// Ô bắt buộc để trống: trình duyệt chặn gửi form ngay lúc bấm, nên kiểm được mà không phải chờ.
		assertTrue((Boolean) reason.evaluate("e => e.validity.valueMissing"));
		assertThat(dialog).isVisible();
		screenshot("02-ly-do-trong");

		// 5 dấu cách qua được required và minlength của trình duyệt; server cắt khoảng trắng rồi mới chặn.
		reason.fill("     ");
		submit.click();

		assertThat(page.getByTestId("flash-error")).hasText("Lý do phải dài từ 5 đến 500 ký tự");
		assertThat(resultRow("pH").getByTestId("result-status")).hasText("Chờ duyệt");
		screenshot("03-ly-do-toan-dau-cach");

		openRejectDialog("pH");
		reason.fill("Sai độ pha loãng mẫu chuẩn");
		submit.click();

		assertThat(flash).hasText("Đã từ chối kết quả pH");
		Locator rejected = page.getByTestId("rejected-result");
		Locator rejectedValue = rejected.getByTestId("rejected-value");
		assertThat(rejectedValue).hasText("6.2 pH");
		assertThat(rejectedValue).hasCSS("text-decoration-line", "line-through");
		assertThat(rejected).containsText("Đã từ chối");
		assertThat(rejected).containsText(actorAt("duyet"));
		assertThat(rejected).containsText("Lý do: Sai độ pha loãng mẫu chuẩn");
		assertThat(page.getByTestId("audit")
			.getByRole(AriaRole.ROW)
			.filter(new Locator.FilterOptions().setHasText("Từ chối"))).containsText("Lý do: Sai độ pha loãng mẫu chuẩn");
		// Chỉ tiêu không còn kết quả hiệu lực nên lại có ô nhập (người duyệt cũng được nhập).
		assertThat(resultRow("pH").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Lưu")))
			.isVisible();
		screenshot("04-da-tu-choi");

		logout();
		login("kiemnghiem1");
		openSample(sample.getCode());
		enterResult("pH", "6.4");

		assertThat(flash).hasText("Đã nhập kết quả pH: 6.4 pH (Đạt)");
		assertThat(resultRow("pH").getByTestId("result-value")).hasText("6.4 pH (Lần 2)");
		assertThat(resultRow("pH").getByTestId("result-status")).hasText("Chờ duyệt");
		assertThat(rejectedValue).hasText("6.2 pH");
		screenshot("05-da-nhap-lai");
	}

	private void approve(String parameterName) {
		resultRow(parameterName).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Duyệt")).click();
	}

	private void openRejectDialog(String parameterName) {
		resultRow(parameterName).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Từ chối")).click();
	}

	/** Kết quả nhập qua service làm tiền điều kiện; đường giao diện của bước nhập đã có TC-E2E-03 kiểm. */
	private void seedResult(LabSample sample, String parameterCode, String value, String actor) {
		Long parameterId = parameterMapper.findByCode(parameterCode).orElseThrow().getId();
		resultService.enter(sample.getId(), new NewResult(parameterId, new BigDecimal(value)), actor);
	}

}
