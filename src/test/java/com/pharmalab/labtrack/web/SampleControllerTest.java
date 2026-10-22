package com.pharmalab.labtrack.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.service.BusinessRuleException;
import com.pharmalab.labtrack.service.LabSampleService;
import com.pharmalab.labtrack.service.RegisterSampleCommand;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(SampleController.class)
class SampleControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	LabSampleService sampleService;

	@Test
	@DisplayName("TC-BR02-09 Gửi form trống: hiện lại form với lỗi tiếng Việt, không gọi service")
	void emptyFormShowsValidationErrors() throws Exception {
		mockMvc.perform(post("/samples"))
			.andExpect(status().isOk())
			.andExpect(view().name("samples/form"))
			.andExpect(model().attributeHasFieldErrors("sampleForm", "productName", "batchNo", "sampleType",
					"receivedAt", "dueDate"))
			.andExpect(content().string(containsString("Vui lòng nhập tên sản phẩm")))
			.andExpect(content().string(containsString("Vui lòng nhập số lô")))
			.andExpect(content().string(containsString("Vui lòng chọn loại mẫu")));

		verify(sampleService, never()).register(any(), any());
	}

	@Test
	@DisplayName("TC-BR02-10 Số lô có dấu hoặc khoảng trắng bị từ chối với đúng thông báo BR-02")
	void batchNoWithDiacriticsIsRejected() throws Exception {
		mockMvc.perform(validFormWith("batchNo", "Lô 01"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("sampleForm", "batchNo", "Pattern"))
			.andExpect(content().string(containsString("Số lô chỉ gồm chữ không dấu, số, dấu - và /")));

		verify(sampleService, never()).register(any(), any());
	}

	@Test
	@DisplayName("TC-BR02-11 Form hợp lệ: cắt khoảng trắng, đăng ký với người demo rồi redirect sang chi tiết (PRG)")
	void validFormRegistersAndRedirects() throws Exception {
		LabSample saved = new LabSample();
		saved.setId(42L);
		saved.setCode("LS-2026-00001");
		when(sampleService.register(any(), eq("demo"))).thenReturn(saved);

		mockMvc.perform(validFormWith("productName", "  Paracetamol 500 mg  "))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/samples/42"))
			.andExpect(flash().attribute("message", "Đã đăng ký mẫu LS-2026-00001"));

		ArgumentCaptor<RegisterSampleCommand> command = ArgumentCaptor.forClass(RegisterSampleCommand.class);
		verify(sampleService).register(command.capture(), eq("demo"));
		assertThat(command.getValue()).isEqualTo(new RegisterSampleCommand("Paracetamol 500 mg", "PA-2601",
				SampleType.FINISHED_PRODUCT, LocalDateTime.parse("2026-10-22T09:30"), LocalDate.parse("2026-10-29")));
	}

	@Test
	@DisplayName("TC-BR02-12 Lỗi nghiệp vụ từ service hiện dưới đúng ô nhập")
	void businessRuleErrorIsShownOnField() throws Exception {
		when(sampleService.register(any(), any()))
			.thenThrow(new BusinessRuleException("dueDate", "Hạn trả kết quả phải từ ngày nhận mẫu trở đi"));

		mockMvc.perform(validForm())
			.andExpect(status().isOk())
			.andExpect(view().name("samples/form"))
			.andExpect(model().attributeHasFieldErrorCode("sampleForm", "dueDate", "businessRule"))
			.andExpect(content().string(containsString("Hạn trả kết quả phải từ ngày nhận mẫu trở đi")));
	}

	@Test
	@DisplayName("TC-BR01-09 Hết dải mã: lỗi chung hiện ở đầu form")
	void globalBusinessRuleErrorIsShown() throws Exception {
		when(sampleService.register(any(), any()))
			.thenThrow(new BusinessRuleException("Đã hết dải mã mẫu của năm 2026"));

		mockMvc.perform(validForm())
			.andExpect(status().isOk())
			.andExpect(model().attributeHasErrors("sampleForm"))
			.andExpect(content().string(containsString("Đã hết dải mã mẫu của năm 2026")));
	}

	private static MockHttpServletRequestBuilder validForm() {
		return validFormWith(null, null);
	}

	/** Form hợp lệ, thay giá trị của một ô ({@code param()} của MockMvc chỉ thêm giá trị chứ không ghi đè). */
	private static MockHttpServletRequestBuilder validFormWith(String field, String value) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("productName", "Paracetamol 500 mg");
		params.put("batchNo", "PA-2601");
		params.put("sampleType", "FINISHED_PRODUCT");
		params.put("receivedAt", "2026-10-22T09:30");
		params.put("dueDate", "2026-10-29");
		if (field != null) {
			params.put(field, value);
		}
		MockHttpServletRequestBuilder request = post("/samples");
		params.forEach((name, paramValue) -> request.param(name, paramValue));
		return request;
	}

}
