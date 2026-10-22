package com.pharmalab.labtrack.web;

import com.pharmalab.labtrack.service.BusinessRuleException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Lỗi nghiệp vụ từ các thao tác POST trên một mẫu: hiện flash {@code error} rồi quay lại trang chi tiết (PRG). Lỗi gắn
 * với ô nhập của form đăng ký vẫn do controller tự bắt để hiện dưới đúng ô.
 */
@ControllerAdvice
public class GlobalControllerAdvice {

	@ExceptionHandler(BusinessRuleException.class)
	String handleBusinessRule(BusinessRuleException ex, HttpServletRequest request,
			RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("error", ex.getMessage());
		String id = sampleIdOf(request);
		if (id == null || "SAMPLE_NOT_FOUND".equals(ex.getCode())) {
			return "redirect:/samples";
		}
		return "redirect:/samples/" + id;
	}

	@SuppressWarnings("unchecked")
	private static String sampleIdOf(HttpServletRequest request) {
		Map<String, String> variables = (Map<String, String>) request
			.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		return (variables != null) ? variables.get("id") : null;
	}

}
