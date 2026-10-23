package com.pharmalab.labtrack.domain;

import java.time.OffsetDateTime;

/**
 * Một dòng lịch sử đổi trạng thái (BR-04), ánh xạ 1-1 với bảng {@code sample_status_history}.
 * Là POJO có setter chứ không phải record như {@link StatusChange}: MyBatis chỉ tự ánh xạ record
 * theo <em>thứ tự cột</em> (tùy chọn {@code argNameBasedConstructorAutoMapping} mặc định tắt), nên
 * chỉ cần đổi thứ tự trong {@code <sql id="columns">} là dữ liệu sai mà không báo lỗi. POJO ánh xạ
 * theo tên cột như {@link LabSample}, và sửa được tại chỗ để đổi {@code changedAt} về múi giờ ứng
 * dụng. {@link StatusChange} vẫn là tham số ghi: đó là lệnh, còn lớp này là dòng đọc ra.
 */
public class StatusHistory {

	private Long id;

	private Long sampleId;

	/** Để trống nghĩa là dòng lúc đăng ký, mẫu chưa có trạng thái cũ (BR-04). */
	private SampleStatus fromStatus;

	private SampleStatus toStatus;

	private String changedBy;

	private OffsetDateTime changedAt;

	private String reason;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getSampleId() {
		return sampleId;
	}

	public void setSampleId(Long sampleId) {
		this.sampleId = sampleId;
	}

	public SampleStatus getFromStatus() {
		return fromStatus;
	}

	public void setFromStatus(SampleStatus fromStatus) {
		this.fromStatus = fromStatus;
	}

	public SampleStatus getToStatus() {
		return toStatus;
	}

	public void setToStatus(SampleStatus toStatus) {
		this.toStatus = toStatus;
	}

	public String getChangedBy() {
		return changedBy;
	}

	public void setChangedBy(String changedBy) {
		this.changedBy = changedBy;
	}

	public OffsetDateTime getChangedAt() {
		return changedAt;
	}

	public void setChangedAt(OffsetDateTime changedAt) {
		this.changedAt = changedAt;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

}
