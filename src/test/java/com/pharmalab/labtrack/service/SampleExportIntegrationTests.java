package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleFilter;
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.StorageCondition;

/**
 * Xuất CSV (BR-16) trên PostgreSQL thật: file đi qua đúng {@code searchWhere} và {@code searchOrder} của danh sách, và
 * cột Bảo quản đọc từ CSDL (BR-18). Mọi mẫu mang từ khóa BR16X và bộ lọc luôn có từ khóa đó, nên dữ liệu cũ còn trong
 * {@code labtrack_test} không lẫn vào file.
 */
@SpringBootTest
@Transactional
class SampleExportIntegrationTests {

	private static final String KEYWORD = "BR16X";

	@Autowired
	private LabSampleService sampleService;

	private LabSample register(String productName, SamplePriority priority, StorageCondition storageCondition) {
		return sampleService.register(new NewSample(productName, "PA-2601", SampleType.FINISHED_PRODUCT, priority,
				storageCondition, null, null), "tiepnhan");
	}

	@Test
	@DisplayName("TC-BR16-08 Xuất trên CSDL thật: chỉ mẫu khớp mọi điều kiện lọc, Khẩn trước như danh sách, cột Bảo quản đọc từ CSDL")
	void exportFollowsListFilterAndOrder() {
		LabSample normal = register("Mẫu xuất " + KEYWORD, SamplePriority.NORMAL, StorageCondition.FROZEN);
		LabSample urgent = register("Mẫu xuất " + KEYWORD, SamplePriority.URGENT, StorageCondition.COOL);
		LabSample cancelled = register("Mẫu xuất " + KEYWORD, SamplePriority.URGENT, null);
		sampleService.cancel(cancelled.getId(), "Gửi nhầm mẫu", "tiepnhan");
		LabSample otherProduct = register("Mẫu khác", SamplePriority.URGENT, StorageCondition.COOL);

		CsvFile file = sampleService.exportCsv(
				new SampleFilter(KEYWORD.toLowerCase(), SampleStatus.RECEIVED, null, null));
		String[] lines = new String(file.content(), StandardCharsets.UTF_8).split("\r\n");

		assertThat(lines).hasSize(3);
		assertThat(lines[1]).startsWith(urgent.getCode() + ",Mẫu xuất BR16X,PA-2601,Thành phẩm,Khẩn,Mát (2–8 °C),"
				+ "Đã tiếp nhận,");
		assertThat(lines[2]).startsWith(normal.getCode() + ",Mẫu xuất BR16X,PA-2601,Thành phẩm,Bình thường,"
				+ "Đông lạnh (≤ −20 °C),Đã tiếp nhận,");
		assertThat(String.join("\n", lines)).doesNotContain(cancelled.getCode()).doesNotContain(otherProduct.getCode());
	}

}
