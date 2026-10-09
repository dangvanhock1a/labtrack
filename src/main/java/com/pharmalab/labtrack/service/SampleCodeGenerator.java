package com.pharmalab.labtrack.service;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pharmalab.labtrack.persistence.SampleCodeCounterMapper;

/** Sinh mã mẫu {@code LS-YYYY-NNNNN} (BR-01). */
@Component
public class SampleCodeGenerator {

	static final int MAX_NUMBER = 99_999;

	private final SampleCodeCounterMapper counterMapper;

	private final Clock clock;

	public SampleCodeGenerator(SampleCodeCounterMapper counterMapper, Clock clock) {
		this.counterMapper = counterMapper;
		this.clock = clock;
	}

	/**
	 * Cấp mã kế tiếp cho mẫu nhận lúc {@code receivedAt}; năm lấy theo múi giờ của
	 * {@link Clock}. Bắt buộc chạy trong transaction của việc thêm mẫu, để bộ đếm và mẫu
	 * cùng commit hoặc cùng rollback.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public String nextCode(OffsetDateTime receivedAt) {
		int year = receivedAt.atZoneSameInstant(clock.getZone()).getYear();
		int number = counterMapper.nextNumber(year);
		if (number > MAX_NUMBER) {
			throw new BusinessException("Đã hết dải mã mẫu của năm " + year);
		}
		return String.format("LS-%04d-%05d", year, number);
	}

}
