package com.example.academicprofile.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class RegisterRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsInvalidLengthsAndEmail() {
        RegisterRequest request = new RegisterRequest(
                "ab", "invalid", "1234567", "1234567",
                "x".repeat(129), "x".repeat(129), "x".repeat(129));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("username", "email", "password", "confirmPassword",
                        "displayName", "department", "title");
    }

    @Test
    void acceptsDocumentedBoundaries() {
        RegisterRequest request = new RegisterRequest(
                "abc", "teacher@example.com", "12345678", "12345678",
                "x".repeat(128), "x".repeat(128), "x".repeat(128));

        assertThat(validator.validate(request)).isEmpty();
    }
}
