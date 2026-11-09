package com.sewlect.outfit.support;

import com.sewlect.outfit.properties.OutfitGenerationProperties;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@AllArgsConstructor
public class BudgetCeilingResolver {

    private static final BigDecimal UNLIMITED_PRICE_CEILING = new BigDecimal("1000000");

    private final OutfitGenerationProperties properties;

    public BigDecimal resolve(BigDecimal averageBudgetPerOutfit) {
        if (averageBudgetPerOutfit == null) {
            return UNLIMITED_PRICE_CEILING;
        }
        return averageBudgetPerOutfit.multiply(BigDecimal.ONE.add(properties.budgetTolerance()));
    }

    public BigDecimal remainingForSlot(BigDecimal outfitTotal, BigDecimal outgoingPrice,
                                       BigDecimal averageBudgetPerOutfit) {
        if (averageBudgetPerOutfit == null) {
            return UNLIMITED_PRICE_CEILING;
        }
        BigDecimal committedElsewhere = outfitTotal.subtract(outgoingPrice);
        return resolve(averageBudgetPerOutfit).subtract(committedElsewhere).max(BigDecimal.ZERO);
    }
}