package com.pharmalab.labtrack.web;

import static com.pharmalab.labtrack.domain.UserRole.ANALYST;
import static com.pharmalab.labtrack.domain.UserRole.RECEPTIONIST;
import static com.pharmalab.labtrack.domain.UserRole.REVIEWER;
import static com.pharmalab.labtrack.security.WithAppUserSecurityContextFactory.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pharmalab.labtrack.config.SecurityConfig;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.ResultAction;
import com.pharmalab.labtrack.domain.ResultAudit;
import com.pharmalab.labtrack.domain.ResultStatus;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.StatusHistory;
import com.pharmalab.labtrack.domain.TestParameter;
import com.pharmalab.labtrack.domain.TestResult;
import com.pharmalab.labtrack.domain.UserRole;
import com.pharmalab.labtrack.domain.Verdict;
import com.pharmalab.labtrack.security.WithAppUser;
import com.pharmalab.labtrack.service.BusinessException;
import com.pharmalab.labtrack.service.InvalidStatusTransitionException;
import com.pharmalab.labtrack.service.LabSampleService;
import com.pharmalab.labtrack.service.NewResult;
import com.pharmalab.labtrack.service.ResultSheetRow;
import com.pharmalab.labtrack.service.TestResultService;

/**
 * Hành vi của controller khi người dùng đã đủ quyền. Quyền theo vai trò (BR-12) được kiểm ở
 * {@link AccessControlTests}. Người dùng mặc định ở đây là Người duyệt, vai trò thấy mọi nút trên
 * trang chi tiết, nên các test trang chi tiết chỉ còn phụ thuộc trạng thái mẫu. Test nào gửi POST
 * thì tự khai báo một vai trò được phép, kèm CSRF token, và kiểm rằng tên đăng nhập của người đó
 * được truyền xuống tầng nghiệp vụ.
 */
@WebMvcTest(SampleController.class)
@Import(SecurityConfig.class)
@WithAppUser(username = "duyet", role = REVIEWER)
class SampleControllerTests {

	private static final long SAMPLE_ID = 42L;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LabSampleService sampleService;

	@MockitoBean
	private TestResultService resultService;

	private static LabSample sampleIn(SampleStatus status) {
		LabSample sample = new LabSample();
		sample.setId(SAMPLE_ID);
		sample.setCode("LS-2026-00042");
		sample.setProductName("Paracetamol 500 mg");
		sample.setBatchNo("PA-2601");
		sample.setSampleType(SampleType.FINISHED_PRODUCT);
		sample.setStatus(status);
		sample.setReceivedAt(OffsetDateTime.parse("2026-10-07T09:00+07:00"));
		sample.setDueDate(LocalDate.parse("2026-10-14"));
		sample.setCreatedBy("tiepnhan");
		return sample;
	}

	private void givenSampleIn(SampleStatus status) {
		when(sampleService.findById(SAMPLE_ID)).thenReturn(Optional.of(sampleIn(status)));
	}

	private static StatusHistory historyRow(SampleStatus from, SampleStatus to, String reason, String changedBy,
			String changedAt) {
		StatusHistory row = new StatusHistory();
		row.setSampleId(SAMPLE_ID);
		row.setFromStatus(from);
		row.setToStatus(to);
		row.setReason(reason);
		row.setChangedBy(changedBy);
		row.setChangedAt(OffsetDateTime.parse(changedAt));
		return row;
	}

	@Test
	@WithAppUser(username = "tiepnhan", role = RECEPTIONIST)
	@DisplayName("TC-BR02-08 Gửi form trống thì hiện lại form với lỗi tiếng Việt ở các trường bắt buộc")
	void emptyFormIsRejected() throws Exception {
		mockMvc.perform(post("/samples").with(csrf())
				.param("productName", "")
				.param("batchNo", "")
				.param("sampleType", "")
				.param("receivedAt", "")
				.param("dueDate", ""))
			.andExpect(status().isOk())
			.andExpect(view().name("samples/form"))
			.andExpect(model().attributeHasFieldErrors("sampleForm", "productName", "batchNo", "sampleType"))
			.andExpect(content().string(containsString("Vui lòng nhập tên sản phẩm")))
			.andExpect(content().string(containsString("Số lô chỉ gồm chữ không dấu, số, dấu - và /")))
			.andExpect(content().string(containsString("Vui lòng chọn loại mẫu")));

		verifyNoInteractions(sampleService);
	}

	@Test
	@DisplayName("TC-BR02-09 LT-101: JVM chạy UTC, danh sách vẫn hiện thời điểm nhận theo giờ VN (02:00Z → 07/10/2026 09:00)")
	void listShowsReceivedAtInVietnamTimeEvenWhenJvmIsUtc() throws Exception {
		// Mock trả thời điểm ở dạng LabSampleService.findAll trả thật: đã qua toClockZone, mang offset của Clock.
		LabSample sample = sampleIn(SampleStatus.RECEIVED);
		sample.setReceivedAt(Instant.parse("2026-10-07T02:00:00Z")
			.atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
			.toOffsetDateTime());
		when(sampleService.findAll()).thenReturn(List.of(sample));

		TimeZone original = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		try {
			mockMvc.perform(get("/samples"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("07/10/2026 09:00")))
				.andExpect(content().string(not(containsString("07/10/2026 02:00"))));
		}
		finally {
			TimeZone.setDefault(original);
		}
	}

	@Test
	@DisplayName("TC-BR02-10 Danh sách: thời điểm nhận mang offset UTC (dạng pgjdbc trả) vẫn hiện theo giờ VN")
	void listShowsReceivedAtInVietnamTimeWhenValueIsUtc() throws Exception {
		LabSample sample = sampleIn(SampleStatus.RECEIVED);
		sample.setReceivedAt(OffsetDateTime.parse("2026-10-07T02:00Z"));
		when(sampleService.findAll()).thenReturn(List.of(sample));

		mockMvc.perform(get("/samples"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("07/10/2026 09:00")))
			.andExpect(content().string(containsString("14/10/2026")))
			.andExpect(content().string(not(containsString("07/10/2026 02:00"))));
	}

	@Test
	@WithAppUser(username = "kiemnghiem1", role = ANALYST)
	@DisplayName("TC-BR03-06 Chuyển trạng thái sai: quay lại trang chi tiết kèm thông báo lỗi dạng flash")
	void startTestingFailureRedirectsToDetailWithFlashError() throws Exception {
		when(sampleService.startTesting(SAMPLE_ID, "kiemnghiem1"))
			.thenThrow(new InvalidStatusTransitionException(SampleStatus.COMPLETED, SampleStatus.IN_TESTING));

		mockMvc.perform(post("/samples/{id}/start", SAMPLE_ID).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/samples/42"))
			.andExpect(flash().attribute("error",
					"Không thể chuyển mẫu từ \"Hoàn thành\" sang \"Đang kiểm nghiệm\""));
	}

	@Test
	@DisplayName("TC-BR03-07 Mẫu Đã tiếp nhận: trang chi tiết có nút Nhận kiểm nghiệm và ô nhập lý do hủy")
	void detailOfReceivedSampleOffersBothActions() throws Exception {
		givenSampleIn(SampleStatus.RECEIVED);

		mockMvc.perform(get("/samples/{id}", SAMPLE_ID))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Nhận kiểm nghiệm")))
			.andExpect(content().string(containsString("Lý do hủy (5–500 ký tự)")))
			.andExpect(content().string(containsString("/samples/42/start")))
			.andExpect(content().string(containsString("/samples/42/cancel")));
	}

	@Test
	@DisplayName("TC-BR03-08 Mẫu Đang kiểm nghiệm: không còn nút Nhận kiểm nghiệm nhưng vẫn hủy được")
	void detailOfTestingSampleOnlyOffersCancel() throws Exception {
		givenSampleIn(SampleStatus.IN_TESTING);

		mockMvc.perform(get("/samples/{id}", SAMPLE_ID))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("Nhận kiểm nghiệm"))))
			.andExpect(content().string(containsString("Lý do hủy (5–500 ký tự)")))
			.andExpect(content().string(containsString("badge text-bg-primary")));
	}

	@Test
	@WithAppUser(username = "tiepnhan", role = RECEPTIONIST)
	@DisplayName("TC-BR05-03 Gửi form hủy: lý do được cắt khoảng trắng rồi chuyển xuống tầng nghiệp vụ")
	void cancelPassesTrimmedReasonToService() throws Exception {
		when(sampleService.cancel(SAMPLE_ID, "Gửi nhầm mẫu", "tiepnhan"))
			.thenReturn(sampleIn(SampleStatus.CANCELLED));

		mockMvc.perform(post("/samples/{id}/cancel", SAMPLE_ID).with(csrf()).param("reason", "  Gửi nhầm mẫu  "))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/samples/42"))
			.andExpect(flash().attribute("message", "Đã hủy mẫu LS-2026-00042"));
	}

	@Test
	@DisplayName("TC-BR03-09 Mẫu đã kết thúc: trang chi tiết không còn nút đổi trạng thái nào")
	void detailOfTerminalSampleHidesActions() throws Exception {
		givenSampleIn(SampleStatus.CANCELLED);

		mockMvc.perform(get("/samples/{id}", SAMPLE_ID))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("Nhận kiểm nghiệm"))))
			.andExpect(content().string(not(containsString("Lý do hủy"))));
	}

	@Test
	@DisplayName("TC-BR04-07 Trang chi tiết hiện lịch sử trạng thái: thời điểm, người, từ → đến, lý do")
	void detailShowsStatusHistory() throws Exception {
		givenSampleIn(SampleStatus.CANCELLED);
		when(sampleService.findStatusHistory(SAMPLE_ID)).thenReturn(List.of(
				historyRow(null, SampleStatus.RECEIVED, "Đăng ký mẫu", "tiepnhan", "2026-10-07T09:05:12+07:00"),
				historyRow(SampleStatus.RECEIVED, SampleStatus.CANCELLED, "Gửi nhầm mẫu", "duyet",
						"2026-10-08T14:30:45+07:00")));

		mockMvc.perform(get("/samples/{id}", SAMPLE_ID))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Lịch sử trạng thái")))
			.andExpect(content().string(containsString("07/10/2026 09:05:12")))
			.andExpect(content().string(containsString("Tạo mới")))
			.andExpect(content().string(containsString("Đăng ký mẫu")))
			.andExpect(content().string(containsString("08/10/2026 14:30:45")))
			.andExpect(content().string(containsString("duyet")))
			.andExpect(content().string(containsString("Gửi nhầm mẫu")))
			.andExpect(content().string(not(containsString("Chưa có lịch sử"))));
	}

	// ---- Kết quả kiểm nghiệm (SPEC-004) ----

	private static final long ASSAY_ID = 1L;

	private static final long PH_ID = 2L;

	private static TestParameter assay() {
		return parameter(ASSAY_ID, "ASSAY", "Định lượng hoạt chất", "%", "95", "105");
	}

	private static TestParameter ph() {
		return parameter(PH_ID, "PH", "pH", "pH", "4.5", "7");
	}

	private static TestParameter parameter(long id, String code, String name, String unit, String min, String max) {
		TestParameter parameter = new TestParameter();
		parameter.setId(id);
		parameter.setCode(code);
		parameter.setName(name);
		parameter.setUnit(unit);
		parameter.setSpecMin(new BigDecimal(min));
		parameter.setSpecMax(new BigDecimal(max));
		parameter.setActive(true);
		parameter.setDisplayOrder((int) id);
		return parameter;
	}

	private static TestResult result(long id, TestParameter parameter, String value, Verdict verdict,
			ResultStatus status, String enteredBy, int attemptNo) {
		TestResult result = new TestResult();
		result.setId(id);
		result.setSampleId(SAMPLE_ID);
		result.setParameterId(parameter.getId());
		result.setValue(new BigDecimal(value));
		result.setUnit(parameter.getUnit());
		result.setSpecMin(parameter.getSpecMin());
		result.setSpecMax(parameter.getSpecMax());
		result.setVerdict(verdict);
		result.setStatus(status);
		result.setEnteredBy(enteredBy);
		result.setEnteredAt(OffsetDateTime.parse("2026-10-08T00:30:15+07:00"));
		result.setParameterCode(parameter.getCode());
		result.setParameterName(parameter.getName());
		result.setParameterActive(true);
		result.setAttemptNo(attemptNo);
		return result;
	}

	private static TestResult rejected(TestResult result, String reviewedBy, String reason) {
		result.setStatus(ResultStatus.REJECTED);
		result.setReviewedBy(reviewedBy);
		result.setReviewedAt(OffsetDateTime.parse("2026-10-08T08:15:00+07:00"));
		result.setRejectReason(reason);
		return result;
	}

	private void givenSheet(ResultSheetRow... rows) {
		when(resultService.findSheet(SAMPLE_ID)).thenReturn(List.of(rows));
	}

	/** Thẻ mở của nút đầu tiên sau {@code marker}: kiểm thuộc tính của nút mà không phụ thuộc thứ tự thuộc tính. */
	private static String buttonAfter(String html, String marker) {
		int start = html.indexOf("<button", html.indexOf(marker));
		return html.substring(start, html.indexOf('>', start) + 1);
	}

	/** Thẻ HTML chứa {@code marker}. */
	private static String tagContaining(String html, String marker) {
		int at = html.indexOf(marker);
		return html.substring(html.lastIndexOf('<', at), html.indexOf('>', at) + 1);
	}

	private String detailPage() throws Exception {
		return mockMvc.perform(get("/samples/{id}", SAMPLE_ID))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	@ParameterizedTest(name = "[{index}] value = [{0}] → {1}")
	@WithAppUser(username = "kiemnghiem1", role = ANALYST)
	@DisplayName("TC-BR06-04 Giá trị sai trên form: quay lại trang chi tiết, flash một lỗi nguyên văn kèm chỉ tiêu, không gọi service")
	@CsvSource(delimiter = '|', textBlock = """
			''        | Vui lòng nhập giá trị
			abc       | Giá trị phải là số, phần thập phân dùng dấu chấm
			98,5      | Giá trị phải là số, phần thập phân dùng dấu chấm
			-1        | Giá trị phải từ 0 trở lên
			1.23456   | Giá trị tối đa 8 chữ số phần nguyên và 4 chữ số thập phân
			-1.23456  | Giá trị phải từ 0 trở lên
			""")
	void invalidValueIsRejectedByForm(String value, String message) throws Exception {
		mockMvc.perform(post("/samples/{id}/results", SAMPLE_ID).with(csrf())
				.param("parameterId", String.valueOf(PH_ID))
				.param("value", value))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/samples/42"))
			.andExpect(flash().attribute("error", message))
			.andExpect(flash().attribute("errorParameterId", PH_ID));

		verifyNoInteractions(resultService);
	}

	@ParameterizedTest(name = "[{index}] parameterId = [{0}]")
	@WithAppUser(username = "kiemnghiem1", role = ANALYST)
	@DisplayName("TC-BR06-04 Thiếu hoặc sai chỉ tiêu: báo Không tìm thấy chỉ tiêu trước lỗi giá trị, không gọi service")
	@CsvSource(delimiter = '|', textBlock = """
			''
			abc
			""")
	void invalidParameterIsReportedFirst(String parameterId) throws Exception {
		mockMvc.perform(post("/samples/{id}/results", SAMPLE_ID).with(csrf())
				.param("parameterId", parameterId)
				.param("value", "abc"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/samples/42"))
			.andExpect(flash().attribute("error", "Không tìm thấy chỉ tiêu"))
			.andExpect(flash().attribute("errorParameterId", nullValue()));

		verifyNoInteractions(resultService);
	}

	@Test
	@WithAppUser(username = "kiemnghiem1", role = ANALYST)
	@DisplayName("TC-BR06-04 Sau khi form báo lỗi, ô nhập của đúng chỉ tiêu đó được tô đỏ")
	void erroneousParameterRowIsHighlighted() throws Exception {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenSheet(new ResultSheetRow(assay(), null, List.of()), new ResultSheetRow(ph(), null, List.of()));

		String html = mockMvc.perform(get("/samples/{id}", SAMPLE_ID).flashAttr("errorParameterId", PH_ID))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(tagContaining(html, "id=\"value-2\"")).contains("is-invalid");
		assertThat(tagContaining(html, "id=\"value-1\"")).doesNotContain("is-invalid");
	}

	@Test
	@WithAppUser(username = "kiemnghiem1", role = ANALYST)
	@DisplayName("TC-BR07-02 Form nhập không có trường đánh giá; request gửi kèm verdict=PASS thì service chỉ nhận chỉ tiêu và giá trị")
	void verdictInRequestIsIgnored() throws Exception {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenSheet(new ResultSheetRow(ph(), null, List.of()));
		when(resultService.enter(eq(SAMPLE_ID), any(), eq("kiemnghiem1")))
			.thenReturn(result(7L, ph(), "7.5", Verdict.FAIL, ResultStatus.PENDING, "kiemnghiem1", 1));

		mockMvc.perform(get("/samples/{id}", SAMPLE_ID))
			.andExpect(content().string(containsString("Giá trị pH (pH)")))
			.andExpect(content().string(containsString("step=\"0.0001\"")))
			.andExpect(content().string(not(containsString("name=\"verdict\""))));
		mockMvc.perform(post("/samples/{id}/results", SAMPLE_ID).with(csrf())
				.param("parameterId", String.valueOf(PH_ID))
				.param("value", "7.5")
				.param("verdict", "PASS"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/samples/42"))
			.andExpect(flash().attribute("message", "Đã nhập kết quả pH: 7.5 pH (Không đạt)"));

		ArgumentCaptor<NewResult> sent = ArgumentCaptor.forClass(NewResult.class);
		verify(resultService).enter(eq(SAMPLE_ID), sent.capture(), eq("kiemnghiem1"));
		assertThat(sent.getValue()).isEqualTo(new NewResult(PH_ID, new BigDecimal("7.5")));
	}

	@Test
	@DisplayName("TC-BR08-06 Người duyệt: kết quả do chính mình nhập có nút Duyệt/Từ chối bị vô hiệu kèm câu giải thích; của người khác thì bật")
	void ownResultHasDisabledReviewButtons() throws Exception {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenSheet(
				new ResultSheetRow(assay(),
						result(11L, assay(), "98.50", Verdict.PASS, ResultStatus.PENDING, "duyet", 1), List.of()),
				new ResultSheetRow(ph(),
						result(12L, ph(), "6.2", Verdict.PASS, ResultStatus.PENDING, "kiemnghiem1", 1), List.of()));

		String html = detailPage();

		assertThat(buttonAfter(html, "/samples/42/results/11/approve\"")).contains("disabled")
			.contains("aria-describedby=\"fourEyes-11\"");
		assertThat(tagContaining(html, "data-bs-target=\"#rejectModal-11\"")).contains("disabled")
			.contains("aria-describedby=\"fourEyes-11\"");
		// Câu giải thích vừa là chữ nhỏ (đích của aria-describedby), vừa là tooltip của thẻ bọc cặp nút: nút
		// disabled không nhận hover.
		String note = "Bạn đã nhập kết quả này; cần người duyệt khác (nguyên tắc 4 mắt)";
		assertThat(tagContaining(html, "id=\"fourEyes-11\"")).startsWith("<small");
		assertThat(html).contains(note + "</small>");
		assertThat(tagContaining(html, "data-bs-title=\"" + note + "\"")).contains("data-bs-toggle=\"tooltip\"")
			.contains("tabindex=\"0\"");
		assertThat(html)
			.doesNotContain("id=\"rejectModal-11\"");

		assertThat(buttonAfter(html, "/samples/42/results/12/approve\"")).doesNotContain("disabled");
		assertThat(tagContaining(html, "data-bs-target=\"#rejectModal-12\"")).doesNotContain("disabled");
		assertThat(html).contains("id=\"rejectModal-12\"")
			.contains("/samples/42/results/12/reject")
			.doesNotContain("fourEyes-12");
	}

	@Test
	@WithAppUser(username = "kiemnghiem1", role = ANALYST)
	@DisplayName("TC-BR08-06 Kiểm nghiệm viên không thấy nút Duyệt/Từ chối, kể cả với kết quả của người khác")
	void analystSeesNoReviewButtons() throws Exception {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenSheet(new ResultSheetRow(ph(),
				result(12L, ph(), "6.2", Verdict.PASS, ResultStatus.PENDING, "kiemnghiem2", 1), List.of()));

		assertThat(detailPage()).contains("6.2 pH")
			.doesNotContain("/approve")
			.doesNotContain("data-bs-target=\"#rejectModal-")
			.doesNotContain("id=\"rejectModal-");
	}

	@Test
	@DisplayName("TC-BR08-08 Mẫu đã hủy còn kết quả Chờ duyệt: không có ô nhập, nút Duyệt/Từ chối, có ghi chú")
	void cancelledSampleWithPendingResultIsReadOnly() throws Exception {
		givenSampleIn(SampleStatus.CANCELLED);
		givenSheet(
				new ResultSheetRow(assay(), null, List.of()),
				new ResultSheetRow(ph(),
						result(12L, ph(), "6.2", Verdict.PASS, ResultStatus.PENDING, "kiemnghiem1", 1), List.of()));

		assertThat(detailPage()).contains("Mẫu đã hủy, kết quả chờ duyệt không còn được xử lý")
			.contains("6.2 pH")
			.contains("Chưa có kết quả")
			.doesNotContain("name=\"value\"")
			.doesNotContain("/approve")
			.doesNotContain("data-bs-target=\"#rejectModal-")
			.doesNotContain("id=\"rejectModal-");
	}

	@Test
	@DisplayName("TC-BR08-08 Mẫu Đang kiểm nghiệm không có ghi chú mẫu đã hủy")
	void sampleInTestingHasNoCancelledNote() throws Exception {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenSheet(new ResultSheetRow(ph(),
				result(12L, ph(), "6.2", Verdict.PASS, ResultStatus.PENDING, "kiemnghiem1", 1), List.of()));

		assertThat(detailPage()).doesNotContain("Mẫu đã hủy, kết quả chờ duyệt không còn được xử lý");
	}

	@Test
	@DisplayName("TC-BR09-04 Bản bị từ chối hiện gạch ngang kèm người từ chối và lý do; bản nhập lại hiện Lần 2; chỉ tiêu chỉ có bản bị từ chối thì nhập lại được")
	void rejectedResultsAreStruckThrough() throws Exception {
		givenSampleIn(SampleStatus.IN_TESTING);
		TestResult oldPh = rejected(result(12L, ph(), "6.2", Verdict.PASS, ResultStatus.PENDING, "kiemnghiem1", 1),
				"duyet", "Sai độ pha loãng");
		TestResult newPh = result(14L, ph(), "6.30", Verdict.PASS, ResultStatus.PENDING, "kiemnghiem1", 2);
		TestResult oldAssay = rejected(
				result(13L, assay(), "94.0", Verdict.FAIL, ResultStatus.PENDING, "kiemnghiem2", 1), "duyet2",
				"Chuẩn đối chiếu hết hạn");
		givenSheet(new ResultSheetRow(assay(), null, List.of(oldAssay)),
				new ResultSheetRow(ph(), newPh, List.of(oldPh)));

		String html = detailPage();

		assertThat(tagContaining(html, ">6.2 pH<")).contains("text-decoration-line-through");
		assertThat(html).contains("Sai độ pha loãng")
			.contains("duyet · 08/10/2026 08:15")
			.contains("6.30 pH")
			.contains("(Lần 2)")
			.contains("Chuẩn đối chiếu hết hạn")
			.contains("name=\"parameterId\" value=\"1\"")
			.doesNotContain("name=\"parameterId\" value=\"2\"");
		assertThat(tagContaining(html, ">6.30 pH<")).doesNotContain("text-decoration-line-through");
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@EnumSource(UserRole.class)
	@DisplayName("TC-BR10-06 Mọi vai trò thấy mục Kết quả kiểm nghiệm và Nhật ký kết quả, thời điểm theo giờ VN")
	void everyRoleSeesResultsAndAudit(UserRole role) throws Exception {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenSheet(new ResultSheetRow(assay(),
				result(11L, assay(), "98.50", Verdict.PASS, ResultStatus.APPROVED, "kiemnghiem1", 1), List.of()));
		when(resultService.findAudit(SAMPLE_ID)).thenReturn(List.of(
				audit(ResultAction.ENTER, "kiemnghiem1", "2026-10-08T00:30:15+07:00", "98.50", Verdict.PASS, null),
				audit(ResultAction.APPROVE, "duyet", "2026-10-08T08:15:00+07:00", "98.50", Verdict.PASS, null),
				audit(ResultAction.REJECT, "duyet2", "2026-10-08T09:45:30+07:00", "98.50", Verdict.PASS,
						"Sai độ pha loãng")));

		mockMvc.perform(get("/samples/{id}", SAMPLE_ID).with(user(principal("nguoidung", "Người dùng thử", role))))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Kết quả kiểm nghiệm")))
			.andExpect(content().string(containsString("95 – 105")))
			.andExpect(content().string(containsString("Nhật ký kết quả")))
			.andExpect(content().string(containsString("08/10/2026 00:30:15")))
			.andExpect(content().string(containsString("98.50 % – Đạt")))
			.andExpect(content().string(containsString("Đã duyệt")))
			.andExpect(content().string(containsString("Lý do: Sai độ pha loãng")))
			.andExpect(content().string(not(containsString("Chưa có thao tác nào trên kết quả"))));
	}

	@Test
	@DisplayName("TC-BR10-06 Chưa có thao tác nào thì Nhật ký kết quả báo trống")
	void emptyAuditIsShown() throws Exception {
		givenSampleIn(SampleStatus.IN_TESTING);

		assertThat(detailPage()).contains("Nhật ký kết quả").contains("Chưa có thao tác nào trên kết quả");
	}

	@Test
	@DisplayName("TC-BR10-09 Trang chi tiết: mọi thời điểm mang offset UTC đều hiện theo giờ VN, kể cả sau nửa đêm VN khi UTC còn hôm trước")
	void detailShowsEveryTimestampInVietnamTimeWhenValuesAreUtc() throws Exception {
		LabSample sample = sampleIn(SampleStatus.IN_TESTING);
		sample.setReceivedAt(OffsetDateTime.parse("2026-10-07T02:00Z"));
		when(sampleService.findById(SAMPLE_ID)).thenReturn(Optional.of(sample));
		when(sampleService.findStatusHistory(SAMPLE_ID)).thenReturn(List.of(
				historyRow(null, SampleStatus.RECEIVED, "Đăng ký mẫu", "tiepnhan", "2026-10-07T02:05:12Z")));
		TestResult oldPh = rejected(result(12L, ph(), "6.2", Verdict.PASS, ResultStatus.PENDING, "kiemnghiem1", 1),
				"duyet", "Sai độ pha loãng");
		oldPh.setReviewedAt(OffsetDateTime.parse("2026-10-07T16:50Z"));
		TestResult newPh = result(14L, ph(), "6.30", Verdict.PASS, ResultStatus.APPROVED, "kiemnghiem1", 2);
		newPh.setEnteredAt(OffsetDateTime.parse("2026-10-07T17:30:15Z"));
		newPh.setReviewedBy("duyet2");
		newPh.setReviewedAt(OffsetDateTime.parse("2026-10-08T03:45Z"));
		givenSheet(new ResultSheetRow(ph(), newPh, List.of(oldPh)));
		when(resultService.findAudit(SAMPLE_ID)).thenReturn(List.of(
				audit(ResultAction.ENTER, "kiemnghiem1", "2026-10-07T17:30:15Z", "6.30", Verdict.PASS, null)));

		assertThat(detailPage()).contains("07/10/2026 09:00")
			.contains("14/10/2026")
			.contains("07/10/2026 09:05:12")
			.contains("duyet · 07/10/2026 23:50")
			.contains("kiemnghiem1 · 08/10/2026 00:30")
			.contains("duyet2 · 08/10/2026 10:45")
			.contains("08/10/2026 00:30:15")
			.doesNotContain("07/10/2026 02:00")
			.doesNotContain("07/10/2026 17:30");
	}

	private static ResultAudit audit(ResultAction action, String actor, String actedAt, String value, Verdict verdict,
			String rejectReason) {
		ResultAudit row = new ResultAudit();
		row.setAction(action);
		row.setActor(actor);
		row.setActedAt(OffsetDateTime.parse(actedAt));
		row.setParameterName("Định lượng hoạt chất");
		row.setValue(value);
		row.setUnit("%");
		row.setVerdict(verdict);
		row.setRejectReason(rejectReason);
		return row;
	}

	@Test
	@DisplayName("TC-BR08-01 Duyệt qua controller: tên đăng nhập được truyền xuống service, flash Đã duyệt kết quả pH")
	void approvePassesUsernameToService() throws Exception {
		when(resultService.approve(SAMPLE_ID, 12L, "duyet"))
			.thenReturn(result(12L, ph(), "6.2", Verdict.PASS, ResultStatus.APPROVED, "kiemnghiem1", 1));

		mockMvc.perform(post("/samples/{id}/results/{resultId}/approve", SAMPLE_ID, 12L).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/samples/42"))
			.andExpect(flash().attribute("message", "Đã duyệt kết quả pH"));
	}

	@Test
	@DisplayName("TC-BR08-01 Lỗi 4 mắt từ service quay về trang chi tiết kèm flash error nguyên văn")
	void fourEyesViolationRedirectsWithFlashError() throws Exception {
		when(resultService.approve(SAMPLE_ID, 11L, "duyet")).thenThrow(new BusinessException("FOUR_EYES", null,
				"Nguyên tắc 4 mắt: bạn không được duyệt hoặc từ chối kết quả do chính mình nhập"));

		mockMvc.perform(post("/samples/{id}/results/{resultId}/approve", SAMPLE_ID, 11L).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/samples/42"))
			.andExpect(flash().attribute("error",
					"Nguyên tắc 4 mắt: bạn không được duyệt hoặc từ chối kết quả do chính mình nhập"));
	}

	@Test
	@DisplayName("TC-BR09-01 Gửi form từ chối: lý do được cắt khoảng trắng rồi chuyển xuống service, flash Đã từ chối kết quả pH")
	void rejectPassesTrimmedReasonToService() throws Exception {
		when(resultService.reject(SAMPLE_ID, 12L, "Sai độ pha loãng", "duyet"))
			.thenReturn(rejected(result(12L, ph(), "6.2", Verdict.PASS, ResultStatus.PENDING, "kiemnghiem1", 1),
					"duyet", "Sai độ pha loãng"));

		mockMvc.perform(post("/samples/{id}/results/{resultId}/reject", SAMPLE_ID, 12L).with(csrf())
				.param("reason", "  Sai độ pha loãng  "))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/samples/42"))
			.andExpect(flash().attribute("message", "Đã từ chối kết quả pH"));
	}

	@Test
	@DisplayName("TC-BR11-11 Hộp thoại kết luận gửi kèm kết luận nó hiện; controller chuyển đúng kết luận đó xuống service, flash Đã kết luận mẫu: Không đạt")
	void concludeFormCarriesShownVerdictToService() throws Exception {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenSheet(new ResultSheetRow(ph(),
				result(12L, ph(), "7.5", Verdict.FAIL, ResultStatus.APPROVED, "kiemnghiem1", 1), List.of()));

		assertThat(tagContaining(detailPage(), "name=\"expectedVerdict\"")).contains("type=\"hidden\"")
			.contains("value=\"FAIL\"");

		LabSample concluded = sampleIn(SampleStatus.COMPLETED);
		concluded.setConclusion(Verdict.FAIL);
		when(sampleService.conclude(SAMPLE_ID, Verdict.FAIL, "duyet")).thenReturn(concluded);

		mockMvc.perform(post("/samples/{id}/conclude", SAMPLE_ID).with(csrf()).param("expectedVerdict", "FAIL"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/samples/42"))
			.andExpect(flash().attribute("message", "Đã kết luận mẫu: Không đạt"));
	}

}
