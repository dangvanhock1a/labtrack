package com.pharmalab.labtrack.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.options.AriaRole;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.service.LabSampleService;
import com.pharmalab.labtrack.service.NewSample;

/**
 * Lớp cha của test E2E (I-6, NFR-05, NFR-08): ứng dụng chạy trên Tomcat thật ở cổng ngẫu nhiên, trình duyệt
 * Playwright thao tác như người dùng. Đặt tên lớp con {@code *IT} để failsafe chạy ở {@code ./mvnw verify}.
 * Kịch bản ở {@code docs/kich-ban-e2e.md}.
 * <p>
 * Trình duyệt mở 1 lần cho mỗi lớp vì khởi động mất vài giây; cách ly giữa các test nằm ở {@link BrowserContext}
 * (cookie, phiên đăng nhập) và ở việc dọn CSDL trước mỗi test.
 * <p>
 * Kênh trình duyệt ({@code -De2e.channel}, failsafe mặc định {@code msedge} trong pom.xml; chạy từ IDE thì đọc
 * biến {@code E2E_BROWSER_CHANNEL}) dùng Edge/Chrome đã cài sẵn trên máy, nên báo Playwright bỏ bước tải Chromium
 * riêng (vài trăm MB, hay bị chặn sau proxy công ty). {@code -De2e.headed=true} mở cửa sổ trình duyệt để xem
 * test chạy.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
abstract class E2ETestBase {

	private static final String PASSWORD = "Passw0rd!";

	private static final Path SCREENSHOTS = Path.of("target", "e2e-screenshots");

	private static final Path TRACES = Path.of("target", "playwright-traces");

	private static Playwright playwright;

	private static Browser browser;

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private LabSampleService sampleService;

	/** {@code <Lop>-<method>}, tiền tố tên file ảnh chụp và trace của test đang chạy. */
	private String testName;

	protected BrowserContext context;

	protected Page page;

	@BeforeAll
	static void launchBrowser() {
		String channel = System.getProperty("e2e.channel", System.getenv("E2E_BROWSER_CHANNEL"));
		Playwright.CreateOptions createOptions = new Playwright.CreateOptions();
		BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
			.setHeadless(!Boolean.getBoolean("e2e.headed"));
		if (channel != null && !channel.isBlank()) {
			createOptions.setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));
			launchOptions.setChannel(channel);
		}
		playwright = Playwright.create(createOptions);
		browser = playwright.chromium().launch(launchOptions);
	}

	@AfterAll
	static void closeBrowser() {
		// Đóng Playwright là đóng luôn trình duyệt nó đã mở.
		if (playwright != null) {
			playwright.close();
		}
	}

	@BeforeEach
	void openContext(TestInfo testInfo) {
		new DbCleaner(jdbcTemplate, transactionManager).clean();
		testName = getClass().getSimpleName() + "-" + testInfo.getTestMethod().orElseThrow().getName();
		context = browser.newContext(new Browser.NewContextOptions()
			.setViewportSize(1366, 900)
			.setLocale("vi-VN")
			.setTimezoneId("Asia/Ho_Chi_Minh"));
		context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
		page = context.newPage();
	}

	@AfterEach
	void closeContext() {
		// @BeforeEach hỏng trước khi mở được context (ví dụ DbCleaner từ chối CSDL): không có gì để lưu.
		if (context == null) {
			return;
		}
		try {
			screenshot("end");
		}
		finally {
			// Trace vẫn được lưu khi chụp ảnh lỗi, vì đó là lúc cần trace nhất.
			context.tracing().stop(new Tracing.StopOptions().setPath(TRACES.resolve(testName + ".zip")));
			context.close();
		}
	}

	protected String url(String path) {
		return "http://localhost:" + port + path;
	}

	protected void login(String username) {
		page.navigate(url("/login"));
		submitLogin(username, PASSWORD);
		// Đăng nhập hỏng thì báo ngay ở đây, thay vì để bước sau của kịch bản báo lỗi khó hiểu.
		assertThat(page).hasURL(url("/samples"));
	}

	/** Điền và gửi form đăng nhập đang mở, không kiểm kết quả: dùng cho kịch bản đăng nhập sai. */
	protected void submitLogin(String username, String password) {
		page.getByLabel("Tên đăng nhập").fill(username);
		page.getByLabel("Mật khẩu").fill(password);
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Đăng nhập")).click();
	}

	protected void logout() {
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Đăng xuất")).click();
		assertThat(page).hasURL(url("/login?logout"));
	}

	protected void screenshot(String name) {
		page.screenshot(new Page.ScreenshotOptions()
			.setPath(SCREENSHOTS.resolve(testName + "-" + name + ".png"))
			.setFullPage(true));
	}

	/** Từ trang danh sách, bấm mã mẫu như người dùng để mở trang chi tiết. */
	protected void openSample(String code) {
		page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(code).setExact(true)).click();
		assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Mẫu " + code))).isVisible();
	}

	/**
	 * Dòng kết quả của một chỉ tiêu trên trang chi tiết, tìm theo ô tiêu đề dòng (tên chỉ tiêu) chứ không theo thứ
	 * tự dòng. Dòng của bản bị từ chối nằm ngay bên dưới nhưng không có ô tiêu đề, nên không lẫn vào đây.
	 */
	protected Locator resultRow(String parameterName) {
		return page.getByTestId("result-table")
			.getByRole(AriaRole.ROW)
			.filter(new Locator.FilterOptions().setHas(page.getByRole(AriaRole.ROWHEADER,
					new Page.GetByRoleOptions().setName(parameterName).setExact(true))));
	}

	/** Nhập kết quả trên trang chi tiết. Ô nhập chỉ có nhãn ẩn "Giá trị <chỉ tiêu> (<đơn vị>)" (NFR-08). */
	protected void enterResult(String parameterName, String value) {
		Locator row = resultRow(parameterName);
		row.getByLabel("Giá trị " + parameterName).fill(value);
		row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Lưu")).click();
	}

	/**
	 * "người · dd/MM/yyyy HH:mm", cách trang chi tiết ghi người nhập và người xử lý kết quả (NFR-04). Không dùng
	 * {@code Pattern.quote}: Playwright chuyển Pattern sang RegExp của JavaScript, vốn không hiểu {@code \Q…\E}.
	 */
	protected static Pattern actorAt(String username) {
		return Pattern.compile("(^|\\s)" + username + " · \\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}");
	}

	/**
	 * Mẫu đang kiểm nghiệm, dựng qua service làm tiền điều kiện. Đường giao diện của đăng ký và nhận kiểm nghiệm
	 * đã có TC-E2E-03 kiểm; đi lại các bước đó qua giao diện chỉ làm test chậm và hỏng lây.
	 */
	protected LabSample seedSampleInTesting() {
		LabSample sample = sampleService.register(
				new NewSample("Paracetamol 500 mg", "PA-2601", SampleType.FINISHED_PRODUCT, null, null), "tiepnhan");
		sampleService.startTesting(sample.getId(), "kiemnghiem1");
		return sample;
	}

}
