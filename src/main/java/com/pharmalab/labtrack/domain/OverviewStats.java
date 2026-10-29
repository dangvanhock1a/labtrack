package com.pharmalab.labtrack.domain;

import java.math.BigDecimal;

/** Ba số của trang Tổng quan đọc trong một câu SQL, kết quả của {@code LabSampleMapper.overviewStats}. */
public class OverviewStats {

	/** BR-14: số mẫu chưa kết thúc có hạn trả trước hôm nay. */
	private long overdueCount;

	/** BR-15: số mẫu hoàn thành trong cửa sổ 30 ngày. */
	private long completedCount;

	/**
	 * BR-15: TAT trung bình, tính bằng giây, của các mẫu trong {@code completedCount}. Là {@code null} khi không có mẫu
	 * nào ({@code avg} của tập rỗng), để trang hiện "—" chứ không hiện 0 giờ.
	 */
	private BigDecimal avgTatSeconds;

	public long getOverdueCount() {
		return overdueCount;
	}

	public void setOverdueCount(long overdueCount) {
		this.overdueCount = overdueCount;
	}

	public long getCompletedCount() {
		return completedCount;
	}

	public void setCompletedCount(long completedCount) {
		this.completedCount = completedCount;
	}

	public BigDecimal getAvgTatSeconds() {
		return avgTatSeconds;
	}

	public void setAvgTatSeconds(BigDecimal avgTatSeconds) {
		this.avgTatSeconds = avgTatSeconds;
	}

}
