package com.pharmalab.labtrack.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SampleCodeCounterMapper {

	/** Tăng nguyên tử bộ đếm của năm và trả về số mới; năm chưa có thì bắt đầu từ 1 (BR-01). */
	int nextNumber(@Param("year") int year);

}
