package com.pharmalab.labtrack.web;

import static com.pharmalab.labtrack.domain.UserRole.ANALYST;
import static com.pharmalab.labtrack.domain.UserRole.RECEPTIONIST;
import static com.pharmalab.labtrack.security.WithAppUserSecurityContextFactory.principal;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.StorageCondition;
import com.pharmalab.labtrack.domain.TestResult;
import com.pharmalab.labtrack.domain.UserRole;
import com.pharmalab.labtrack.domain.Verdict;
import com.pharmalab.labtrack.security.WithAppUser;
import com.pharmalab.labtrack.service.CsvFile;
import com.pharmalab.labtrack.service.LabSampleService;
import com.pharmalab.labtrack.service.Overview;
import com.pharmalab.labtrack.service.SamplePage;
import com.pharmalab.labtrack.service.TestResultService;

/**
 * Phân quyền theo URL và theo nút trên giao diện (BR-12), và trang đăng nhập (UC-01). Trang 403 nằm
 * ở {@link ForbiddenPageIntegrationTests}, vì nó chỉ hiện qua Tomcat thật.
 * <p>
 * {@code @WebMvcTest} không tự nạp lớp {@code @Configuration}, nên phải {@code @Import}
 * {@link SecurityConfig}; thiếu nó thì test chạy trên cấu hình bảo mật mặc định của Boot, nơi mọi
 * người đã đăng nhập đều qua được.
 */
@WebMvcTest({ SampleController.class, LoginController.class, DashboardController.class })
@Import(SecurityConfig.class)
class AccessControlTests {

	private static final long SAMPLE_ID = 42L;

	private static final long RESULT_ID = 7L;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LabSampleService sampleService;

	@MockitoBean
	private TestResultService resultService;

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
		sample.setPriority(SamplePriority.NORMAL);
		sample.setStorageCondition(StorageCondition.ROOM_TEMPERATURE);
		sample.setStatus(SampleStatus.RECEIVED);
		sample.setReceivedAt(OffsetDateTime.parse("2026-10-07T09:00+07:00"));
		sample.setDueDate(LocalDate.parse("2026-10-14"));
		sample.setCreatedBy("tiepnhan");
		return sample;
	}

	/** Danh sách mẫu rỗng: service là mock, chưa stub thì {@code search} trả null và trang danh sách lỗi. */
	private void givenNoSamples() {
		when(sampleService.search(any(), anyInt())).thenReturn(new SamplePage(List.of(), 1, 20, 0));
	}

	/** Tổng quan không có mẫu nào: chưa stub thì {@code overview} trả null và trang Tổng quan lỗi. */
	private void givenEmptyOverview() {
		Map<SampleStatus, Long> counts = new EnumMap<>(SampleStatus.class);
		for (SampleStatus status : SampleStatus.values()) {
			counts.put(status, 0L);
		}
		when(sampleService.overview()).thenReturn(new Overview(counts, 0, 0, null, List.of()));
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
		givenNoSamples();

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
		givenNoSamples();

		mockMvc.perform(get("/samples").with(user(principal("nguoidung", "Người dùng thử", role))))
			.andExpect(status().isOk())
			.andExpect(content().string(presentIf(visible, "href=\"/samples/new\"")));
	}

	private static TestResult phResult() {
		TestResult result = new TestResult();
		result.setId(RESULT_ID);
		result.setSampleId(SAMPLE_ID);
		result.setParameterName("pH");
		result.setValue(new BigDecimal("6.2"));
		result.setUnit("pH");
		result.setVerdict(Verdict.PASS);
		return result;
	}

	@ParameterizedTest(name = "[{index}] {0}: được nhập = {1}")
	@DisplayName("TC-BR12-15 Nhập kết quả: Kiểm nghiệm viên, Người duyệt qua lớp bảo mật; Tiếp nhận, Quản lý nhận 403")
	@CsvSource(textBlock = """
			ANALYST,        true
			REVIEWER,       true
			RECEPTIONIST,   false
			MANAGER,        false
			""")
	void enterResultFollowsRole(UserRole role, boolean allowed) throws Exception {
		when(resultService.enter(eq(SAMPLE_ID), any(), eq("nguoidung"))).thenReturn(phResult());

		mockMvc.perform(post("/samples/{id}/results", SAMPLE_ID)
				.with(user(principal("nguoidung", "Người dùng thử", role)))
				.with(csrf())
				.param("parameterId", "2")
				.param("value", "6.2"))
			.andExpect(allowed ? redirectedUrl("/samples/42") : status().isForbidden());

		if (!allowed) {
			verifyNoInteractions(resultService);
		}
	}

	@ParameterizedTest(name = "[{index}] {0} {1}: được phép = {2}")
	@DisplayName("TC-BR12-16 Duyệt và từ chối kết quả: chỉ Người duyệt qua lớp bảo mật, các vai trò khác nhận 403")
	@CsvSource(textBlock = """
			REVIEWER,       approve,  true
			REVIEWER,       reject,   true
			ANALYST,        approve,  false
			ANALYST,        reject,   false
			RECEPTIONIST,   approve,  false
			RECEPTIONIST,   reject,   false
			MANAGER,        approve,  false
			MANAGER,        reject,   false
			""")
	void reviewResultOnlyForReviewer(UserRole role, String action, boolean allowed) throws Exception {
		when(resultService.approve(SAMPLE_ID, RESULT_ID, "nguoidung")).thenReturn(phResult());
		when(resultService.reject(SAMPLE_ID, RESULT_ID, "Sai độ pha loãng", "nguoidung")).thenReturn(phResult());

		mockMvc.perform(post("/samples/{id}/results/{resultId}/{action}", SAMPLE_ID, RESULT_ID, action)
				.with(user(principal("nguoidung", "Người dùng thử", role)))
				.with(csrf())
				.param("reason", "Sai độ pha loãng"))
			.andExpect(allowed ? redirectedUrl("/samples/42") : status().isForbidden());

		if (!allowed) {
			verifyNoInteractions(resultService);
		}
	}

	@ParameterizedTest(name = "[{index}] POST {0}")
	@DisplayName("TC-BR12-17 Ba endpoint kết quả: chưa đăng nhập thì về trang đăng nhập; thiếu CSRF token thì 403")
	@CsvSource(textBlock = """
			/samples/42/results
			/samples/42/results/7/approve
			/samples/42/results/7/reject
			""")
	void resultEndpointsRequireLoginAndCsrf(String url) throws Exception {
		mockMvc.perform(post(url).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string(HttpHeaders.LOCATION, endsWith("/login")));
		mockMvc.perform(post(url).with(user(principal("duyet", "Phạm Thị Hoa", UserRole.REVIEWER))))
			.andExpect(status().isForbidden());

		verifyNoInteractions(resultService);
	}

	@ParameterizedTest(name = "[{index}] {0}: được kết luận = {1}")
	@DisplayName("TC-BR12-18 Kết luận mẫu: chỉ Người duyệt qua lớp bảo mật, các vai trò khác nhận 403")
	@CsvSource(textBlock = """
			REVIEWER,       true
			ANALYST,        false
			RECEPTIONIST,   false
			MANAGER,        false
			""")
	void concludeOnlyForReviewer(UserRole role, boolean allowed) throws Exception {
		LabSample concluded = receivedSample();
		concluded.setStatus(SampleStatus.COMPLETED);
		concluded.setConclusion(Verdict.PASS);
		when(sampleService.conclude(SAMPLE_ID, Verdict.PASS, "nguoidung")).thenReturn(concluded);

		mockMvc.perform(post("/samples/{id}/conclude", SAMPLE_ID)
				.param("expectedVerdict", "PASS")
				.with(user(principal("nguoidung", "Người dùng thử", role)))
				.with(csrf()))
			.andExpect(allowed ? redirectedUrl("/samples/42") : status().isForbidden());

		if (!allowed) {
			verifyNoInteractions(sampleService);
		}
	}

	@Test
	@DisplayName("TC-BR12-19 Kết luận mẫu: chưa đăng nhập thì về trang đăng nhập; thiếu CSRF token thì 403, kể cả Người duyệt")
	void concludeRequiresLoginAndCsrf() throws Exception {
		mockMvc.perform(post("/samples/{id}/conclude", SAMPLE_ID).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string(HttpHeaders.LOCATION, endsWith("/login")));
		mockMvc.perform(post("/samples/{id}/conclude", SAMPLE_ID)
				.with(user(principal("duyet", "Hoàng Văn Duy", UserRole.REVIEWER))))
			.andExpect(status().isForbidden());

		verifyNoInteractions(sampleService);
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@DisplayName("TC-BR12-20 Trang Tổng quan / mở cho cả 4 vai trò đã đăng nhập (BR-15, ma trận mục 9)")
	@CsvSource({ "RECEPTIONIST", "ANALYST", "REVIEWER", "MANAGER" })
	void overviewOpenForEveryRole(UserRole role) throws Exception {
		givenEmptyOverview();

		mockMvc.perform(get("/").with(user(principal("nguoidung", "Người dùng thử", role))))
			.andExpect(status().isOk())
			.andExpect(view().name("dashboard"))
			.andExpect(content().string(containsString("Tổng quan")));
	}

	@Test
	@DisplayName("TC-BR12-21 Chưa đăng nhập: mở trang Tổng quan / thì bị chuyển tới trang đăng nhập")
	void anonymousOverviewIsRedirectedToLogin() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string(HttpHeaders.LOCATION, endsWith("/login")));

		verifyNoInteractions(sampleService);
	}

	@ParameterizedTest(name = "[{index}] {0}: HTTP {1}")
	@DisplayName("TC-BR12-22 GET /samples/export.csv: Người duyệt và Quản lý tải được, Tiếp nhận và Kiểm nghiệm viên gõ thẳng URL thì nhận 403 (BR-16, ma trận mục 9)")
	@CsvSource(textBlock = """
			RECEPTIONIST,   403
			ANALYST,        403
			REVIEWER,       200
			MANAGER,        200
			""")
	void exportOnlyForReviewerAndManager(UserRole role, int httpStatus) throws Exception {
		when(sampleService.exportCsv(any())).thenReturn(new CsvFile("labtrack-mau-2026-10-29.csv", new byte[0]));

		mockMvc.perform(get("/samples/export.csv").with(user(principal("nguoidung", "Người dùng thử", role))))
			.andExpect(status().is(httpStatus));

		verify(sampleService, times(httpStatus == 200 ? 1 : 0)).exportCsv(any());
	}

	@Test
	@DisplayName("TC-BR12-23 Chưa đăng nhập: gõ thẳng /samples/export.csv thì bị chuyển tới trang đăng nhập, không xuất gì")
	void anonymousExportIsRedirectedToLogin() throws Exception {
		mockMvc.perform(get("/samples/export.csv"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string(HttpHeaders.LOCATION, endsWith("/login")));

		verifyNoInteractions(sampleService);
	}

	@ParameterizedTest(name = "[{index}] {0}: thấy Xuất CSV = {1}")
	@DisplayName("TC-BR12-24 Chỉ Người duyệt và Quản lý thấy nút Xuất CSV trên danh sách mẫu")
	@CsvSource(textBlock = """
			RECEPTIONIST,   false
			ANALYST,        false
			REVIEWER,       true
			MANAGER,        true
			""")
	void exportButtonOnlyForReviewerAndManager(UserRole role, boolean visible) throws Exception {
		givenNoSamples();

		mockMvc.perform(get("/samples").with(user(principal("nguoidung", "Người dùng thử", role))))
			.andExpect(status().isOk())
			.andExpect(content().string(presentIf(visible, "href=\"/samples/export.csv\"")))
			.andExpect(content().string(presentIf(visible, "Xuất CSV")));
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
