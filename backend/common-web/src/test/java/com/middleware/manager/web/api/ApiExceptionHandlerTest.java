package com.middleware.manager.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.UnauthorizedException;
import com.middleware.manager.web.api.dto.ApiError;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("TC-WEB-001 缺少必填查询参数返回 PARAM_INVALID 400")
    void missingRequestParameterReturnsBadRequest() {
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("page", "int");

        ResponseEntity<ApiError> response = handler.handleRequestParameter(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    @DisplayName("TC-WEB-002 查询参数类型错误返回 PARAM_INVALID 400")
    void requestParameterTypeMismatchReturnsBadRequest() {
        TypeMismatchException cause = new TypeMismatchException("invalid", Integer.class);
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "invalid", Integer.class, "page", null, cause);

        ResponseEntity<ApiError> response = handler.handleRequestParameter(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    @DisplayName("TC-WEB-003 方法级参数校验失败返回 PARAM_INVALID 400")
    void constraintViolationReturnsBadRequest() {
        ResponseEntity<ApiError> response = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    @DisplayName("TC-WEB-004 登录认证失败返回 LOGIN_FAILED 401")
    void loginFailureReturnsUnauthorized() {
        ResponseEntity<ApiError> response = handler.handleUnauthorized(
                new UnauthorizedException(ErrorCode.LOGIN_FAILED, ErrorMessages.LOGIN_FAILED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(401);
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorMessages.LOGIN_FAILED);
    }
}
