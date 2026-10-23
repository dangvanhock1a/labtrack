package com.pharmalab.labtrack.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.pharmalab.labtrack.TestcontainersConfiguration;
import com.pharmalab.labtrack.persistence.TestParameterMapper;

/**
 * Luồng kết quả trọn vẹn qua Tomcat thật (SPEC-004 mục 5.2): đăng nhập bằng 6 tài khoản demo, CSRF, phân quyền
 * theo URL, flash sau redirect, nguyên tắc 4 mắt và nhật ký kết quả. Dữ liệu được commit và ở lại trong CSDL
 * (SPEC-004 L1), nên test chỉ đọc trên mẫu nó vừa tạo.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ResultFlowIntegrationTests {

	private static final Pattern CSRF_TOKEN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

	/** Ô "Thao tác" của mỗi dòng nhật ký; ô "Chi tiết" có khoảng trắng bao quanh nên không khớp. */
	private static final Pattern AUDIT_ACTION = Pattern.compile("<td>(Nhập|Duyệt|Từ chối)</td>");

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private TestParameterMapper parameterMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/** Một người dùng với phiên riêng: giữ cookie như trình duyệt và không tự đi theo redirect. */
	private final class Browser {

		private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

		private String csrfToken;

		Browser(String username) throws Exception {
			HttpResponse<String> login = post("/login", "username", username, "password", "Passw0rd!", "_csrf",
					csrfTokenIn(get("/login").body()));
			assertThat(login.statusCode()).as("đăng nhập %s", username).isEqualTo(302);
			// Spring Security đổi CSRF token khi đăng nhập; token mới giữ nguyên tới hết phiên.
			this.csrfToken = csrfTokenIn(get("/samples").body());
		}

		private HttpRequest.Builder request(String path) {
			return HttpRequest.newBuilder(URI.create("http://localhost:" + port).resolve(path))
				.header("Accept", "text/html");
		}

		HttpResponse<String> get(String path) throws Exception {
			return client.send(request(path).GET().build(), BodyHandlers.ofString());
		}

		private HttpResponse<String> post(String path, String... namesAndValues) throws Exception {
			StringBuilder form = new StringBuilder();
			for (int i = 0; i < namesAndValues.length; i += 2) {
				if (form.length() > 0) {
					form.append('&');
				}
				form.append(namesAndValues[i]).append('=')
					.append(URLEncoder.encode(namesAndValues[i + 1], StandardCharsets.UTF_8));
			}
			return client.send(request(path).header("Content-Type", "application/x-www-form-urlencoded")
				.POST(BodyPublishers.ofString(form.toString()))
				.build(), BodyHandlers.ofString());
		}

		/** Gửi form kèm CSRF token của phiên, như form có {@code th:action}. */
		HttpResponse<String> submit(String path, String... namesAndValues) throws Exception {
			String[] withToken = new String[namesAndValues.length + 2];
			System.arraycopy(namesAndValues, 0, withToken, 0, namesAndValues.length);
			withToken[namesAndValues.length] = "_csrf";
			withToken[namesAndValues.length + 1] = csrfToken;
			return post(path, withToken);
		}

		/** Gửi form, đi theo redirect như trình duyệt, trả về trang đích (có flash). */
		String submitAndFollow(String path, String... namesAndValues) throws Exception {
			HttpResponse<String> response = submit(path, namesAndValues);
			assertThat(response.statusCode()).as("POST %s", path).isEqualTo(302);
			return get(locationOf(response)).body();
		}

	}

	private static String csrfTokenIn(String html) {
		Matcher matcher = CSRF_TOKEN.matcher(html);
		assertThat(matcher.find()).as("trang có CSRF token").isTrue();
		return matcher.group(1);
	}

	/** Đường dẫn trong header Location, dù server trả URL tuyệt đối hay tương đối. */
	private static String locationOf(HttpResponse<String> response) {
		return URI.create(response.headers().firstValue("Location").orElseThrow()).getPath();
	}

	private String parameterId(String code) {
		return String.valueOf(parameterMapper.findByCode(code).orElseThrow().getId());
	}

	/** Id kết quả hiệu lực của chỉ tiêu, để dựng URL duyệt/từ chối như nút trên trang. */
	private long effectiveResultId(long sampleId, String code) {
		return jdbcTemplate.queryForObject("""
				SELECT r.id FROM test_result r JOIN test_parameter p ON p.id = r.parameter_id
				WHERE r.sample_id = ? AND p.code = ? AND r.status IN ('PENDING', 'APPROVED')
				""", Long.class, sampleId, code);
	}

	private static String section(String html, String titleId) {
		int start = html.indexOf("id=\"" + titleId + "\"");
		return html.substring(start, html.indexOf("</section>", start));
	}

	private static String tagContaining(String html, String marker) {
		int at = html.indexOf(marker);
		return html.substring(html.lastIndexOf('<', at), html.indexOf('>', at) + 1);
	}

	private static String buttonAfter(String html, String marker) {
		int start = html.indexOf("<button", html.indexOf(marker));
		return html.substring(start, html.indexOf('>', start) + 1);
	}

	@Test
	@DisplayName("TC-BR10-07 Luồng trọn qua HTTP thật: nhập, từ chối, nhập lại, 4 mắt, duyệt bởi người thứ hai; nhật ký có đúng 6 dòng")
	void fullResultFlow() throws Exception {
		// 1. Tiếp nhận đăng ký mẫu.
		HttpResponse<String> registered = new Browser("tiepnhan").submit("/samples", "productName",
				"Paracetamol 500 mg", "batchNo", "PA-2601", "sampleType", "FINISHED_PRODUCT");
		assertThat(registered.statusCode()).isEqualTo(302);
		String sample = locationOf(registered);
		assertThat(sample).matches("/samples/\\d+");
		long sampleId = Long.parseLong(sample.substring("/samples/".length()));

		// 2. Kiểm nghiệm viên nhận kiểm nghiệm.
		Browser analyst = new Browser("kiemnghiem1");
		assertThat(analyst.submitAndFollow(sample + "/start")).contains("Đang kiểm nghiệm");

		// 3. Nhập pH 6.2.
		assertThat(analyst.submitAndFollow(sample + "/results", "parameterId", parameterId("PH"), "value", "6.2"))
			.contains("Đã nhập kết quả pH: 6.2 pH (Đạt)");
		long firstPh = effectiveResultId(sampleId, "PH");

		// 4. Kiểm nghiệm viên gửi thẳng POST duyệt: SecurityConfig chặn.
		assertThat(analyst.submit(sample + "/results/" + firstPh + "/approve").statusCode()).isEqualTo(403);

		// 5. Người duyệt từ chối kèm lý do.
		Browser reviewer = new Browser("duyet");
		assertThat(reviewer.submitAndFollow(sample + "/results/" + firstPh + "/reject", "reason", "Sai độ pha loãng"))
			.contains("Đã từ chối kết quả pH");

		// 6. Nhập lại pH: bản cũ gạch ngang, bản mới là Lần 2.
		String afterReEntry = analyst.submitAndFollow(sample + "/results", "parameterId", parameterId("PH"), "value",
				"6.30");
		assertThat(tagContaining(afterReEntry, ">6.2 pH<")).contains("text-decoration-line-through");
		assertThat(afterReEntry).contains("Sai độ pha loãng").contains("(Lần 2)");
		long secondPh = effectiveResultId(sampleId, "PH");

		// 7. Người duyệt tự nhập ASSAY: trên trang của chính họ, nút Duyệt của ASSAY bị vô hiệu.
		String reviewerPage = reviewer.submitAndFollow(sample + "/results", "parameterId", parameterId("ASSAY"),
				"value", "98.50");
		long assay = effectiveResultId(sampleId, "ASSAY");
		assertThat(buttonAfter(reviewerPage, "/results/" + assay + "/approve\"")).contains("disabled");

		// 8. Vẫn gửi POST duyệt: service chặn theo nguyên tắc 4 mắt.
		assertThat(reviewer.submitAndFollow(sample + "/results/" + assay + "/approve"))
			.contains("Nguyên tắc 4 mắt: bạn không được duyệt hoặc từ chối kết quả do chính mình nhập");

		// 9. Người duyệt thứ hai duyệt cả hai kết quả.
		Browser secondReviewer = new Browser("duyet2");
		assertThat(secondReviewer.submitAndFollow(sample + "/results/" + secondPh + "/approve"))
			.contains("Đã duyệt kết quả pH");
		assertThat(secondReviewer.submitAndFollow(sample + "/results/" + assay + "/approve"))
			.contains("Đã duyệt kết quả Định lượng hoạt chất");

		// 10. Quản lý xem: 2 kết quả Đã duyệt; nhật ký 6 dòng, bước 4 và 8 thất bại nên không có dòng.
		String managerPage = new Browser("quanly").get(sample).body();
		String results = section(managerPage, "resultsTitle");
		assertThat(results).contains("98.50 %").contains("6.30 pH");
		assertThat(results.split(">Đã duyệt</span>", -1)).hasSize(3);
		List<String> actions = new ArrayList<>();
		Matcher action = AUDIT_ACTION.matcher(section(managerPage, "resultAuditTitle"));
		while (action.find()) {
			actions.add(action.group(1));
		}
		assertThat(actions).containsExactly("Nhập", "Từ chối", "Nhập", "Nhập", "Duyệt", "Duyệt");
	}

}
