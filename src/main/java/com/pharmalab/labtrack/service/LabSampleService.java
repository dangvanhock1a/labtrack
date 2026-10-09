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
import com.pharmalab.labtrack.persistence.LabSampleMapper;

@Service
public class LabSampleService {

	static final int DEFAULT_DUE_DAYS = 7;

	private final LabSampleMapper sampleMapper;

	private final SampleCodeGenerator codeGenerator;

	private final Clock clock;

	public LabSampleService(LabSampleMapper sampleMapper, SampleCodeGenerator codeGenerator, Clock clock) {
		this.sampleMapper = sampleMapper;
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
	 * đếm mã khi dữ liệu sai.
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

	/** Nhận mẫu vào kiểm nghiệm: Đã tiếp nhận → Đang kiểm nghiệm (BR-03). */
	@Transactional
	public LabSample startTesting(long id, String actor) {
		throw new UnsupportedOperationException("Chưa implement BR-03");
	}

	/** Hủy mẫu chưa kết thúc, bắt buộc lý do 5–500 ký tự sau khi cắt khoảng trắng (BR-03, BR-05). */
	@Transactional
	public LabSample cancel(long id, String reason, String actor) {
		throw new UnsupportedOperationException("Chưa implement BR-05");
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
	}

	/** pgjdbc trả TIMESTAMPTZ theo UTC; đổi về múi giờ của ứng dụng để hiển thị đúng giờ. */
	private LabSample toClockZone(LabSample sample) {
		sample.setReceivedAt(inClockZone(sample.getReceivedAt()));
		sample.setCreatedAt(inClockZone(sample.getCreatedAt()));
		sample.setUpdatedAt(inClockZone(sample.getUpdatedAt()));
		return sample;
	}

	private OffsetDateTime inClockZone(OffsetDateTime value) {
		return (value != null) ? value.atZoneSameInstant(clock.getZone()).toOffsetDateTime() : null;
	}

}
