package com.pharmalab.labtrack.persistence;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.pharmalab.labtrack.domain.ResultAction;
import com.pharmalab.labtrack.domain.ResultAudit;

/**
 * Audit trail kết quả (BR-10, NFR-03). Chỉ có thêm và đọc: không có method sửa hay xóa, và trigger trong V4
 * chặn mọi UPDATE/DELETE/TRUNCATE trên {@code result_audit} kể cả khi ghi thẳng bằng SQL.
 */
@Mapper
public interface ResultAuditMapper {

	/**
	 * Ghi một dòng audit cho thao tác vừa làm trên kết quả {@code resultId}. {@code detail} được dựng bằng SQL
	 * từ chính dòng {@code test_result} lúc này, nên nó là dữ liệu đã lưu chứ không phải bản Java tự dựng.
	 * Trả về số dòng được thêm: 0 nghĩa là không có kết quả đó, tầng nghiệp vụ coi là lỗi hệ thống.
	 */
	int insert(@Param("resultId") long resultId, @Param("action") ResultAction action, @Param("actor") String actor,
			@Param("actedAt") OffsetDateTime actedAt);

	/** Nhật ký kết quả của một mẫu, cũ trước mới sau (UC-12). */
	List<ResultAudit> findBySample(long sampleId);

}
