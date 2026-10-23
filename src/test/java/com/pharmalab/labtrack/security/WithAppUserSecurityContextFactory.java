package com.pharmalab.labtrack.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import com.pharmalab.labtrack.domain.AppUser;
import com.pharmalab.labtrack.domain.UserRole;

/** Dựng {@link SecurityContext} cho {@link WithAppUser}. */
public class WithAppUserSecurityContextFactory implements WithSecurityContextFactory<WithAppUser> {

	@Override
	public SecurityContext createSecurityContext(WithAppUser annotation) {
		AppUserPrincipal principal = principal(annotation.username(), annotation.fullName(), annotation.role());
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
		return context;
	}

	/**
	 * Annotation không nhận được tham số của {@code @ParameterizedTest}, nên test lặp qua các vai trò
	 * dùng hàm này với {@code with(user(...))} của MockMvc.
	 */
	public static AppUserPrincipal principal(String username, String fullName, UserRole role) {
		AppUser user = new AppUser();
		user.setUsername(username);
		user.setFullName(fullName);
		user.setRole(role);
		user.setEnabled(true);
		return new AppUserPrincipal(user);
	}

}
