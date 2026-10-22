package com.pharmalab.labtrack.service;

import com.pharmalab.labtrack.config.LabtrackProperties;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.persistence.LabSampleMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LabSampleService {

	static final int DEFAULT_TURNAROUND_DAYS = 7;

	private final LabSampleMapper sampleMapper;
	private final SampleCodeGenerator codeGenerator;
	private final Clock clock;
	private final ZoneId zone;

	public LabSampleService(LabSampleMapper sampleMapper, SampleCodeGenerator codeGenerator, Clock clock,
			LabtrackProperties properties) {
		this.sampleMapper = sampleMapper;
		this.codeGenerator = codeGenerator;
		this.clock = clock;
		this.zone = properties.timeZone();
	}

	/** Giá trị mặc định của form đăng ký: nhận lúc bây giờ (giờ VN, tròn phút), hạn trả sau 7 ngày (BR-02). */
	public RegisterSampleCommand newRegistrationDefaults() {
		LocalDateTime now = LocalDateTime.now(clock.withZone(zone)).truncatedTo(ChronoUnit.MINUTES);
		return new RegisterSampleCommand(null, null, null, now, now.toLocalDate().plusDays(DEFAULT_TURNAROUND_DAYS));
	}

	/** Đăng ký mẫu mới ở trạng thái Đã tiếp nhận (UC-02, BR-01, BR-02). */
	@Transactional
	public LabSample register(RegisterSampleCommand command, String actor) {
		OffsetDateTime receivedAt = command.receivedAt().atZone(zone).toOffsetDateTime();
		if (receivedAt.toInstant().isAfter(clock.instant())) {
			throw new BusinessRuleException("receivedAt", "Thời điểm nhận mẫu không được ở tương lai");
		}
		if (command.dueDate().isBefore(command.receivedAt().toLocalDate())) {
			throw new BusinessRuleException("dueDate", "Hạn trả kết quả phải từ ngày nhận mẫu trở đi");
		}

		OffsetDateTime now = OffsetDateTime.now(clock);
		LabSample sample = new LabSample();
		sample.setCode(codeGenerator.nextCode(receivedAt));
		sample.setProductName(command.productName());
		sample.setBatchNo(command.batchNo());
		sample.setSampleType(command.sampleType());
		sample.setStatus(SampleStatus.RECEIVED);
		sample.setReceivedAt(receivedAt);
		sample.setDueDate(command.dueDate());
		sample.setCreatedBy(actor);
		sample.setCreatedAt(now);
		sample.setUpdatedAt(now);
		sampleMapper.insert(sample);
		return sample;
	}

	public List<LabSample> findAll() {
		return sampleMapper.findAll();
	}

	public Optional<LabSample> findById(long id) {
		return sampleMapper.findById(id);
	}

}
