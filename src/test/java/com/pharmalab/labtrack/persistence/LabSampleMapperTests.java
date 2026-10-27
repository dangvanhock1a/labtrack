package com.pharmalab.labtrack.persistence;

import static com.pharmalab.labtrack.domain.SamplePriority.LOW;
import static com.pharmalab.labtrack.domain.SamplePriority.NORMAL;
import static com.pharmalab.labtrack.domain.SamplePriority.URGENT;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.pharmalab.labtrack.TestcontainersConfiguration;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.service.LabSampleService;
import com.pharmalab.labtrack.service.NewSample;

/**
 * SQL của {@code LabSampleMapper.xml} trên PostgreSQL thật: cột thiếu trong {@code <sql id="columns">} hay câu INSERT
 * chỉ lộ ra khi đọc lên thấy {@code null} hoặc khi CSDL từ chối câu lệnh.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class LabSampleMapperTests {

	@Autowired
	private LabSampleMapper sampleMapper;

	@Autowired
	private LabSampleService sampleService;

	/** Mẫu ghi thẳng qua mapper. Mã năm 2000 để không trùng mã {@code SampleCodeGenerator} cấp cho test khác. */
	private LabSample insertSample(String code, SamplePriority priority) {
		LabSample sample = new LabSample();
		sample.setCode(code);
		sample.setProductName("Paracetamol 500 mg");
		sample.setBatchNo("PA-2601");
		sample.setSampleType(SampleType.FINISHED_PRODUCT);
		sample.setPriority(priority);
		sample.setStatus(SampleStatus.RECEIVED);
		sample.setReceivedAt(OffsetDateTime.parse("2000-03-01T09:00+07:00"));
		sample.setDueDate(LocalDate.of(2000, 3, 8));
		sample.setCreatedBy("tiepnhan");
		sampleMapper.insert(sample);
		return sample;
	}

	@Test
	@DisplayName("TC-BR13-01 Ghi và đọc mức ưu tiên: Thấp và Khẩn ghi qua mapper giữ nguyên; đăng ký qua service không chọn mức ưu tiên thì Bình thường")
	void writesAndReadsPriority() {
		LabSample low = insertSample("LS-2000-00001", LOW);
		LabSample urgent = insertSample("LS-2000-00002", URGENT);
		LabSample registered = sampleService.register(
				new NewSample("Paracetamol 500 mg", "PA-2601", SampleType.FINISHED_PRODUCT, null, null), "tiepnhan");

		assertThat(Stream.of(low, urgent, registered)
			.map(sample -> sampleMapper.findById(sample.getId()).orElseThrow().getPriority()))
			.containsExactly(LOW, URGENT, NORMAL);
	}

}
