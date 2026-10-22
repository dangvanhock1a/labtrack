package com.pharmalab.labtrack.persistence;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleStatusChange;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LabSampleMapper {

	/** Thêm mẫu và gán id sinh ra vào {@code sample}. */
	void insert(LabSample sample);

	Optional<LabSample> findById(long id);

	/** Mẫu mới nhận lên đầu. */
	List<LabSample> findAll();

	/**
	 * Đổi trạng thái chỉ khi mẫu vẫn đang ở {@code change.fromStatus()} (BR-03).
	 *
	 * @return số dòng được cập nhật; 0 nghĩa là người khác vừa đổi trạng thái
	 */
	int updateStatus(SampleStatusChange change);

}
