package com.huiyi.common.exception;

import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.R;
import com.huiyi.common.result.ResultCode;
import com.huiyi.modules.log.ErrorLogService;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorLogService errorLogService;

    @ExceptionHandler(BusinessException.class)
    public R<Void> business(BusinessException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> invalid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getDefaultMessage() : "参数错误";
        return R.fail(ResultCode.PARAM_INVALID.getCode(), msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> constraint(ConstraintViolationException e) {
        return R.fail(ResultCode.PARAM_INVALID.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> other(Exception e) {
        log.error("unhandled exception", e);
        errorLogService.record(e);   // 入 error_log 供排查(设计表16)
        return R.fail(ResultCode.SERVER_ERROR);
    }
}
