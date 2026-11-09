package com.sewlect.outfit.support;

import com.sewlect.outfit.properties.OutfitGenerationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetCeilingResolverTest {

    private BudgetCeilingResolver resolver;

    @BeforeEach
    void setUp() {
        OutfitGenerationProperties properties = new OutfitGenerationProperties(
                50, 500, new BigDecimal("0.10"), 15, 3);
        resolver = new BudgetCeilingResolver(properties);
    }

    @Test
    void resolve_nullBudget_returnsUnlimitedSentinel() {
        assertThat(resolver.resolve(null)).isGreaterThan(new BigDecimal("100000"));
    }

    @Test
    void resolve_realBudget_appliesTolerance() {
        assertThat(resolver.resolve(new BigDecimal("100"))).isEqualByComparingTo(new BigDecimal("110.00"));
    }

    @Test
    void remainingForSlot_nullBudget_returnsTheSameUnlimitedSentinelAsResolve() {
        assertThat(resolver.remainingForSlot(new BigDecimal("100"), new BigDecimal("40"), null))
                .isEqualByComparingTo(resolver.resolve(null));
    }

    @Test
    void remainingForSlot_occupiedSlot_isCeilingMinusEverythingExceptTheOutgoingItem() {
        assertThat(resolver.remainingForSlot(new BigDecimal("100"), new BigDecimal("40"), new BigDecimal("100")))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void remainingForSlot_emptySlotWithZeroOutgoingPrice_isCeilingMinusTheWholeOutfit() {
        assertThat(resolver.remainingForSlot(new BigDecimal("80"), BigDecimal.ZERO, new BigDecimal("100")))
                .isEqualByComparingTo("30.00");
    }

    @Test
    void remainingForSlot_restOfOutfitAlreadyAboveCeiling_isFlooredAtZero() {
        assertThat(resolver.remainingForSlot(new BigDecimal("200"), new BigDecimal("10"), new BigDecimal("100")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
