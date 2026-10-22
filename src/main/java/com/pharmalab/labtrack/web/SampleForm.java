package com.pharmalab.labtrack.web;

import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.service.RegisterSampleCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Form đăng ký mẫu, chỉ kiểm tra định dạng (BR-02). Ràng buộc theo thời gian ("không ở tương lai", hạn trả so với
 * ngày nhận) nằm ở service vì cần Clock và múi giờ nghiệp vụ; không dùng @PastOrPresent vì nó so với giờ JVM.
 */
public class SampleForm {

	@NotBlank(message = "Vui lòng nhập tên sản phẩm")
	@Size(max = 200, message = "Tên sản phẩm tối đa 200 ký tự")
	private String productName;

	// Dùng * thay vì + để ô trống chỉ báo lỗi của @NotBlank.
	@NotBlank(message = "Vui lòng nhập số lô")
	@Size(max = 50, message = "Số lô tối đa 50 ký tự")
	@Pattern(regexp = "[A-Za-z0-9/-]*", message = "Số lô chỉ gồm chữ không dấu, số, dấu - và /")
	private String batchNo;

	@NotNull(message = "Vui lòng chọn loại mẫu")
	private SampleType sampleType;

	// Khớp định dạng của <input type="datetime-local">; giờ treo tường VN.
	@NotNull(message = "Vui lòng nhập thời điểm nhận mẫu")
	@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
	private LocalDateTime receivedAt;

	@NotNull(message = "Vui lòng nhập hạn trả kết quả")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate dueDate;

	static SampleForm from(RegisterSampleCommand command) {
		SampleForm form = new SampleForm();
		form.setProductName(command.productName());
		form.setBatchNo(command.batchNo());
		form.setSampleType(command.sampleType());
		form.setReceivedAt(command.receivedAt());
		form.setDueDate(command.dueDate());
		return form;
	}

	RegisterSampleCommand toCommand() {
		return new RegisterSampleCommand(productName, batchNo, sampleType, receivedAt, dueDate);
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
