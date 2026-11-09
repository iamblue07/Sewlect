package com.sewlect.outfit.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.service.ProductSearchService;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.identity.enums.Sex;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.service.UserProfileQueryService;
import com.sewlect.outfit.domain.SlotEditTarget;
import com.sewlect.outfit.properties.OutfitGenerationProperties;
import com.sewlect.outfit.properties.OutfitRefinementProperties;
import com.sewlect.outfit.dto.AlternativeCandidateResponse;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptRefinementServiceTest {

    private static final Set<String> MEN_AND_UNISEX = Set.of("Men", "Unisex");

    @Mock
    private PromptExtractionService promptExtractionService;

    @Mock
    private PromptQueryEmbeddingService promptQueryEmbeddingService;

    @Mock
    private ProductSearchService productSearchService;

    @Mock
    private OutfitCompatibilityScorer outfitCompatibilityScorer;

    @Mock
    private OutfitItemQueryService outfitItemQueryService;

    @Mock
    private UserProfileQueryService userProfileQueryService;

    private PromptRefinementService service;

    private final UUID outfitId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        OutfitGenerationProperties generationProperties = new OutfitGenerationProperties(
                50, 500, new BigDecimal("0.10"), 15, 3);
        BudgetCeilingResolver budgetCeilingResolver = new BudgetCeilingResolver(generationProperties);
        service = new PromptRefinementService(
                promptExtractionService, promptQueryEmbeddingService, productSearchService,
                new SlotPriceCeilingResolver(userProfileQueryService, outfitItemQueryService, budgetCeilingResolver),
                new AlternativeCandidateRanker(outfitCompatibilityScorer), new OutfitRefinementProperties(20));
    }

    @Test
    void refine_returnsOnlyCandidatesSharingTheTargetsGarmentRole() {
        Product target = productWith(GarmentRole.TOP, "Tshirts", "Men", new BigDecimal("40"));
        Product other = productWith(GarmentRole.BOTTOM, "Jeans", "Men", new BigDecimal("60"));
        when(promptExtractionService.extractSingleSlot("make it dressier", GarmentRole.TOP))
                .thenReturn("a sharper blazer");
        when(promptQueryEmbeddingService.embed("a sharper blazer")).thenReturn(Vector.of(new float[]{1f, 0f, 0f}));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(Sex.MALE, null));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));

        Product differentArticleTypeSameRole = productWith(GarmentRole.TOP, "Blazers", "Men", new BigDecimal("55"));
        when(productSearchService.findNearest(eq(GarmentRole.TOP), any(), any(), any(), anyInt()))
                .thenReturn(resultsOf(differentArticleTypeSameRole));
        when(outfitCompatibilityScorer.score(any())).thenReturn(breakdown("0.7"));

        List<AlternativeCandidateResponse> results = service.refine(targetOf(target, other), userId, "make it dressier");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).productId()).isEqualTo(differentArticleTypeSameRole.getId());
    }

    @Test
    void refine_candidateSameAsTargetProduct_isExcludedFromResults() {
        Product target = productWith(GarmentRole.TOP, "Tshirts", "Men", new BigDecimal("40"));
        Product other = productWith(GarmentRole.BOTTOM, "Jeans", "Men", new BigDecimal("60"));
        when(promptExtractionService.extractSingleSlot(any(), eq(GarmentRole.TOP))).thenReturn("something");
        when(promptQueryEmbeddingService.embed(any())).thenReturn(Vector.of(new float[]{1f, 0f, 0f}));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(Sex.MALE, null));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));
        when(productSearchService.findNearest(eq(GarmentRole.TOP), any(), any(), any(), anyInt()))
                .thenReturn(resultsOf(target));

        List<AlternativeCandidateResponse> results = service.refine(targetOf(target, other), userId, "something");

        assertThat(results).isEmpty();
    }

    @Test
    void refine_queriesWithTheSlotsRemainingBudgetAsPriceCeiling() {
        Product target = productWith(GarmentRole.TOP, "Tshirts", "Men", new BigDecimal("40"));
        Product other = productWith(GarmentRole.BOTTOM, "Jeans", "Men", new BigDecimal("60"));
        when(promptExtractionService.extractSingleSlot(any(), eq(GarmentRole.TOP))).thenReturn("something pricier");
        when(promptQueryEmbeddingService.embed(any())).thenReturn(Vector.of(new float[]{1f, 0f, 0f}));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(Sex.MALE, new BigDecimal("100")));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));
        when(productSearchService.findNearest(any(), any(), any(), any(), anyInt())).thenReturn(resultsOf());

        service.refine(targetOf(target, other), userId, "something pricier");

        ArgumentCaptor<BigDecimal> ceilingCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(productSearchService).findNearest(eq(GarmentRole.TOP), any(), ceilingCaptor.capture(), any(), anyInt());
        assertThat(ceilingCaptor.getValue()).isEqualByComparingTo("50");
    }

    @Test
    void refine_queriesUsingTheTargetsEligibleGendersNotViewerProfile() {
        Product target = productWith(GarmentRole.TOP, "Tshirts", "Men", new BigDecimal("40"));
        Product other = productWith(GarmentRole.BOTTOM, "Jeans", "Men", new BigDecimal("60"));
        when(promptExtractionService.extractSingleSlot(any(), eq(GarmentRole.TOP))).thenReturn("something");
        when(promptQueryEmbeddingService.embed(any())).thenReturn(Vector.of(new float[]{1f, 0f, 0f}));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(Sex.FEMALE, null));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));
        when(productSearchService.findNearest(any(), any(), any(), any(), anyInt())).thenReturn(resultsOf());

        service.refine(targetOf(target, other), userId, "something");

        ArgumentCaptor<Set<String>> gendersCaptor = ArgumentCaptor.forClass(Set.class);
        verify(productSearchService).findNearest(eq(GarmentRole.TOP), gendersCaptor.capture(), any(), any(), anyInt());
        assertThat(gendersCaptor.getValue()).containsExactlyInAnyOrder("Men", "Unisex");
    }

    @Test
    void refine_sortsResultsByProjectedFinalScoreDescending() {
        Product target = productWith(GarmentRole.TOP, "Tshirts", "Men", new BigDecimal("40"));
        Product other = productWith(GarmentRole.BOTTOM, "Jeans", "Men", new BigDecimal("60"));
        when(promptExtractionService.extractSingleSlot(any(), eq(GarmentRole.TOP))).thenReturn("something");
        when(promptQueryEmbeddingService.embed(any())).thenReturn(Vector.of(new float[]{1f, 0f, 0f}));
        when(userProfileQueryService.getById(userId)).thenReturn(profileWith(Sex.MALE, null));
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(new BigDecimal("100"));

        Product lowScore = productWith(GarmentRole.TOP, "Tshirts", "Men", new BigDecimal("35"));
        Product highScore = productWith(GarmentRole.TOP, "Tshirts", "Men", new BigDecimal("38"));
        when(productSearchService.findNearest(eq(GarmentRole.TOP), any(), any(), any(), anyInt()))
                .thenReturn(resultsOf(lowScore, highScore));
        when(outfitCompatibilityScorer.score(any()))
                .thenReturn(breakdown("0.4"))
                .thenReturn(breakdown("0.9"));

        List<AlternativeCandidateResponse> results = service.refine(targetOf(target, other), userId, "something");

        assertThat(results).extracting(AlternativeCandidateResponse::productId)
                .containsExactly(highScore.getId(), lowScore.getId());
    }

    private SlotEditTarget targetOf(Product current, Product... others) {
        return new SlotEditTarget(outfitId, current.getGarmentRole(), current, List.of(others), MEN_AND_UNISEX);
    }

    private UserProfile profileWith(Sex sex, BigDecimal budget) {
        return UserProfile.builder().userId(userId).sex(sex).averageBudgetPerOutfit(budget).build();
    }

    private Product productWith(GarmentRole role, String articleType, String gender, BigDecimal basePrice) {
        return Product.builder()
                .id(UUID.randomUUID())
                .articleType(articleType)
                .gender(gender)
                .basePrice(basePrice)
                .garmentRole(role)
                .productDisplayName("Product")
                .imageUrl("https://example.com/product.jpg")
                .build();
    }

    private List<Product> resultsOf(Product... products) {
        return List.of(products);
    }

    private CompatibilityScoreBreakdown breakdown(String finalScore) {
        BigDecimal score = new BigDecimal(finalScore);
        return new CompatibilityScoreBreakdown(score, score, score, score, score);
    }
}
