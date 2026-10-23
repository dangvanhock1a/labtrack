package com.pharmalab.labtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;

import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.pharmalab.labtrack.TestcontainersConfiguration;
import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.TestResult;
import com.pharmalab.labtrack.persistence.TestParameterMapper;

/**
 * Hai transaction thật cùng đụng một mẫu (SPEC-004 R14). Mỗi test cho luồng A làm thao tác của mình trong một
 * transaction rồi giữ nguyên, chờ tới khi luồng B bị PostgreSQL bắt chờ khóa dòng mẫu, mới cho A commit. Cách
 * này cho kết quả xác định, không phụ thuộc luồng nào chạy nhanh hơn.
 * <p>
 * Không có transaction của test, nên dữ liệu được commit và ở lại trong CSDL: trigger chặn DELETE (SPEC-004
 * L1). Vì vậy mọi phép đếm đều giới hạn trong mẫu do chính test tạo. Mọi lần chờ đều có thời hạn, và latch được
 * mở trong {@code finally}, để lỗi logic làm test đỏ chứ không làm treo cả bộ test.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TestResultConcurrencyTests {

	private static final long TIMEOUT_SECONDS = 10;

	@Autowired
	private TestResultService resultService;

	@Autowired
	private LabSampleService sampleService;

	@Autowired
	private TestParameterMapper parameterMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final ExecutorService executor = Executors.newFixedThreadPool(2);

	@AfterEach
	void stopThreads() {
		executor.shutdownNow();
	}

	private long sampleInTesting() {
		LabSample sample = sampleService.register(
				new NewSample("Paracetamol 500 mg", "PA-2601", SampleType.FINISHED_PRODUCT, null, null), "tiepnhan");
		sampleService.startTesting(sample.getId(), "kiemnghiem1");
		return sample.getId();
	}

	private NewResult ph(String value) {
		return new NewResult(parameterMapper.findByCode("PH").orElseThrow().getId(), new BigDecimal(value));
	}

	/**
	 * Chạy {@code work} trong một transaction ở luồng A và giữ transaction đó tới khi {@code release} mở. Trả về
	 * sau khi {@code work} đã chạy xong, tức A đang giữ khóa.
	 */
	private <T> Future<T> holdingTransaction(Callable<T> work, CountDownLatch release) throws InterruptedException {
		CountDownLatch done = new CountDownLatch(1);
		Future<T> future = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
			try {
				T result = work.call();
				done.countDown();
				if (!release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
					throw new IllegalStateException("Luồng A chờ quá lâu mà không được cho commit");
				}
				return result;
			}
			catch (RuntimeException ex) {
				throw ex;
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
			finally {
				done.countDown();
			}
		}));
		assertThat(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("luồng A đã làm xong thao tác").isTrue();
		return future;
	}

	/** Chờ tới khi có một phiên trong CSDL test đang bị chặn bởi khóa, tức luồng B đã đụng vào dòng A giữ. */
	private void awaitWaitingForLock() throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
		while (jdbcTemplate.queryForObject("""
				SELECT count(*) FROM pg_stat_activity
				WHERE datname = current_database() AND wait_event_type = 'Lock'
				""", Integer.class) == 0) {
			if (System.nanoTime() > deadline) {
				fail("Luồng B không bị chặn bởi khóa dòng mẫu");
			}
			Thread.sleep(20);
		}
	}

	private static <T> T get(Future<T> future) throws Exception {
		return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	private static Throwable failureOf(Future<?> future) {
		Throwable thrown = catchThrowable(() -> future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		assertThat(thrown).as("luồng B phải thất bại").isInstanceOf(ExecutionException.class);
		return thrown.getCause();
	}

	private int auditRows(long sampleId, String... actions) {
		return jdbcTemplate.queryForObject("""
				SELECT count(*) FROM result_audit a JOIN test_result r ON r.id = a.result_id
				WHERE r.sample_id = ? AND a.action = ANY (?)
				""", Integer.class, sampleId, actions);
	}

	private int resultRows(long sampleId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM test_result WHERE sample_id = ?", Integer.class,
				sampleId);
	}

	@Test
	@DisplayName("TC-BR06-08 Hai người cùng nhập pH cho một mẫu: người sau chờ khóa, rồi nhận đã có kết quả hiệu lực; đúng 1 dòng audit ENTER")
	void concurrentEntryOfSameParameter() throws Exception {
		long sampleId = sampleInTesting();
		CountDownLatch release = new CountDownLatch(1);
		Future<TestResult> first;
		Future<TestResult> second;
		try {
			first = holdingTransaction(() -> resultService.enter(sampleId, ph("6.2"), "kiemnghiem1"), release);
			second = executor.submit(() -> resultService.enter(sampleId, ph("6.3"), "kiemnghiem2"));
			awaitWaitingForLock();
			assertThat(second.isDone()).isFalse();
		}
		finally {
			release.countDown();
		}

		assertThat(get(first).getValue().toPlainString()).isEqualTo("6.2");
		assertThat(failureOf(second)).isInstanceOfSatisfying(BusinessException.class, ex -> {
			assertThat(ex.getCode()).isEqualTo("RESULT_ALREADY_EFFECTIVE");
			assertThat(ex).hasMessage("Chỉ tiêu \"pH\" đã có kết quả đang hiệu lực");
		});
		assertThat(resultRows(sampleId)).isEqualTo(1);
		assertThat(auditRows(sampleId, "ENTER")).isEqualTo(1);
	}

	@Test
	@DisplayName("TC-BR06-10 Chiều 1: đang nhập kết quả thì hủy mẫu phải chờ; nhập xong mới hủy được, kết quả vẫn Chờ duyệt")
	void cancelWaitsForResultEntry() throws Exception {
		long sampleId = sampleInTesting();
		CountDownLatch release = new CountDownLatch(1);
		Future<TestResult> entry;
		Future<LabSample> cancel;
		try {
			entry = holdingTransaction(() -> resultService.enter(sampleId, ph("6.2"), "kiemnghiem1"), release);
			cancel = executor.submit(() -> sampleService.cancel(sampleId, "Gửi nhầm mẫu", "tiepnhan"));
			awaitWaitingForLock();
			assertThat(cancel.isDone()).isFalse();
		}
		finally {
			release.countDown();
		}

		get(entry);
		assertThat(get(cancel).getStatus()).isEqualTo(SampleStatus.CANCELLED);
		assertThat(jdbcTemplate.queryForList("SELECT status FROM test_result WHERE sample_id = ?", String.class,
				sampleId)).containsExactly("PENDING");
		assertThat(auditRows(sampleId, "ENTER")).isEqualTo(1);
	}

	@Test
	@DisplayName("TC-BR06-10 Chiều 2: đang hủy mẫu thì nhập kết quả phải chờ; hủy xong thì nhận chỉ nhập cho mẫu đang kiểm nghiệm")
	void entryWaitsForCancelAndSeesCancelledSample() throws Exception {
		long sampleId = sampleInTesting();
		CountDownLatch release = new CountDownLatch(1);
		Future<LabSample> cancel;
		Future<TestResult> entry;
		try {
			cancel = holdingTransaction(() -> sampleService.cancel(sampleId, "Gửi nhầm mẫu", "tiepnhan"), release);
			entry = executor.submit(() -> resultService.enter(sampleId, ph("6.2"), "kiemnghiem1"));
			awaitWaitingForLock();
			assertThat(entry.isDone()).isFalse();
		}
		finally {
			release.countDown();
		}

		assertThat(get(cancel).getStatus()).isEqualTo(SampleStatus.CANCELLED);
		assertThat(failureOf(entry)).isInstanceOfSatisfying(BusinessException.class, ex -> {
			assertThat(ex.getCode()).isEqualTo("SAMPLE_NOT_IN_TESTING");
			assertThat(ex).hasMessage("Chỉ nhập kết quả cho mẫu đang kiểm nghiệm");
		});
		assertThat(resultRows(sampleId)).isZero();
	}

	@Test
	@DisplayName("TC-BR08-07 duyet duyệt và duyet2 từ chối cùng một kết quả: người sau chờ khóa rồi nhận đã được xử lý; đúng 1 dòng audit duyệt/từ chối")
	void concurrentApproveAndReject() throws Exception {
		long sampleId = sampleInTesting();
		TestResult ph = resultService.enter(sampleId, ph("6.2"), "kiemnghiem1");
		CountDownLatch release = new CountDownLatch(1);
		Future<TestResult> approve;
		Future<TestResult> reject;
		try {
			approve = holdingTransaction(() -> resultService.approve(sampleId, ph.getId(), "duyet"), release);
			reject = executor.submit(() -> resultService.reject(sampleId, ph.getId(), "Sai độ pha loãng", "duyet2"));
			awaitWaitingForLock();
			assertThat(reject.isDone()).isFalse();
		}
		finally {
			release.countDown();
		}

		assertThat(get(approve).getReviewedBy()).isEqualTo("duyet");
		assertThat(failureOf(reject)).isInstanceOfSatisfying(BusinessException.class, ex -> {
			assertThat(ex.getCode()).isEqualTo("RESULT_ALREADY_PROCESSED");
			assertThat(ex).hasMessage("Kết quả này đã được xử lý trước đó");
		});
		assertThat(jdbcTemplate.queryForList("SELECT status FROM test_result WHERE sample_id = ?", String.class,
				sampleId)).containsExactly("APPROVED");
		assertThat(auditRows(sampleId, "APPROVE", "REJECT")).isEqualTo(1);
	}

}
