package com.sewlect.outfit.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.service.ProductSearchService;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.service.UserProfileQueryService;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.domain.SlotEditTarget;
import com.sewlect.outfit.dto.AlternativeCandidateResponse;
import com.sewlect.outfit.properties.OutfitGenerationProperties;
import com.sewlect.outfit.properties.OutfitSwapProperties;
import com.sewlect.outfit.support.AlternativeCandidateRanker;
import com.sewlect.outfit.support.BudgetCeilingResolver;
import com.sewlect.outfit.support.SlotPriceCeilingResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Vector;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GarmentAlternativesServiceTest {

    private static final Set<String> MEN_AND_UNISEX = Set.of("Men", "Unisex");

    @Mock
    private ProductSearchService productSearchService;

    @Mock
    private OutfitCompatibilityScorer compatibilityScorer;

    @Mock
    private OutfitItemQueryService outfitItemQueryService;

    @Mock
    private UserProfileQueryService userProfileQueryService;

    private GarmentAlternativesService service;

    private final UUID outfitId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private Product top;
    private Product bottom;
    private Product shoes;
    private Product scarf;

    @BeforeEach
    void setUp() {
        BudgetCeilingResolver budgetCeilingResolver = new BudgetCeilingResolver(
                new OutfitGenerationProperties(50, 500, new BigDecimal("0.10"), 15, 3));
        service = new GarmentAlternativesService(productSearchService,
                new SlotPriceCeilingResolver(userProfileQueryService, outfitItemQueryService, budgetCeilingResolver),
                new AlternativeCandidateRanker(compatibilityScorer), new OutfitSwapProperties(20));

        top = product(GarmentRole.TOP, "Shirts", "40", new float[]{1f, 0f, 0f});
        bottom = product(GarmentRole.BOTTOM, "Jeans", "50", new float[]{0f, 1f, 0f});
        shoes = product(GarmentRole.FOOTWEAR, "Casual Shoes", "30", new float[]{0f, 0f, 1f});
        scarf = product(GarmentRole.ACCESSORY, "Scarves", "20", new float[]{1f, 1f, 1f});
    }

    @Test
    void listAlternatives_occupiedSlot_queriesTheCurrentArticleTypeExcludingTheCurrentProduct() {
        stubProfileAndTotal(null, new BigDecimal("140"));
        when(productSearchService.findNearestOfArticleType(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        service.listAlternatives(scarfTarget(), userId);

        verify(productSearchService).findNearestOfArticleType(
                eq("Scarves"), eq(MEN_AND_UNISEX), any(), eq(scarf.getId()), any(), eq(20));
        verify(productSearchService, never()).findNearest(any(), any(), any(), any(), anyInt());
    }

    @Test
    void listAlternatives_emptySlot_queriesTheWholeRole() {
        stubProfileAndTotal(null, new BigDecimal("120"));
        when(productSearchService.findNearest(any(), any(), any(), any(), anyInt())).thenReturn(List.of());

        service.listAlternatives(emptyAccessoryTarget(), userId);

        verify(productSearchService).findNearest(eq(GarmentRole.ACCESSORY), eq(MEN_AND_UNISEX), any(), any(), eq(20));
        verify(productSearchService, never()).findNearestOfArticleType(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void listAlternatives_referenceIsTheCentroidOfTheOtherItemsOnly() {
        stubProfileAndTotal(null, new BigDecimal("140"));
        when(productSearchService.findNearestOfArticleType(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        service.listAlternatives(scarfTarget(), userId);

        ArgumentCaptor<Vector> referenceCaptor = ArgumentCaptor.forClass(Vector.class);
        verify(productSearchService).findNearestOfArticleType(
                any(), any(), any(), any(), referenceCaptor.capture(), anyInt());
        float third = 1f / 3f;
        assertThat(referenceCaptor.getValue().toFloatArray()).containsExactly(third, third, third);
    }

    @Test
    void listAlternatives_priceCeilingIsTheSlotsRemainingBudget() {
        stubProfileAndTotal(new BigDecimal("140"), new BigDecimal("140"));
        when(productSearchService.findNearestOfArticleType(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        service.listAlternatives(scarfTarget(), userId);

        ArgumentCaptor<BigDecimal> ceilingCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(productSearchService).findNearestOfArticleType(
                any(), any(), ceilingCaptor.capture(), any(), any(), anyInt());
        assertThat(ceilingCaptor.getValue()).isEqualByComparingTo("34.00");
    }

    @Test
    void listAlternatives_emptySlotCeilingSubtractsTheWholeOutfit() {
        stubProfileAndTotal(new BigDecimal("140"), new BigDecimal("120"));
        when(productSearchService.findNearest(any(), any(), any(), any(), anyInt())).thenReturn(List.of());

        service.listAlternatives(emptyAccessoryTarget(), userId);

        ArgumentCaptor<BigDecimal> ceilingCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(productSearchService).findNearest(any(), any(), ceilingCaptor.capture(), any(), anyInt());
        assertThat(ceilingCaptor.getValue()).isEqualByComparingTo("34.00");
    }

    @Test
    void listAlternatives_scoresEachCandidateWithTheOthersAndSortsByFinalScoreDescending() {
        stubProfileAndTotal(null, new BigDecimal("140"));
        Product weaker = product(GarmentRole.ACCESSORY, "Scarves", "15", new float[]{1f, 0f, 0f});
        Product stronger = product(GarmentRole.ACCESSORY, "Scarves", "18", new float[]{0f, 1f, 0f});
        when(productSearchService.findNearestOfArticleType(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(weaker, stronger));
        when(compatibilityScorer.score(List.of(top, bottom, shoes, weaker))).thenReturn(breakdown("0.4"));
        when(compatibilityScorer.score(List.of(top, bottom, shoes, stronger))).thenReturn(breakdown("0.9"));

        List<AlternativeCandidateResponse> results = service.listAlternatives(scarfTarget(), userId);

        assertThat(results).extracting(AlternativeCandidateResponse::productId)
                .containsExactly(stronger.getId(), weaker.getId());
        assertThat(results.get(0).projectedBreakdown().finalScore()).isEqualByComparingTo("0.9");
    }

    @Test
    void listAlternatives_noCandidates_returnsEmptyWithoutScoring() {
        stubProfileAndTotal(null, new BigDecimal("140"));
        when(productSearchService.findNearestOfArticleType(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        assertThat(service.listAlternatives(scarfTarget(), userId)).isEmpty();
        verify(compatibilityScorer, never()).score(anyList());
    }

    private SlotEditTarget scarfTarget() {
        return new SlotEditTarget(outfitId, GarmentRole.ACCESSORY, scarf, List.of(top, bottom, shoes), MEN_AND_UNISEX);
    }

    private SlotEditTarget emptyAccessoryTarget() {
        return new SlotEditTarget(outfitId, GarmentRole.ACCESSORY, null, List.of(top, bottom, shoes), MEN_AND_UNISEX);
    }

    private void stubProfileAndTotal(BigDecimal averageBudget, BigDecimal outfitTotal) {
        when(userProfileQueryService.getById(userId)).thenReturn(
                UserProfile.builder().userId(userId).averageBudgetPerOutfit(averageBudget).build());
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(outfitTotal);
    }

    private Product product(GarmentRole role, String articleType, String basePrice, float[] embedding) {
        return Product.builder()
                .id(UUID.randomUUID())
                .garmentRole(role)
                .articleType(articleType)
                .gender("Men")
                .basePrice(new BigDecimal(basePrice))
                .primaryColor("black")
                .textEmbedding(embedding)
                .productDisplayName("Product")
                .imageUrl("https://example.com/p.jpg")
                .build();
    }

    private CompatibilityScoreBreakdown breakdown(String finalScore) {
        BigDecimal score = new BigDecimal(finalScore);
        return new CompatibilityScoreBreakdown(score, score, score, score, score);
    }
}
