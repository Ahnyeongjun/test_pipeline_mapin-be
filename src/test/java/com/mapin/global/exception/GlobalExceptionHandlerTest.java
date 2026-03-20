package com.mapin.global.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("ResponseStatusException은 지정된 상태 코드와 reason을 반환한다")
    void handleResponseStatus_returnsStatusAndReason() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "콘텐츠 없음");

        ResponseEntity<Map<String, String>> response = handler.handleResponseStatus(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("message", "콘텐츠 없음");
    }

    @Test
    @DisplayName("IllegalArgumentException은 400 Bad Request를 반환한다")
    void handleIllegalArgument_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("잘못된 URL");

        ResponseEntity<Map<String, String>> response = handler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "잘못된 URL");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException은 모든 필드 에러를 반환한다")
    void handleValidation_returnsAllFieldErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError error1 = new FieldError("req", "url", "url은 필수입니다.");
        FieldError error2 = new FieldError("req", "source", "source는 필수입니다.");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(error1, error2));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<Map<String, String>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String message = response.getBody().get("message");
        assertThat(message).contains("url: url은 필수입니다.");
        assertThat(message).contains("source: source는 필수입니다.");
    }

    @Test
    @DisplayName("처리되지 않은 예외는 500 Internal Server Error를 반환한다")
    void handleUnexpected_returns500() {
        Exception ex = new RuntimeException("예상치 못한 오류");

        ResponseEntity<Map<String, String>> response = handler.handleUnexpected(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("message", "서버 오류가 발생했습니다.");
    }
}
