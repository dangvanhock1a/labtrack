package com.pharmalab.labtrack.persistence;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleFilter;
import com.pharmalab.labtrack.domain.StatusChange;
import com.pharmalab.labtrack.domain.Verdict;

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

	/**
	 * Số mẫu khớp bộ lọc (BR-13). {@code keyword} là mẫu LIKE đã escape, hoặc {@code null} khi không tìm theo từ khóa;
	 * {@code filter.keyword()} không được đọc ở đây.
	 */
	long countSearch(@Param("filter") SampleFilter filter, @Param("keyword") String keyword);

	/**
	 * Một trang mẫu khớp bộ lọc, cùng điều kiện với {@link #countSearch}: Khẩn trước rồi nhận mới nhất trước (BR-13),
	 * mỗi mẫu kèm {@code approvedCount} và {@code effectiveCount}.
	 */
	List<LabSample> search(@Param("filter") SampleFilter filter, @Param("keyword") String keyword,
			@Param("limit") int limit, @Param("offset") int offset);

	/**
	 * Đổi trạng thái nếu mẫu vẫn còn ở {@code change.fromStatus()} (BR-03). Trả về số dòng được
	 * cập nhật: 0 nghĩa là người khác vừa đổi trạng thái trước.
	 */
	int updateStatus(StatusChange change);

	/**
	 * Kết luận mẫu (BR-11): như {@link #updateStatus}, nhưng ghi cùng lúc kết luận và thời điểm hoàn thành
	 * ({@code change.changedAt()}), vì {@code ck_lab_sample_completed} (V5) không cho mẫu Hoàn thành thiếu hai cột
	 * đó. Trả về số dòng được cập nhật: 0 nghĩa là mẫu không còn ở {@code change.fromStatus()}.
	 */
	int conclude(@Param("change") StatusChange change, @Param("conclusion") Verdict conclusion);

}
