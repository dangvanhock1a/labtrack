package com.pharmalab.labtrack.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Trang đăng nhập tiếng Việt (UC-01), thay cho trang tiếng Anh mà Spring Security tự sinh. Lớp này
 * chỉ trả view: {@code POST /login} do bộ lọc của Spring Security xử lý, không đi qua controller.
 */
@Controller
public class LoginController {

	@GetMapping("/login")
	String login() {
		return "login";
	}

}
