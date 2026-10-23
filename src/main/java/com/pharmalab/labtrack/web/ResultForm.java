package com.pharmalab.labtrack.web;

import java.math.BigDecimal;

import com.pharmalab.labtrack.service.NewResult;
import com.pharmalab.labtrack.service.TestResultService;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Form nhập một kết quả (BR-06). Cố ý không có trường đánh giá: request gửi kèm {@code verdict} thì Spring
 * không có chỗ để gán, nên đánh giá chỉ đến từ tiêu chuẩn (BR-07). Thông báo lấy từ
 * {@link TestResultService} để form và tầng nghiệp vụ báo cùng một câu; tầng nghiệp vụ vẫn kiểm tra lại, vì
 * {@code @Digits} có thể bỏ số 0 ở cuối trước khi đếm (SPEC-004 L3).
 */
public class ResultForm {

	@NotNull(message = TestResultService.PARAMETER_NOT_FOUND)
	private Long parameterId;

	@NotNull(message = TestResultService.VALUE_REQUIRED)
	@DecimalMin(value = "0", message = TestResultService.VALUE_NEGATIVE)
	@Digits(integer = 8, fraction = 4, message = TestResultService.VALUE_TOO_LONG)
	private BigDecimal value;

	NewResult toNewResult() {
		return new NewResult(parameterId, value);
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

}
