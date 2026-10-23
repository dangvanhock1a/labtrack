package com.pharmalab.labtrack.web;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

/**
 * Định dạng ngày giờ cho template, gọi qua bean {@code fmt}: {@code ${@fmt.dateTime(x)}} (NFR-04).
 * Mọi formatter mang sẵn múi giờ {@code labtrack.time-zone}, nên một thời điểm luôn được đổi về giờ
 * Việt Nam trước khi in, dù giá trị mang offset nào (pgjdbc trả UTC) và JVM chạy múi giờ nào. Không
 * dùng {@code #temporals}: nó in {@code OffsetDateTime} theo offset sẵn có của giá trị và in
 * {@code Instant} theo múi giờ JVM, nên giờ hiển thị phụ thuộc vào việc tầng dưới đã đổi múi giờ chưa.
 */
public class DisplayFormat {

	private final DateTimeFormatter dateTime;

	private final DateTimeFormatter dateTimeSeconds;

	private final DateTimeFormatter date;

	public DisplayFormat(ZoneId zone) {
		this.dateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zone);
		this.dateTimeSeconds = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(zone);
		this.date = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(zone);
	}

	/** Thời điểm dạng {@code dd/MM/yyyy HH:mm}. {@code null} thì trả {@code null}, như {@code #temporals}. */
	public String dateTime(TemporalAccessor value) {
		return format(this.dateTime, value);
	}

	/** Thời điểm kèm giây, cho lịch sử trạng thái và nhật ký kết quả (SPEC-004 R17). */
	public String dateTimeSeconds(TemporalAccessor value) {
		return format(this.dateTimeSeconds, value);
	}

	/**
	 * Ngày dạng {@code dd/MM/yyyy}. {@code LocalDate} (hạn trả) không có múi giờ nên in nguyên; một thời
	 * điểm thì lấy ngày theo giờ Việt Nam.
	 */
	public String date(TemporalAccessor value) {
		return format(this.date, value);
	}

	private static String format(DateTimeFormatter formatter, TemporalAccessor value) {
		return (value != null) ? formatter.format(value) : null;
	}

}
