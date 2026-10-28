package com.pharmalab.labtrack.web;

import java.nio.charset.StandardCharsets;

import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import com.pharmalab.labtrack.domain.SampleFilter;
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;

/**
 * Bộ lọc danh sách mẫu (BR-13), đọc từ query string: form lọc gửi bằng GET nên URL mang đủ bộ lọc, tải lại trang hay
 * gửi link cho người khác đều ra cùng danh sách. Không có ràng buộc Bean Validation: giá trị không đổi được sang enum
 * hay số (thường do gõ tay trên URL) để trống trường đó, và {@code SampleController.list} bỏ qua lỗi binding, tức là
 * không lọc theo trường đó.
 */
public class SampleFilterForm {

	private String q;

	private SampleStatus status;

	private SamplePriority priority;

	private SampleType sampleType;

	/** BR-14. Không có tham số (ô bỏ chọn không được gửi) hay giá trị không đọc được thì là {@code false}. */
	private boolean overdueOnly;

	private Integer page;

	SampleFilter toFilter() {
		return new SampleFilter(q, status, priority, sampleType, overdueOnly);
	}

	int pageOrFirst() {
		return (page != null) ? page : 1;
	}

	/** Có lọc gì không: danh sách rỗng thì báo "Chưa có mẫu nào." hay "Không có mẫu nào khớp bộ lọc.". */
	public boolean isActive() {
		return q != null || status != null || priority != null || sampleType != null || overdueOnly;
	}

	/**
	 * Đường dẫn tới trang {@code page} với cùng bộ lọc, chỉ ghi tham số có giá trị. Từ khóa được percent-encode toàn
	 * bộ, kể cả {@code +} và {@code %}: để nguyên {@code +} thì máy chủ đọc thành dấu cách và "C+" thành "C ".
	 */
	public String pageUrl(int page) {
		UriComponentsBuilder url = UriComponentsBuilder.fromPath("/samples");
		if (q != null) {
			url.queryParam("q", UriUtils.encode(q, StandardCharsets.UTF_8));
		}
		if (status != null) {
			url.queryParam("status", status.name());
		}
		if (priority != null) {
			url.queryParam("priority", priority.name());
		}
		if (sampleType != null) {
			url.queryParam("sampleType", sampleType.name());
		}
		if (overdueOnly) {
			url.queryParam("overdueOnly", true);
		}
		return url.queryParam("page", page).build(true).toUriString();
	}

	public String getQ() {
		return q;
	}

	public void setQ(String q) {
		this.q = q;
	}

	public SampleStatus getStatus() {
		return status;
	}

	public void setStatus(SampleStatus status) {
		this.status = status;
	}

	public SamplePriority getPriority() {
		return priority;
	}

	public void setPriority(SamplePriority priority) {
		this.priority = priority;
	}

	public SampleType getSampleType() {
		return sampleType;
	}

	public void setSampleType(SampleType sampleType) {
		this.sampleType = sampleType;
	}

	public boolean isOverdueOnly() {
		return overdueOnly;
	}

	public void setOverdueOnly(boolean overdueOnly) {
		this.overdueOnly = overdueOnly;
	}

	public Integer getPage() {
		return page;
	}

	public void setPage(Integer page) {
		this.page = page;
	}

}
