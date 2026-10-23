package com.pharmalab.labtrack.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.pharmalab.labtrack.domain.StatusChange;
import com.pharmalab.labtrack.domain.StatusHistory;

@Mapper
public interface StatusHistoryMapper {

	/**
	 * Ghi một dòng lịch sử (BR-04). Dùng chung {@link StatusChange} với
	 * {@link LabSampleMapper#updateStatus}: một lần đổi trạng thái chỉ dựng một đối tượng, nên hai
	 * câu lệnh không thể lệch nhau về người thực hiện hay thời điểm. Gọi trong transaction của tầng
	 * nghiệp vụ, nên dòng lịch sử và trạng thái mới cùng commit hoặc cùng rollback.
	 */
	void insert(StatusChange change);

	/** Lịch sử của một mẫu, cũ trước mới sau. */
	List<StatusHistory> findBySampleId(long sampleId);

}
