package com.pharmalab.labtrack.service;

import java.math.BigDecimal;

/**
 * Dữ liệu nhập một kết quả (BR-06): chỉ tiêu và giá trị đo. Không có đánh giá Đạt/Không đạt, vì hệ thống tự
 * tính từ tiêu chuẩn (BR-07); request gửi kèm đánh giá thì phần đó không có chỗ để vào.
 */
public record NewResult(Long parameterId, BigDecimal value) {
}
