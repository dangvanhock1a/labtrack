package com.pharmalab.labtrack.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Một kết quả kiểm nghiệm, ánh xạ với bảng {@code test_result} (BR-06..BR-09), kèm vài cột đọc từ
 * {@code test_parameter} để hiển thị. POJO chứ không phải record, cùng lý do với {@link StatusHistory}.
 * <p>
 * {@code unit}, {@code specMin}, {@code specMax} là bản chụp từ danh mục lúc nhập (BR-07, SPEC-004 D7):
 * đánh giá và hiển thị luôn dùng bản chụp, nên danh mục đổi sau đó không làm đổi kết quả đã có.
 * {@code value} giữ đúng số chữ số người nhập ({@code 98.50}); hiển thị bằng {@code toPlainString()}.
 */
public class TestResult {

	private Long id;

	private Long sampleId;

	private Long parameterId;

	private BigDecimal value;

	private String unit;

	private BigDecimal specMin;

	private BigDecimal specMax;

	private Verdict verdict;

	private ResultStatus status;

	private String enteredBy;

	private OffsetDateTime enteredAt;

	private String reviewedBy;

	private OffsetDateTime reviewedAt;

	private String rejectReason;

	/** BR-09: bản Đã từ chối mà kết quả này nhập lại thay cho; để trống ở lần nhập đầu. */
	private Long replacesId;

	private String parameterCode;

	private String parameterName;

	private boolean parameterActive;

	/** Lần nhập thứ mấy của chỉ tiêu này trên mẫu ("Lần n"); chỉ có khi đọc theo mẫu. */
	private Integer attemptNo;

	/** Tiêu chuẩn đã chụp lúc nhập, không phải tiêu chuẩn hiện hành của danh mục. */
	public Spec spec() {
		return new Spec(specMin, specMax);
	}

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

	public Long getParameterId() {
		return parameterId;
	}

	public void setParameterId(Long parameterId) {
		this.parameterId = parameterId;
	}

	public BigDecimal getValue() {
		return value;
	}

	public void setValue(BigDecimal value) {
		this.value = value;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public BigDecimal getSpecMin() {
		return specMin;
	}

	public void setSpecMin(BigDecimal specMin) {
		this.specMin = specMin;
	}

	public BigDecimal getSpecMax() {
		return specMax;
	}

	public void setSpecMax(BigDecimal specMax) {
		this.specMax = specMax;
	}

	public Verdict getVerdict() {
		return verdict;
	}

	public void setVerdict(Verdict verdict) {
		this.verdict = verdict;
	}

	public ResultStatus getStatus() {
		return status;
	}

	public void setStatus(ResultStatus status) {
		this.status = status;
	}

	public String getEnteredBy() {
		return enteredBy;
	}

	public void setEnteredBy(String enteredBy) {
		this.enteredBy = enteredBy;
	}

	public OffsetDateTime getEnteredAt() {
		return enteredAt;
	}

	public void setEnteredAt(OffsetDateTime enteredAt) {
		this.enteredAt = enteredAt;
	}

	public String getReviewedBy() {
		return reviewedBy;
	}

	public void setReviewedBy(String reviewedBy) {
		this.reviewedBy = reviewedBy;
	}

	public OffsetDateTime getReviewedAt() {
		return reviewedAt;
	}

	public void setReviewedAt(OffsetDateTime reviewedAt) {
		this.reviewedAt = reviewedAt;
	}

	public String getRejectReason() {
		return rejectReason;
	}

	public void setRejectReason(String rejectReason) {
		this.rejectReason = rejectReason;
	}

	public Long getReplacesId() {
		return replacesId;
	}

	public void setReplacesId(Long replacesId) {
		this.replacesId = replacesId;
	}

	public String getParameterCode() {
		return parameterCode;
	}

	public void setParameterCode(String parameterCode) {
		this.parameterCode = parameterCode;
	}

	public String getParameterName() {
		return parameterName;
	}

	public void setParameterName(String parameterName) {
		this.parameterName = parameterName;
	}

	public boolean isParameterActive() {
		return parameterActive;
	}

	public void setParameterActive(boolean parameterActive) {
		this.parameterActive = parameterActive;
	}

	public Integer getAttemptNo() {
		return attemptNo;
	}

	public void setAttemptNo(Integer attemptNo) {
		this.attemptNo = attemptNo;
	}

}
