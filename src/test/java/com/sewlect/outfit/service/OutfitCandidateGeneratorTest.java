package com.sewlect.outfit.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.service.ProductSearchService;
import com.sewlect.catalog.service.ProductStyleTagQueryService;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.identity.enums.Sex;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.service.UserStylePreferenceQueryService;
import com.sewlect.outfit.properties.OutfitCompatibilityProperties;
import com.sewlect.outfit.properties.OutfitDiversityProperties;
import com.sewlect.outfit.properties.OutfitGenerationProperties;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.enums.OutfitSource;
import com.sewlect.outfit.service.OutfitPersistenceService.PersistenceCandidate;
import com.sewlect.outfit.support.BudgetCeilingResolver;
import com.sewlect.outfit.support.OutfitGenderFilterResolver;
import com.sewlect.outfit.support.OutfitItemSetHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitCandidateGeneratorTest {

    // July - outside the outerwear window, so required-slot-only tests don't need an OUTERWEAR stub
    private static final Clock SUMMER_CLOCK = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ProductSearchService productSearchService;

    @Mock
    private ProductStyleTagQueryService productStyleTagQueryService;

    @Mock
    private UserStylePreferenceQueryService userStylePreferenceQueryService;

    @Mock
    private OutfitPersistenceService outfitPersistenceService;

    private OutfitCompatibilityScorer scorer;
    private OutfitGenderFilterResolver genderFilterResolver;
    private OutfitItemSetHasher itemSetHasher;

    @BeforeEach
    void setUp() {
        scorer = new OutfitCompatibilityScorer(
                new OutfitCompatibilityProperties(new BigDecimal("0.5"), new BigDecimal("0.5")));
        genderFilterResolver = new OutfitGenderFilterResolver();
        itemSetHasher = new OutfitItemSetHasher();
        lenient().when(userStylePreferenceQueryService.findPreferredStyleTagIds(any())).thenReturn(Set.of());
        lenient().when(outfitPersistenceService.saveOrReuseBatch(any()))
                .thenAnswer(invocation -> invocation.<List<PersistenceCandidate>>getArgument(0).stream()
                        .map(candidate -> Outfit.builder()
                                .id(UUID.randomUUID())
                                .itemSetHash(candidate.itemSetHash())
                                .build())
                        .toList());
        stubSlotCandidates(GarmentRole.OUTERWEAR, List.of());
        stubSlotCandidates(GarmentRole.ACCESSORY, List.of());
    }

    @Test
    void generate_bothAnchorPoolsEmpty_returnsEmptyListWithoutThrowing() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        stubAnchorPools(List.of(), List.of());

        OutfitCandidateGenerator generator = generatorWithProperties(50, 20, 1);

        assertThat(generator.generate(profile)).isEmpty();
    }

    @Test
    void generate_requiredSlotNeverHasCandidates_everyCycleFailsAndReturnsEmpty() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        stubAnchorPools(List.of(productWithRole(GarmentRole.TOP)), List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of());
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of());

        OutfitCandidateGenerator generator = generatorWithProperties(5, 20, 1);

        assertThat(generator.generate(profile)).isEmpty();
        verify(outfitPersistenceService, never()).saveOrReuseBatch(any());
    }

    @Test
    void generate_sufficientSupply_returnsExactlyBatchSize() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        List<Product> anchors = List.of(
                productWithRole(GarmentRole.TOP), productWithRole(GarmentRole.TOP), productWithRole(GarmentRole.TOP));
        stubAnchorPools(anchors, List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of(productWithRole(GarmentRole.BOTTOM)));
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of(productWithRole(GarmentRole.FOOTWEAR)));

        OutfitCandidateGenerator generator = generatorWithProperties(3, 50, 1);

        assertThat(generator.generate(profile)).hasSize(3);
    }

    @Test
    void generate_moreAttemptsThanAnchorsAvailable_stopsWhenAnchorsExhaustedRatherThanLooping() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        List<Product> anchors = List.of(productWithRole(GarmentRole.TOP), productWithRole(GarmentRole.TOP));
        stubAnchorPools(anchors, List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of(productWithRole(GarmentRole.BOTTOM)));
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of(productWithRole(GarmentRole.FOOTWEAR)));

        OutfitCandidateGenerator generator = generatorWithProperties(10, 100, 1);

        assertThat(generator.generate(profile)).hasSizeLessThanOrEqualTo(6);
    }

    @Test
    void generate_moreOutfitsRequestedThanAnchors_repeatsAnchorsButNeverExceedsTheDiversityCap() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        Product anchor1 = productWithRole(GarmentRole.TOP);
        Product anchor2 = productWithRole(GarmentRole.TOP);
        stubAnchorPools(List.of(anchor1, anchor2), List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of(productWithRole(GarmentRole.BOTTOM)));
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of(productWithRole(GarmentRole.FOOTWEAR)));

        OutfitCandidateGenerator generator = generatorWithProperties(6, 100, 1);
        generator.generate(profile);

        ArgumentCaptor<List<PersistenceCandidate>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(outfitPersistenceService).saveOrReuseBatch(batchCaptor.capture());

        Map<UUID, Long> anchorUsageCounts = batchCaptor.getValue().stream()
                .map(candidate -> candidate.products().get(0).getId())
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));

        assertThat(anchorUsageCounts.values()).allMatch(count -> count <= 3);
    }

    @Test
    void generate_existingOutfitWithSameHash_isReusedWithoutSavingANewOne() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        stubAnchorPools(List.of(productWithRole(GarmentRole.TOP)), List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of(productWithRole(GarmentRole.BOTTOM)));
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of(productWithRole(GarmentRole.FOOTWEAR)));
        Outfit existing = Outfit.builder().id(UUID.randomUUID()).build();
        doReturn(List.of(existing)).when(outfitPersistenceService).saveOrReuseBatch(any());

        OutfitCandidateGenerator generator = generatorWithProperties(1, 10, 1);

        assertThat(generator.generate(profile)).containsExactly(existing);
    }

    @Test
    void generate_nullBudget_appliesAnEffectivelyUnlimitedPriceCeiling() {
        UserProfile profile = profileWith(Sex.OTHER, null);
        stubAnchorPools(List.of(productWithRole(GarmentRole.TOP)), List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of(productWithRole(GarmentRole.BOTTOM)));
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of(productWithRole(GarmentRole.FOOTWEAR)));

        OutfitCandidateGenerator generator = generatorWithProperties(1, 10, 1);
        generator.generate(profile);

        ArgumentCaptor<BigDecimal> priceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(productSearchService).findEligibleIds(eq(GarmentRole.TOP), any(), priceCaptor.capture());
        assertThat(priceCaptor.getValue()).isGreaterThan(new BigDecimal("100000"));
    }

    @Test
    void generate_maleSex_resolvesToMenAndUnisexGenders() {
        UserProfile profile = profileWith(Sex.MALE, new BigDecimal("200"));
        stubAnchorPools(List.of(productWithRole(GarmentRole.TOP)), List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of(productWithRole(GarmentRole.BOTTOM)));
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of(productWithRole(GarmentRole.FOOTWEAR)));

        OutfitCandidateGenerator generator = generatorWithProperties(1, 10, 1);
        generator.generate(profile);

        ArgumentCaptor<Set<String>> gendersCaptor = ArgumentCaptor.forClass(Set.class);
        verify(productSearchService).findEligibleIds(eq(GarmentRole.TOP), gendersCaptor.capture(), any());
        assertThat(gendersCaptor.getValue()).containsExactlyInAnyOrder("Men", "Unisex");
    }

    private OutfitCandidateGenerator generatorWithProperties(int batchSize, int maxAttempts, int topKPerSlot) {
        OutfitGenerationProperties properties = new OutfitGenerationProperties(
                batchSize, maxAttempts, new BigDecimal("0.10"), topKPerSlot, 2);
        OutfitDiversityProperties diversityProperties = new OutfitDiversityProperties(3);
        return new OutfitCandidateGenerator(
                productSearchService, productStyleTagQueryService, userStylePreferenceQueryService,
                new OutfitBatchPersistenceService(outfitPersistenceService), scorer, properties, new Random(42),
                SUMMER_CLOCK,
                genderFilterResolver, itemSetHasher, new BudgetCeilingResolver(properties), diversityProperties);
    }

    private void stubAnchorPools(List<Product> topPool, List<Product> fullBodyPool) {
        lenient().when(productSearchService.findEligibleIds(eq(GarmentRole.TOP), any(), any()))
                .thenReturn(topPool.stream().map(Product::getId).toList());
        lenient().when(productSearchService.findEligibleIds(eq(GarmentRole.FULL_BODY), any(), any()))
                .thenReturn(fullBodyPool.stream().map(Product::getId).toList());
        Stream.concat(topPool.stream(), fullBodyPool.stream()).forEach(anchor ->
                lenient().when(productSearchService.findById(anchor.getId())).thenReturn(Optional.of(anchor)));
    }

    private void stubSlotCandidates(GarmentRole role, List<Product> candidates) {
        lenient().when(productSearchService.findNearestByOccasion(
                        eq(role), any(), any(), any(), any(), anyInt()))
                .thenReturn(candidates);
        lenient().when(productSearchService.findNearest(
                        eq(role), any(), any(), any(), anyInt()))
                .thenReturn(candidates);
    }

    private UserProfile profileWith(Sex sex, BigDecimal budget) {
        return UserProfile.builder().userId(UUID.randomUUID()).sex(sex).averageBudgetPerOutfit(budget).build();
    }

    private Product productWithRole(GarmentRole role) {
        return Product.builder()
                .id(UUID.randomUUID())
                .garmentRole(role)
                .primaryColor("black")
                .basePrice(new BigDecimal("50"))
                .occasion("casual")
                .textEmbedding(new float[]{1f, 0f, 0f})
                .build();
    }

    @Test
    void generate_severalCycles_persistsEveryCandidateInASingleBatch() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        List<Product> anchors = List.of(
                productWithRole(GarmentRole.TOP), productWithRole(GarmentRole.TOP), productWithRole(GarmentRole.TOP));
        stubAnchorPools(anchors, List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of(productWithRole(GarmentRole.BOTTOM)));
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of(productWithRole(GarmentRole.FOOTWEAR)));

        OutfitCandidateGenerator generator = generatorWithProperties(3, 50, 1);
        List<Outfit> outfits = generator.generate(profile);

        ArgumentCaptor<List<PersistenceCandidate>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(outfitPersistenceService, times(1)).saveOrReuseBatch(batchCaptor.capture());
        assertThat(batchCaptor.getValue()).hasSize(3)
                .allSatisfy(candidate -> assertThat(candidate.source()).isEqualTo(OutfitSource.PROFILE_GENERATED));
        assertThat(outfits).extracting(Outfit::getItemSetHash)
                .containsExactlyElementsOf(batchCaptor.getValue().stream().map(PersistenceCandidate::itemSetHash).toList());
    }

    @Test
    void generate_batchLosesAPersistenceRace_retriesExactlyOnce() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        stubAnchorPools(List.of(productWithRole(GarmentRole.TOP)), List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of(productWithRole(GarmentRole.BOTTOM)));
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of(productWithRole(GarmentRole.FOOTWEAR)));
        Outfit persisted = Outfit.builder().id(UUID.randomUUID()).build();
        doThrow(new DataIntegrityViolationException("duplicate item_set_hash"))
                .doReturn(List.of(persisted))
                .when(outfitPersistenceService).saveOrReuseBatch(any());

        OutfitCandidateGenerator generator = generatorWithProperties(1, 10, 1);

        assertThat(generator.generate(profile)).containsExactly(persisted);
        verify(outfitPersistenceService, times(2)).saveOrReuseBatch(any());
    }

    @Test
    void generate_batchFailsTwice_propagatesRatherThanRetryingForever() {
        UserProfile profile = profileWith(Sex.OTHER, new BigDecimal("200"));
        stubAnchorPools(List.of(productWithRole(GarmentRole.TOP)), List.of());
        stubSlotCandidates(GarmentRole.BOTTOM, List.of(productWithRole(GarmentRole.BOTTOM)));
        stubSlotCandidates(GarmentRole.FOOTWEAR, List.of(productWithRole(GarmentRole.FOOTWEAR)));
        doThrow(new DataIntegrityViolationException("duplicate item_set_hash"))
                .when(outfitPersistenceService).saveOrReuseBatch(any());

        OutfitCandidateGenerator generator = generatorWithProperties(1, 10, 1);

        assertThatThrownBy(() -> generator.generate(profile)).isInstanceOf(DataIntegrityViolationException.class);
        verify(outfitPersistenceService, times(2)).saveOrReuseBatch(any());
    }
}
