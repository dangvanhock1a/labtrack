package com.pharmalab.labtrack.domain;

/**
 * Số mẫu của một trạng thái (BR-15), một dòng của {@code LabSampleMapper.countByStatus}. Trạng thái không có mẫu nào
 * thì không có dòng; {@code LabSampleService.overview} tự lấp 0.
 */
public class StatusCount {

	private SampleStatus status;

	private long count;

	public SampleStatus getStatus() {
		return status;
	}

	public void setStatus(SampleStatus status) {
		this.status = status;
	}

	public long getCount() {
		return count;
	}

	public void setCount(long count) {
		this.count = count;
	}

}
