package com.pharmalab.labtrack.persistence;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.pharmalab.labtrack.domain.AppUser;

@Mapper
public interface AppUserMapper {

	/**
	 * Tài khoản theo tên đăng nhập, so khớp phân biệt hoa thường như ràng buộc {@code UNIQUE} của
	 * CSDL. Trả về cả tài khoản bị khóa: kiểm tra {@code enabled} là việc của Spring Security.
	 */
	Optional<AppUser> findByUsername(String username);

}
