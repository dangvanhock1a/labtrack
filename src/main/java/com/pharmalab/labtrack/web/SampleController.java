package com.pharmalab.labtrack.web;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.service.BusinessException;
import com.pharmalab.labtrack.service.LabSampleService;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/samples")
public class SampleController {

	/** Người thực hiện tạm thời, thay bằng người đăng nhập ở Lab 06. */
	static final String DEMO_USER = "demo";

	private final LabSampleService sampleService;

	public SampleController(LabSampleService sampleService) {
		this.sampleService = sampleService;
	}

	/** Cắt khoảng trắng hai đầu và coi ô để trống là null, trước khi Bean Validation chạy. */
	@InitBinder
	void trimStrings(WebDataBinder binder) {
		binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
	}

	@ModelAttribute("sampleTypes")
	SampleType[] sampleTypes() {
		return SampleType.values();
	}

	@GetMapping
	String list(Model model) {
		model.addAttribute("samples", sampleService.findAll());
		return "samples/list";
	}

	@GetMapping("/new")
	String newForm(Model model) {
		model.addAttribute("sampleForm", SampleForm.from(sampleService.newSampleDefaults()));
		return "samples/form";
	}

	@PostMapping
	String register(@Valid @ModelAttribute("sampleForm") SampleForm form, BindingResult bindingResult,
			RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			return "samples/form";
		}
		LabSample sample;
		try {
			sample = sampleService.register(form.toNewSample(), DEMO_USER);
		}
		catch (BusinessException ex) {
			if (ex.getField() != null) {
				bindingResult.rejectValue(ex.getField(), "business", ex.getMessage());
			}
			else {
				bindingResult.reject("business", ex.getMessage());
			}
			return "samples/form";
		}
		redirectAttributes.addFlashAttribute("message", "Đã đăng ký mẫu " + sample.getCode());
		redirectAttributes.addAttribute("id", sample.getId());
		return "redirect:/samples/{id}";
	}

	@GetMapping("/{id}")
	String detail(@PathVariable long id, Model model) {
		LabSample sample = sampleService.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mẫu"));
		model.addAttribute("sample", sample);
		return "samples/detail";
	}

	/**
	 * Nhận mẫu vào kiểm nghiệm (BR-03). Lỗi nghiệp vụ do
	 * {@link GlobalControllerAdvice} xử lý, nên ở đây không có try/catch.
	 */
	@PostMapping("/{id}/start")
	String startTesting(@PathVariable long id, RedirectAttributes redirectAttributes) {
		LabSample sample = sampleService.startTesting(id, DEMO_USER);
		redirectAttributes.addFlashAttribute("message",
				"Mẫu " + sample.getCode() + " đã chuyển sang " + sample.getStatus().getLabel());
		redirectAttributes.addAttribute("id", id);
		return "redirect:/samples/{id}";
	}

	/** Hủy mẫu kèm lý do (BR-05). Lý do để trống gửi lên dưới dạng {@code null} do {@link #trimStrings}. */
	@PostMapping("/{id}/cancel")
	String cancel(@PathVariable long id, @RequestParam(required = false) String reason,
			RedirectAttributes redirectAttributes) {
		LabSample sample = sampleService.cancel(id, reason, DEMO_USER);
		redirectAttributes.addFlashAttribute("message", "Đã hủy mẫu " + sample.getCode());
		redirectAttributes.addAttribute("id", id);
		return "redirect:/samples/{id}";
	}

}
