package com.pharmalab.labtrack.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.test.context.support.WithSecurityContext;

import com.pharmalab.labtrack.domain.UserRole;

/**
 * Chạy test với một người đã đăng nhập có principal là {@link AppUserPrincipal}, giống principal
 * mà {@link AppUserDetailsService} tạo khi đăng nhập thật. Test nào render trang phải dùng
 * annotation này thay cho {@code @WithMockUser}: navbar đọc {@code principal.fullName} và
 * {@code principal.role.label}, mà principal {@code User} của {@code @WithMockUser} không có hai
 * thuộc tính đó nên trang sẽ lỗi khi render.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@WithSecurityContext(factory = WithAppUserSecurityContextFactory.class)
public @interface WithAppUser {

	String username();

	UserRole role();

	String fullName() default "Người dùng thử";

}
