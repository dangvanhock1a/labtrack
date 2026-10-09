package com.pharmalab.labtrack.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pharmalab.labtrack.service.LabSampleService;

@WebMvcTest(SampleController.class)
class SampleControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LabSampleService sampleService;

	@Test
	@DisplayName("TC-BR02-08 Gửi form trống thì hiện lại form với lỗi tiếng Việt ở các trường bắt buộc")
	void emptyFormIsRejected() throws Exception {
		mockMvc.perform(post("/samples")
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

}
