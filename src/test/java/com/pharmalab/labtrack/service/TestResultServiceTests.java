package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.ResultAction;
import com.pharmalab.labtrack.domain.ResultReview;
import com.pharmalab.labtrack.domain.ResultStatus;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.TestParameter;
import com.pharmalab.labtrack.domain.TestResult;
import com.pharmalab.labtrack.domain.Verdict;
import com.pharmalab.labtrack.persistence.LabSampleMapper;
import com.pharmalab.labtrack.persistence.ResultAuditMapper;
import com.pharmalab.labtrack.persistence.TestParameterMapper;
import com.pharmalab.labtrack.persistence.TestResultMapper;

/** Kết quả kiểm nghiệm: nhập (BR-06, BR-07), duyệt và từ chối theo nguyên tắc 4 mắt (BR-08, BR-09), audit (BR-10). */
class TestResultServiceTests {

	/** 00:30 ngày 08/10/2026 giờ VN, khi UTC còn là 17:30 ngày 07/10. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T17:30:00Z"),
			ZoneId.of("Asia/Ho_Chi_Minh"));

	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-08T00:30+07:00");

	private static final long SAMPLE_ID = 42L;

	private static final long PH_ID = 2L;

	private static final long RESULT_ID = 7L;

	private final LabSampleMapper sampleMapper = mock(LabSampleMapper.class);

	private final TestParameterMapper parameterMapper = mock(TestParameterMapper.class);

	private final TestResultMapper resultMapper = mock(TestResultMapper.class);

	private final ResultAuditMapper auditMapper = mock(ResultAuditMapper.class);

	private final TestResultService service = new TestResultService(sampleMapper, parameterMapper, resultMapper,
			auditMapper, CLOCK);

	@BeforeEach
	void insertAssignsIdAndAuditWritesOneRow() {
		doAnswer(invocation -> {
			invocation.<TestResult>getArgument(0).setId(RESULT_ID);
			return null;
		}).when(resultMapper).insert(any());
		when(auditMapper.insert(anyLong(), any(), any(), any())).thenReturn(1);
	}

	private void givenSampleIn(SampleStatus status) {
		LabSample sample = new LabSample();
		sample.setId(SAMPLE_ID);
		sample.setCode("LS-2026-00042");
		sample.setStatus(status);
		when(sampleMapper.lockById(SAMPLE_ID)).thenReturn(Optional.of(sample));
	}

	/** pH 4.5 – 7, theo V4. */
	private TestParameter givenPh(boolean active) {
		TestParameter ph = new TestParameter();
		ph.setId(PH_ID);
		ph.setCode("PH");
		ph.setName("pH");
		ph.setUnit("pH");
		ph.setSpecMin(new BigDecimal("4.5"));
		ph.setSpecMax(new BigDecimal("7"));
		ph.setActive(active);
		ph.setDisplayOrder(2);
		when(parameterMapper.findById(PH_ID)).thenReturn(Optional.of(ph));
		return ph;
	}

	private void givenResult(long sampleId, ResultStatus status, String enteredBy) {
		TestResult result = new TestResult();
		result.setId(RESULT_ID);
		result.setSampleId(sampleId);
		result.setParameterId(PH_ID);
		result.setValue(new BigDecimal("6.2"));
		result.setUnit("pH");
		result.setSpecMin(new BigDecimal("4.5"));
		result.setSpecMax(new BigDecimal("7"));
		result.setVerdict(Verdict.PASS);
		result.setStatus(status);
		result.setEnteredBy(enteredBy);
		result.setEnteredAt(OffsetDateTime.parse("2026-10-07T16:00Z"));
		when(resultMapper.findById(RESULT_ID)).thenReturn(Optional.of(result));
	}

	private TestResult insertedResult() {
		ArgumentCaptor<TestResult> inserted = ArgumentCaptor.forClass(TestResult.class);
		verify(resultMapper).insert(inserted.capture());
		return inserted.getValue();
	}

	private static NewResult ph(String value) {
		return new NewResult(PH_ID, (value != null) ? new BigDecimal(value) : null);
	}

	@Test
	@DisplayName("TC-BR06-01 Mẫu Đang kiểm nghiệm: nhập được, chụp tiêu chuẩn, tự đánh giá, ghi 1 dòng audit ENTER cùng thời điểm (00:30 giờ VN khi UTC còn là hôm trước)")
	void entersResultForSampleInTesting() {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenPh(true);

		TestResult returned = service.enter(SAMPLE_ID, ph("7.0001"), "kiemnghiem1");

		TestResult inserted = insertedResult();
		assertThat(returned).isSameAs(inserted);
		assertThat(inserted.getSampleId()).isEqualTo(SAMPLE_ID);
		assertThat(inserted.getParameterId()).isEqualTo(PH_ID);
		assertThat(inserted.getValue().toPlainString()).isEqualTo("7.0001");
		assertThat(inserted.getUnit()).isEqualTo("pH");
		assertThat(inserted.getSpecMin().toPlainString()).isEqualTo("4.5");
		assertThat(inserted.getSpecMax().toPlainString()).isEqualTo("7");
		assertThat(inserted.getVerdict()).isEqualTo(Verdict.FAIL);
		assertThat(inserted.getStatus()).isEqualTo(ResultStatus.PENDING);
		assertThat(inserted.getEnteredBy()).isEqualTo("kiemnghiem1");
		assertThat(inserted.getEnteredAt()).isEqualTo(NOW);
		assertThat(inserted.getReplacesId()).isNull();
		verify(auditMapper).insert(RESULT_ID, ResultAction.ENTER, "kiemnghiem1", NOW);
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@EnumSource(value = SampleStatus.class, names = { "RECEIVED", "COMPLETED", "CANCELLED" })
	@DisplayName("TC-BR06-01 Mẫu không Đang kiểm nghiệm thì không nhập được kết quả, không INSERT, không audit")
	void enterRequiresSampleInTesting(SampleStatus status) {
		givenSampleIn(status);
		givenPh(true);

		assertThatThrownBy(() -> service.enter(SAMPLE_ID, ph("6.2"), "kiemnghiem1"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("SAMPLE_NOT_IN_TESTING");
				assertThat(ex).hasMessage("Chỉ nhập kết quả cho mẫu đang kiểm nghiệm");
			});
		verify(resultMapper, never()).insert(any());
		verifyNoInteractions(auditMapper);
	}

	@Test
	@DisplayName("TC-BR06-01 Mẫu không tồn tại → Không tìm thấy mẫu")
	void enterRequiresExistingSample() {
		assertThatThrownBy(() -> service.enter(SAMPLE_ID, ph("6.2"), "kiemnghiem1"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("SAMPLE_NOT_FOUND");
				assertThat(ex).hasMessage("Không tìm thấy mẫu");
			});
		verify(resultMapper, never()).insert(any());
	}

	@ParameterizedTest(name = "[{index}] parameterId = {0}")
	@NullSource
	@ValueSource(longs = 999L)
	@DisplayName("TC-BR06-02 Chỉ tiêu không tồn tại → Không tìm thấy chỉ tiêu")
	void enterRequiresExistingParameter(Long parameterId) {
		givenSampleIn(SampleStatus.IN_TESTING);

		assertThatThrownBy(() -> service.enter(SAMPLE_ID, new NewResult(parameterId, BigDecimal.ONE), "kiemnghiem1"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("PARAMETER_NOT_FOUND");
				assertThat(ex.getField()).isEqualTo("parameterId");
				assertThat(ex).hasMessage("Không tìm thấy chỉ tiêu");
			});
		verify(resultMapper, never()).insert(any());
		verifyNoInteractions(auditMapper);
	}

	@Test
	@DisplayName("TC-BR06-02 Chỉ tiêu đã ngừng sử dụng → thông báo có tên chỉ tiêu")
	void enterRejectsInactiveParameter() {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenPh(false);

		assertThatThrownBy(() -> service.enter(SAMPLE_ID, ph("6.2"), "kiemnghiem1"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("PARAMETER_INACTIVE");
				assertThat(ex.getField()).isEqualTo("parameterId");
				assertThat(ex).hasMessage("Chỉ tiêu \"pH\" đã ngừng sử dụng");
			});
		verify(resultMapper, never()).insert(any());
		verifyNoInteractions(auditMapper);
	}

	@ParameterizedTest(name = "[{index}] {0} → lưu {1}")
	@CsvSource(textBlock = """
			0,              0
			0.0000,         0.0000
			98.50,          98.50
			99999999.9999,  99999999.9999
			1e3,            1000
			""")
	@DisplayName("TC-BR06-03 Giá trị hợp lệ được lưu đúng số chữ số người nhập; ký hiệu khoa học 1e3 lưu thành 1000")
	void acceptsValidValues(BigDecimal value, String stored) {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenPh(true);

		service.enter(SAMPLE_ID, new NewResult(PH_ID, value), "kiemnghiem1");

		assertThat(insertedResult().getValue().toPlainString()).isEqualTo(stored);
	}

	@ParameterizedTest(name = "[{index}] [{0}] → {1}")
	@CsvSource(textBlock = """
			,           Vui lòng nhập giá trị
			-0.0001,    Giá trị phải từ 0 trở lên
			100000000,  Giá trị tối đa 8 chữ số phần nguyên và 4 chữ số thập phân
			1.23456,    Giá trị tối đa 8 chữ số phần nguyên và 4 chữ số thập phân
			98.50000,   Giá trị tối đa 8 chữ số phần nguyên và 4 chữ số thập phân
			""")
	@DisplayName("TC-BR06-03 Giá trị trống, âm, quá 8 chữ số nguyên hoặc quá 4 chữ số thập phân (kể cả số 0 cuối) bị chặn trước khi khóa mẫu")
	void rejectsInvalidValues(BigDecimal value, String message) {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenPh(true);

		assertThatThrownBy(() -> service.enter(SAMPLE_ID, new NewResult(PH_ID, value), "kiemnghiem1"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("INVALID_VALUE");
				assertThat(ex.getField()).isEqualTo("value");
				assertThat(ex).hasMessage(message);
			});
		verifyNoInteractions(sampleMapper, resultMapper, auditMapper);
	}

	@Test
	@DisplayName("TC-BR06-06 Đã có kết quả hiệu lực (Chờ duyệt hoặc Đã duyệt) cho chỉ tiêu → chặn, không INSERT, không audit")
	void enterRejectsSecondEffectiveResult() {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenPh(true);
		when(resultMapper.existsEffective(SAMPLE_ID, PH_ID)).thenReturn(true);

		assertThatThrownBy(() -> service.enter(SAMPLE_ID, ph("6.2"), "kiemnghiem1"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("RESULT_ALREADY_EFFECTIVE");
				assertThat(ex.getField()).isEqualTo("parameterId");
				assertThat(ex).hasMessage("Chỉ tiêu \"pH\" đã có kết quả đang hiệu lực");
			});
		verify(resultMapper, never()).insert(any());
		verifyNoInteractions(auditMapper);
	}

	@Test
	@DisplayName("TC-BR06-06 Chỉ có bản Đã từ chối → nhập lại được, bản mới trỏ về bản bị từ chối gần nhất chưa bị thay")
	void reEntryReplacesLatestRejectedResult() {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenPh(true);
		when(resultMapper.findReplaceableRejectedId(SAMPLE_ID, PH_ID)).thenReturn(Optional.of(5L));

		service.enter(SAMPLE_ID, ph("6.2"), "kiemnghiem1");

		assertThat(insertedResult().getReplacesId()).isEqualTo(5L);
		verify(auditMapper).insert(RESULT_ID, ResultAction.ENTER, "kiemnghiem1", NOW);
	}

	@Test
	@DisplayName("TC-BR06-06 CSDL báo trùng uq_test_result_effective → cùng thông báo đã có kết quả hiệu lực, không audit")
	void duplicateKeyOnEffectiveIndexBecomesBusinessError() {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenPh(true);
		doThrow(new DuplicateKeyException(
				"ERROR: duplicate key value violates unique constraint \"uq_test_result_effective\""))
			.when(resultMapper).insert(any());

		assertThatThrownBy(() -> service.enter(SAMPLE_ID, ph("6.2"), "kiemnghiem1"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("RESULT_ALREADY_EFFECTIVE");
				assertThat(ex).hasMessage("Chỉ tiêu \"pH\" đã có kết quả đang hiệu lực");
			});
		verifyNoInteractions(auditMapper);
	}

	@Test
	@DisplayName("TC-BR06-06 Trùng khóa ở ràng buộc khác (uq_test_result_replaces) không bị đổi thành lỗi nghiệp vụ")
	void otherDuplicateKeyIsRethrown() {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenPh(true);
		doThrow(new DuplicateKeyException(
				"ERROR: duplicate key value violates unique constraint \"uq_test_result_replaces\""))
			.when(resultMapper).insert(any());

		assertThatThrownBy(() -> service.enter(SAMPLE_ID, ph("6.2"), "kiemnghiem1"))
			.isInstanceOf(DuplicateKeyException.class);
		verifyNoInteractions(auditMapper);
	}

	@Test
	@DisplayName("TC-BR10-03 Ghi audit không được đúng 1 dòng là lỗi hệ thống, để transaction rollback cả kết quả vừa INSERT")
	void auditRowCountOtherThanOneFails() {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenPh(true);
		when(auditMapper.insert(anyLong(), any(), any(), any())).thenReturn(0);

		assertThatThrownBy(() -> service.enter(SAMPLE_ID, ph("6.2"), "kiemnghiem1"))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("TC-BR08-01 Người duyệt khác người nhập: duyệt được, UPDATE kèm người duyệt và thời điểm, 1 dòng audit APPROVE")
	void approvesResultEnteredBySomeoneElse() {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenResult(SAMPLE_ID, ResultStatus.PENDING, "kiemnghiem1");
		when(resultMapper.review(any())).thenReturn(1);

		TestResult approved = service.approve(SAMPLE_ID, RESULT_ID, "duyet");

		assertThat(approved.getStatus()).isEqualTo(ResultStatus.APPROVED);
		assertThat(approved.getReviewedBy()).isEqualTo("duyet");
		assertThat(approved.getReviewedAt()).isEqualTo(NOW);
		assertThat(approved.getEnteredAt()).isEqualTo(OffsetDateTime.parse("2026-10-07T23:00+07:00"));
		verify(resultMapper).review(new ResultReview(RESULT_ID, SAMPLE_ID, ResultStatus.APPROVED, "duyet", NOW, null));
		verify(auditMapper).insert(RESULT_ID, ResultAction.APPROVE, "duyet", NOW);
	}

	@Test
	@DisplayName("TC-BR08-01 Duyệt kết quả do chính mình nhập → nguyên tắc 4 mắt, không UPDATE, không audit")
	void approveOwnResultViolatesFourEyes() {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenResult(SAMPLE_ID, ResultStatus.PENDING, "duyet");

		assertThatThrownBy(() -> service.approve(SAMPLE_ID, RESULT_ID, "duyet"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("FOUR_EYES");
				assertThat(ex).hasMessage(
						"Nguyên tắc 4 mắt: bạn không được duyệt hoặc từ chối kết quả do chính mình nhập");
			});
		verify(resultMapper, never()).review(any());
		verifyNoInteractions(auditMapper);
	}

	@Test
	@DisplayName("TC-BR08-01 Từ chối kết quả do chính mình nhập → nguyên tắc 4 mắt, không UPDATE, không audit")
	void rejectOwnResultViolatesFourEyes() {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenResult(SAMPLE_ID, ResultStatus.PENDING, "duyet");

		assertThatThrownBy(() -> service.reject(SAMPLE_ID, RESULT_ID, "Sai pha loãng", "duyet"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("FOUR_EYES"));
		verify(resultMapper, never()).review(any());
		verifyNoInteractions(auditMapper);
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@EnumSource(value = ResultStatus.class, names = { "APPROVED", "REJECTED" })
	@DisplayName("TC-BR08-02 Kết quả đã Duyệt hoặc đã Từ chối → đã được xử lý, kể cả khi là kết quả của chính mình")
	void reviewRequiresPendingResult(ResultStatus status) {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenResult(SAMPLE_ID, status, "duyet");

		assertThatThrownBy(() -> service.approve(SAMPLE_ID, RESULT_ID, "duyet"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("RESULT_ALREADY_PROCESSED");
				assertThat(ex).hasMessage("Kết quả này đã được xử lý trước đó");
			});
		assertThatThrownBy(() -> service.reject(SAMPLE_ID, RESULT_ID, "Sai pha loãng", "duyet2"))
			.isInstanceOfSatisfying(BusinessException.class,
					ex -> assertThat(ex.getCode()).isEqualTo("RESULT_ALREADY_PROCESSED"));
		verify(resultMapper, never()).review(any());
		verifyNoInteractions(auditMapper);
	}

	@Test
	@DisplayName("TC-BR08-02 UPDATE … WHERE status = 'PENDING' không khớp dòng nào → đã được xử lý, không audit")
	void reviewUpdatingNoRowIsAlreadyProcessed() {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenResult(SAMPLE_ID, ResultStatus.PENDING, "kiemnghiem1");
		when(resultMapper.review(any())).thenReturn(0);

		assertThatThrownBy(() -> service.approve(SAMPLE_ID, RESULT_ID, "duyet"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("RESULT_ALREADY_PROCESSED");
				assertThat(ex).hasMessage("Kết quả này đã được xử lý trước đó");
			});
		verifyNoInteractions(auditMapper);
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@EnumSource(value = SampleStatus.class, names = { "RECEIVED", "COMPLETED", "CANCELLED" })
	@DisplayName("TC-BR08-03 Mẫu không Đang kiểm nghiệm → không duyệt, không từ chối được, kể cả kết quả còn Chờ duyệt")
	void reviewRequiresSampleInTesting(SampleStatus status) {
		givenSampleIn(status);
		givenResult(SAMPLE_ID, ResultStatus.PENDING, "kiemnghiem1");

		assertThatThrownBy(() -> service.approve(SAMPLE_ID, RESULT_ID, "duyet"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("SAMPLE_NOT_IN_TESTING");
				assertThat(ex).hasMessage("Chỉ duyệt hoặc từ chối kết quả của mẫu đang kiểm nghiệm");
			});
		assertThatThrownBy(() -> service.reject(SAMPLE_ID, RESULT_ID, "Sai pha loãng", "duyet"))
			.isInstanceOfSatisfying(BusinessException.class,
					ex -> assertThat(ex.getCode()).isEqualTo("SAMPLE_NOT_IN_TESTING"));
		verify(resultMapper, never()).review(any());
		verifyNoInteractions(auditMapper);
	}

	@Test
	@DisplayName("TC-BR08-04 Kết quả không tồn tại → Không tìm thấy kết quả")
	void reviewRequiresExistingResult() {
		givenSampleIn(SampleStatus.IN_TESTING);

		assertThatThrownBy(() -> service.approve(SAMPLE_ID, RESULT_ID, "duyet"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("RESULT_NOT_FOUND");
				assertThat(ex).hasMessage("Không tìm thấy kết quả");
			});
		verify(resultMapper, never()).review(any());
		verifyNoInteractions(auditMapper);
	}

	@Test
	@DisplayName("TC-BR08-04 Kết quả thuộc mẫu khác mẫu trong URL → Không tìm thấy kết quả")
	void reviewRequiresResultOfSampleInUrl() {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenResult(SAMPLE_ID + 1, ResultStatus.PENDING, "kiemnghiem1");

		assertThatThrownBy(() -> service.reject(SAMPLE_ID, RESULT_ID, "Sai pha loãng", "duyet"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("RESULT_NOT_FOUND");
				assertThat(ex).hasMessage("Không tìm thấy kết quả");
			});
		verify(resultMapper, never()).review(any());
		verifyNoInteractions(auditMapper);
	}

	@ParameterizedTest(name = "[{index}] lý do dài {0} ký tự")
	@ValueSource(ints = { 0, 4, 501 })
	@DisplayName("TC-BR09-01 Lý do từ chối dưới 5 hoặc trên 500 ký tự bị chặn trước khi khóa mẫu")
	void rejectRequiresReasonOfFiveToFiveHundredCharacters(int length) {
		assertRejectReasonRequired("x".repeat(length));
	}

	@ParameterizedTest(name = "[{index}] lý do = [{0}]")
	@NullSource
	@ValueSource(strings = { "     ", "  abcd  " })
	@DisplayName("TC-BR09-01 Lý do từ chối null, chỉ có khoảng trắng, hoặc dưới 5 ký tự sau khi cắt bị chặn")
	void rejectRequiresNonBlankReason(String reason) {
		assertRejectReasonRequired(reason);
	}

	private void assertRejectReasonRequired(String reason) {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenResult(SAMPLE_ID, ResultStatus.PENDING, "kiemnghiem1");

		assertThatThrownBy(() -> service.reject(SAMPLE_ID, RESULT_ID, reason, "duyet"))
			.isInstanceOfSatisfying(BusinessException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("REASON_REQUIRED");
				assertThat(ex.getField()).isEqualTo("reason");
				assertThat(ex).hasMessage("Lý do phải dài từ 5 đến 500 ký tự");
			});
		verifyNoInteractions(sampleMapper, resultMapper, auditMapper);
	}

	@ParameterizedTest(name = "[{index}] lý do dài {0} ký tự")
	@ValueSource(ints = { 5, 500 })
	@DisplayName("TC-BR09-01 Lý do 5 và 500 ký tự (sau khi cắt khoảng trắng) được nhận, lưu bản đã cắt, 1 dòng audit REJECT")
	void rejectStoresTrimmedReason(int length) {
		givenSampleIn(SampleStatus.IN_TESTING);
		givenResult(SAMPLE_ID, ResultStatus.PENDING, "kiemnghiem1");
		when(resultMapper.review(any())).thenReturn(1);
		String reason = "x".repeat(length);

		TestResult rejected = service.reject(SAMPLE_ID, RESULT_ID, "   " + reason + "   ", "duyet");

		assertThat(rejected.getStatus()).isEqualTo(ResultStatus.REJECTED);
		assertThat(rejected.getRejectReason()).isEqualTo(reason);
		verify(resultMapper)
			.review(new ResultReview(RESULT_ID, SAMPLE_ID, ResultStatus.REJECTED, "duyet", NOW, reason));
		verify(auditMapper).insert(RESULT_ID, ResultAction.REJECT, "duyet", NOW);
	}

}
