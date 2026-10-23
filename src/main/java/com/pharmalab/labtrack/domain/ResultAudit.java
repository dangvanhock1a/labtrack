package com.pharmalab.labtrack.domain;

import java.time.OffsetDateTime;

/**
 * Một dòng nhật ký kết quả để hiển thị (BR-10, UC-12): dòng {@code result_audit} kèm tên chỉ tiêu, và các
 * trường lấy từ {@code detail} là ảnh chụp kết quả ngay sau thao tác. {@code value} là chuỗi đọc từ JSONB,
 * giữ đúng cách viết lúc lưu ({@code "98.50"}). POJO chứ không phải record, cùng lý do với {@link StatusHistory}.
 */
public class ResultAudit {

	private Long id;

	private ResultAction action;

	private String actor;

	private OffsetDateTime actedAt;

	private String parameterName;

	private String value;

	private String unit;

	private Verdict verdict;

	private String rejectReason;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public ResultAction getAction() {
		return action;
	}

	public void setAction(ResultAction action) {
		this.action = action;
	}

	public String getActor() {
		return actor;
	}

	public void setActor(String actor) {
		this.actor = actor;
	}

	public OffsetDateTime getActedAt() {
		return actedAt;
	}

	public void setActedAt(OffsetDateTime actedAt) {
		this.actedAt = actedAt;
	}

	public String getParameterName() {
		return parameterName;
	}

	public void setParameterName(String parameterName) {
		this.parameterName = parameterName;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public Verdict getVerdict() {
		return verdict;
	}

	public void setVerdict(Verdict verdict) {
		this.verdict = verdict;
	}

	public String getRejectReason() {
		return rejectReason;
	}

	public void setRejectReason(String rejectReason) {
		this.rejectReason = rejectReason;
	}

}
