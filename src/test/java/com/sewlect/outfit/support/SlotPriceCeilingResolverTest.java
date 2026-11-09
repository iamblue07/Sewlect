package com.sewlect.outfit.support;

import com.sewlect.catalog.entity.Product;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.service.UserProfileQueryService;
import com.sewlect.outfit.domain.SlotEditTarget;
import com.sewlect.outfit.properties.OutfitGenerationProperties;
import com.sewlect.outfit.service.OutfitItemQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotPriceCeilingResolverTest {

    @Mock
    private UserProfileQueryService userProfileQueryService;

    @Mock
    private OutfitItemQueryService outfitItemQueryService;

    private BudgetCeilingResolver budgetCeilingResolver;
    private SlotPriceCeilingResolver resolver;

    private final UUID outfitId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        budgetCeilingResolver = new BudgetCeilingResolver(
                new OutfitGenerationProperties(50, 500, new BigDecimal("0.10"), 15, 3));
        resolver = new SlotPriceCeilingResolver(userProfileQueryService, outfitItemQueryService, budgetCeilingResolver);
    }

    @Test
    void resolve_occupiedSlot_leavesTheCeilingMinusEverythingButTheOutgoingItem() {
        stub(new BigDecimal("100"), new BigDecimal("100"));

        BigDecimal ceiling = resolver.resolve(target(product(GarmentRole.TOP, "40")), userId);

        assertThat(ceiling).isEqualByComparingTo("50.00");
    }

    @Test
    void resolve_emptySlot_leavesTheCeilingMinusTheWholeOutfit() {
        stub(new BigDecimal("100"), new BigDecimal("80"));

        BigDecimal ceiling = resolver.resolve(target(null), userId);

        assertThat(ceiling).isEqualByComparingTo("30.00");
    }

    @Test
    void resolve_profileWithoutBudget_returnsTheUnlimitedCeiling() {
        stub(null, new BigDecimal("500"));

        BigDecimal ceiling = resolver.resolve(target(product(GarmentRole.TOP, "40")), userId);

        assertThat(ceiling).isEqualByComparingTo(budgetCeilingResolver.resolve(null));
    }

    @Test
    void resolve_restOfOutfitAlreadyOverBudget_isFlooredAtZero() {
        stub(new BigDecimal("100"), new BigDecimal("300"));

        BigDecimal ceiling = resolver.resolve(target(product(GarmentRole.TOP, "40")), userId);

        assertThat(ceiling).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private void stub(BigDecimal averageBudget, BigDecimal outfitTotal) {
        when(userProfileQueryService.getById(userId)).thenReturn(
                UserProfile.builder().userId(userId).averageBudgetPerOutfit(averageBudget).build());
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(outfitTotal);
    }

    private SlotEditTarget target(Product current) {
        GarmentRole role = current == null ? GarmentRole.OUTERWEAR : current.getGarmentRole();
        return new SlotEditTarget(outfitId, role, current, List.of(), Set.of("Men", "Unisex"));
    }

    private Product product(GarmentRole role, String price) {
        return Product.builder().id(UUID.randomUUID()).garmentRole(role).basePrice(new BigDecimal(price)).build();
    }
}
