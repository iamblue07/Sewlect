package com.sewlect.feed.service;

import com.sewlect.catalog.service.ProductStyleTagQueryService;
import com.sewlect.feed.properties.FeedRankingProperties;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.properties.OutfitGenerationProperties;
import com.sewlect.outfit.service.OutfitItemQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedRankingServiceTest {

    @Mock
    private ProductStyleTagQueryService productStyleTagQueryService;

    @Mock
    private OutfitItemQueryService outfitItemQueryService;

    private final OutfitGenerationProperties generationProperties =
            new OutfitGenerationProperties(50, 500, new BigDecimal("0.10"), 15, 3);
    private final FeedRankingProperties rankingProperties =
            new FeedRankingProperties(new BigDecimal("0.15"), new BigDecimal("0.5"), new BigDecimal("0.5"));

    private FeedRankingService service;

    @BeforeEach
    void setUp() {
        service = new FeedRankingService(
                productStyleTagQueryService, outfitItemQueryService, generationProperties, rankingProperties);
    }

    @Test
    void rankScores_bothSignalsNeutral_equalsCompatibilityScoreExactly() {
        UserProfile profile = profileWith(null);
        Outfit outfit = outfitWith("0.8000");

        BigDecimal result = scoreOf(profile, outfit, noStylePreferences());

        assertThat(result).isEqualByComparingTo("0.8000");
    }

    @Test
    void rankScores_perfectPersonalization_appliesUpperMultiplierBound() {
        UserProfile profile = profileWith(new BigDecimal("100"));
        Outfit outfit = outfitWith("0.8000");
        UUID tag = UUID.randomUUID();
        stubOutfitTags(outfit, tag);
        stubTotalPrice(outfit, new BigDecimal("100"));

        BigDecimal result = scoreOf(profile, outfit, Set.of(tag));

        assertThat(result).isEqualByComparingTo(new BigDecimal("0.8000").multiply(new BigDecimal("1.15")));
    }

    @Test
    void rankScores_worstPersonalization_appliesLowerMultiplierBound() {
        UserProfile profile = profileWith(new BigDecimal("100"));
        Outfit outfit = outfitWith("0.8000");
        stubOutfitTags(outfit, UUID.randomUUID());
        stubTotalPrice(outfit, new BigDecimal("1000"));

        BigDecimal result = scoreOf(profile, outfit, Set.of(UUID.randomUUID()));

        assertThat(result).isEqualByComparingTo(new BigDecimal("0.8000").multiply(new BigDecimal("0.85")));
    }

    @Test
    void rankScores_noOutfits_returnsAnEmptyMapWithoutQuerying() {
        assertThat(service.rankScores(profileWith(new BigDecimal("100")), List.of(), Set.of(UUID.randomUUID())))
                .isEmpty();
        verifyNoInteractions(outfitItemQueryService, productStyleTagQueryService);
    }

    @Test
    void rankScores_manyOutfits_loadsProductsTagsAndPricesOnceForTheWholeBatch() {
        UserProfile profile = profileWith(new BigDecimal("100"));
        Outfit first = outfitWith("1.0000");
        Outfit second = outfitWith("0.5000");
        Outfit third = outfitWith("0.2500");
        List<UUID> outfitIds = List.of(first.getId(), second.getId(), third.getId());
        UUID firstProduct = UUID.randomUUID();
        UUID sharedProduct = UUID.randomUUID();
        UUID thirdProduct = UUID.randomUUID();
        UUID tag = UUID.randomUUID();
        when(outfitItemQueryService.findProductIdsForOutfits(outfitIds)).thenReturn(Map.of(
                first.getId(), List.of(firstProduct, sharedProduct),
                second.getId(), List.of(sharedProduct),
                third.getId(), List.of(thirdProduct)));
        when(productStyleTagQueryService.findStyleTagIdsForProducts(any())).thenReturn(Map.of(sharedProduct, Set.of(tag)));
        when(outfitItemQueryService.sumBasePriceForOutfits(outfitIds)).thenReturn(Map.of(
                first.getId(), new BigDecimal("100"),
                second.getId(), new BigDecimal("100"),
                third.getId(), new BigDecimal("100")));

        Map<UUID, BigDecimal> scores = service.rankScores(profile, List.of(first, second, third), Set.of(tag));

        assertThat(scores).containsOnlyKeys(outfitIds);
        assertThat(scores.get(first.getId())).isEqualByComparingTo(new BigDecimal("1.15"));
        assertThat(scores.get(second.getId())).isEqualByComparingTo(new BigDecimal("0.5").multiply(new BigDecimal("1.15")));
        assertThat(scores.get(third.getId())).isEqualByComparingTo(
                new BigDecimal("0.2500").multiply(new BigDecimal("1.075")).setScale(4, RoundingMode.HALF_UP));
        verify(outfitItemQueryService, times(1)).findProductIdsForOutfits(any());
        verify(productStyleTagQueryService, times(1)).findStyleTagIdsForProducts(any());
        verify(outfitItemQueryService, times(1)).sumBasePriceForOutfits(any());
    }

    @Test
    void rankScores_productsSharedAcrossOutfits_areLookedUpOnlyOnce() {
        UserProfile profile = profileWith(null);
        Outfit first = outfitWith("1.0000");
        Outfit second = outfitWith("1.0000");
        UUID sharedProduct = UUID.randomUUID();
        when(outfitItemQueryService.findProductIdsForOutfits(List.of(first.getId(), second.getId()))).thenReturn(Map.of(
                first.getId(), List.of(sharedProduct), second.getId(), List.of(sharedProduct)));
        when(productStyleTagQueryService.findStyleTagIdsForProducts(List.of(sharedProduct))).thenReturn(Map.of());

        service.rankScores(profile, List.of(first, second), Set.of(UUID.randomUUID()));

        verify(productStyleTagQueryService).findStyleTagIdsForProducts(List.of(sharedProduct));
    }

    @Test
    void styleOverlap_emptyPreferredSet_isNeutralAndSkipsFurtherLookups() {
        UserProfile profile = profileWith(null);
        Outfit outfit = outfitWith("0.5000");

        scoreOf(profile, outfit, noStylePreferences());

        verifyNoInteractions(productStyleTagQueryService);
        verify(outfitItemQueryService, times(0)).findProductIdsForOutfits(any());
    }

    @Test
    void styleOverlap_outfitHasNoStyleTagsButUserHasPreferences_isNeutral() {
        UserProfile profile = profileWith(null);
        Outfit outfit = outfitWith("0.5000");
        when(outfitItemQueryService.findProductIdsForOutfits(List.of(outfit.getId())))
                .thenReturn(Map.of(outfit.getId(), List.of(UUID.randomUUID())));
        when(productStyleTagQueryService.findStyleTagIdsForProducts(any())).thenReturn(Map.of());

        BigDecimal result = scoreOf(profile, outfit, Set.of(UUID.randomUUID()));

        assertThat(result).isEqualByComparingTo("0.5000");
    }

    @Test
    void styleOverlap_partialOverlap_computesExactJaccardRatio() {
        UserProfile profile = profileWith(null);
        Outfit outfit = outfitWith("1.0000");
        UUID tagA = UUID.randomUUID();
        UUID tagB = UUID.randomUUID();
        UUID tagC = UUID.randomUUID();
        stubOutfitTags(outfit, tagA, tagC);

        BigDecimal result = scoreOf(profile, outfit, Set.of(tagA, tagB));

        BigDecimal jaccard = BigDecimal.ONE.divide(new BigDecimal("3"), 10, RoundingMode.HALF_UP);
        BigDecimal expectedPersonalization = jaccard.multiply(new BigDecimal("0.5"))
                .add(new BigDecimal("0.5").multiply(new BigDecimal("0.5")));
        BigDecimal expectedMultiplier = BigDecimal.ONE.add(new BigDecimal("0.15")
                .multiply(new BigDecimal("2"))
                .multiply(expectedPersonalization.subtract(new BigDecimal("0.5"))));
        assertThat(result).isEqualByComparingTo(
                new BigDecimal("1.0000").multiply(expectedMultiplier).setScale(4, RoundingMode.HALF_UP));
    }

    @Test
    void styleOverlap_identicalSets_isExactlyOne() {
        UserProfile profile = profileWith(null);
        Outfit outfit = outfitWith("1.0000");
        UUID tagA = UUID.randomUUID();
        UUID tagB = UUID.randomUUID();
        stubOutfitTags(outfit, tagA, tagB);

        BigDecimal result = scoreOf(profile, outfit, Set.of(tagA, tagB));

        assertThat(result).isEqualByComparingTo(new BigDecimal("1.0000").multiply(new BigDecimal("1.075")));
    }

    @Test
    void styleOverlap_tagsSpreadOverSeveralProducts_areUnionedPerOutfit() {
        UserProfile profile = profileWith(null);
        Outfit outfit = outfitWith("1.0000");
        UUID topProduct = UUID.randomUUID();
        UUID shoeProduct = UUID.randomUUID();
        UUID tagA = UUID.randomUUID();
        UUID tagB = UUID.randomUUID();
        when(outfitItemQueryService.findProductIdsForOutfits(List.of(outfit.getId())))
                .thenReturn(Map.of(outfit.getId(), List.of(topProduct, shoeProduct)));
        when(productStyleTagQueryService.findStyleTagIdsForProducts(any()))
                .thenReturn(Map.of(topProduct, Set.of(tagA), shoeProduct, Set.of(tagB)));

        BigDecimal result = scoreOf(profile, outfit, Set.of(tagA, tagB));

        assertThat(result).isEqualByComparingTo(new BigDecimal("1.0000").multiply(new BigDecimal("1.075")));
    }

    @Test
    void budgetFit_nullBudget_isNeutralAndNeverQueriesPrice() {
        UserProfile profile = profileWith(null);
        Outfit outfit = outfitWith("0.6000");

        scoreOf(profile, outfit, noStylePreferences());

        verify(outfitItemQueryService, times(0)).sumBasePriceForOutfits(any());
    }

    @Test
    void budgetFit_priceExactlyAtBudget_isPlateauOne() {
        UserProfile profile = profileWith(new BigDecimal("200"));
        Outfit outfit = outfitWith("1.0000");
        stubTotalPrice(outfit, new BigDecimal("200"));

        BigDecimal result = scoreOf(profile, outfit, noStylePreferences());

        assertThat(result).isEqualByComparingTo(new BigDecimal("1.0000").multiply(new BigDecimal("1.075")));
    }

    @Test
    void budgetFit_priceUnderBudget_isStillPlateauOneNotRewardedFurther() {
        UserProfile profile = profileWith(new BigDecimal("200"));
        Outfit outfit = outfitWith("1.0000");
        stubTotalPrice(outfit, new BigDecimal("50"));

        BigDecimal result = scoreOf(profile, outfit, noStylePreferences());

        assertThat(result).isEqualByComparingTo(new BigDecimal("1.0000").multiply(new BigDecimal("1.075")));
    }

    @Test
    void budgetFit_priceExactlyAtToleranceEdge_isClampedToZero() {
        UserProfile profile = profileWith(new BigDecimal("100"));
        Outfit outfit = outfitWith("1.0000");
        stubTotalPrice(outfit, new BigDecimal("110"));

        BigDecimal result = scoreOf(profile, outfit, noStylePreferences());

        assertThat(result).isEqualByComparingTo(new BigDecimal("1.0000").multiply(new BigDecimal("0.925")));
    }

    @Test
    void budgetFit_priceWildlyBeyondToleranceEdge_staysClampedToZeroNotNegative() {
        UserProfile profile = profileWith(new BigDecimal("100"));
        Outfit outfit = outfitWith("1.0000");
        stubTotalPrice(outfit, new BigDecimal("999999"));

        BigDecimal result = scoreOf(profile, outfit, noStylePreferences());

        assertThat(result).isEqualByComparingTo(new BigDecimal("1.0000").multiply(new BigDecimal("0.925")));
    }

    @Test
    void budgetFit_priceHalfwayThroughToleranceBand_decaysLinearlyToHalf() {
        UserProfile profile = profileWith(new BigDecimal("100"));
        Outfit outfit = outfitWith("1.0000");
        stubTotalPrice(outfit, new BigDecimal("105"));

        BigDecimal result = scoreOf(profile, outfit, noStylePreferences());

        assertThat(result).isEqualByComparingTo("1.0000");
    }

    @Test
    void budgetFit_zeroBudgetWithAnyRealPrice_isImmediatelyClampedToZero_noDivisionByZero() {
        UserProfile profile = profileWith(BigDecimal.ZERO);
        Outfit outfit = outfitWith("1.0000");
        stubTotalPrice(outfit, new BigDecimal("10"));

        BigDecimal result = scoreOf(profile, outfit, noStylePreferences());

        assertThat(result).isEqualByComparingTo(new BigDecimal("1.0000").multiply(new BigDecimal("0.925")));
    }

    @Test
    void budgetFit_zeroBudgetAndZeroPrice_isPlateauOneNotDivisionByZero() {
        UserProfile profile = profileWith(BigDecimal.ZERO);
        Outfit outfit = outfitWith("1.0000");
        stubTotalPrice(outfit, BigDecimal.ZERO);

        BigDecimal result = scoreOf(profile, outfit, noStylePreferences());

        assertThat(result).isEqualByComparingTo(new BigDecimal("1.0000").multiply(new BigDecimal("1.075")));
    }

    @Test
    void rankScores_result_isAlwaysScaledToFourDecimalPlaces() {
        UserProfile profile = profileWith(null);
        Outfit outfit = outfitWith("0.3333");

        BigDecimal result = scoreOf(profile, outfit, noStylePreferences());

        assertThat(result.scale()).isEqualTo(4);
    }

    private BigDecimal scoreOf(UserProfile profile, Outfit outfit, Set<UUID> preferredStyleTagIds) {
        return service.rankScores(profile, List.of(outfit), preferredStyleTagIds).get(outfit.getId());
    }

    private UserProfile profileWith(BigDecimal budget) {
        return UserProfile.builder().userId(UUID.randomUUID()).averageBudgetPerOutfit(budget).build();
    }

    private Outfit outfitWith(String compatibilityScore) {
        return Outfit.builder().id(UUID.randomUUID()).compatibilityScore(new BigDecimal(compatibilityScore)).build();
    }

    private Set<UUID> noStylePreferences() {
        return Set.of();
    }

    private void stubOutfitTags(Outfit outfit, UUID... tagIds) {
        UUID productId = UUID.randomUUID();
        when(outfitItemQueryService.findProductIdsForOutfits(List.of(outfit.getId())))
                .thenReturn(Map.of(outfit.getId(), List.of(productId)));
        when(productStyleTagQueryService.findStyleTagIdsForProducts(List.of(productId)))
                .thenReturn(Map.of(productId, Set.of(tagIds)));
    }

    private void stubTotalPrice(Outfit outfit, BigDecimal price) {
        when(outfitItemQueryService.sumBasePriceForOutfits(List.of(outfit.getId())))
                .thenReturn(Map.of(outfit.getId(), price));
    }
}
