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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * Trang 403 qua Tomcat thật. Trong {@code @WebMvcTest}, phản hồi 403 không được chuyển sang
 * {@code /error}, và Boot cũng không chọn template {@code error/NNN} vì không có bean
 * {@code DispatcherServlet}. Chỉ server thật mới cho thấy trang người dùng nhận được, và cho thấy
 * người đăng nhập vẫn còn trong security context khi trang lỗi được render.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ForbiddenPageIntegrationTests {

	private static final Pattern CSRF_TOKEN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

	@Value("${local.server.port}")
	private int port;

	/** Giữ cookie phiên giữa các request như trình duyệt, và không tự đi theo redirect. */
	private final HttpClient browser = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

	private HttpRequest.Builder request(String path) {
		// Thiếu Accept: text/html thì /error của Boot trả JSON thay vì trang HTML.
		return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Accept", "text/html");
	}

	private HttpResponse<String> get(String path) throws Exception {
		return browser.send(request(path).GET().build(), BodyHandlers.ofString());
	}

	private HttpResponse<String> postForm(String path, String... namesAndValues) throws Exception {
		StringBuilder form = new StringBuilder();
		for (int i = 0; i < namesAndValues.length; i += 2) {
			if (form.length() > 0) {
				form.append('&');
			}
			form.append(namesAndValues[i]).append('=')
				.append(URLEncoder.encode(namesAndValues[i + 1], StandardCharsets.UTF_8));
		}
		return browser.send(request(path).header("Content-Type", "application/x-www-form-urlencoded")
			.POST(BodyPublishers.ofString(form.toString()))
			.build(), BodyHandlers.ofString());
	}

	private static String csrfTokenIn(String html) {
		Matcher matcher = CSRF_TOKEN.matcher(html);
		assertThat(matcher.find()).as("trang có CSRF token").isTrue();
		return matcher.group(1);
	}

	@Test
	@DisplayName("TC-BR12-11 Kiểm nghiệm viên gõ thẳng URL đăng ký mẫu: trang 403 tiếng Việt nêu vai trò, không phải trang lỗi trắng")
	void signedInUserWithoutRoleSeesForbiddenPage() throws Exception {
		String token = csrfTokenIn(get("/login").body());
		HttpResponse<String> login = postForm("/login", "username", "kiemnghiem1", "password", "Passw0rd!",
				"_csrf", token);
		assertThat(login.statusCode()).isEqualTo(302);
		assertThat(login.headers().firstValue("Location")).hasValueSatisfying(
				location -> assertThat(location).endsWith("/samples"));

		HttpResponse<String> response = get("/samples/new");

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.body())
			.contains("Bạn không có quyền thực hiện thao tác này", "Kiểm nghiệm viên", "Lê Văn Minh",
					"Về danh sách mẫu")
			.doesNotContain("Phiên làm việc đã hết hạn", "Whitelabel");
	}

	@Test
	@DisplayName("TC-BR12-12 Gửi form sau khi phiên hết hạn (CSRF token không còn khớp): trang 403 mời đăng nhập lại")
	void postAfterSessionExpiredSeesSignInAgainPage() throws Exception {
		HttpResponse<String> response = postForm("/samples", "productName", "Paracetamol 500 mg", "batchNo",
				"PA-2601", "sampleType", "FINISHED_PRODUCT", "_csrf", "token-cua-phien-cu");

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.body())
			.contains("Phiên làm việc đã hết hạn", "href=\"/login\"")
			.doesNotContain("Đăng xuất", "Whitelabel");
	}

}
