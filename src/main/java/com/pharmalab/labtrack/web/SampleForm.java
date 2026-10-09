package com.pharmalab.labtrack.web;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.service.NewSample;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Form đăng ký mẫu. Chỉ kiểm tra định dạng (BR-02); quy tắc về thời điểm nhận và hạn trả
 * nằm ở {@code LabSampleService} vì cần đồng hồ và múi giờ.
 */
public class SampleForm {

	static final String BATCH_NO_MESSAGE = "Số lô chỉ gồm chữ không dấu, số, dấu - và /";

	@NotBlank(message = "Vui lòng nhập tên sản phẩm")
	@Size(max = 200, message = "Tên sản phẩm tối đa 200 ký tự")
	private String productName;

	@NotNull(message = BATCH_NO_MESSAGE)
	@Pattern(regexp = "[A-Za-z0-9/-]{1,50}", message = BATCH_NO_MESSAGE)
	private String batchNo;

	@NotNull(message = "Vui lòng chọn loại mẫu")
	private SampleType sampleType;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private LocalDateTime receivedAt;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate dueDate;

	static SampleForm from(NewSample sample) {
		SampleForm form = new SampleForm();
		form.setProductName(sample.productName());
		form.setBatchNo(sample.batchNo());
		form.setSampleType(sample.sampleType());
		form.setReceivedAt(sample.receivedAt());
		form.setDueDate(sample.dueDate());
		return form;
	}

	NewSample toNewSample() {
		return new NewSample(productName, batchNo, sampleType, receivedAt, dueDate);
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

	public LocalDateTime getReceivedAt() {
		return receivedAt;
	}

	public void setReceivedAt(LocalDateTime receivedAt) {
		this.receivedAt = receivedAt;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public void setDueDate(LocalDate dueDate) {
		this.dueDate = dueDate;
	}

}
