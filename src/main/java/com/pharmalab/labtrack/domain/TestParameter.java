package com.pharmalab.labtrack.domain;

import java.math.BigDecimal;

/**
 * Một chỉ tiêu kiểm nghiệm trong danh mục, ánh xạ 1-1 với bảng {@code test_parameter} (BR-06, BR-07).
 * Danh mục chỉ đổi bằng migration (LT-BRD mục 3). POJO chứ không phải record, cùng lý do với
 * {@link StatusHistory}.
 */
public class TestParameter {

	private Long id;

	private String code;

	private String name;

	private String unit;

	private BigDecimal specMin;

	private BigDecimal specMax;

	/** BR-06: chỉ chỉ tiêu đang được dùng mới nhận kết quả mới. */
	private boolean active;

	private int displayOrder;

	/** Tiêu chuẩn hiện hành; lúc nhập, giá trị này được chụp vào kết quả (BR-07, SPEC-004 D7). */
	public Spec spec() {
		return new Spec(specMin, specMax);
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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
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

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public int getDisplayOrder() {
		return displayOrder;
	}

	public void setDisplayOrder(int displayOrder) {
		this.displayOrder = displayOrder;
	}

}
