package com.pharmalab.labtrack.web;

import java.util.Map;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pharmalab.labtrack.service.BusinessException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Nơi xử lý lỗi nghiệp vụ chung cho mọi controller. Nhờ nó, các method đổi trạng thái mẫu gọi
 * thẳng tầng nghiệp vụ mà không cần try/catch: thông báo tiếng Việt trong
 * {@link BusinessException} được đẩy sang flash "error", người dùng quay lại đúng trang chi tiết
 * của mẫu vừa thao tác (BR-03, BR-05).
 */
@ControllerAdvice
public class GlobalControllerAdvice {

	@ExceptionHandler(BusinessException.class)
	String handleBusinessException(BusinessException ex, HttpServletRequest request,
			RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("error", ex.getMessage());
		String sampleId = uriVariable(request, "id");
		if (sampleId == null) {
			return "redirect:/samples";
		}
		redirectAttributes.addAttribute("id", sampleId);
		return "redirect:/samples/{id}";
	}

	/** Biến đường dẫn của request đang lỗi, ví dụ {@code id} trong {@code /samples/{id}/start}. */
	private static String uriVariable(HttpServletRequest request, String name) {
		Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		return (variables instanceof Map<?, ?> map) ? (String) map.get(name) : null;
	}

}
