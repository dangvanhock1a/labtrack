package com.pharmalab.labtrack.web;

import com.pharmalab.labtrack.config.LabtrackProperties;
import com.pharmalab.labtrack.domain.SampleStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Định dạng để hiển thị, dùng trong template qua {@code ${@fmt.dateTime(...)}}. Mọi chỗ hiển thị giờ đi qua
 * đây để luôn đổi sang múi giờ nghiệp vụ, kể cả khi JVM/DB trả về UTC (NFR-04).
 */
@Component("fmt")
public class DisplayFormat {

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final ZoneId zone;

	public DisplayFormat(LabtrackProperties properties) {
		this.zone = properties.timeZone();
	}

	public String dateTime(OffsetDateTime value) {
		return (value != null) ? value.atZoneSameInstant(zone).format(DATE_TIME) : "";
	}

	public String date(LocalDate value) {
		return (value != null) ? value.format(DATE) : "";
	}

	/** Lớp màu Bootstrap cho badge trạng thái mẫu. */
	public String statusBadge(SampleStatus status) {
		return switch (status) {
			case RECEIVED -> "bg-secondary";
			case IN_TESTING -> "bg-primary";
			case COMPLETED -> "bg-success";
			case CANCELLED -> "bg-dark";
		};
	}

}
