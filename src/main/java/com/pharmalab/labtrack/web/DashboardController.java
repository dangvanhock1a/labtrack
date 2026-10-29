package com.pharmalab.labtrack.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.pharmalab.labtrack.service.LabSampleService;

/**
 * Trang chủ là trang Tổng quan (BR-15), nơi người dùng tới sau khi đăng nhập. Tách khỏi {@link SampleController} vì
 * controller đó gắn {@code @RequestMapping("/samples")} và các {@code @ModelAttribute} danh sách chọn, trang này
 * không cần.
 */
@Controller
public class DashboardController {

	private final LabSampleService sampleService;

	public DashboardController(LabSampleService sampleService) {
		this.sampleService = sampleService;
	}

	@GetMapping("/")
	String overview(Model model) {
		model.addAttribute("overview", sampleService.overview());
		return "dashboard";
	}

}
