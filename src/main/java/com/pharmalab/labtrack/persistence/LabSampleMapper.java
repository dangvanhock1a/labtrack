package com.pharmalab.labtrack.persistence;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.pharmalab.labtrack.domain.LabSample;

@Mapper
public interface LabSampleMapper {

	/** Thêm mẫu mới; sau khi gọi, {@code sample.id} được gán khóa do CSDL sinh. */
	void insert(LabSample sample);

	Optional<LabSample> findById(long id);

	/** Mọi mẫu, thời điểm nhận mới nhất trước. */
	List<LabSample> findAll();

}
