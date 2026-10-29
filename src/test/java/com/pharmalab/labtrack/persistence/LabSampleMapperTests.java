package com.pharmalab.labtrack.persistence;

import static com.pharmalab.labtrack.domain.SamplePriority.LOW;
import static com.pharmalab.labtrack.domain.SamplePriority.NORMAL;
import static com.pharmalab.labtrack.domain.SamplePriority.URGENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.pharmalab.labtrack.domain.LabSample;
import com.pharmalab.labtrack.domain.SampleFilter;
import com.pharmalab.labtrack.domain.SamplePriority;
import com.pharmalab.labtrack.domain.SampleStatus;
import com.pharmalab.labtrack.domain.SampleType;
import com.pharmalab.labtrack.domain.TestParameter;
import com.pharmalab.labtrack.domain.TestResult;
import com.pharmalab.labtrack.service.LabSampleService;
import com.pharmalab.labtrack.service.NewResult;
import com.pharmalab.labtrack.service.NewSample;
import com.pharmalab.labtrack.service.SamplePage;
import com.pharmalab.labtrack.service.TestResultService;

/**
 * SQL của {@code LabSampleMapper.xml} trên PostgreSQL thật: cột thiếu trong {@code <sql id="columns">} hay câu INSERT
 * chỉ lộ ra khi đọc lên thấy {@code null} hoặc khi CSDL từ chối câu lệnh. Test tra cứu (BR-13) đi qua
 * {@link LabSampleService#search}, vì từ khóa chỉ được escape ở đó; mỗi test đặt một chuỗi riêng (BR13S, BR13T…) vào
 * tên sản phẩm và tìm theo chuỗi đó, để mẫu do test khác commit vào CSDL không lọt vào kết quả.
 */
@SpringBootTest
@Transactional
class LabSampleMapperTests {

	private static final OffsetDateTime MARCH_1 = OffsetDateTime.parse("2000-03-01T09:00+07:00");

	@Autowired
	private LabSampleMapper sampleMapper;

	@Autowired
	private TestParameterMapper parameterMapper;

	@Autowired
	private LabSampleService sampleService;

	@Autowired
	private TestResultService resultService;

	/**
	 * Mẫu Đã tiếp nhận ghi thẳng qua mapper; {@code customizer} đổi các trường cần khác mặc định trước khi ghi. Mã năm
	 * 2000 để không trùng mã {@code SampleCodeGenerator} cấp cho test khác; hạn trả cuối tháng 3 để mọi thời điểm nhận
	 * trong tháng 3 đều qua {@code ck_lab_sample_due_date}.
	 */
	private LabSample insertSample(String code, Consumer<LabSample> customizer) {
		LabSample sample = new LabSample();
		sample.setCode(code);
		sample.setProductName("Paracetamol 500 mg");
		sample.setBatchNo("PA-2601");
		sample.setSampleType(SampleType.FINISHED_PRODUCT);
		sample.setPriority(NORMAL);
		sample.setStatus(SampleStatus.RECEIVED);
		sample.setReceivedAt(MARCH_1);
		sample.setDueDate(LocalDate.of(2000, 3, 31));
		sample.setCreatedBy("tiepnhan");
		customizer.accept(sample);
		sampleMapper.insert(sample);
		return sample;
	}

	private LabSample insertSample(String code, SamplePriority priority) {
		return insertSample(code, sample -> sample.setPriority(priority));
	}

	/** Mẫu của TC-BR13-12: cùng tên sản phẩm có BR13S, khác nhau ở ưu tiên, trạng thái, loại mẫu và ngày nhận. */
	private void insertFilterable(String code, SamplePriority priority, SampleStatus status, SampleType type,
			int receivedDay) {
		insertSample(code, sample -> {
			sample.setProductName("Mẫu lọc BR13S");
			sample.setPriority(priority);
			sample.setStatus(status);
			sample.setSampleType(type);
			sample.setReceivedAt(MARCH_1.withDayOfMonth(receivedDay));
		});
	}

	private SamplePage search(String keyword) {
		return sampleService.search(new SampleFilter(keyword, null, null, null), 1);
	}

	private static List<String> codes(SamplePage page) {
		return page.samples().stream().map(LabSample::getCode).toList();
	}

	@Test
	@DisplayName("TC-BR13-01 Ghi và đọc mức ưu tiên: Thấp và Khẩn ghi qua mapper giữ nguyên; đăng ký qua service không chọn mức ưu tiên thì Bình thường")
	void writesAndReadsPriority() {
		LabSample low = insertSample("LS-2000-00001", LOW);
		LabSample urgent = insertSample("LS-2000-00002", URGENT);
		LabSample registered = sampleService.register(
				new NewSample("Paracetamol 500 mg", "PA-2601", SampleType.FINISHED_PRODUCT, null, null), "tiepnhan");

		assertThat(Stream.of(low, urgent, registered)
			.map(sample -> sampleMapper.findById(sample.getId()).orElseThrow().getPriority()))
			.containsExactly(LOW, URGENT, NORMAL);
	}

	@Test
	@DisplayName("TC-BR13-10 Từ khóa tìm trong mã mẫu, tên sản phẩm và số lô, không phân biệt hoa thường, kể cả chữ có dấu tiếng Việt")
	void keywordMatchesCodeProductOrBatchIgnoringCase() {
		insertSample("LS-2000-00011", sample -> sample.setProductName("Glucose khan Br13x"));
		insertSample("LS-2000-00012", sample -> sample.setBatchNo("QK-13ZA"));
		insertSample("LS-2000-00013", sample -> sample.setProductName("ĐƯỜNG KÍNH BR13Y"));
		// Đủ các chữ hoa có dấu trong bảng Latin-1 (Á À Ã Â É È Ê Í Ì Ó Ò Õ Ô Ú Ù Ý): collation theo locale Windows
		// Japanese_Japan.932 không gập được chúng, xem chú thích searchWhere trong LabSampleMapper.xml.
		insertSample("LS-2000-00014", sample -> sample.setProductName("ÁÀÃÂ ÉÈÊ ÍÌ ÓÒÕÔ ÚÙ Ý BR13Z"));

		assertThat(codes(search("ls-2000-00011"))).containsExactly("LS-2000-00011");
		assertThat(codes(search("GLUCOSE KHAN BR13X"))).containsExactly("LS-2000-00011");
		assertThat(codes(search("qk-13za"))).containsExactly("LS-2000-00012");
		assertThat(codes(search("đường kính br13y"))).containsExactly("LS-2000-00013");
		assertThat(codes(search("áàãâ éèê íì óòõô úù ý br13z"))).containsExactly("LS-2000-00014");
	}

	@Test
	@DisplayName("TC-BR13-11 % _ \\ trong từ khóa là ký tự thường: \"70%\" không khớp \"70x\", \"B_1\" không khớp \"BX1\", \"A\\B\" không khớp \"AB\"")
	void likeWildcardsInKeywordAreLiteral() {
		insertSample("LS-2000-00021", sample -> sample.setProductName("Cồn 70% BR13P"));
		insertSample("LS-2000-00022", sample -> sample.setProductName("Cồn 70x BR13P"));
		insertSample("LS-2000-00023", sample -> sample.setProductName("Vitamin B_1 BR13Q"));
		insertSample("LS-2000-00024", sample -> sample.setProductName("Vitamin BX1 BR13Q"));
		insertSample("LS-2000-00025", sample -> sample.setProductName("Hộp A\\B BR13R"));
		insertSample("LS-2000-00026", sample -> sample.setProductName("Hộp AB BR13R"));

		assertThat(codes(search("70% br13p"))).containsExactly("LS-2000-00021");
		assertThat(codes(search("b_1 br13q"))).containsExactly("LS-2000-00023");
		assertThat(codes(search("a\\b br13r"))).containsExactly("LS-2000-00025");
		// Đối chứng: không có ký tự đặc biệt thì cả hai mẫu đều khớp.
		assertThat(codes(search("BR13P"))).containsExactlyInAnyOrder("LS-2000-00021", "LS-2000-00022");
	}

	@Test
	@DisplayName("TC-BR13-12 Lọc trạng thái, ưu tiên, loại mẫu kết hợp bằng AND; sắp Khẩn → Bình thường → Thấp, rồi nhận mới nhất trước, rồi mẫu ghi sau trước")
	void filtersCombineWithAndAndUrgentComesFirst() {
		insertFilterable("LS-2000-00031", URGENT, SampleStatus.RECEIVED, SampleType.FINISHED_PRODUCT, 1);
		insertFilterable("LS-2000-00032", URGENT, SampleStatus.IN_TESTING, SampleType.RAW_MATERIAL, 2);
		insertFilterable("LS-2000-00033", NORMAL, SampleStatus.RECEIVED, SampleType.RAW_MATERIAL, 3);
		insertFilterable("LS-2000-00034", NORMAL, SampleStatus.RECEIVED, SampleType.FINISHED_PRODUCT, 5);
		// Cùng mức ưu tiên và cùng thời điểm nhận với 00034: id lớn hơn nên đứng trước.
		insertFilterable("LS-2000-00035", NORMAL, SampleStatus.RECEIVED, SampleType.FINISHED_PRODUCT, 5);
		insertFilterable("LS-2000-00036", LOW, SampleStatus.CANCELLED, SampleType.FINISHED_PRODUCT, 6);

		assertThat(codes(search("BR13S"))).containsExactly("LS-2000-00032", "LS-2000-00031", "LS-2000-00035",
				"LS-2000-00034", "LS-2000-00033", "LS-2000-00036");
		assertThat(codes(sampleService.search(new SampleFilter("BR13S", SampleStatus.RECEIVED, null, null), 1)))
			.containsExactly("LS-2000-00031", "LS-2000-00035", "LS-2000-00034", "LS-2000-00033");
		assertThat(codes(sampleService.search(new SampleFilter("BR13S", null, NORMAL, null), 1)))
			.containsExactly("LS-2000-00035", "LS-2000-00034", "LS-2000-00033");
		assertThat(codes(sampleService.search(new SampleFilter("BR13S", null, null, SampleType.RAW_MATERIAL), 1)))
			.containsExactly("LS-2000-00032", "LS-2000-00033");
		SamplePage allThree = sampleService.search(
				new SampleFilter("BR13S", SampleStatus.RECEIVED, NORMAL, SampleType.FINISHED_PRODUCT), 1);
		assertThat(codes(allThree)).containsExactly("LS-2000-00035", "LS-2000-00034");
		assertThat(allThree.total()).isEqualTo(2);
		SamplePage none = sampleService.search(new SampleFilter("BR13S", SampleStatus.IN_TESTING, LOW, null), 1);
		assertThat(none.samples()).isEmpty();
		assertThat(none.total()).isZero();
	}

	@Test
	@DisplayName("TC-BR13-13 Phân trang 20 dòng: 23 mẫu thành trang 20 và trang 3, không lặp không sót; xin trang quá cuối thì nhận trang cuối")
	void twentyThreeSamplesMakeTwoPagesWithoutGapsOrDuplicates() {
		List<String> oldestFirst = new ArrayList<>();
		for (int i = 0; i < 23; i++) {
			int minutes = i;
			oldestFirst.add(insertSample("LS-2000-%05d".formatted(100 + i), sample -> {
				sample.setProductName("Mẫu phân trang BR13T");
				sample.setReceivedAt(MARCH_1.plusMinutes(minutes));
			}).getCode());
		}
		List<String> newestFirst = oldestFirst.reversed();
		SampleFilter filter = new SampleFilter("BR13T", null, null, null);

		SamplePage first = sampleService.search(filter, 1);
		SamplePage second = sampleService.search(filter, 2);

		assertThat(codes(first)).containsExactlyElementsOf(newestFirst.subList(0, 20));
		assertThat(List.of(first.from(), first.to(), first.total())).containsExactly(1L, 20L, 23L);
		assertThat(codes(second)).containsExactlyElementsOf(newestFirst.subList(20, 23));
		assertThat(List.of(second.from(), second.to(), second.total())).containsExactly(21L, 23L, 23L);
		SamplePage beyond = sampleService.search(filter, 9);
		assertThat(beyond.page()).isEqualTo(2);
		assertThat(codes(beyond)).isEqualTo(codes(second));
	}

	@Test
	@DisplayName("TC-BR13-14 Cột Đã duyệt: 1 Đã duyệt, 1 Chờ duyệt, 1 Đã từ chối → 1/2; mẫu chưa có kết quả → 0/0; số mẫu không bị nhân theo số kết quả")
	void progressCountsApprovedOverEffectiveResults() {
		LabSample tested = insertSample("LS-2000-00041", sample -> {
			sample.setProductName("Mẫu tiến độ BR13U");
			sample.setStatus(SampleStatus.IN_TESTING);
			sample.setReceivedAt(MARCH_1.plusDays(1));
		});
		insertSample("LS-2000-00042", sample -> sample.setProductName("Mẫu tiến độ BR13U"));
		List<Long> parameterIds = parameterMapper.findAll().stream()
			.filter(TestParameter::isActive)
			.map(TestParameter::getId)
			.limit(3)
			.toList();
		assertThat(parameterIds).hasSize(3);
		List<TestResult> results = parameterIds.stream()
			.map(id -> resultService.enter(tested.getId(), new NewResult(id, new BigDecimal("100")), "kiemnghiem1"))
			.toList();
		resultService.approve(tested.getId(), results.get(0).getId(), "duyet");
		resultService.reject(tested.getId(), results.get(1).getId(), "Pha sai nồng độ chuẩn", "duyet");

		SamplePage page = search("BR13U");

		assertThat(page.total()).isEqualTo(2);
		assertThat(page.samples())
			.extracting(LabSample::getCode, LabSample::getApprovedCount, LabSample::getEffectiveCount)
			.containsExactly(tuple("LS-2000-00041", 1, 2), tuple("LS-2000-00042", 0, 0));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@EnumSource(SampleType.class)
	@DisplayName("TC-BR02-32 Ghi qua mapper rồi đọc lại: cả 4 loại mẫu được lưu và đọc nguyên vẹn")
	void writesAndReadsEverySampleType(SampleType type) {
		LabSample saved = insertSample("LS-2000-0009" + type.ordinal(), sample -> sample.setSampleType(type));

		assertThat(sampleMapper.findById(saved.getId()).orElseThrow().getSampleType()).isEqualTo(type);
	}

}
