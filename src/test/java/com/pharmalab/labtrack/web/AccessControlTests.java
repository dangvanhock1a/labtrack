package com.pharmalab.labtrack.web;

import static com.pharmalab.labtrack.domain.UserRole.ANALYST;
import static com.pharmalab.labtrack.domain.UserRole.RECEPTIONIST;
import static com.pharmalab.labtrack.security.WithAppUserSecurityContextFactory.principal;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.pharmalab.labtrack.config.SecurityConfig;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.UserRole;
import com.pharmalab.labtrack.security.WithAppUser;
import com.pharmalab.labtrack.service.LabSampleService;

/**
 * Phân quyền theo URL và theo nút trên giao diện (BR-12), và trang đăng nhập (UC-01). Trang 403 nằm
 * ở {@link ForbiddenPageIntegrationTests}, vì nó chỉ hiện qua Tomcat thật.
 * <p>
 * {@code @WebMvcTest} không tự nạp lớp {@code @Configuration}, nên phải {@code @Import}
 * {@link SecurityConfig}; thiếu nó thì test chạy trên cấu hình bảo mật mặc định của Boot, nơi mọi
 * người đã đăng nhập đều qua được.
 */
@WebMvcTest({ SampleController.class, LoginController.class })
@Import(SecurityConfig.class)
class AccessControlTests {

	private static final long SAMPLE_ID = 42L;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LabSampleService sampleService;

	private static MockHttpServletRequestBuilder validRegistration() {
		return post("/samples")
			.param("productName", "Paracetamol 500 mg")
			.param("batchNo", "PA-2601")
			.param("sampleType", "FINISHED_PRODUCT");
	}

	private static LabSample receivedSample() {
		LabSample sample = new LabSample();
		sample.setId(SAMPLE_ID);
		sample.setCode("LS-2026-00042");
		sample.setProductName("Paracetamol 500 mg");
		sample.setBatchNo("PA-2601");
		sample.setSampleType(SampleType.FINISHED_PRODUCT);
		sample.setStatus(SampleStatus.RECEIVED);
		sample.setReceivedAt(OffsetDateTime.parse("2026-10-07T09:00+07:00"));
		sample.setDueDate(LocalDate.parse("2026-10-14"));
		sample.setCreatedBy("tiepnhan");
		return sample;
	}

	private static Matcher<String> presentIf(boolean expected, String text) {
		return expected ? containsString(text) : not(containsString(text));
	}

	@Test
	@DisplayName("TC-BR12-01 Chưa đăng nhập: mở danh sách mẫu thì bị chuyển tới trang đăng nhập")
	void anonymousIsRedirectedToLogin() throws Exception {
		mockMvc.perform(get("/samples"))
			.andExpect(status().is3xxRedirection())
			// So phần đuôi: tùy cấu hình, Spring Security trả URL tuyệt đối hoặc tương đối.
			.andExpect(header().string(HttpHeaders.LOCATION, endsWith("/login")));

		verifyNoInteractions(sampleService);
	}

	@Test
	@WithAppUser(username = "kiemnghiem1", role = ANALYST)
	@DisplayName("TC-BR12-02 Kiểm nghiệm viên gõ thẳng URL form đăng ký mẫu thì nhận 403")
	void analystCannotOpenRegistrationForm() throws Exception {
		mockMvc.perform(get("/samples/new"))
			.andExpect(status().isForbidden());

		verifyNoInteractions(sampleService);
	}

	@Test
	@WithAppUser(username = "tiepnhan", role = RECEPTIONIST)
	@DisplayName("TC-BR12-03 POST không kèm CSRF token thì nhận 403, kể cả khi người gửi đủ quyền")
	void postWithoutCsrfTokenIsForbidden() throws Exception {
		mockMvc.perform(validRegistration())
			.andExpect(status().isForbidden());

		verifyNoInteractions(sampleService);
	}

	@Test
	@WithAppUser(username = "tiepnhan", role = RECEPTIONIST)
	@DisplayName("TC-BR12-04 Tiếp nhận gửi form đăng ký hợp lệ kèm CSRF token thì mẫu được ghi tên người đăng nhập")
	void receptionistCanRegisterSample() throws Exception {
		LabSample saved = new LabSample();
		saved.setId(SAMPLE_ID);
		saved.setCode("LS-2026-00042");
		when(sampleService.register(any(), eq("tiepnhan"))).thenReturn(saved);

		mockMvc.perform(validRegistration().with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/samples/42"));
	}

	@Test
	@DisplayName("TC-BR12-06 Trang đăng nhập mở được khi chưa đăng nhập, ô nhập có nhãn và form có CSRF token")
	void loginPageIsPublicAndAccessible() throws Exception {
		mockMvc.perform(get("/login"))
			.andExpect(status().isOk())
			.andExpect(view().name("login"))
			.andExpect(content().string(containsString("<label for=\"username\"")))
			.andExpect(content().string(containsString("<label for=\"password\"")))
			.andExpect(content().string(containsString("name=\"_csrf\"")))
			.andExpect(content().string(not(containsString("Đăng xuất"))));
	}

	@Test
	@WithAppUser(username = "kiemnghiem1", role = ANALYST, fullName = "Lê Văn Minh")
	@DisplayName("TC-BR12-07 Menu hiện họ tên, vai trò của người đăng nhập và nút Đăng xuất")
	void navbarShowsSignedInUser() throws Exception {
		mockMvc.perform(get("/samples"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Lê Văn Minh")))
			.andExpect(content().string(containsString("Kiểm nghiệm viên")))
			.andExpect(content().string(containsString("action=\"/logout\"")))
			.andExpect(content().string(containsString("Đăng xuất")));
	}

	@ParameterizedTest(name = "[{index}] {0}: Nhận kiểm nghiệm = {1}, Hủy mẫu = {2}")
	@DisplayName("TC-BR12-08 Trang chi tiết mẫu Đã tiếp nhận chỉ hiện nút mà vai trò được phép dùng")
	@CsvSource(textBlock = """
			# vai trò       nhận KN  hủy
			ANALYST,        true,    false
			RECEPTIONIST,   false,   true
			REVIEWER,       true,    true
			MANAGER,        false,   false
			""")
	void detailActionsFollowRole(UserRole role, boolean canStart, boolean canCancel) throws Exception {
		when(sampleService.findById(SAMPLE_ID)).thenReturn(Optional.of(receivedSample()));

		mockMvc.perform(get("/samples/{id}", SAMPLE_ID).with(user(principal("nguoidung", "Người dùng thử", role))))
			.andExpect(status().isOk())
			.andExpect(content().string(presentIf(canStart, "Nhận kiểm nghiệm")))
			.andExpect(content().string(presentIf(canStart, "/samples/42/start")))
			.andExpect(content().string(presentIf(canCancel, "data-bs-target=\"#cancelModal\"")))
			.andExpect(content().string(presentIf(canCancel, "/samples/42/cancel")));
	}

	@ParameterizedTest(name = "[{index}] {0}: thấy Đăng ký mẫu = {1}")
	@DisplayName("TC-BR12-09 Chỉ Tiếp nhận mẫu thấy link và nút Đăng ký mẫu")
	@CsvSource(textBlock = """
			RECEPTIONIST,   true
			ANALYST,        false
			REVIEWER,       false
			MANAGER,        false
			""")
	void registerLinkOnlyForReceptionist(UserRole role, boolean visible) throws Exception {
		mockMvc.perform(get("/samples").with(user(principal("nguoidung", "Người dùng thử", role))))
			.andExpect(status().isOk())
			.andExpect(content().string(presentIf(visible, "href=\"/samples/new\"")));
	}

	@ParameterizedTest(name = "[{index}] /login?{0}")
	@DisplayName("TC-BR12-10 Trang đăng nhập báo bằng tiếng Việt khi sai thông tin hoặc vừa đăng xuất")
	@CsvSource({ "error, Tên đăng nhập hoặc mật khẩu không đúng", "logout, Bạn đã đăng xuất" })
	void loginPageMessages(String param, String message) throws Exception {
		mockMvc.perform(get("/login").param(param, ""))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(message)));
	}

}
