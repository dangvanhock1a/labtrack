package com.pharmalab.labtrack.web;

import static com.pharmalab.labtrack.domain.UserRole.MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pharmalab.labtrack.config.SecurityConfig;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.security.WithAppUser;
import com.pharmalab.labtrack.service.LabSampleService;
import com.pharmalab.labtrack.service.Overview;

/**
 * Trang Tổng quan (BR-15) hiển thị đúng số liệu service trả về. Số liệu tính thế nào nằm ở
 * {@code LabSampleOverviewTests} và {@code OverviewIntegrationTests}; quyền xem trang nằm ở {@link AccessControlTests}.
 */
@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
@WithAppUser(username = "quanly", role = MANAGER)
class DashboardControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LabSampleService sampleService;

	private static Map<SampleStatus, Long> counts(long received, long inTesting, long completed, long cancelled) {
		Map<SampleStatus, Long> counts = new EnumMap<>(SampleStatus.class);
		counts.put(SampleStatus.RECEIVED, received);
		counts.put(SampleStatus.IN_TESTING, inTesting);
		counts.put(SampleStatus.COMPLETED, completed);
		counts.put(SampleStatus.CANCELLED, cancelled);
		return counts;
	}

	private static LabSample attentionSample(long id, String code, SamplePriority priority, boolean overdue) {
		LabSample sample = new LabSample();
		sample.setId(id);
		sample.setCode(code);
		sample.setProductName("Paracetamol 500 mg");
		sample.setBatchNo("PA-2601");
		sample.setSampleType(SampleType.FINISHED_PRODUCT);
		sample.setPriority(priority);
		sample.setStatus(SampleStatus.IN_TESTING);
		sample.setReceivedAt(OffsetDateTime.parse("2026-10-07T09:00+07:00"));
		sample.setDueDate(LocalDate.parse("2026-10-14"));
		sample.setOverdue(overdue);
		return sample;
	}

	private String render(Overview overview) throws Exception {
		when(sampleService.overview()).thenReturn(overview);
		return mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(view().name("dashboard"))
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	/** Chữ bên trong thẻ mang {@code data-testid} đã cho, bỏ khoảng trắng hai đầu. */
	private static String textOf(String html, String testId) {
		int at = html.indexOf("data-testid=\"" + testId + "\"");
		assertThat(at).as(testId).isNotNegative();
		int start = html.indexOf('>', at) + 1;
		return html.substring(start, html.indexOf('<', start)).strip();
	}

	/** HTML của từng dòng bảng "Mẫu cần chú ý", theo thứ tự hiển thị. */
	private static List<String> attentionRows(String html) {
		String[] parts = html.split("data-testid=\"attention-row\"");
		return List.of(parts).subList(1, parts.length).stream().map(row -> row.substring(0, row.indexOf("</tr>"))).toList();
	}

	@Test
	@DisplayName("TC-BR15-12 Trang Tổng quan hiện đủ 4 trạng thái kể cả trạng thái có 0 mẫu, số quá hạn, số hoàn thành 30 ngày và TAT \"12.5 giờ\"")
	void dashboardShowsCountsAndAverageTat() throws Exception {
		String html = render(new Overview(counts(3, 0, 7, 1), 2, 5, new BigDecimal("12.5"), List.of()));

		assertThat(html).contains("<h1 class=\"h3 mb-3\">Tổng quan</h1>");
		assertThat(textOf(html, "status-count-RECEIVED")).isEqualTo("3");
		assertThat(textOf(html, "status-count-IN_TESTING")).isEqualTo("0");
		assertThat(textOf(html, "status-count-COMPLETED")).isEqualTo("7");
		assertThat(textOf(html, "status-count-CANCELLED")).isEqualTo("1");
		assertThat(textOf(html, "overdue-count")).isEqualTo("2");
		assertThat(textOf(html, "completed-count")).isEqualTo("5");
		assertThat(textOf(html, "avg-tat")).isEqualTo("12.5 giờ");
		assertThat(html).doesNotContain("Chưa có mẫu hoàn thành trong 30 ngày");
	}

	@Test
	@DisplayName("TC-BR15-13 Chưa có mẫu hoàn thành trong 30 ngày: ô TAT hiện \"—\" kèm chú thích; không có mẫu cần chú ý thì báo \"Không có mẫu cần chú ý.\"")
	void emptyDashboardShowsDashAndNoAttentionMessage() throws Exception {
		String html = render(new Overview(counts(0, 0, 0, 0), 0, 0, null, List.of()));

		assertThat(textOf(html, "avg-tat")).isEqualTo("—");
		assertThat(html).contains("Chưa có mẫu hoàn thành trong 30 ngày");
		assertThat(html).contains("Không có mẫu cần chú ý.");
		assertThat(attentionRows(html)).isEmpty();
	}

	@Test
	@DisplayName("TC-BR15-14 Bảng mẫu cần chú ý giữ thứ tự service trả về, mã mẫu dẫn tới chi tiết; nhãn Quá hạn chỉ ở mẫu quá hạn (BR-14)")
	void attentionTableShowsOverdueBadgeOnlyOnOverdueRows() throws Exception {
		String html = render(new Overview(counts(1, 1, 0, 0), 1, 0, null, List.of(
				attentionSample(41, "LS-2026-00041", SamplePriority.NORMAL, true),
				attentionSample(42, "LS-2026-00042", SamplePriority.URGENT, false))));

		List<String> rows = attentionRows(html);
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0)).contains("href=\"/samples/41\"", "LS-2026-00041", "data-testid=\"overdue-badge\"");
		assertThat(rows.get(1)).contains("href=\"/samples/42\"", "LS-2026-00042", "Khẩn")
			.doesNotContain("data-testid=\"overdue-badge\"");
		assertThat(html).doesNotContain("Không có mẫu cần chú ý.");
	}

}
