package com.pharmalab.labtrack.persistence;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SampleCodeCounterMapper {

	/**
	 * Tăng bộ đếm của năm lên 1 và trả về số mới (năm chưa có thì bắt đầu từ 1). Dòng bộ
	 * đếm bị khóa tới khi transaction kết thúc, nên phải gọi trong cùng transaction với
	 * việc thêm mẫu (BR-01).
	 */
	int nextNumber(int year);

}
