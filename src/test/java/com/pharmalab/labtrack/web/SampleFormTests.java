package com.pharmalab.labtrack.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.pharmalab.labtrack.domain.SampleType;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Bean Validation của form đăng ký (BR-02), không cần Spring. Validator không đi qua {@code trimStrings} của
 * controller, nên giá trị ở đây là giá trị thô; việc cắt khoảng trắng kiểm ở {@link SampleControllerTests}.
 */
class SampleFormTests {

	private static final String BATCH_MESSAGE = "Số lô chỉ gồm chữ không dấu, số, dấu - và /";

	private static ValidatorFactory factory;

	private static Validator validator;

	@BeforeAll
	static void setUp() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void tearDown() {
		factory.close();
	}

	static Stream<String> validNames() {
		return Stream.of("P", "Paracetamol 500 mg", "Đ".repeat(200));
	}

	static Stream<String> validBatches() {
		return Stream.of("P", "PA-2601", "A-1/2", "A".repeat(50));
	}

	static Stream<String> invalidBatches() {
		return Stream.of("A".repeat(51), "LÔ-01", "PA 2601", "PA_01", "PA.01", "PA#1", "PA\t01");
	}

	private static SampleForm form(String productName, String batchNo) {
		SampleForm form = new SampleForm();
		form.setProductName(productName);
		form.setBatchNo(batchNo);
		form.setSampleType(SampleType.FINISHED_PRODUCT);
		return form;
	}

	private static List<String> messages(SampleForm form, String property) {
		Set<ConstraintViolation<SampleForm>> violations = validator.validateProperty(form, property);
		return violations.stream().map(ConstraintViolation::getMessage).toList();
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("validNames")
	@DisplayName("TC-BR02-19 Tên sản phẩm 1 và 200 ký tự được nhận")
	void acceptsProductNameWithinLimits(String name) {
		assertThat(messages(form(name, "PA-2601"), "productName")).isEmpty();
	}

	@ParameterizedTest(name = "[{index}] [{0}]")
	@ValueSource(strings = { "", " ", "   ", "\t" })
	@DisplayName("TC-BR02-20 Tên sản phẩm rỗng hoặc chỉ khoảng trắng bị chặn: \"Vui lòng nhập tên sản phẩm\"")
	void rejectsBlankProductName(String name) {
		assertThat(messages(form(name, "PA-2601"), "productName")).contains("Vui lòng nhập tên sản phẩm");
	}

	@Test
	@DisplayName("TC-BR02-21 Tên sản phẩm 201 ký tự bị chặn ở productName: \"Tên sản phẩm tối đa 200 ký tự\"")
	void rejectsProductNameOver200() {
		assertThat(messages(form("Đ".repeat(201), "PA-2601"), "productName"))
			.containsExactly("Tên sản phẩm tối đa 200 ký tự");
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("validBatches")
	@DisplayName("TC-BR02-22 Số lô 1 đến 50 ký tự gồm chữ không dấu, số, - và / được nhận")
	void acceptsValidBatchNo(String batchNo) {
		assertThat(messages(form("Paracetamol", batchNo), "batchNo")).isEmpty();
	}

	@ParameterizedTest(name = "[{index}] [{0}]")
	@MethodSource("invalidBatches")
	@DisplayName("TC-BR02-23 Số lô dài 51 ký tự hoặc có ký tự ngoài chữ không dấu, số, - và / bị chặn với câu của BR-02")
	void rejectsInvalidBatchNo(String batchNo) {
		assertThat(messages(form("Paracetamol", batchNo), "batchNo")).containsExactly(BATCH_MESSAGE);
	}

	@Test
	@DisplayName("TC-BR02-24 Số lô trống hoặc thiếu bị chặn với câu của BR-02")
	void rejectsEmptyBatchNo() {
		assertThat(messages(form("Paracetamol", ""), "batchNo")).contains(BATCH_MESSAGE);
		assertThat(messages(form("Paracetamol", null), "batchNo")).contains(BATCH_MESSAGE);
	}

}
