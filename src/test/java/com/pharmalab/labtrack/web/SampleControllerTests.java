package com.pharmalab.labtrack.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.service.InvalidStatusTransitionException;
import com.pharmalab.labtrack.service.LabSampleService;

@WebMvcTest(SampleController.class)
class SampleControllerTests {

	private static final long SAMPLE_ID = 42L;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LabSampleService sampleService;

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

	@Test
	@DisplayName("TC-BR03-06 Chuyển trạng thái sai: quay lại trang chi tiết kèm thông báo lỗi dạng flash")
	void startTestingFailureRedirectsToDetailWithFlashError() throws Exception {
		when(sampleService.startTesting(SAMPLE_ID, SampleController.DEMO_USER))
			.thenThrow(new InvalidStatusTransitionException(SampleStatus.COMPLETED, SampleStatus.IN_TESTING));

		mockMvc.perform(post("/samples/{id}/start", SAMPLE_ID))
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
	@DisplayName("TC-BR05-03 Gửi form hủy: lý do được cắt khoảng trắng rồi chuyển xuống tầng nghiệp vụ")
	void cancelPassesTrimmedReasonToService() throws Exception {
		when(sampleService.cancel(SAMPLE_ID, "Gửi nhầm mẫu", SampleController.DEMO_USER))
			.thenReturn(sampleIn(SampleStatus.CANCELLED));

		mockMvc.perform(post("/samples/{id}/cancel", SAMPLE_ID).param("reason", "  Gửi nhầm mẫu  "))
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

}
