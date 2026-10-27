package com.pharmalab.labtrack.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.pharmalab.labtrack.service.BusinessException;

class BatchNoNormalizerTest {

	/** 10 chữ + 10 số: đúng biên trên 20 ký tự. */
	private static final String TWENTY_CHARS = "ABCDEFGHIJ0123456789";

	@ParameterizedTest(name = "[{index}] \"{0}\" → \"{1}\"")
	@DisplayName("ON-01 Đường chính: cắt khoảng trắng đầu/cuối, chuyển chữ hoa, giữ '-' và '/'")
	@CsvSource(textBlock = """
			# nhập,       sau chuẩn hóa
			' pa-2601 ',  PA-2601
			PA-2601,      PA-2601
			lo/26-01,     LO/26-01
			2601,         2601
			a,            A
			""")
	void normalizesValidBatchNo(String raw, String expected) {
		assertThat(BatchNoNormalizer.normalize(raw)).isEqualTo(expected);
	}

	@Test
	@DisplayName("ON-02 null → BATCH_NO_INVALID")
	void rejectsNull() {
		assertInvalid(null);
	}

	@Test
	@DisplayName("ON-03 Chuỗi rỗng → BATCH_NO_INVALID (0 ký tự, dưới biên 1)")
	void rejectsEmpty() {
		assertInvalid("");
	}

	@Test
	@DisplayName("ON-04 Toàn khoảng trắng → BATCH_NO_INVALID (cắt xong còn 0 ký tự)")
	void rejectsBlank() {
		assertInvalid("   ");
	}

	@ParameterizedTest(name = "[{index}] \"{0}\"")
	@DisplayName("ON-05 Ký tự ngoài A-Z, 0-9, '-', '/' → BATCH_NO_INVALID: chữ có dấu và khoảng trắng giữa, tách riêng từng lỗi")
	@ValueSource(strings = { "LÔ 01", "LÔ01", "LO 01" })
	void rejectsDisallowedCharacters(String raw) {
		assertInvalid(raw);
	}

	@ParameterizedTest(name = "[{index}] \"{0}\"")
	@DisplayName("ON-06 Đúng 20 ký tự sau chuẩn hóa là hợp lệ, kể cả khi chuỗi nhập dài hơn vì khoảng trắng đầu/cuối")
	@ValueSource(strings = { TWENTY_CHARS, "  abcdefghij0123456789  " })
	void acceptsTwentyCharacters(String raw) {
		assertThat(BatchNoNormalizer.normalize(raw)).isEqualTo(TWENTY_CHARS);
	}

	@Test
	@DisplayName("ON-07 21 ký tự → BATCH_NO_INVALID")
	void rejectsTwentyOneCharacters() {
		assertInvalid(TWENTY_CHARS + "K");
	}

	private static void assertInvalid(String raw) {
		assertThatThrownBy(() -> BatchNoNormalizer.normalize(raw))
			.isInstanceOfSatisfying(BusinessException.class,
					ex -> assertThat(ex.getCode()).isEqualTo("BATCH_NO_INVALID"));
	}

}
