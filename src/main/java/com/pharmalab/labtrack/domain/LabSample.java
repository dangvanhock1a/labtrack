package com.pharmalab.labtrack.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Một mẫu kiểm nghiệm, ánh xạ 1-1 với bảng {@code lab_sample}. */
public class LabSample {

	private Long id;

	private String code;

	private String productName;

	private String batchNo;

	private SampleType sampleType;

	private SamplePriority priority;

	/** BR-18. */
	private StorageCondition storageCondition;

	private SampleStatus status;

	private OffsetDateTime receivedAt;

	private LocalDate dueDate;

	private String createdBy;

	private OffsetDateTime createdAt;

	private OffsetDateTime updatedAt;

	/** BR-11: để trống tới khi mẫu được kết luận; cùng có hay cùng trống với {@code completedAt} (V5). */
	private Verdict conclusion;

	private OffsetDateTime completedAt;

	/**
	 * BR-13: số kết quả Đã duyệt và số kết quả hiệu lực (Chờ duyệt hoặc Đã duyệt) của mẫu. Không phải cột của
	 * {@code lab_sample}: chỉ {@code LabSampleMapper.search} điền, đường đọc khác để {@code null}.
	 */
	private Integer approvedCount;

	private Integer effectiveCount;

	/**
	 * BR-14: cờ quá hạn cho template, vì template không có {@link java.time.Clock} để tự tính "hôm nay". Không phải
	 * cột: {@code LabSampleService} gán bằng {@link #isOverdue(LocalDate)} lúc đọc mẫu để hiển thị, đường đọc khác để
	 * {@code false}.
	 */
	private boolean overdue;

	/**
	 * BR-14: quá hạn khi chưa kết thúc và hạn trả trước {@code today}; hạn trả đúng hôm nay thì chưa. {@code today} do
	 * người gọi truyền vào (ngày theo {@link java.time.Clock}) chứ không tự lấy, để ngày theo giờ VN và không phụ thuộc
	 * múi giờ JVM. Cùng điều kiện với lọc {@code overdueOnly} trong {@code LabSampleMapper.xml}. Đối tượng chưa có trạng
	 * thái hay hạn trả (dựng tay, chưa đọc đủ cột) thì không tính là quá hạn.
	 */
	public boolean isOverdue(LocalDate today) {
		return status != null && !status.isTerminal() && dueDate != null && dueDate.isBefore(today);
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getProductName() {
		return productName;
	}

	public void setProductName(String productName) {
		this.productName = productName;
	}

	public String getBatchNo() {
		return batchNo;
	}

	public void setBatchNo(String batchNo) {
		this.batchNo = batchNo;
	}

	public SampleType getSampleType() {
		return sampleType;
	}

	public void setSampleType(SampleType sampleType) {
		this.sampleType = sampleType;
	}

	public SamplePriority getPriority() {
		return priority;
	}

	public void setPriority(SamplePriority priority) {
		this.priority = priority;
	}

	public StorageCondition getStorageCondition() {
		return storageCondition;
	}

	public void setStorageCondition(StorageCondition storageCondition) {
		this.storageCondition = storageCondition;
	}

	public SampleStatus getStatus() {
		return status;
	}

	public void setStatus(SampleStatus status) {
		this.status = status;
	}

	public OffsetDateTime getReceivedAt() {
		return receivedAt;
	}

	public void setReceivedAt(OffsetDateTime receivedAt) {
		this.receivedAt = receivedAt;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public void setDueDate(LocalDate dueDate) {
		this.dueDate = dueDate;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(OffsetDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(OffsetDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Verdict getConclusion() {
		return conclusion;
	}

	public void setConclusion(Verdict conclusion) {
		this.conclusion = conclusion;
	}

	public OffsetDateTime getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(OffsetDateTime completedAt) {
		this.completedAt = completedAt;
	}

	public Integer getApprovedCount() {
		return approvedCount;
	}

	public void setApprovedCount(Integer approvedCount) {
		this.approvedCount = approvedCount;
	}

	public Integer getEffectiveCount() {
		return effectiveCount;
	}

	public void setEffectiveCount(Integer effectiveCount) {
		this.effectiveCount = effectiveCount;
	}

	public boolean isOverdue() {
		return overdue;
	}

	public void setOverdue(boolean overdue) {
		this.overdue = overdue;
	}

}
