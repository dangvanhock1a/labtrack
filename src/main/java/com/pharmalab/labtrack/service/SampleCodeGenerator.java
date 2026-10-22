package com.pharmalab.labtrack.service;

import com.pharmalab.labtrack.config.LabtrackProperties;
import com.pharmalab.labtrack.persistence.SampleCodeCounterMapper;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** Sinh mã mẫu {@code LS-YYYY-NNNNN} (BR-01). */
@Component
public class SampleCodeGenerator {

	static final int MAX_NUMBER = 99_999;

	private final SampleCodeCounterMapper counterMapper;
	private final ZoneId zone;

	public SampleCodeGenerator(SampleCodeCounterMapper counterMapper, LabtrackProperties properties) {
		this.counterMapper = counterMapper;
		this.zone = properties.timeZone();
	}

	/**
	 * {@code YYYY} là năm nhận mẫu theo múi giờ nghiệp vụ. Gọi trong transaction đăng ký để số đã cấp được
	 * thu hồi khi rollback.
	 */
	public String nextCode(OffsetDateTime receivedAt) {
		int year = receivedAt.atZoneSameInstant(zone).getYear();
		int number = counterMapper.nextNumber(year);
		if (number > MAX_NUMBER) {
			throw new BusinessRuleException("Đã hết dải mã mẫu của năm " + year);
		}
		return "LS-%04d-%05d".formatted(year, number);
	}

}
