package com.pharmalab.labtrack.persistence;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.pharmalab.labtrack.domain.TestParameter;

@Mapper
public interface TestParameterMapper {

	/**
	 * Mọi chỉ tiêu theo thứ tự hiển thị, kể cả chỉ tiêu đã ngừng dùng: trang chi tiết vẫn phải hiện chỉ tiêu
	 * ngừng dùng khi mẫu đã có kết quả cho nó, chỉ là không có ô nhập (SPEC-004 R2).
	 */
	List<TestParameter> findAll();

	Optional<TestParameter> findById(long id);

	/** Tra theo mã ổn định ({@code ASSAY}, {@code PH}…) thay vì id do CSDL sinh, chủ yếu cho test. */
	Optional<TestParameter> findByCode(String code);

}
