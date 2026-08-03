package cn.gov.enterprise.modules.system.log.support;

import cn.gov.enterprise.common.api.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

@Component
public class SensitiveDataSanitizer {
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d)(\\d{4})(\\d{4})(?!\\d)");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)([\\\"']?(?:password|passwd|pwd|token|authorization|jwt|id[_-]?card|identity[_-]?card)"
                    + "[\\\"']?\\s*[:=]\\s*)[\\\"']?([^\\s,;}&\\\"]+)[\\\"']?");
    private final ObjectMapper objectMapper;

    public SensitiveDataSanitizer(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public String requestParameters(String[] names, Object[] values) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            if (value instanceof ServletRequest || value instanceof ServletResponse
                    || value instanceof BindingResult || value instanceof MultipartFile) continue;
            parameters.put(names != null && index < names.length ? names[index] : "arg" + index, value);
        }
        return sanitize(parameters, 240);
    }

    public String responseSummary(Object response) {
        if (response instanceof ApiResponse<?> apiResponse) {
            return truncate("code=" + apiResponse.code() + ",message=" + apiResponse.message(), 160);
        }
        return response == null ? "empty" : truncate("type=" + response.getClass().getSimpleName(), 160);
    }

    public String sanitize(Object value, int maxLength) {
        try {
            JsonNode root = objectMapper.valueToTree(value);
            mask(root, null);
            return truncate(objectMapper.writeValueAsString(root), maxLength);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            return "[UNAVAILABLE]";
        }
    }

    public String sanitizeMessage(String value, int maxLength) {
        if (value == null) return null;
        String sanitized = SECRET_ASSIGNMENT.matcher(value).replaceAll("$1[REDACTED]");
        sanitized = JWT.matcher(ID_CARD.matcher(sanitized).replaceAll("[ID_CARD_REDACTED]"))
                .replaceAll("[JWT_REDACTED]");
        return truncate(PHONE.matcher(sanitized).replaceAll("$1****$3"), maxLength);
    }

    private void mask(JsonNode node, String fieldName) {
        if (node instanceof ObjectNode objectNode) {
            var fields = new ArrayList<Map.Entry<String, JsonNode>>();
            objectNode.fields().forEachRemaining(fields::add);
            for (var entry : fields) {
                if (isSecret(entry.getKey())) objectNode.put(entry.getKey(), "******");
                else if (isPhone(entry.getKey()) && entry.getValue().isTextual())
                    objectNode.put(entry.getKey(), maskPhone(entry.getValue().asText()));
                else if (entry.getValue().isTextual()) objectNode.put(entry.getKey(), sanitizeText(entry.getValue().asText()));
                else mask(entry.getValue(), entry.getKey());
            }
        } else if (node instanceof ArrayNode arrayNode) {
            for (int index = 0; index < arrayNode.size(); index++) {
                JsonNode item = arrayNode.get(index);
                if (item.isTextual()) arrayNode.set(index,
                        com.fasterxml.jackson.databind.node.TextNode.valueOf(sanitizeText(item.asText())));
                else mask(item, fieldName);
            }
        }
    }

    private String sanitizeText(String value) {
        return sanitizeMessage(value, Integer.MAX_VALUE);
    }

    private boolean isSecret(String field) {
        String key = field.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return key.contains("password") || key.contains("passwd") || key.equals("pwd")
                || key.contains("token") || key.contains("authorization") || key.contains("jwt")
                || key.contains("idcard") || key.contains("identitycard");
    }

    private boolean isPhone(String field) {
        String key = field.toLowerCase(Locale.ROOT);
        return key.contains("phone") || key.contains("mobile");
    }

    private String maskPhone(String phone) {
        return phone.matches("\\d{11}") ? phone.substring(0, 3) + "****" + phone.substring(7) : "******";
    }

    public String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
