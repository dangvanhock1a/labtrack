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

	/**
	 * Đọc mẫu và khóa dòng của nó tới hết transaction ({@code SELECT … FOR UPDATE}). Mọi thao tác trên kết
	 * quả gọi method này trước tiên (SPEC-004 R14): hủy mẫu phải chờ, nên trạng thái mẫu đọc được ở đây
	 * vẫn đúng cho tới khi thao tác commit. Chỉ có ý nghĩa khi gọi trong transaction.
	 */
	Optional<LabSample> lockById(long id);

	/** Mọi mẫu, thời điểm nhận mới nhất trước. */
	List<LabSample> findAll();

	/**
	 * Đổi trạng thái nếu mẫu vẫn còn ở {@code change.fromStatus()} (BR-03). Trả về số dòng được
	 * cập nhật: 0 nghĩa là người khác vừa đổi trạng thái trước.
	 */
	int updateStatus(StatusChange change);

}
