package com.enterprise.bulkimport.service.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Component
public class RowValidator {

    private final Validator validator;

    public RowValidator(Validator validator) {
        this.validator = validator;
    }

    public record ValidationOutcome(boolean valid, List<String> errors, PersonImportRow row) {
    }

    public ValidationOutcome validate(Map<String, String> rawRow) {
        List<String> mappingErrors = new ArrayList<>();
        PersonImportRow row = mapRow(rawRow, mappingErrors);

        if (!mappingErrors.isEmpty()) {
            return new ValidationOutcome(false, mappingErrors, row);
        }

        Set<ConstraintViolation<PersonImportRow>> violations = validator.validate(row);
        if (violations.isEmpty()) {
            return new ValidationOutcome(true, List.of(), row);
        }

        List<String> errors = violations.stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .toList();
        return new ValidationOutcome(false, errors, row);
    }

    /**
     * String -> typed field mapping happens here, separately from Bean
     * Validation, because a malformed "age" cell (e.g. "twenty") is a type
     * error, not a constraint violation — @Min/@Max never even get to run on
     * something that isn't an Integer.
     */
    private PersonImportRow mapRow(Map<String, String> rawRow, List<String> mappingErrors) {
        PersonImportRow row = new PersonImportRow();
        row.setName(trimToNull(rawRow.get("name")));
        row.setEmail(trimToNull(rawRow.get("email")));
        row.setDepartment(trimToNull(rawRow.get("department")));

        String rawAge = trimToNull(rawRow.get("age"));
        if (rawAge != null) {
            try {
                row.setAge(Integer.parseInt(rawAge));
            } catch (NumberFormatException e) {
                mappingErrors.add("age must be a whole number, got: '" + rawAge + "'");
            }
        }
        // a null age is left for @NotNull to report, so the error message stays consistent

        return row;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
