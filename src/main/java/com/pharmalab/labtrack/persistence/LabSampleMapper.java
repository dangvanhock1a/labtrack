package com.pharmalab.labtrack.persistence;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.OverviewStats;
import com.pharmalab.labtrack.domain.SampleFilter;
import com.pharmalab.labtrack.domain.StatusChange;
import com.pharmalab.labtrack.domain.StatusCount;
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
	 * {@code filter.keyword()} không được đọc ở đây. {@code today} là ngày hôm nay theo {@code Clock}, dùng khi
	 * {@code filter.overdueOnly()} (BR-14): SQL không tự lấy {@code CURRENT_DATE}, vì nó theo múi giờ phiên CSDL chứ
	 * không theo giờ Việt Nam.
	 */
	long countSearch(@Param("filter") SampleFilter filter, @Param("keyword") String keyword,
			@Param("today") LocalDate today);

	/**
	 * Một trang mẫu khớp bộ lọc, cùng điều kiện với {@link #countSearch}: Khẩn trước rồi nhận mới nhất trước (BR-13),
	 * mỗi mẫu kèm {@code approvedCount} và {@code effectiveCount}.
	 */
	List<LabSample> search(@Param("filter") SampleFilter filter, @Param("keyword") String keyword,
			@Param("today") LocalDate today, @Param("limit") int limit, @Param("offset") int offset);

	/**
	 * Số mẫu theo trạng thái (BR-15). Chỉ có dòng cho trạng thái đang có mẫu; trạng thái thêm vào {@code SampleStatus}
	 * sau này được đếm mà không phải sửa câu SQL.
	 */
	List<StatusCount> countByStatus();

	/**
	 * Số mẫu quá hạn theo {@code today} (BR-14), số mẫu hoàn thành từ {@code since} và TAT trung bình của chúng
	 * (BR-15), trong một câu. Hai mốc do service tính theo {@code Clock}, cùng lý do với {@link #countSearch}.
	 */
	OverviewStats overviewStats(@Param("today") LocalDate today, @Param("since") OffsetDateTime since);

	/**
	 * Tối đa {@code limit} mẫu cần chú ý (BR-15): chưa kết thúc, và Khẩn hoặc quá hạn theo {@code today} (BR-14). Hạn
	 * trả sớm nhất trước; cùng hạn thì Khẩn trước, rồi nhận sớm trước.
	 */
	List<LabSample> findAttention(@Param("today") LocalDate today, @Param("limit") int limit);

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
