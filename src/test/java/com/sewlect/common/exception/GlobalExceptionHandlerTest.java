package com.sewlect.common.exception;

import com.sewlect.common.exception.dto.ErrorResponse;
import com.sewlect.common.exception.support.ErrorResponseFactory;
import com.sewlect.common.logging.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private static final String REQUEST_URI = "/api/v1/test";
    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(
                new ErrorResponseFactory(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)));
    }

    @AfterEach
    void clearMdc() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @Test
    void handleAppException_resourceNotFound_returns404WithErrorResponseShape() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found");

        ResponseEntity<ErrorResponse> response = handler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.error()).isEqualTo("Not Found");
        assertThat(body.message()).isEqualTo("User not found");
        assertThat(body.path()).isEqualTo(REQUEST_URI);
        assertThat(body.timestamp()).isEqualTo(FIXED_INSTANT);
        assertThat(body.fieldErrors()).isNull();
    }

    @Test
    void handleAppException_badRequest_returns400() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        BadRequestException ex = new BadRequestException("Malformed input");

        ResponseEntity<ErrorResponse> response = handler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
    }

    @Test
    void handleAppException_externalService_returns502() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        ExternalServiceException ex = new ExternalServiceException("R2 unreachable");

        ResponseEntity<ErrorResponse> response = handler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().status()).isEqualTo(502);
    }

    @Test
    void handleAppException_multipleSubtypes_allHandledByOneMethodReadingStatusFromInstance() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        AppException[] exceptions = {
                new ResourceNotFoundException("not found"),
                new BadRequestException("bad request"),
                new ConflictException("conflict"),
                new UnauthorizedException("unauthorized"),
                new ExternalServiceException("external")
        };

        for (AppException ex : exceptions) {
            ResponseEntity<ErrorResponse> response = handler.handleAppException(ex, request);
            assertThat(response.getStatusCode()).isEqualTo(ex.getStatus());
            assertThat(response.getBody().status()).isEqualTo(ex.getStatus().value());
        }
    }

    @Test
    void handleAppException_correlationIdInMdc_isCopiedOntoTheResponseBody() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);

        ResponseEntity<ErrorResponse> response =
                handler.handleAppException(new BadRequestException("bad"), request);

        assertThat(response.getBody().correlationId()).isEqualTo(correlationId);
    }

    @Test
    void handleAppException_noCorrelationIdInMdc_leavesTheFieldNullWithoutFailing() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        ResponseEntity<ErrorResponse> response =
                handler.handleAppException(new BadRequestException("bad"), request);

        assertThat(response.getBody().correlationId()).isNull();
    }

    @Test
    void handleValidationException_returns400WithFieldErrorsMapAndSummaryMessage() throws NoSuchMethodException {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        Method dummyMethod = getClass().getDeclaredMethod("dummyValidationTarget", String.class);
        MethodParameter methodParameter = new MethodParameter(dummyMethod, 0);
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "registerRequest");
        bindingResult.addError(new FieldError("registerRequest", "email", "must be a well-formed email address"));
        bindingResult.addError(new FieldError("registerRequest", "password", "size must be between 8 and 2147483647"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body.message()).isEqualTo("Request validation failed");
        assertThat(body.fieldErrors())
                .containsEntry("email", "must be a well-formed email address")
                .containsEntry("password", "size must be between 8 and 2147483647")
                .hasSize(2);
    }

    @Test
    void handleValidationException_twoViolationsOnSameField_areMergedRatherThanThrowing() throws NoSuchMethodException {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        Method dummyMethod = getClass().getDeclaredMethod("dummyValidationTarget", String.class);
        MethodParameter methodParameter = new MethodParameter(dummyMethod, 0);
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "registerRequest");
        bindingResult.addError(new FieldError("registerRequest", "password", "must not be blank"));
        bindingResult.addError(new FieldError("registerRequest", "password", "size must be between 8 and 64"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getBody().fieldErrors()).hasSize(1);
        assertThat(response.getBody().fieldErrors().get("password"))
                .contains("must not be blank")
                .contains("size must be between 8 and 64");
    }

    @Test
    void handleValidationException_nullDefaultMessage_fallsBackWithoutNpe() throws NoSuchMethodException {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);

        Method dummyMethod = getClass().getDeclaredMethod("dummyValidationTarget", String.class);
        MethodParameter methodParameter = new MethodParameter(dummyMethod, 0);
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "registerRequest");
        bindingResult.addError(new FieldError("registerRequest", "email", null));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getBody().fieldErrors()).containsEntry("email", "is invalid");
    }

    @Test
    void handleGenericException_returns500WithGenericMessage_doesNotLeakStackTraceOrClassName() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        RuntimeException ex = new NullPointerException("some internal field was null, deep in a service");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = response.getBody();
        assertThat(body.message()).isEqualTo("Intercepted an unexpected error!");
        assertThat(body.message()).doesNotContain("NullPointerException");
        assertThat(body.message()).doesNotContain("some internal field was null");
    }

    @Test
    void handleNoResourceFound_returns404WithErrorResponseShape() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, REQUEST_URI, REQUEST_URI);

        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().path()).isEqualTo(REQUEST_URI);
    }

    @Test
    void handleTypeMismatch_malformedPathVariable_returns400NotTheGeneric500() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "not-a-uuid", UUID.class, "outfitId", null, new IllegalArgumentException("Invalid UUID string"));

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Parameter 'outfitId' has an invalid value: expected UUID");
    }

    @Test
    void handleTypeMismatch_nullRequiredType_fallsBackToGenericPhrasingWithoutNpe() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "bogus", null, "someParam", null, new IllegalArgumentException("no type"));

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Parameter 'someParam' has an invalid value: expected the expected type");
    }

    @Test
    void handleMalformedRequestBody_returns400WithGenericMessage_doesNotLeakParserInternals() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        // The two-arg constructor is used deliberately: the single-String constructor is
        // deprecated in this Spring version. The HttpInputMessage is null because the handler
        // only reads ex.getMessage() — it never calls ex.getHttpInputMessage().
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected character ('}' (code 125)): was expecting double-quote to start field name",
                (HttpInputMessage) null);

        ResponseEntity<ErrorResponse> response = handler.handleMalformedRequestBody(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("The request body is missing or malformed JSON");
    }

    @Test
    void handleMissingParameter_returns400NamingTheMissingParameter() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("cursor", "String");

        ResponseEntity<ErrorResponse> response = handler.handleMissingParameter(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Required parameter 'cursor' is missing");
    }

    @Test
    void handleMethodNotSupported_returns405NamingTheRejectedMethod() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("DELETE");

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().status()).isEqualTo(405);
        assertThat(response.getBody().message()).isEqualTo("HTTP method 'DELETE' is not supported for this endpoint");
    }

    @Test
    void handleDataIntegrityViolation_returns409_doesNotLeakConstraintOrSqlDetails() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"users_email_key\": Key (email)=(x@example.com) already exists.");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorResponse body = response.getBody();
        assertThat(body.status()).isEqualTo(409);
        assertThat(body.message()).isEqualTo("The request conflicts with the current state of the resource");
        assertThat(body.message()).doesNotContain("users_email_key", "x@example.com", "constraint");
    }

    @SuppressWarnings("unused")
    private void dummyValidationTarget(String param) {
        // Exists only to give MethodParameter a real Method + parameter index to reflect on.
    }

    @Test
    void handleAppException_contentPolicyViolation_returns422() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        ContentPolicyViolationException ex = new ContentPolicyViolationException("Blocked by the content safety check");

        ResponseEntity<ErrorResponse> response = handler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().status()).isEqualTo(422);
        assertThat(response.getBody().message()).isEqualTo("Blocked by the content safety check");
    }
}
