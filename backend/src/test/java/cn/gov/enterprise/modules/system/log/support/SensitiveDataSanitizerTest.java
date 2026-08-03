package cn.gov.enterprise.modules.system.log.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SensitiveDataSanitizerTest {
    private final SensitiveDataSanitizer sanitizer = new SensitiveDataSanitizer(new ObjectMapper());

    @Test
    void masksCredentialsTokenIdentityCardAndPhone() {
        String result = sanitizer.sanitize(Map.of(
                "password", "Secret123",
                "authorization", "Bearer eyJabcdefgh.abcdefgh.abcdefgh",
                "idCard", "370102199001011234",
                "phone", "13812345678"), 500);

        assertThat(result).doesNotContain("Secret123", "eyJabcdefgh", "370102199001011234", "13812345678")
                .contains("******", "138****5678");
    }

    @Test
    void removesJwtAndIdentityCardFromFreeText() {
        String result = sanitizer.sanitizeMessage(
                "password=Secret123 token=eyJabcdefgh.abcdefgh.abcdefgh id=370102199001011234 phone=13812345678", 200);
        assertThat(result).contains("[REDACTED]", "[ID_CARD_REDACTED]", "138****5678")
                .doesNotContain("Secret123", "eyJabcdefgh", "370102199001011234", "13812345678");
    }
}
