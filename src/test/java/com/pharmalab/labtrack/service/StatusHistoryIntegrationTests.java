package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pharmalab.labtrack.TestcontainersConfiguration;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleFilter;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.StatusHistory;

/** Lịch sử trạng thái (BR-04) trên PostgreSQL thật: số dòng, nội dung dòng và rollback cùng transaction. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class StatusHistoryIntegrationTests {

	@Autowired
	private LabSampleService sampleService;

	private LabSample registerSample(String actor) {
		return sampleService.register(
				new NewSample("Paracetamol 500 mg", "PA-2601", SampleType.FINISHED_PRODUCT, null, null), actor);
	}

	@Test
	@DisplayName("TC-BR04-08 Đăng ký rồi hủy sinh đúng 2 dòng lịch sử, lý do hủy được lưu (BR-05)")
	void registerThenCancelWritesTwoRows() {
		LabSample sample = registerSample("tiepnhan");

		sampleService.cancel(sample.getId(), "  Gửi nhầm mẫu  ", "duyet");

		List<StatusHistory> history = sampleService.findStatusHistory(sample.getId());
		assertThat(history)
			.extracting(StatusHistory::getFromStatus, StatusHistory::getToStatus, StatusHistory::getReason,
					StatusHistory::getChangedBy)
			.containsExactly(tuple(null, SampleStatus.RECEIVED, "Đăng ký mẫu", "tiepnhan"),
					tuple(SampleStatus.RECEIVED, SampleStatus.CANCELLED, "Gửi nhầm mẫu", "duyet"));
		// So với giá trị cũng đọc từ CSDL: PostgreSQL làm tròn tới micro giây, Clock của JVM thì không.
		LabSample cancelled = sampleService.findById(sample.getId()).orElseThrow();
		assertThat(history.get(1).getChangedAt()).isEqualTo(cancelled.getUpdatedAt());
		// pgjdbc trả UTC; đọc ra phải là giờ Việt Nam (Asia/Ho_Chi_Minh không có giờ mùa hè).
		assertThat(history).allSatisfy(row -> assertThat(row.getChangedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(7)));
	}

	@Test
	@DisplayName("TC-BR04-09 Chuyển trạng thái sai (mẫu đã hủy → nhận kiểm nghiệm) thì không có dòng lịch sử mới")
	void invalidTransitionAddsNoRow() {
		LabSample sample = registerSample("tiepnhan");
		sampleService.cancel(sample.getId(), "Gửi nhầm mẫu", "tiepnhan");

		assertThatThrownBy(() -> sampleService.startTesting(sample.getId(), "kiemnghiem1"))
			.isInstanceOf(InvalidStatusTransitionException.class);

		assertThat(sampleService.findStatusHistory(sample.getId())).hasSize(2)
			.last()
			.extracting(StatusHistory::getToStatus)
			.isEqualTo(SampleStatus.CANCELLED);
		assertThat(sampleService.findById(sample.getId()).orElseThrow().getStatus())
			.isEqualTo(SampleStatus.CANCELLED);
	}

	/**
	 * TC-BR04-09 không chứng minh được "cùng transaction": lỗi xảy ra trước mọi câu ghi, và
	 * transaction của test bao ngoài nên không thấy được rollback. Test này chạy không có transaction
	 * của test, để {@code register()} tự commit hoặc rollback; vì rollback nên không để lại dữ liệu.
	 */
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@DisplayName("TC-BR04-10 Ghi dòng lịch sử lỗi thì việc đăng ký rollback theo, không có mẫu nào được tạo")
	void historyFailureRollsBackRegistration() {
		long before = sampleService.search(SampleFilter.NONE, 1).total();

		// lab_sample.created_by (V1) nhận chuỗi trắng, ck_status_history_changed_by (V2) thì không:
		// câu INSERT mẫu chạy được, câu INSERT lịch sử ngay sau đó bị CSDL chặn.
		assertThatThrownBy(() -> registerSample("   ")).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_status_history_changed_by");

		assertThat(sampleService.search(SampleFilter.NONE, 1).total()).isEqualTo(before);
	}

}
