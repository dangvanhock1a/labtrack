package com.pharmalab.labtrack.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.ResultAction;
import com.pharmalab.labtrack.domain.ResultAudit;
import com.pharmalab.labtrack.domain.ResultReview;
import com.pharmalab.labtrack.domain.ResultStatus;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.TestParameter;
import com.pharmalab.labtrack.domain.TestResult;
import com.pharmalab.labtrack.persistence.LabSampleMapper;
import com.pharmalab.labtrack.persistence.ResultAuditMapper;
import com.pharmalab.labtrack.persistence.TestParameterMapper;
import com.pharmalab.labtrack.persistence.TestResultMapper;

/**
 * Kết quả kiểm nghiệm: nhập, tự đánh giá, duyệt hoặc từ chối theo nguyên tắc 4 mắt, và audit trail
 * (BR-06..BR-10, SPEC-004).
 * <p>
 * Mọi thao tác ghi khóa dòng mẫu trước tiên ({@link LabSampleMapper#lockById}) rồi mới kiểm tra (SPEC-004
 * R14). Hủy mẫu phải chờ khóa đó, nên trạng thái mẫu đọc được vẫn đúng tới lúc commit; hai người thao tác
 * trên cùng mẫu cũng phải xếp hàng, người sau thấy dữ liệu người trước vừa ghi. Lỗi không phụ thuộc dữ liệu
 * (giá trị, lý do) được kiểm tra trước khi khóa, như {@link LabSampleService#cancel}.
 * <p>
 * Mỗi thao tác thành công ghi đúng 1 dòng audit trong cùng transaction (BR-10): ghi audit lỗi thì thao tác
 * rollback theo, thao tác bị chặn thì không có dòng nào.
 */
@Service
public class TestResultService {

	/** Dùng chung với {@code ResultForm}: form và tầng nghiệp vụ báo cùng một câu (SPEC-004 mục 2.2). */
	public static final String VALUE_REQUIRED = "Vui lòng nhập giá trị";

	public static final String VALUE_NEGATIVE = "Giá trị phải từ 0 trở lên";

	public static final String VALUE_TOO_LONG = "Giá trị tối đa 8 chữ số phần nguyên và 4 chữ số thập phân";

	public static final String PARAMETER_NOT_FOUND = "Không tìm thấy chỉ tiêu";

	static final int MAX_INTEGER_DIGITS = 8;

	static final int MAX_FRACTION_DIGITS = 4;

	private final LabSampleMapper sampleMapper;

	private final TestParameterMapper parameterMapper;

	private final TestResultMapper resultMapper;

	private final ResultAuditMapper auditMapper;

	private final Clock clock;

	public TestResultService(LabSampleMapper sampleMapper, TestParameterMapper parameterMapper,
			TestResultMapper resultMapper, ResultAuditMapper auditMapper, Clock clock) {
		this.sampleMapper = sampleMapper;
		this.parameterMapper = parameterMapper;
		this.resultMapper = resultMapper;
		this.auditMapper = auditMapper;
		this.clock = clock;
	}

	/**
	 * Nhập kết quả cho một chỉ tiêu của mẫu Đang kiểm nghiệm (BR-06). Tiêu chuẩn hiện hành được chụp vào kết
	 * quả và đánh giá được tính trên chính bản chụp đó (BR-07). Chỉ tiêu từng bị từ chối thì bản mới trỏ về bản
	 * bị từ chối gần nhất chưa có ai thay (BR-09).
	 */
	@Transactional
	public TestResult enter(long sampleId, NewResult newResult, String actor) {
		BigDecimal value = checkedValue(newResult.value());
		LabSample sample = lockSample(sampleId);
		if (sample.getStatus() != SampleStatus.IN_TESTING) {
			throw new BusinessException("SAMPLE_NOT_IN_TESTING", null, "Chỉ nhập kết quả cho mẫu đang kiểm nghiệm");
		}
		TestParameter parameter = Optional.ofNullable(newResult.parameterId())
			.flatMap(parameterMapper::findById)
			.orElseThrow(() -> new BusinessException("PARAMETER_NOT_FOUND", "parameterId", PARAMETER_NOT_FOUND));
		if (!parameter.isActive()) {
			throw new BusinessException("PARAMETER_INACTIVE", "parameterId",
					"Chỉ tiêu \"" + parameter.getName() + "\" đã ngừng sử dụng");
		}
		if (resultMapper.existsEffective(sampleId, parameter.getId())) {
			throw alreadyEffective(parameter);
		}

		TestResult result = new TestResult();
		result.setSampleId(sampleId);
		result.setParameterId(parameter.getId());
		result.setValue(value);
		result.setUnit(parameter.getUnit());
		result.setSpecMin(parameter.getSpecMin());
		result.setSpecMax(parameter.getSpecMax());
		result.setVerdict(result.spec().evaluate(value));
		result.setStatus(ResultStatus.PENDING);
		result.setEnteredBy(actor);
		result.setEnteredAt(OffsetDateTime.now(clock));
		result.setReplacesId(resultMapper.findReplaceableRejectedId(sampleId, parameter.getId()).orElse(null));
		result.setParameterCode(parameter.getCode());
		result.setParameterName(parameter.getName());
		result.setParameterActive(true);
		try {
			resultMapper.insert(result);
		}
		catch (DuplicateKeyException ex) {
			// Với khóa mẫu ở trên thì không tới được đây; nếu có đường ghi nào bỏ qua khóa, CSDL vẫn giữ BR-06.
			if (String.valueOf(ex.getMessage()).contains("uq_test_result_effective")) {
				throw alreadyEffective(parameter);
			}
			throw ex;
		}
		writeAudit(result.getId(), ResultAction.ENTER, actor, result.getEnteredAt());
		return result;
	}

	/** Duyệt kết quả Chờ duyệt của người khác (BR-08). */
	@Transactional
	public TestResult approve(long sampleId, long resultId, String actor) {
		return review(sampleId, resultId, ResultStatus.APPROVED, null, actor);
	}

	/**
	 * Từ chối kết quả Chờ duyệt của người khác, bắt buộc lý do 5–500 ký tự sau khi cắt khoảng trắng (BR-09).
	 * Kết quả bị từ chối được giữ lại; chỉ tiêu đó nhập lại được thành một bản mới.
	 */
	@Transactional
	public TestResult reject(long sampleId, long resultId, String reason, String actor) {
		String trimmedReason = (reason != null) ? reason.trim() : "";
		if (trimmedReason.length() < LabSampleService.MIN_REASON_LENGTH
				|| trimmedReason.length() > LabSampleService.MAX_REASON_LENGTH) {
			throw new BusinessException("REASON_REQUIRED", "reason", "Lý do phải dài từ 5 đến 500 ký tự");
		}
		return review(sampleId, resultId, ResultStatus.REJECTED, trimmedReason, actor);
	}

	/**
	 * Bảng kết quả của trang chi tiết (UC-12): mỗi chỉ tiêu đang dùng một dòng theo thứ tự hiển thị, cộng các
	 * chỉ tiêu đã ngừng dùng mà mẫu đã có kết quả (SPEC-004 R2).
	 */
	public List<ResultSheetRow> findSheet(long sampleId) {
		Map<Long, List<TestResult>> resultsByParameter = resultMapper.findBySample(sampleId).stream()
			.map(this::toClockZone)
			.collect(Collectors.groupingBy(TestResult::getParameterId));
		return parameterMapper.findAll().stream()
			.filter(parameter -> parameter.isActive() || resultsByParameter.containsKey(parameter.getId()))
			.map(parameter -> ResultSheetRow.of(parameter, resultsByParameter.getOrDefault(parameter.getId(), List.of())))
			.toList();
	}

	/** Nhật ký kết quả của một mẫu, cũ trước mới sau (BR-10, UC-12). */
	public List<ResultAudit> findAudit(long sampleId) {
		List<ResultAudit> audit = auditMapper.findBySample(sampleId);
		audit.forEach(row -> row.setActedAt(inClockZone(row.getActedAt())));
		return audit;
	}

	/**
	 * Thứ tự kiểm tra theo SPEC-004 mục 2.3. Trạng thái kết quả được xét trước nguyên tắc 4 mắt, nên kết quả của
	 * chính mình mà đã có người khác xử lý thì báo "đã được xử lý". Câu UPDATE kèm điều kiện Chờ duyệt chặn lần
	 * nữa; với khóa mẫu thì nó luôn khớp, nhưng vẫn giữ để không phụ thuộc vào khóa.
	 */
	private TestResult review(long sampleId, long resultId, ResultStatus toStatus, String reason, String actor) {
		LabSample sample = lockSample(sampleId);
		if (sample.getStatus() != SampleStatus.IN_TESTING) {
			throw new BusinessException("SAMPLE_NOT_IN_TESTING", null,
					"Chỉ duyệt hoặc từ chối kết quả của mẫu đang kiểm nghiệm");
		}
		TestResult result = resultMapper.findById(resultId)
			.filter(found -> found.getSampleId() == sampleId)
			.orElseThrow(() -> new BusinessException("RESULT_NOT_FOUND", null, "Không tìm thấy kết quả"));
		if (result.getStatus() != ResultStatus.PENDING) {
			throw alreadyProcessed();
		}
		if (result.getEnteredBy().equals(actor)) {
			throw new BusinessException("FOUR_EYES", null,
					"Nguyên tắc 4 mắt: bạn không được duyệt hoặc từ chối kết quả do chính mình nhập");
		}
		ResultReview review = new ResultReview(resultId, sampleId, toStatus, actor, OffsetDateTime.now(clock), reason);
		if (resultMapper.review(review) == 0) {
			throw alreadyProcessed();
		}
		ResultAction action = (toStatus == ResultStatus.APPROVED) ? ResultAction.APPROVE : ResultAction.REJECT;
		writeAudit(resultId, action, actor, review.reviewedAt());
		result.setStatus(toStatus);
		result.setReviewedBy(actor);
		result.setReviewedAt(review.reviewedAt());
		result.setRejectReason(reason);
		return toClockZone(result);
	}

	/**
	 * BR-06: ≥ 0, tối đa 8 chữ số phần nguyên và 4 chữ số thập phân. Đếm trên giá trị gốc, nên số 0 ở cuối
	 * cũng tính ({@code 98.50000} bị chặn), không dựa vào {@code @Digits} của form (SPEC-004 L3). Ký hiệu khoa
	 * học có scale âm ({@code 1e3}) được đổi về scale 0, để giá trị trả về và hiển thị là {@code 1000}.
	 */
	private static BigDecimal checkedValue(BigDecimal value) {
		if (value == null) {
			throw new BusinessException("INVALID_VALUE", "value", VALUE_REQUIRED);
		}
		if (value.signum() < 0) {
			throw new BusinessException("INVALID_VALUE", "value", VALUE_NEGATIVE);
		}
		BigDecimal normalized = (value.scale() < 0) ? value.setScale(0) : value;
		if (normalized.scale() > MAX_FRACTION_DIGITS
				|| normalized.precision() - normalized.scale() > MAX_INTEGER_DIGITS) {
			throw new BusinessException("INVALID_VALUE", "value", VALUE_TOO_LONG);
		}
		return normalized;
	}

	private LabSample lockSample(long sampleId) {
		return sampleMapper.lockById(sampleId)
			.orElseThrow(() -> new BusinessException("SAMPLE_NOT_FOUND", null, "Không tìm thấy mẫu"));
	}

	/** BR-10: đúng 1 dòng. Khác 1 là lỗi hệ thống, ném ra để transaction rollback cả thao tác vừa ghi. */
	private void writeAudit(long resultId, ResultAction action, String actor, OffsetDateTime actedAt) {
		int rows = auditMapper.insert(resultId, action, actor, actedAt);
		if (rows != 1) {
			throw new IllegalStateException(
					"Ghi audit " + action + " cho kết quả " + resultId + " được " + rows + " dòng, cần đúng 1");
		}
	}

	private static BusinessException alreadyEffective(TestParameter parameter) {
		return new BusinessException("RESULT_ALREADY_EFFECTIVE", "parameterId",
				"Chỉ tiêu \"" + parameter.getName() + "\" đã có kết quả đang hiệu lực");
	}

	private static BusinessException alreadyProcessed() {
		return new BusinessException("RESULT_ALREADY_PROCESSED", null, "Kết quả này đã được xử lý trước đó");
	}

	/** pgjdbc trả TIMESTAMPTZ theo UTC; đổi về múi giờ ứng dụng như {@code LabSampleService.toClockZone}. */
	private TestResult toClockZone(TestResult result) {
		result.setEnteredAt(inClockZone(result.getEnteredAt()));
		result.setReviewedAt(inClockZone(result.getReviewedAt()));
		return result;
	}

	private OffsetDateTime inClockZone(OffsetDateTime value) {
		return (value != null) ? value.atZoneSameInstant(clock.getZone()).toOffsetDateTime() : null;
	}

}
