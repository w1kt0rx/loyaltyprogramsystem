package com.example.loyaltyprogram.exception.handler;

import com.example.loyaltyprogram.dto.ErrorMessageDto;
import com.example.loyaltyprogram.dto.response.ErrorResponse;
import com.example.loyaltyprogram.dto.response.FieldErrorDetail;
import com.example.loyaltyprogram.exception.BusinessException;
import com.example.loyaltyprogram.exception.FieldValidationException;
import com.example.loyaltyprogram.exception.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        log.warn("Business exception occurred: code='{}', message='{}', status={}",
                ex.getErrorCode(), ex.getMessage(), ex.getStatus());
        return status(ex);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessageDto handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidRequestException) {
            return new ErrorMessageDto(cause.getMessage());
        }

        return new ErrorMessageDto("Wrong json format");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());

        ErrorResponse body = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "DATA_CONFLICT",
                "The request conflicts with existing data");

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ErrorResponse> handleFieldValidation(FieldValidationException ex) {
        FieldErrorDetail detail = new FieldErrorDetail(ex.getField(), ex.getMessage(), ex.getRejectedValue());
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(), ex.getErrorCode(),
                "Request validation failed", List.of(detail));
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled internal server error occurred", ex);

        ErrorResponse body = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "An unexpected error occurred");

        return ResponseEntity.internalServerError().body(body);
    }

    private ResponseEntity<ErrorResponse> status(BusinessException ex) {
        ErrorResponse body = new ErrorResponse(ex.getStatus().value(), ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }
}