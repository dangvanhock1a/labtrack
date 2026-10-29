package com.pharmalab.labtrack.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.StorageCondition;
import com.pharmalab.labtrack.service.LabSampleService;
import com.pharmalab.labtrack.service.NewSample;

/**
 * Xuất CSV (UC-10, BR-16) kèm cột Bảo quản (BR-18). Nội dung từng ô đã có {@code SampleCsvTests} kiểm; ở đây chỉ
 * kiểm phần chỉ trình duyệt thấy được: file tải qua Tomcat thật (tên từ {@code Content-Disposition}, BOM và UTF-8
 * qua HTTP), link mang đúng bộ lọc form vừa gửi, và phân quyền của {@code SecurityConfig}.
 */
class SampleExportIT extends E2ETestBase {

	private static final String COOL_URGENT_BATCH = "AM-2601";

	private static final String DEFAULT_URGENT_BATCH = "VC-2601";

	private static final String NORMAL_BATCH = "IB-2601";

	private static final String HEADER = "Mã mẫu,Sản phẩm,Số lô,Loại mẫu,Ưu tiên,Bảo quản,Trạng thái,Ngày nhận,Hạn trả,Quá hạn,Kết luận";

	@Autowired
	private LabSampleService sampleService;

	@Autowired
	private Clock clock;

	@Test
	@DisplayName("TC-E2E-09 Quản lý lọc Khẩn rồi xuất CSV: tên file theo ngày, có BOM, chỉ mẫu Khẩn theo thứ tự danh sách, cột Bảo quản đúng; Kiểm nghiệm viên không thấy nút và bị 403 (BR-16, BR-18, BR-12)")
	void exportFollowsCurrentFilter() throws IOException {
		LabSample cool = sampleService.register(new NewSample("Amoxicillin 250 mg", COOL_URGENT_BATCH,
				SampleType.FINISHED_PRODUCT, SamplePriority.URGENT, StorageCondition.COOL, null, null), "tiepnhan");
		LabSample roomTemperature = sampleService.register(new NewSample("Vitamin C 500 mg", DEFAULT_URGENT_BATCH,
				SampleType.FINISHED_PRODUCT, SamplePriority.URGENT, null, null), "tiepnhan");
		sampleService.register(new NewSample("Ibuprofen 400 mg", NORMAL_BATCH, SampleType.FINISHED_PRODUCT,
				SamplePriority.NORMAL, null, null), "tiepnhan");

		// 1. Quản lý thấy nút Xuất CSV trên danh sách.
		login("quanly");
		openSampleList();

		Locator main = page.getByRole(AriaRole.MAIN);
		// Cùng một locator cho các bước có nút và bước không có nút (bước 5), để bước phủ định không xanh nhờ sai tên.
		Locator exportLink = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Xuất CSV").setExact(true));
		assertThat(main).containsText("Hiển thị 1–3 / tổng 3 mẫu");
		assertThat(exportLink).isVisible();
		screenshot("01-danh-sach");

		// 2. Lọc Khẩn: link xuất mang đúng bộ lọc đó.
		page.getByLabel("Ưu tiên").selectOption(new SelectOption().setLabel("Khẩn"));
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Lọc").setExact(true)).click();

		assertThat(page).hasURL(Pattern.compile("priority=URGENT"));
		assertThat(main).containsText("Hiển thị 1–2 / tổng 2 mẫu");
		assertThat(exportLink).hasAttribute("href", "/samples/export.csv?priority=URGENT");
		screenshot("02-loc-khan");

		// 3. Tải file: chỉ hai mẫu Khẩn, theo thứ tự danh sách (cùng ưu tiên thì nhận sau đứng trước).
		Download download = page.waitForDownload(exportLink::click);

		assertEquals("labtrack-mau-" + LocalDate.now(clock) + ".csv", download.suggestedFilename());
		byte[] bytes = Files.readAllBytes(download.path());
		assertArrayEquals(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF }, Arrays.copyOf(bytes, 3));
		String csv = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
		assertTrue(csv.endsWith("\r\n"), csv);
		assertFalse(csv.contains(NORMAL_BATCH), csv);
		List<String> lines = List.of(csv.split("\r\n"));
		assertEquals(3, lines.size(), csv);
		assertEquals(HEADER, lines.get(0));
		assertCsvRow(lines.get(1), roomTemperature, "Nhiệt độ phòng (15–30 °C)");
		assertCsvRow(lines.get(2), cool, "Mát (2–8 °C)");
		assertThat(page).hasURL(Pattern.compile("priority=URGENT"));
		screenshot("03-da-xuat-csv");

		// 4. Trang chi tiết hiện cùng điều kiện bảo quản như trong file (BR-18).
		listRow(COOL_URGENT_BATCH)
			.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(cool.getCode()).setExact(true))
			.click();

		assertThat(page).hasURL(url("/samples/" + cool.getId()));
		assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Mẫu " + cool.getCode())))
			.isVisible();
		assertThat(page.getByTestId("storage-condition")).hasText("Mát (2–8 °C)");
		screenshot("04-chi-tiet-bao-quan");

		// 5. Kiểm nghiệm viên xem cùng danh sách nhưng không có nút xuất (BR-12).
		logout();
		login("kiemnghiem1");
		openSampleList();

		assertThat(main).containsText("Hiển thị 1–3 / tổng 3 mẫu");
		assertThat(exportLink).hasCount(0);
		screenshot("05-kiem-nghiem-vien-khong-co-nut");

		// 6. Gõ thẳng URL: server chặn, không chỉ giao diện giấu nút. Nếu server trả file thì navigate ném lỗi.
		Response response = page.navigate(url("/samples/export.csv?priority=URGENT"));

		assertEquals(403, response.status());
		assertThat(page.getByRole(AriaRole.HEADING,
				new Page.GetByRoleOptions().setName("Bạn không có quyền thực hiện thao tác này"))).isVisible();
		assertThat(main).containsText("Kiểm nghiệm viên");
		screenshot("06-trang-403");
	}

	private void openSampleList() {
		page.getByRole(AriaRole.NAVIGATION)
			.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Danh sách mẫu"))
			.click();
		assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Danh sách mẫu"))).isVisible();
	}

	private Locator listRow(String batchNo) {
		return page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(batchNo));
	}

	/**
	 * Một dòng dữ liệu của file. Ngày nhận và Hạn trả tùy giờ chạy test nên không so với hằng: so với chữ trên dòng
	 * cùng mẫu của danh sách đang mở, nơi in cùng định dạng (NFR-04).
	 */
	private void assertCsvRow(String line, LabSample sample, String storageLabel) {
		List<String> cells = List.of(line.split(",", -1));
		assertEquals(11, cells.size(), line);
		assertEquals(List.of(sample.getCode(), sample.getProductName(), sample.getBatchNo(), "Thành phẩm", "Khẩn",
				storageLabel, "Đã tiếp nhận"), cells.subList(0, 7));
		assertEquals(List.of("Không", ""), cells.subList(9, 11));
		Locator row = listRow(sample.getBatchNo());
		assertThat(row).containsText(cells.get(7));
		assertThat(row).containsText(cells.get(8));
	}

}
