package com.pharmalab.labtrack.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pharmalab.labtrack.TestcontainersConfiguration;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

// Dùng năm 2999 cho mã và ngày nhận để không đụng dữ liệu dev có sẵn; mọi test đều rollback.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class LabSampleMapperTest {

	@Autowired
	LabSampleMapper mapper;

	@Test
	@DisplayName("insert gán id; findById đọc lại đủ trường, enum lưu theo tên")
	void insertAndFindById() {
		LabSample sample = sample("LS-2999-00001", OffsetDateTime.parse("2999-01-01T03:00:00Z"));

		mapper.insert(sample);

		assertThat(sample.getId()).isNotNull();
		LabSample found = mapper.findById(sample.getId()).orElseThrow();
		assertThat(found.getCode()).isEqualTo("LS-2999-00001");
		assertThat(found.getProductName()).isEqualTo("Paracetamol 500 mg");
		assertThat(found.getBatchNo()).isEqualTo("PA-2601/A");
		assertThat(found.getSampleType()).isEqualTo(SampleType.FINISHED_PRODUCT);
		assertThat(found.getStatus()).isEqualTo(SampleStatus.RECEIVED);
		assertThat(found.getReceivedAt().toInstant()).isEqualTo(sample.getReceivedAt().toInstant());
		assertThat(found.getDueDate()).isEqualTo(sample.getDueDate());
		assertThat(found.getCreatedBy()).isEqualTo("demo");
	}

	@Test
	@DisplayName("findById trả về rỗng khi không có mẫu")
	void findByIdMissing() {
		assertThat(mapper.findById(-1)).isEmpty();
	}

	@Test
	@DisplayName("Mọi giá trị SampleType đều qua được CHECK ck_lab_sample_type")
	void everySampleTypeMatchesDbCheck() {
		int no = 1;
		for (SampleType type : SampleType.values()) {
			LabSample sample = sample("LS-2999-%05d".formatted(no++), OffsetDateTime.parse("2999-01-01T03:00:00Z"));
			sample.setSampleType(type);
			mapper.insert(sample);
			assertThat(mapper.findById(sample.getId()).orElseThrow().getSampleType()).isEqualTo(type);
		}
	}

	@Test
	@DisplayName("findAll sắp theo thời điểm nhận giảm dần")
	void findAllOrdersByReceivedAtDesc() {
		LabSample older = sample("LS-2999-00001", OffsetDateTime.parse("2999-01-01T03:00:00Z"));
		LabSample newer = sample("LS-2999-00002", OffsetDateTime.parse("2999-06-01T03:00:00Z"));
		mapper.insert(older);
		mapper.insert(newer);

		List<LabSample> all = mapper.findAll();

		assertThat(all).extracting(LabSample::getCode).startsWith("LS-2999-00002", "LS-2999-00001");
	}

	@Test
	@DisplayName("TC-BR01-07 DB chặn mã sai định dạng dù ghi thẳng qua mapper")
	void dbRejectsMalformedCode() {
		LabSample sample = sample("LS-99-1", OffsetDateTime.parse("2999-01-01T03:00:00Z"));

		assertThatThrownBy(() -> mapper.insert(sample)).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_lab_sample_code_format");
	}

	@Test
	@DisplayName("TC-BR01-08 DB chặn mã trùng")
	void dbRejectsDuplicateCode() {
		mapper.insert(sample("LS-2999-00001", OffsetDateTime.parse("2999-01-01T03:00:00Z")));

		LabSample duplicate = sample("LS-2999-00001", OffsetDateTime.parse("2999-01-01T03:00:00Z"));
		assertThatThrownBy(() -> mapper.insert(duplicate)).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uq_lab_sample_code");
	}

	@Test
	@DisplayName("TC-BR02-08 DB chặn hạn trả trước ngày nhận tính theo giờ VN")
	void dbRejectsDueDateBeforeReceivedDateInVietnam() {
		// 2999-01-01T18:00Z là 01:00 ngày 02/01 giờ VN; theo UTC vẫn là ngày 01/01.
		LabSample sample = sample("LS-2999-00001", OffsetDateTime.parse("2999-01-01T18:00:00Z"));
		sample.setDueDate(LocalDate.parse("2999-01-01"));

		assertThatThrownBy(() -> mapper.insert(sample)).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_lab_sample_due_date");
	}

	private static LabSample sample(String code, OffsetDateTime receivedAt) {
		LabSample sample = new LabSample();
		sample.setCode(code);
		sample.setProductName("Paracetamol 500 mg");
		sample.setBatchNo("PA-2601/A");
		sample.setSampleType(SampleType.FINISHED_PRODUCT);
		sample.setStatus(SampleStatus.RECEIVED);
		sample.setReceivedAt(receivedAt);
		sample.setDueDate(receivedAt.toLocalDate().plusDays(7));
		sample.setCreatedBy("demo");
		sample.setCreatedAt(receivedAt);
		sample.setUpdatedAt(receivedAt);
		return sample;
	}

}
