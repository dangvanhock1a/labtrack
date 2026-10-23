package com.pharmalab.labtrack.persistence;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.pharmalab.labtrack.domain.ResultReview;
import com.pharmalab.labtrack.domain.TestResult;

/**
 * Kết quả kiểm nghiệm (BR-06..BR-09). Không có method xóa: kết quả bị từ chối được giữ lại (NFR-03), và
 * trigger trong V4 chặn mọi DELETE. Method nhiều tham số khai báo {@code @Param} để không phụ thuộc việc
 * biên dịch có cờ {@code -parameters} hay không.
 */
@Mapper
public interface TestResultMapper {

	/**
	 * Thêm kết quả mới; sau khi gọi, {@code result.id} được gán khóa do CSDL sinh. Hai kết quả hiệu lực cho
	 * cùng (mẫu, chỉ tiêu) bị {@code uq_test_result_effective} chặn, kể cả khi tầng nghiệp vụ bỏ sót (BR-06).
	 */
	void insert(TestResult result);

	/** Một kết quả kèm mã, tên chỉ tiêu; {@code attemptNo} để trống. */
	Optional<TestResult> findById(long id);

	/** Mọi kết quả của một mẫu, kể cả bản bị từ chối, theo thứ tự hiển thị của chỉ tiêu rồi thứ tự nhập. */
	List<TestResult> findBySample(long sampleId);

	/** Mẫu đã có kết quả Chờ duyệt hoặc Đã duyệt cho chỉ tiêu này chưa (BR-06). */
	boolean existsEffective(@Param("sampleId") long sampleId, @Param("parameterId") long parameterId);

	/**
	 * Bản Đã từ chối gần nhất của (mẫu, chỉ tiêu) mà chưa có bản nào nhập lại thay (BR-09). Lần nhập tới trỏ
	 * về nó qua {@code replaces_id}; rỗng nghĩa là lần nhập đầu.
	 */
	Optional<Long> findReplaceableRejectedId(@Param("sampleId") long sampleId,
			@Param("parameterId") long parameterId);

	/**
	 * Duyệt hoặc từ chối nếu kết quả vẫn Chờ duyệt và thuộc đúng mẫu (BR-08). Trả về số dòng được cập nhật:
	 * 0 nghĩa là người khác vừa xử lý trước, hoặc kết quả không thuộc mẫu này.
	 */
	int review(ResultReview review);

}
