package com.sewlect.outfit.support;

import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.service.UserProfileQueryService;
import com.sewlect.outfit.domain.SlotEditTarget;
import com.sewlect.outfit.service.OutfitItemQueryService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@AllArgsConstructor
public class SlotPriceCeilingResolver {

    private final UserProfileQueryService userProfileQueryService;
    private final OutfitItemQueryService outfitItemQueryService;
    private final BudgetCeilingResolver budgetCeilingResolver;

    public BigDecimal resolve(SlotEditTarget target, UUID userId) {
        UserProfile profile = userProfileQueryService.getById(userId);
        BigDecimal outfitTotal = outfitItemQueryService.sumBasePrice(target.outfitId());
        return budgetCeilingResolver.remainingForSlot(
                outfitTotal, target.outgoingPrice(), profile.getAverageBudgetPerOutfit());
    }
}
