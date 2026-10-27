package com.pharmalab.labtrack.web;

import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
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
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.TestResult;
import com.pharmalab.labtrack.domain.Verdict;
import com.pharmalab.labtrack.service.BusinessException;
import com.pharmalab.labtrack.service.ConclusionReadiness;
import com.pharmalab.labtrack.service.LabSampleService;
import com.pharmalab.labtrack.service.ResultSheetRow;
import com.pharmalab.labtrack.service.TestResultService;

import jakarta.validation.Valid;

/**
 * Người thực hiện ghi vào mẫu và lịch sử là tên đăng nhập, lấy từ {@link Principal} mà Spring MVC
 * truyền vào. Dùng {@code Principal} chứ không dùng {@code @AuthenticationPrincipal}, để controller
 * không phải biết lớp principal cụ thể và tầng nghiệp vụ chỉ nhận một chuỗi, không phụ thuộc
 * Spring Security. Các method ghi dữ liệu đều đòi vai trò trong {@code SecurityConfig}, nên
 * {@code principal} ở đây không bao giờ null.
 */
@Controller
@RequestMapping("/samples")
public class SampleController {

	/**
	 * Thứ tự chọn lỗi khi form kết quả có nhiều lỗi cùng lúc (SPEC-004 mục 2.2), theo mã chung nhất của lỗi.
	 * Lỗi của {@code parameterId} luôn đứng đầu, xem {@link #resultErrorRank}.
	 */
	private static final List<String> RESULT_ERROR_ORDER = List.of("typeMismatch", "NotNull", "DecimalMin", "Digits");

	private final LabSampleService sampleService;

	private final TestResultService resultService;

	private final MessageSource messageSource;

	public SampleController(LabSampleService sampleService, TestResultService resultService,
			MessageSource messageSource) {
		this.sampleService = sampleService;
		this.resultService = resultService;
		this.messageSource = messageSource;
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

	@ModelAttribute("priorities")
	SamplePriority[] priorities() {
		return SamplePriority.values();
	}

	@ModelAttribute("statuses")
	SampleStatus[] statuses() {
		return SampleStatus.values();
	}

	/**
	 * Danh sách mẫu có tìm kiếm, lọc và phân trang (BR-13). {@code bindingErrors} có mặt để giá trị lọc hay số trang
	 * không đổi được kiểu không thành lỗi 400: trường đó để trống, tức là không lọc theo nó.
	 */
	@GetMapping
	String list(@ModelAttribute("filter") SampleFilterForm filter, BindingResult bindingErrors, Model model) {
		model.addAttribute("samplePage", sampleService.search(filter.toFilter(), filter.pageOrFirst()));
		return "samples/list";
	}

	@GetMapping("/new")
	String newForm(Model model) {
		model.addAttribute("sampleForm", SampleForm.from(sampleService.newSampleDefaults()));
		return "samples/form";
	}

	@PostMapping
	String register(@Valid @ModelAttribute("sampleForm") SampleForm form, BindingResult bindingResult,
			Principal principal, RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			return "samples/form";
		}
		LabSample sample;
		try {
			sample = sampleService.register(form.toNewSample(), principal.getName());
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
		model.addAttribute("history", sampleService.findStatusHistory(id));
		List<ResultSheetRow> resultSheet = resultService.findSheet(id);
		model.addAttribute("resultSheet", resultSheet);
		model.addAttribute("resultAudit", resultService.findAudit(id));
		// BR-11: nút Kết luận mẫu chỉ đọc kết quả này, cùng hàm mà LabSampleService.conclude dùng để chặn.
		model.addAttribute("conclusionReadiness", ConclusionReadiness.ofSheet(resultSheet));
		return "samples/detail";
	}

	/**
	 * Nhận mẫu vào kiểm nghiệm (BR-03). Lỗi nghiệp vụ do
	 * {@link GlobalControllerAdvice} xử lý, nên ở đây không có try/catch.
	 */
	@PostMapping("/{id}/start")
	String startTesting(@PathVariable long id, Principal principal, RedirectAttributes redirectAttributes) {
		LabSample sample = sampleService.startTesting(id, principal.getName());
		redirectAttributes.addFlashAttribute("message",
				"Mẫu " + sample.getCode() + " đã chuyển sang " + sample.getStatus().getLabel());
		redirectAttributes.addAttribute("id", id);
		return "redirect:/samples/{id}";
	}

	/** Hủy mẫu kèm lý do (BR-05). Lý do để trống gửi lên dưới dạng {@code null} do {@link #trimStrings}. */
	@PostMapping("/{id}/cancel")
	String cancel(@PathVariable long id, @RequestParam(required = false) String reason, Principal principal,
			RedirectAttributes redirectAttributes) {
		LabSample sample = sampleService.cancel(id, reason, principal.getName());
		redirectAttributes.addFlashAttribute("message", "Đã hủy mẫu " + sample.getCode());
		redirectAttributes.addAttribute("id", id);
		return "redirect:/samples/{id}";
	}

	/**
	 * Kết luận mẫu (BR-11). {@code expectedVerdict} là kết luận hộp thoại đã hiện cho người duyệt; thiếu thì Spring
	 * trả 400. Lỗi nghiệp vụ do {@link GlobalControllerAdvice} xử lý.
	 */
	@PostMapping("/{id}/conclude")
	String conclude(@PathVariable long id, @RequestParam Verdict expectedVerdict, Principal principal,
			RedirectAttributes redirectAttributes) {
		LabSample sample = sampleService.conclude(id, expectedVerdict, principal.getName());
		redirectAttributes.addFlashAttribute("message", "Đã kết luận mẫu: " + sample.getConclusion().getLabel());
		redirectAttributes.addAttribute("id", id);
		return "redirect:/samples/{id}";
	}

	/**
	 * Nhập kết quả (BR-06). Form nằm ngay trên trang chi tiết nên lỗi không hiện lại form như
	 * {@link #register}, mà thành flash {@code error} kèm {@code errorParameterId} để trang tô đỏ đúng dòng.
	 * Chỉ báo một lỗi; lỗi {@code typeMismatch} lấy câu chữ từ {@code messages.properties} qua
	 * {@link MessageSource}. Lỗi nghiệp vụ do {@link GlobalControllerAdvice} xử lý.
	 */
	@PostMapping("/{id}/results")
	String enterResult(@PathVariable long id, @Valid @ModelAttribute ResultForm form, BindingResult bindingResult,
			Principal principal, RedirectAttributes redirectAttributes, Locale locale) {
		redirectAttributes.addAttribute("id", id);
		if (bindingResult.hasErrors()) {
			ObjectError error = bindingResult.getAllErrors().stream()
				.min(Comparator.comparingInt(SampleController::resultErrorRank))
				.orElseThrow();
			redirectAttributes.addFlashAttribute("error", messageSource.getMessage(error, locale));
			if (form.getParameterId() != null) {
				redirectAttributes.addFlashAttribute("errorParameterId", form.getParameterId());
			}
			return "redirect:/samples/{id}";
		}
		TestResult result = resultService.enter(id, form.toNewResult(), principal.getName());
		redirectAttributes.addFlashAttribute("message", "Đã nhập kết quả " + result.getParameterName() + ": "
				+ result.getValue().toPlainString() + " " + result.getUnit() + " (" + result.getVerdict().getLabel() + ")");
		return "redirect:/samples/{id}";
	}

	/** Duyệt kết quả (BR-08). */
	@PostMapping("/{id}/results/{resultId}/approve")
	String approveResult(@PathVariable long id, @PathVariable long resultId, Principal principal,
			RedirectAttributes redirectAttributes) {
		TestResult result = resultService.approve(id, resultId, principal.getName());
		redirectAttributes.addFlashAttribute("message", "Đã duyệt kết quả " + result.getParameterName());
		redirectAttributes.addAttribute("id", id);
		return "redirect:/samples/{id}";
	}

	/** Từ chối kết quả kèm lý do (BR-09). Lý do để trống gửi lên dưới dạng {@code null} do {@link #trimStrings}. */
	@PostMapping("/{id}/results/{resultId}/reject")
	String rejectResult(@PathVariable long id, @PathVariable long resultId,
			@RequestParam(required = false) String reason, Principal principal,
			RedirectAttributes redirectAttributes) {
		TestResult result = resultService.reject(id, resultId, reason, principal.getName());
		redirectAttributes.addFlashAttribute("message", "Đã từ chối kết quả " + result.getParameterName());
		redirectAttributes.addAttribute("id", id);
		return "redirect:/samples/{id}";
	}

	private static int resultErrorRank(ObjectError error) {
		if (error instanceof FieldError fieldError && "parameterId".equals(fieldError.getField())) {
			return -1;
		}
		int rank = RESULT_ERROR_ORDER.indexOf(error.getCode());
		return (rank >= 0) ? rank : RESULT_ERROR_ORDER.size();
	}

}
