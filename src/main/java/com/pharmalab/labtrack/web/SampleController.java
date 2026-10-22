package com.pharmalab.labtrack.web;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.service.BusinessRuleException;
import com.pharmalab.labtrack.service.LabSampleService;
import jakarta.validation.Valid;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/samples")
public class SampleController {

	// TODO Lab 06: lấy người thực hiện từ Spring Security thay cho hằng này.
	static final String CURRENT_USER = "demo";

	private final LabSampleService sampleService;

	public SampleController(LabSampleService sampleService) {
		this.sampleService = sampleService;
	}

	/** Cắt khoảng trắng hai đầu; chuỗi rỗng thành null để @NotBlank/@NotNull bắt được. */
	@InitBinder
	void initBinder(WebDataBinder binder) {
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
		model.addAttribute("sampleForm", SampleForm.from(sampleService.newRegistrationDefaults()));
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
			sample = sampleService.register(form.toCommand(), CURRENT_USER);
		}
		catch (BusinessRuleException ex) {
			if (ex.getField() != null) {
				bindingResult.rejectValue(ex.getField(), "businessRule", ex.getMessage());
			}
			else {
				bindingResult.reject("businessRule", ex.getMessage());
			}
			return "samples/form";
		}
		redirectAttributes.addAttribute("id", sample.getId());
		redirectAttributes.addFlashAttribute("message", "Đã đăng ký mẫu " + sample.getCode());
		return "redirect:/samples/{id}";
	}

	@GetMapping("/{id:\\d+}")
	String detail(@PathVariable long id, Model model) {
		LabSample sample = sampleService.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mẫu"));
		model.addAttribute("sample", sample);
		return "samples/detail";
	}

}
