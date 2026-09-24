package com.weiguang.common.exception;

import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;

import java.util.Arrays;

/**
 * 全局异常处理：BizException → 对应错误码；参数类异常 → 10001；未知异常 → 50001。
 * 未匹配的静态资源（如未暴露的 Actuator 端点）保持 404，不转换为统一返回体。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常 code={} message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        return failOfFieldError(e.getBindingResult().getFieldError());
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        return failOfFieldError(e.getBindingResult().getFieldError());
    }

    /** 长度类约束（@Size）→ 10002 内容超长；其余（@NotBlank/@Min/@Max 等）→ 10001 参数校验失败 */
    private Result<Void> failOfFieldError(FieldError fieldError) {
        if (fieldError == null) {
            return Result.fail(ErrorCode.PARAM_INVALID);
        }
        int code = isSizeViolation(fieldError) ? ErrorCode.CONTENT_TOO_LONG : ErrorCode.PARAM_INVALID;
        String message = fieldError.getDefaultMessage();
        return Result.fail(code, message == null ? ErrorCode.messageOf(code) : message);
    }

    private boolean isSizeViolation(FieldError fieldError) {
        String[] codes = fieldError.getCodes();
        return codes != null && Arrays.stream(codes).anyMatch(code -> code != null && code.startsWith("Size"));
    }

    @ExceptionHandler({ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public Result<Void> handleParamException(Exception e) {
        log.warn("参数异常：{}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.notFound().build();
    }

    /** 超出容器 multipart 上限（大于 25MB）同样按内容超长处理 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        return Result.fail(ErrorCode.CONTENT_TOO_LONG);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("未捕获异常", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }
}
