package com.sewlect.outfit.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.service.ProductSearchService;
import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.service.UserProfileQueryService;
import com.sewlect.outfit.domain.OutfitEngagement;
import com.sewlect.outfit.domain.SlotEditTarget;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.enums.OutfitSource;
import com.sewlect.outfit.properties.OutfitGenerationProperties;
import com.sewlect.outfit.support.BudgetCeilingResolver;
import com.sewlect.outfit.support.OutfitResponseAssembler;
import com.sewlect.outfit.support.OutfitShapeValidator;
import com.sewlect.outfit.support.SlotPriceCeilingResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitEditServiceTest {

    private static final Set<String> MEN_AND_UNISEX = Set.of("Men", "Unisex");

    @Mock
    private ProductSearchService productSearchService;

    @Mock
    private UserProfileQueryService userProfileQueryService;

    @Mock
    private OutfitItemQueryService outfitItemQueryService;

    @Mock
    private OutfitVariantService outfitVariantService;

    @Mock
    private OutfitResponseAssembler outfitResponseAssembler;

    private OutfitEditService service;

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
        SlotPriceCeilingResolver slotPriceCeilingResolver = new SlotPriceCeilingResolver(
                userProfileQueryService, outfitItemQueryService, budgetCeilingResolver);
        service = new OutfitEditService(productSearchService, slotPriceCeilingResolver, new OutfitShapeValidator(),
                outfitVariantService, outfitResponseAssembler);

        top = product(GarmentRole.TOP, "Men", "40");
        bottom = product(GarmentRole.BOTTOM, "Men", "50");
        shoes = product(GarmentRole.FOOTWEAR, "Men", "30");
        scarf = product(GarmentRole.ACCESSORY, "Unisex", "20");
    }

    @Test
    void swap_unknownProduct_throwsResourceNotFoundAndNeverPersists() {
        UUID productId = UUID.randomUUID();
        when(productSearchService.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.swap(scarfTarget(), productId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(outfitVariantService);
    }

    @Test
    void swap_candidateOfADifferentRole_throwsBadRequest() {
        Product sneakers = product(GarmentRole.FOOTWEAR, "Men", "20");
        stubFound(sneakers);

        assertThatThrownBy(() -> service.swap(scarfTarget(), sneakers.getId(), userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("garment role");
        verifyNoInteractions(outfitVariantService);
    }

    @Test
    void swap_candidateOutsideTheEligibleGenderPool_throwsBadRequest() {
        Product womensNecklace = product(GarmentRole.ACCESSORY, "Women", "20");
        stubFound(womensNecklace);

        assertThatThrownBy(() -> service.swap(scarfTarget(), womensNecklace.getId(), userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("gender");
        verifyNoInteractions(outfitVariantService);
    }

    @Test
    void swap_candidateIsTheCurrentProduct_throwsBadRequest() {
        stubFound(scarf);

        assertThatThrownBy(() -> service.swap(scarfTarget(), scarf.getId(), userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already in this slot");
        verifyNoInteractions(outfitVariantService);
    }

    @Test
    void swap_candidateWithoutEmbedding_throwsBadRequest() {
        Product unembedded = product(GarmentRole.ACCESSORY, "Men", "20");
        unembedded.setTextEmbedding(null);
        stubFound(unembedded);

        assertThatThrownBy(() -> service.swap(scarfTarget(), unembedded.getId(), userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not enriched");
        verifyNoInteractions(outfitVariantService);
    }

    @Test
    void swap_candidateWithoutPrimaryColor_throwsBadRequest() {
        Product colourless = product(GarmentRole.ACCESSORY, "Men", "20");
        colourless.setPrimaryColor(null);
        stubFound(colourless);

        assertThatThrownBy(() -> service.swap(scarfTarget(), colourless.getId(), userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not enriched");
        verifyNoInteractions(outfitVariantService);
    }

    @Test
    void swap_candidateBreakingTheBudget_throwsBadRequest() {
        Product pricey = product(GarmentRole.ACCESSORY, "Men", "35");
        stubFound(pricey);
        stubBudget(new BigDecimal("140"), new BigDecimal("140"));

        assertThatThrownBy(() -> service.swap(scarfTarget(), pricey.getId(), userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("budget");
        verifyNoInteractions(outfitVariantService);
    }

    @Test
    void swap_candidateExactlyAtTheBudgetCeiling_isAccepted() {
        Product atCeiling = product(GarmentRole.ACCESSORY, "Men", "34");
        stubFound(atCeiling);
        stubBudget(new BigDecimal("140"), new BigDecimal("140"));
        Outfit variant = stubVariant(OutfitSource.MANUAL_SWAP);

        OutfitResponse response = service.swap(scarfTarget(), atCeiling.getId(), userId);

        assertThat(response.outfitId()).isEqualTo(variant.getId());
    }

    @Test
    void swap_nullProfileBudget_neverRejectsOnPrice() {
        Product luxury = product(GarmentRole.ACCESSORY, "Men", "99999");
        stubFound(luxury);
        stubBudget(null, new BigDecimal("140"));
        Outfit variant = stubVariant(OutfitSource.MANUAL_SWAP);

        OutfitResponse response = service.swap(scarfTarget(), luxury.getId(), userId);

        assertThat(response.outfitId()).isEqualTo(variant.getId());
    }

    @Test
    void swap_acceptedAcrossArticleTypes_createsAManualSwapVariantOfTheOthersPlusTheCandidate() {
        Product necklace = product(GarmentRole.ACCESSORY, "Men", "25");
        necklace.setArticleType("Necklace and Chains");
        stubFound(necklace);
        stubBudget(null, new BigDecimal("140"));
        Outfit variant = stubVariant(OutfitSource.MANUAL_SWAP);

        OutfitResponse response = service.swap(scarfTarget(), necklace.getId(), userId);

        ArgumentCaptor<List<Product>> productsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outfitVariantService).createVariant(productsCaptor.capture(), eq(OutfitSource.MANUAL_SWAP));
        assertThat(productsCaptor.getValue()).containsExactly(top, bottom, shoes, necklace);
        verify(outfitResponseAssembler).toResponse(variant, userId);
        assertThat(response.outfitId()).isEqualTo(variant.getId());
    }

    @Test
    void add_createsAManualAdditionVariantWithZeroOutgoingPriceInTheBudgetCheck() {
        Product belt = product(GarmentRole.ACCESSORY, "Men", "34");
        stubFound(belt);
        stubBudget(new BigDecimal("140"), new BigDecimal("120"));
        Outfit variant = stubVariant(OutfitSource.MANUAL_ADDITION);

        OutfitResponse response = service.add(emptyAccessoryTarget(), belt.getId(), userId);

        ArgumentCaptor<List<Product>> productsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outfitVariantService).createVariant(productsCaptor.capture(), eq(OutfitSource.MANUAL_ADDITION));
        assertThat(productsCaptor.getValue()).containsExactly(top, bottom, shoes, belt);
        assertThat(response.outfitId()).isEqualTo(variant.getId());
    }

    @Test
    void add_candidatePushingTheWholeOutfitOverBudget_throwsBadRequest() {
        Product belt = product(GarmentRole.ACCESSORY, "Men", "35");
        stubFound(belt);
        stubBudget(new BigDecimal("140"), new BigDecimal("120"));

        assertThatThrownBy(() -> service.add(emptyAccessoryTarget(), belt.getId(), userId))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(outfitVariantService);
    }

    @Test
    void remove_optionalItem_createsAManualRemovalVariantOfTheOthers() {
        Outfit variant = stubVariant(OutfitSource.MANUAL_REMOVAL);

        OutfitResponse response = service.remove(scarfTarget(), userId);

        ArgumentCaptor<List<Product>> productsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outfitVariantService).createVariant(productsCaptor.capture(), eq(OutfitSource.MANUAL_REMOVAL));
        assertThat(productsCaptor.getValue()).containsExactly(top, bottom, shoes);
        verify(outfitResponseAssembler).toResponse(variant, userId);
        assertThat(response.outfitId()).isEqualTo(variant.getId());
        verifyNoInteractions(productSearchService, userProfileQueryService);
    }

    @Test
    void remove_mandatoryRole_throwsBadRequest() {
        SlotEditTarget topTarget = new SlotEditTarget(outfitId, GarmentRole.TOP, top,
                List.of(bottom, shoes, scarf), MEN_AND_UNISEX);

        assertThatThrownBy(() -> service.remove(topTarget, userId)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(outfitVariantService);
    }

    @Test
    void remove_emptySlot_throwsBadRequest() {
        assertThatThrownBy(() -> service.remove(emptyAccessoryTarget(), userId))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(outfitVariantService);
    }

    private SlotEditTarget scarfTarget() {
        return new SlotEditTarget(outfitId, GarmentRole.ACCESSORY, scarf, List.of(top, bottom, shoes), MEN_AND_UNISEX);
    }

    private SlotEditTarget emptyAccessoryTarget() {
        return new SlotEditTarget(outfitId, GarmentRole.ACCESSORY, null, List.of(top, bottom, shoes), MEN_AND_UNISEX);
    }

    private void stubFound(Product product) {
        when(productSearchService.findById(product.getId())).thenReturn(Optional.of(product));
    }

    private void stubBudget(BigDecimal averageBudget, BigDecimal outfitTotal) {
        when(userProfileQueryService.getById(userId)).thenReturn(
                UserProfile.builder().userId(userId).averageBudgetPerOutfit(averageBudget).build());
        when(outfitItemQueryService.sumBasePrice(outfitId)).thenReturn(outfitTotal);
    }

    private Outfit stubVariant(OutfitSource source) {
        Outfit variant = Outfit.builder().id(UUID.randomUUID()).source(source).build();
        when(outfitVariantService.createVariant(any(), eq(source))).thenReturn(variant);
        when(outfitResponseAssembler.toResponse(variant, userId)).thenReturn(
                new OutfitResponse(variant.getId(), null, null, List.of(), OutfitEngagement.none()));
        return variant;
    }

    private Product product(GarmentRole role, String gender, String basePrice) {
        return Product.builder()
                .id(UUID.randomUUID())
                .garmentRole(role)
                .gender(gender)
                .articleType("Scarves")
                .basePrice(new BigDecimal(basePrice))
                .primaryColor("black")
                .textEmbedding(new float[]{1f, 0f, 0f})
                .build();
    }
}
