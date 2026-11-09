package com.sewlect.outfit.support;

import com.sewlect.catalog.entity.Product;
import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.exception.ConflictException;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.domain.SlotEditTarget;
import com.sewlect.outfit.entity.OutfitItem;
import com.sewlect.outfit.service.OutfitItemQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotEditTargetResolverTest {

    @Mock
    private OutfitItemQueryService outfitItemQueryService;

    private SlotEditTargetResolver resolver;

    private final UUID outfitId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        resolver = new SlotEditTargetResolver(
                outfitItemQueryService, new OutfitGenderFilterResolver(), new OutfitShapeValidator());
    }

    @Test
    void forItem_unknownOutfit_propagatesResourceNotFound() {
        when(outfitItemQueryService.findItems(outfitId))
                .thenThrow(new ResourceNotFoundException("Outfit not found: " + outfitId));

        assertThatThrownBy(() -> resolver.forItem(outfitId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void forItem_itemNotInOutfit_throwsResourceNotFound() {
        when(outfitItemQueryService.findItems(outfitId)).thenReturn(List.of(
                item(product(GarmentRole.TOP, "Men")), item(product(GarmentRole.BOTTOM, "Men"))));

        assertThatThrownBy(() -> resolver.forItem(outfitId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(outfitId.toString());
    }

    @Test
    void forItem_resolvesRoleFromTheItemsSlotAndSeparatesCurrentFromOthers() {
        Product top = product(GarmentRole.TOP, "Men");
        Product bottom = product(GarmentRole.BOTTOM, "Men");
        Product scarf = product(GarmentRole.ACCESSORY, "Unisex");
        OutfitItem scarfItem = item(scarf);
        when(outfitItemQueryService.findItems(outfitId)).thenReturn(List.of(item(top), item(bottom), scarfItem));

        SlotEditTarget target = resolver.forItem(outfitId, scarfItem.getId());

        assertThat(target.outfitId()).isEqualTo(outfitId);
        assertThat(target.role()).isEqualTo(GarmentRole.ACCESSORY);
        assertThat(target.currentProduct()).isSameAs(scarf);
        assertThat(target.otherProducts()).containsExactly(top, bottom);
        assertThat(target.hasCurrent()).isTrue();
        assertThat(target.outgoingPrice()).isEqualByComparingTo(scarf.getBasePrice());
    }

    @Test
    void forItem_gendersComeFromTheCurrentItemsOwnGender() {
        Product top = product(GarmentRole.TOP, "Women");
        OutfitItem topItem = item(top);
        when(outfitItemQueryService.findItems(outfitId)).thenReturn(List.of(
                topItem, item(product(GarmentRole.BOTTOM, "Women"))));

        SlotEditTarget target = resolver.forItem(outfitId, topItem.getId());

        assertThat(target.eligibleGenders()).containsExactlyInAnyOrder("Women", "Unisex");
    }

    @Test
    void forItem_unisexCurrentItem_admitsEveryGender() {
        Product scarf = product(GarmentRole.ACCESSORY, "Unisex");
        OutfitItem scarfItem = item(scarf);
        when(outfitItemQueryService.findItems(outfitId)).thenReturn(List.of(
                item(product(GarmentRole.FULL_BODY, "Women")), scarfItem));

        SlotEditTarget target = resolver.forItem(outfitId, scarfItem.getId());

        assertThat(target.eligibleGenders()).containsExactlyInAnyOrder("Men", "Women", "Boys", "Girls", "Unisex");
    }

    @ParameterizedTest
    @EnumSource(value = GarmentRole.class, names = {"TOP", "BOTTOM", "FULL_BODY", "FOOTWEAR"})
    void forEmptySlot_mandatoryRole_throwsBadRequestWithoutLoadingTheOutfit(GarmentRole role) {
        assertThatThrownBy(() -> resolver.forEmptySlot(outfitId, role))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(outfitItemQueryService);
    }

    @Test
    void forEmptySlot_unknownOutfit_propagatesResourceNotFound() {
        when(outfitItemQueryService.findItems(outfitId))
                .thenThrow(new ResourceNotFoundException("Outfit not found: " + outfitId));

        assertThatThrownBy(() -> resolver.forEmptySlot(outfitId, GarmentRole.ACCESSORY))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void forEmptySlot_slotAlreadyOccupied_throwsConflict() {
        when(outfitItemQueryService.findItems(outfitId)).thenReturn(List.of(
                item(product(GarmentRole.TOP, "Men")), item(product(GarmentRole.BOTTOM, "Men")),
                item(product(GarmentRole.FOOTWEAR, "Men")), item(product(GarmentRole.ACCESSORY, "Men"))));

        assertThatThrownBy(() -> resolver.forEmptySlot(outfitId, GarmentRole.ACCESSORY))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void forEmptySlot_hasNoCurrentProductAndEveryItemCountsAsOther() {
        Product top = product(GarmentRole.TOP, "Men");
        Product bottom = product(GarmentRole.BOTTOM, "Men");
        Product shoes = product(GarmentRole.FOOTWEAR, "Men");
        when(outfitItemQueryService.findItems(outfitId)).thenReturn(List.of(item(top), item(bottom), item(shoes)));

        SlotEditTarget target = resolver.forEmptySlot(outfitId, GarmentRole.OUTERWEAR);

        assertThat(target.role()).isEqualTo(GarmentRole.OUTERWEAR);
        assertThat(target.currentProduct()).isNull();
        assertThat(target.hasCurrent()).isFalse();
        assertThat(target.outgoingPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(target.otherProducts()).containsExactly(top, bottom, shoes);
    }

    @Test
    void forEmptySlot_gendersComeFromTheFullBodyGarmentWhenPresent() {
        when(outfitItemQueryService.findItems(outfitId)).thenReturn(List.of(
                item(product(GarmentRole.FOOTWEAR, "Unisex")), item(product(GarmentRole.FULL_BODY, "Girls"))));

        SlotEditTarget target = resolver.forEmptySlot(outfitId, GarmentRole.ACCESSORY);

        assertThat(target.eligibleGenders()).containsExactlyInAnyOrder("Girls", "Unisex");
    }

    @Test
    void forEmptySlot_gendersComeFromTheTopWhenThereIsNoFullBody() {
        when(outfitItemQueryService.findItems(outfitId)).thenReturn(List.of(
                item(product(GarmentRole.BOTTOM, "Unisex")), item(product(GarmentRole.FOOTWEAR, "Unisex")),
                item(product(GarmentRole.TOP, "Men"))));

        SlotEditTarget target = resolver.forEmptySlot(outfitId, GarmentRole.ACCESSORY);

        assertThat(target.eligibleGenders()).containsExactlyInAnyOrder("Men", "Unisex");
    }

    @Test
    void forEmptySlot_noCoreGarment_admitsEveryGender() {
        when(outfitItemQueryService.findItems(outfitId)).thenReturn(List.of(
                item(product(GarmentRole.FOOTWEAR, "Men"))));

        SlotEditTarget target = resolver.forEmptySlot(outfitId, GarmentRole.ACCESSORY);

        assertThat(target.eligibleGenders()).containsExactlyInAnyOrder("Men", "Women", "Boys", "Girls", "Unisex");
    }

    private Product product(GarmentRole role, String gender) {
        return Product.builder()
                .id(UUID.randomUUID())
                .garmentRole(role)
                .gender(gender)
                .basePrice(new BigDecimal("25.00"))
                .build();
    }

    private OutfitItem item(Product product) {
        return OutfitItem.builder().id(UUID.randomUUID()).product(product).slot(product.getGarmentRole()).build();
    }
}
