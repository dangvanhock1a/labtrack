package com.pharmalab.labtrack.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.StatusChange;
import com.pharmalab.labtrack.domain.StatusHistory;
import com.pharmalab.labtrack.persistence.LabSampleMapper;
import com.pharmalab.labtrack.persistence.StatusHistoryMapper;
import com.pharmalab.labtrack.persistence.TestResultMapper;

@Service
public class LabSampleService {

	static final int DEFAULT_DUE_DAYS = 7;

	static final int MIN_REASON_LENGTH = 5;

	static final int MAX_REASON_LENGTH = 500;

	/** Lý do của dòng lịch sử lúc đăng ký: không do người dùng nhập nên là nhãn cố định (BR-04). */
	static final String REGISTER_REASON = "Đăng ký mẫu";

	/** Lý do của dòng lịch sử lúc kết luận, nối với nhãn Đạt/Không đạt (BR-04, BR-11). */
	static final String CONCLUSION_REASON_PREFIX = "Kết luận: ";

	private final LabSampleMapper sampleMapper;

	private final StatusHistoryMapper historyMapper;

	private final TestResultMapper resultMapper;

	private final SampleCodeGenerator codeGenerator;

	private final Clock clock;

	public LabSampleService(LabSampleMapper sampleMapper, StatusHistoryMapper historyMapper,
			TestResultMapper resultMapper, SampleCodeGenerator codeGenerator, Clock clock) {
		this.sampleMapper = sampleMapper;
		this.historyMapper = historyMapper;
		this.resultMapper = resultMapper;
		this.codeGenerator = codeGenerator;
		this.clock = clock;
	}

	/** Giá trị điền sẵn cho form đăng ký: nhận lúc này, hạn trả sau 7 ngày (BR-02). */
	public NewSample newSampleDefaults() {
		LocalDateTime now = now();
		return new NewSample(null, null, null, now, now.toLocalDate().plusDays(DEFAULT_DUE_DAYS));
	}

	/**
	 * Đăng ký mẫu mới ở trạng thái Đã tiếp nhận (BR-01, BR-02). Thời điểm nhận và hạn trả
	 * được kiểm tra theo múi giờ của {@link Clock} trước khi cấp mã, để không giữ khóa bộ
	 * đếm mã khi dữ liệu sai. Dòng lịch sử Tạo mới → Đã tiếp nhận ghi trong cùng transaction
	 * (BR-04): ghi lỗi thì mẫu và số thứ tự mã cũng rollback theo. Thời điểm của dòng này lấy
	 * từ {@link Clock} chứ không lấy thời điểm nhận, vì thời điểm nhận có thể được nhập lùi.
	 */
	@Transactional
	public LabSample register(NewSample newSample, String actor) {
		LocalDateTime receivedLocal = newSample.receivedAt() != null ? newSample.receivedAt() : now();
		OffsetDateTime receivedAt = receivedLocal.atZone(clock.getZone()).toOffsetDateTime();
		if (receivedAt.isAfter(OffsetDateTime.now(clock))) {
			throw new BusinessException("receivedAt", "Thời điểm nhận mẫu không được ở tương lai");
		}
		LocalDate receivedDate = receivedLocal.toLocalDate();
		LocalDate dueDate = newSample.dueDate() != null ? newSample.dueDate()
				: receivedDate.plusDays(DEFAULT_DUE_DAYS);
		if (dueDate.isBefore(receivedDate)) {
			throw new BusinessException("dueDate", "Hạn trả kết quả phải từ ngày nhận mẫu trở đi");
		}

		LabSample sample = new LabSample();
		sample.setCode(codeGenerator.nextCode(receivedAt));
		sample.setProductName(newSample.productName());
		sample.setBatchNo(newSample.batchNo());
		sample.setSampleType(newSample.sampleType());
		sample.setStatus(SampleStatus.RECEIVED);
		sample.setReceivedAt(receivedAt);
		sample.setDueDate(dueDate);
		sample.setCreatedBy(actor);
		sampleMapper.insert(sample);
		historyMapper.insert(new StatusChange(sample.getId(), null, SampleStatus.RECEIVED, REGISTER_REASON, actor,
				OffsetDateTime.now(clock)));
		return sample;
	}

	public List<LabSample> findAll() {
		List<LabSample> samples = sampleMapper.findAll();
		samples.forEach(this::toClockZone);
		return samples;
	}

	public Optional<LabSample> findById(long id) {
		return sampleMapper.findById(id).map(this::toClockZone);
	}

	/** Lịch sử đổi trạng thái của một mẫu, cũ trước mới sau (BR-04). */
	public List<StatusHistory> findStatusHistory(long sampleId) {
		List<StatusHistory> history = historyMapper.findBySampleId(sampleId);
		history.forEach(this::toClockZone);
		return history;
	}

	/** Nhận mẫu vào kiểm nghiệm: Đã tiếp nhận → Đang kiểm nghiệm (BR-03). */
	@Transactional
	public LabSample startTesting(long id, String actor) {
		return changeStatus(id, SampleStatus.IN_TESTING, null, actor);
	}

	/**
	 * Hủy mẫu chưa kết thúc, bắt buộc lý do 5–500 ký tự sau khi cắt khoảng trắng (BR-03, BR-05).
	 * Lý do được kiểm tra trước khi đọc mẫu: nó không phụ thuộc dữ liệu trong CSDL. Lý do đã cắt
	 * khoảng trắng được lưu vào dòng lịch sử của lần hủy (BR-04).
	 */
	@Transactional
	public LabSample cancel(long id, String reason, String actor) {
		String trimmedReason = (reason != null) ? reason.trim() : "";
		if (trimmedReason.length() < MIN_REASON_LENGTH || trimmedReason.length() > MAX_REASON_LENGTH) {
			throw new BusinessException("REASON_REQUIRED", "reason", "Lý do phải dài từ 5 đến 500 ký tự");
		}
		return changeStatus(id, SampleStatus.CANCELLED, trimmedReason, actor);
	}

	/**
	 * Kết luận mẫu: Đang kiểm nghiệm → Hoàn thành, kèm kết luận Đạt/Không đạt (BR-11). Khóa dòng mẫu trước khi đọc
	 * kết quả (SPEC-004 D9, R14): nhập, duyệt, từ chối kết quả cũng khóa dòng này, nên không ai thêm hay đổi kết quả
	 * được giữa lúc xét điều kiện và lúc ghi; không khóa thì mẫu có thể Hoàn thành khi vừa có một kết quả Chờ duyệt
	 * mới. Điều kiện về kết quả và kết luận tính bằng {@link ConclusionReadiness}, cùng hàm trang chi tiết dùng để
	 * bật nút. Câu UPDATE kèm trạng thái cũ chặn lần nữa, như {@link #changeStatus}; với khóa thì nó luôn khớp.
	 * Kết luận, thời điểm hoàn thành và dòng lịch sử lấy từ cùng một {@link StatusChange} (BR-04).
	 */
	@Transactional
	public LabSample conclude(long id, String actor) {
		LabSample sample = sampleMapper.lockById(id)
			.map(this::toClockZone)
			.orElseThrow(() -> new BusinessException("SAMPLE_NOT_FOUND", null, "Không tìm thấy mẫu"));
		SampleStatus fromStatus = sample.getStatus();
		if (!fromStatus.canTransitionTo(SampleStatus.COMPLETED)) {
			throw new InvalidStatusTransitionException(fromStatus, SampleStatus.COMPLETED);
		}
		ConclusionReadiness readiness = ConclusionReadiness.of(resultMapper.findBySample(id));
		if (!readiness.ready()) {
			throw readiness.toException();
		}
		StatusChange change = new StatusChange(id, fromStatus, SampleStatus.COMPLETED,
				CONCLUSION_REASON_PREFIX + readiness.verdict().getLabel(), actor, OffsetDateTime.now(clock));
		if (sampleMapper.conclude(change, readiness.verdict()) == 0) {
			throw concurrentUpdate();
		}
		historyMapper.insert(change);
		sample.setStatus(SampleStatus.COMPLETED);
		sample.setConclusion(readiness.verdict());
		sample.setCompletedAt(change.changedAt());
		sample.setUpdatedAt(change.changedAt());
		return sample;
	}

	/**
	 * Đổi trạng thái một mẫu (BR-03). Bảng chuyển trong {@link SampleStatus} chặn trước để báo
	 * lỗi rõ ràng; câu UPDATE kèm trạng thái cũ chặn lần nữa, nên khi hai người bấm cùng lúc chỉ
	 * một người đổi được, người còn lại cập nhật 0 dòng và được yêu cầu tải lại trang.
	 * Dòng lịch sử (BR-04) chỉ ghi sau khi UPDATE thành công, và ghi bằng chính {@link StatusChange}
	 * đó, nên lịch sử không thể lệch với trạng thái về người thực hiện hay thời điểm.
	 */
	private LabSample changeStatus(long id, SampleStatus toStatus, String reason, String actor) {
		LabSample sample = findById(id)
			.orElseThrow(() -> new BusinessException("SAMPLE_NOT_FOUND", null, "Không tìm thấy mẫu"));
		SampleStatus fromStatus = sample.getStatus();
		if (!fromStatus.canTransitionTo(toStatus)) {
			throw new InvalidStatusTransitionException(fromStatus, toStatus);
		}
		StatusChange change = new StatusChange(id, fromStatus, toStatus, reason, actor,
				OffsetDateTime.now(clock));
		if (sampleMapper.updateStatus(change) == 0) {
			throw concurrentUpdate();
		}
		historyMapper.insert(change);
		sample.setStatus(toStatus);
		sample.setUpdatedAt(change.changedAt());
		return sample;
	}

	/** BR-03: câu UPDATE kèm trạng thái cũ khớp 0 dòng, tức người khác vừa đổi trạng thái trước. */
	private static BusinessException concurrentUpdate() {
		return new BusinessException("CONCURRENT_UPDATE", null, "Mẫu vừa được người khác cập nhật, hãy tải lại trang");
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
	}

	/** pgjdbc trả TIMESTAMPTZ theo UTC; đổi về múi giờ của ứng dụng để hiển thị đúng giờ. */
	private LabSample toClockZone(LabSample sample) {
		sample.setReceivedAt(inClockZone(sample.getReceivedAt()));
		sample.setCreatedAt(inClockZone(sample.getCreatedAt()));
		sample.setUpdatedAt(inClockZone(sample.getUpdatedAt()));
		sample.setCompletedAt(inClockZone(sample.getCompletedAt()));
		return sample;
	}

	/** Cùng lý do với {@link #toClockZone(LabSample)}: lịch sử phải hiện đúng giờ Việt Nam. */
	private StatusHistory toClockZone(StatusHistory history) {
		history.setChangedAt(inClockZone(history.getChangedAt()));
		return history;
	}

	private OffsetDateTime inClockZone(OffsetDateTime value) {
		return (value != null) ? value.atZoneSameInstant(clock.getZone()).toOffsetDateTime() : null;
	}

}
