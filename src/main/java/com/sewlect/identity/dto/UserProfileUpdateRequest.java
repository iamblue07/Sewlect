package com.sewlect.identity.dto;

import com.sewlect.identity.enums.Sex;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UserProfileUpdateRequest(

        @Past
        LocalDate birthDate,

        Sex sex,

        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax(value = "300")
        BigDecimal heightCm,

        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax(value = "500")
        BigDecimal weightKg,

        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax(value = "50")
        BigDecimal footLengthCm,

        @PositiveOrZero
        @Digits(integer = 8, fraction = 2)
        BigDecimal averageBudgetPerOutfit,

        @Pattern(regexp = "[A-Z]{3}", message = "must be a three-letter ISO 4217 code such as EUR")
        String currency
) {
}
