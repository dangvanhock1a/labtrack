package com.pharmalab.labtrack.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.OverviewStats;
import com.pharmalab.labtrack.domain.SampleFilter;
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.StatusChange;
import com.pharmalab.labtrack.domain.StatusHistory;
import com.pharmalab.labtrack.domain.Verdict;
import com.pharmalab.labtrack.persistence.LabSampleMapper;
import com.pharmalab.labtrack.persistence.StatusHistoryMapper;
import com.pharmalab.labtrack.persistence.TestResultMapper;

@Service
public class LabSampleService {

	static final int DEFAULT_DUE_DAYS = 7;

	/** BR-13. Trùng {@code DEFAULT} của cột {@code lab_sample.priority} (V6), giá trị mà mẫu có trước V6 nhận. */
	static final SamplePriority DEFAULT_PRIORITY = SamplePriority.NORMAL;

	static final int MIN_REASON_LENGTH = 5;

	static final int MAX_REASON_LENGTH = 500;

	/** Lý do của dòng lịch sử lúc đăng ký: không do người dùng nhập nên là nhãn cố định (BR-04). */
	static final String REGISTER_REASON = "Đăng ký mẫu";

	/** Lý do của dòng lịch sử lúc kết luận, nối với nhãn Đạt/Không đạt (BR-04, BR-11). */
	static final String CONCLUSION_REASON_PREFIX = "Kết luận: ";

	/** BR-15: cửa sổ của số mẫu hoàn thành và TAT trung bình, tính bằng ngày lịch giờ VN, kể cả hôm nay. */
	static final int OVERVIEW_WINDOW_DAYS = 30;

	/** BR-15: số mẫu tối đa trong bảng "Mẫu cần chú ý". */
	static final int ATTENTION_LIMIT = 10;

	private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

	private final LabSampleMapper sampleMapper;

	private final StatusHistoryMapper historyMapper;

	private final TestResultMapper resultMapper;

	private final SampleCodeGenerator codeGenerator;

	private final Clock clock;

	/** Số mẫu mỗi trang danh sách (BR-13: 20), đọc từ {@code labtrack.page-size}. */
	private final int pageSize;

	public LabSampleService(LabSampleMapper sampleMapper, StatusHistoryMapper historyMapper,
			TestResultMapper resultMapper, SampleCodeGenerator codeGenerator, Clock clock,
			@Value("${labtrack.page-size}") int pageSize) {
		this.sampleMapper = sampleMapper;
		this.historyMapper = historyMapper;
		this.resultMapper = resultMapper;
		this.codeGenerator = codeGenerator;
		this.clock = clock;
		this.pageSize = pageSize;
	}

	/** Giá trị điền sẵn cho form đăng ký: ưu tiên Bình thường, nhận lúc này, hạn trả sau 7 ngày (BR-02, BR-13). */
	public NewSample newSampleDefaults() {
		LocalDateTime now = now();
		return new NewSample(null, null, null, DEFAULT_PRIORITY, now, now.toLocalDate().plusDays(DEFAULT_DUE_DAYS));
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
		sample.setPriority(newSample.priority() != null ? newSample.priority() : DEFAULT_PRIORITY);
		sample.setStatus(SampleStatus.RECEIVED);
		sample.setReceivedAt(receivedAt);
		sample.setDueDate(dueDate);
		sample.setCreatedBy(actor);
		sampleMapper.insert(sample);
		historyMapper.insert(new StatusChange(sample.getId(), null, SampleStatus.RECEIVED, REGISTER_REASON, actor,
				OffsetDateTime.now(clock)));
		return sample;
	}

	/**
	 * Một trang danh sách mẫu theo bộ lọc (BR-13). Đếm trước rồi mới lấy trang, để số trang ngoài khoảng (gõ tay trên
	 * URL, hay trang cuối vừa hết mẫu) được kéo về trang có thật gần nhất thay vì hiện trang trống. Đúng hai câu SQL
	 * cho một trang (NFR-01): tiến độ đếm ngay trong câu lấy trang. "Hôm nay" của lọc quá hạn và của cờ quá hạn từng
	 * mẫu (BR-14) là cùng một ngày theo {@link Clock}, nên mẫu lọt bộ lọc luôn mang nhãn Quá hạn.
	 */
	public SamplePage search(SampleFilter filter, int page) {
		String keyword = likePattern(filter.keyword());
		LocalDate today = LocalDate.now(clock);
		long total = sampleMapper.countSearch(filter, keyword, today);
		int current = Math.clamp(page, 1, SamplePage.pageCount(total, pageSize));
		List<LabSample> samples = sampleMapper.search(filter, keyword, today, pageSize, (current - 1) * pageSize);
		samples.forEach(sample -> markOverdue(toClockZone(sample), today));
		return new SamplePage(samples, current, pageSize, total);
	}

	/**
	 * Mẫu LIKE "chứa từ khóa", hoặc {@code null} khi không có từ khóa. Escape {@code \} trước rồi mới tới {@code %} và
	 * {@code _}, để chúng là ký tự thường (BR-13) kể cả khi người dùng gõ {@code \}; ký tự escape khớp
	 * {@code ESCAPE '\'} trong {@code LabSampleMapper.xml}.
	 */
	static String likePattern(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}
		return "%" + keyword.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
	}

	/**
	 * Số liệu trang Tổng quan (BR-15), ba câu SQL. Cửa sổ 30 ngày là 30 ngày lịch giờ VN tính cả hôm nay, bắt đầu từ
	 * 00:00 (câu hỏi 3 {@code docs/cau-hoi-lam-ro.md}), nên số liệu không đổi trong suốt một ngày, và "hôm nay" của
	 * cửa sổ trùng "hôm nay" của quá hạn (BR-14). TAT làm tròn ở đây chứ không trong SQL, để unit test kiểm được cách
	 * làm tròn.
	 */
	public Overview overview() {
		LocalDate today = LocalDate.now(clock);
		OffsetDateTime since = today.minusDays(OVERVIEW_WINDOW_DAYS - 1).atStartOfDay(clock.getZone()).toOffsetDateTime();
		Map<SampleStatus, Long> statusCounts = new EnumMap<>(SampleStatus.class);
		for (SampleStatus status : SampleStatus.values()) {
			statusCounts.put(status, 0L);
		}
		sampleMapper.countByStatus().forEach(row -> statusCounts.put(row.getStatus(), row.getCount()));
		OverviewStats stats = sampleMapper.overviewStats(today, since);
		BigDecimal avgTatHours = (stats.getAvgTatSeconds() != null)
				? stats.getAvgTatSeconds().divide(SECONDS_PER_HOUR, 1, RoundingMode.HALF_UP) : null;
		List<LabSample> attention = sampleMapper.findAttention(today, ATTENTION_LIMIT);
		attention.forEach(sample -> markOverdue(toClockZone(sample), today));
		return new Overview(statusCounts, stats.getOverdueCount(), stats.getCompletedCount(), avgTatHours, attention);
	}

	/** Mẫu cho trang chi tiết, kèm cờ quá hạn theo ngày hôm nay của {@link Clock} (BR-14). */
	public Optional<LabSample> findById(long id) {
		LocalDate today = LocalDate.now(clock);
		return sampleMapper.findById(id).map(sample -> markOverdue(toClockZone(sample), today));
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
	 * bật nút. {@code expectedVerdict} là kết luận hộp thoại đã hiện cho người duyệt: kết quả có thể đổi trong lúc
	 * hộp thoại mở, nên tính lại ra khác thì từ chối, không lưu một kết luận người bấm chưa thấy, vì Hoàn thành không
	 * sửa lại được. Câu UPDATE kèm trạng thái cũ chặn lần nữa, như {@link #changeStatus}; với khóa thì nó luôn khớp.
	 * Kết luận, thời điểm hoàn thành và dòng lịch sử lấy từ cùng một {@link StatusChange} (BR-04).
	 */
	@Transactional
	public LabSample conclude(long id, Verdict expectedVerdict, String actor) {
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
		if (readiness.verdict() != expectedVerdict) {
			throw new BusinessException("CONCLUSION_CHANGED", null, "Kết quả của mẫu vừa thay đổi nên kết luận bây giờ là \""
					+ readiness.verdict().getLabel() + "\"; hãy xem lại rồi kết luận lại");
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

	/** BR-14: gán cờ cho template, vì template không có {@link Clock}; điều kiện nằm ở {@link LabSample#isOverdue}. */
	private static LabSample markOverdue(LabSample sample, LocalDate today) {
		sample.setOverdue(sample.isOverdue(today));
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
