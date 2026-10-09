package com.pharmalab.labtrack.persistence;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.StatusChange;

@Mapper
public interface LabSampleMapper {

	/** Thêm mẫu mới; sau khi gọi, {@code sample.id} được gán khóa do CSDL sinh. */
	void insert(LabSample sample);

	Optional<LabSample> findById(long id);

	/** Mọi mẫu, thời điểm nhận mới nhất trước. */
	List<LabSample> findAll();

	/**
	 * Đổi trạng thái nếu mẫu vẫn còn ở {@code change.fromStatus()} (BR-03). Trả về số dòng được
	 * cập nhật: 0 nghĩa là người khác vừa đổi trạng thái trước.
	 */
	int updateStatus(StatusChange change);

}
