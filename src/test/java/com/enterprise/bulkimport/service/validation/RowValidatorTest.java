package com.enterprise.bulkimport.service.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RowValidatorTest {

    private RowValidator rowValidator;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        rowValidator = new RowValidator(validator);
    }

    private Map<String, String> row(String name, String email, String age, String department) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("email", email);
        row.put("age", age);
        row.put("department", department);
        return row;
    }

    @Test
    void validRow_passes() {
        RowValidator.ValidationOutcome outcome =
                rowValidator.validate(row("John Smith", "john.smith@example.com", "34", "Engineering"));

        assertTrue(outcome.valid(), () -> "expected valid, got errors: " + outcome.errors());
    }

    @Test
    void missingName_isInvalid() {
        RowValidator.ValidationOutcome outcome =
                rowValidator.validate(row("", "missing.name@example.com", "29", "Marketing"));

        assertFalse(outcome.valid());
        assertTrue(outcome.errors().stream().anyMatch(e -> e.contains("name")));
    }

    @Test
    void malformedEmail_isInvalid() {
        RowValidator.ValidationOutcome outcome =
                rowValidator.validate(row("Wei Chen", "not-an-email", "41", "Finance"));

        assertFalse(outcome.valid());
        assertTrue(outcome.errors().stream().anyMatch(e -> e.contains("email")));
    }

    @Test
    void negativeAge_isInvalid() {
        RowValidator.ValidationOutcome outcome =
                rowValidator.validate(row("Ana Torres", "ana.torres@example.com", "-5", "Sales"));

        assertFalse(outcome.valid());
        assertTrue(outcome.errors().stream().anyMatch(e -> e.contains("age")));
    }

    @Test
    void nonNumericAge_isInvalidWithClearMessage() {
        RowValidator.ValidationOutcome outcome =
                rowValidator.validate(row("Sam Lee", "sam.lee@example.com", "twenty", "Support"));

        assertFalse(outcome.valid());
        assertTrue(outcome.errors().stream().anyMatch(e -> e.contains("whole number")));
    }

    @Test
    void missingAge_isInvalid() {
        RowValidator.ValidationOutcome outcome =
                rowValidator.validate(row("Sam Lee", "sam.lee@example.com", "", "Support"));

        assertFalse(outcome.valid());
        assertTrue(outcome.errors().stream().anyMatch(e -> e.contains("age")));
    }

    @Test
    void ageOver150_isInvalid() {
        RowValidator.ValidationOutcome outcome =
                rowValidator.validate(row("Old Timer", "old@example.com", "200", "Legacy"));

        assertFalse(outcome.valid());
    }
}
