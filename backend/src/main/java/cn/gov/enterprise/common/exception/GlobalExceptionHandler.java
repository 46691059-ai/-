package cn.gov.enterprise.common.exception;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.system.log.support.ExceptionAuditRecorder;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/** REST接口全局异常处理器，统一转换为ApiResponse响应。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ExceptionAuditRecorder exceptionAuditRecorder;

    public GlobalExceptionHandler() { this.exceptionAuditRecorder = null; }

    @org.springframework.beans.factory.annotation.Autowired
    public GlobalExceptionHandler(ExceptionAuditRecorder exceptionAuditRecorder) {
        this.exceptionAuditRecorder = exceptionAuditRecorder;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        HttpStatus status = "B0404".equals(exception.getCode()) ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        return failure(status, exception.getMessage(), exception);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidation(Exception exception) {
        String message = exception instanceof MethodArgumentNotValidException validationException
                ? validationException.getBindingResult().getAllErrors().stream()
                        .findFirst().map(error -> error.getDefaultMessage()).orElse("请求参数校验失败")
                : exception.getMessage();
        return failure(HttpStatus.BAD_REQUEST, message, exception);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleJsonParse(HttpMessageNotReadableException exception) {
        log.warn("Request JSON parse failed: {}", exception.getClass().getSimpleName());
        return failure(HttpStatus.BAD_REQUEST, "请求体格式错误或JSON解析失败", exception);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException exception) {
        return failure(HttpStatus.BAD_REQUEST, "缺少必填参数: " + exception.getParameterName(), exception);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException exception) {
        return failure(HttpStatus.BAD_REQUEST, "缺少必填请求头: " + exception.getHeaderName(), exception);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return failure(HttpStatus.BAD_REQUEST, "参数类型错误: " + exception.getName(), exception);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return failure(HttpStatus.METHOD_NOT_ALLOWED, "不支持的HTTP请求方法", exception);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return failure(HttpStatus.PAYLOAD_TOO_LARGE, "上传文件大小超过限制", exception);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException exception) {
        log.warn("Multipart request failed: {}", exception.getClass().getSimpleName());
        return failure(HttpStatus.BAD_REQUEST, "文件上传请求处理失败", exception);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException exception) {
        return failure(HttpStatus.UNAUTHORIZED, "身份认证失败", exception);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return failure(HttpStatus.FORBIDDEN, "无访问权限", exception);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKey(DuplicateKeyException exception) {
        log.warn("Duplicate business key rejected: {}", exception.getMostSpecificCause().getClass().getSimpleName());
        return failure(HttpStatus.CONFLICT, "数据已存在，请勿重复提交", exception);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabase(DataAccessException exception) {
        log.error("Database operation failed: {}", exception.getClass().getSimpleName());
        return failure(HttpStatus.SERVICE_UNAVAILABLE, "数据库服务暂不可用", exception);
    }

    @ExceptionHandler({RedisConnectionFailureException.class, RedisSystemException.class})
    public ResponseEntity<ApiResponse<Void>> handleRedis(RuntimeException exception) {
        log.error("Redis operation failed: {}", exception.getClass().getSimpleName());
        return failure(HttpStatus.SERVICE_UNAVAILABLE, "缓存服务暂不可用", exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled application exception", exception);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误", exception);
    }

    private ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String message, Throwable exception) {
        if (exceptionAuditRecorder != null) {
            exceptionAuditRecorder.record(exception, null);
        }
        return ResponseEntity.status(status).body(ApiResponse.failure(status.value(), message));
    }
}
