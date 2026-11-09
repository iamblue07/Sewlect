package com.sewlect.outfit.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.entity.OutfitItem;
import com.sewlect.outfit.repository.OutfitItemRepository;
import com.sewlect.outfit.repository.OutfitRepository;
import com.sewlect.outfit.support.OutfitShapeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitItemQueryServiceTest {

    @Mock
    private OutfitItemRepository outfitItemRepository;

    @Mock
    private OutfitRepository outfitRepository;

    private OutfitItemQueryService service;

    private UUID outfitId;

    @BeforeEach
    void setUp() {
        service = new OutfitItemQueryService(outfitItemRepository, outfitRepository, new OutfitShapeValidator());
        outfitId = UUID.randomUUID();
    }

    @Test
    void findProductsForTryon_outfitNotFound_throwsResourceNotFoundExceptionAndNeverQueriesItems() {
        when(outfitRepository.findById(outfitId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findProductsForTryon(outfitId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(outfitId.toString());

        verify(outfitItemRepository, never()).findByOutfitId(any());
    }

    @Test
    void findProductsForTryon_emptyOutfit_returnsEmptyList() {
        when(outfitRepository.findById(outfitId)).thenReturn(Optional.of(Outfit.builder().id(outfitId).build()));
        when(outfitItemRepository.findByOutfitId(outfitId)).thenReturn(List.of());

        List<Product> result = service.findProductsForTryon(outfitId);

        assertThat(result).isEmpty();
    }

    @Test
    void findProductsForTryon_mapsItemsToTheirProductsOnly() {
        when(outfitRepository.findById(outfitId)).thenReturn(Optional.of(Outfit.builder().id(outfitId).build()));
        Product topProduct = productWithId();
        OutfitItem topItem = itemWithSlot(topProduct, GarmentRole.TOP);
        when(outfitItemRepository.findByOutfitId(outfitId)).thenReturn(List.of(topItem));

        List<Product> result = service.findProductsForTryon(outfitId);

        assertThat(result).containsExactly(topProduct);
    }

    @Test
    void findProductsForTryon_fullOutfit_sortsIntoTryOnOrderRegardlessOfRepositoryOrder() {
        when(outfitRepository.findById(outfitId)).thenReturn(Optional.of(Outfit.builder().id(outfitId).build()));
        Product accessoryProduct = productWithId();
        Product footwearProduct = productWithId();
        Product outerwearProduct = productWithId();
        Product topProduct = productWithId();
        Product bottomProduct = productWithId();

        OutfitItem accessoryItem = itemWithSlot(accessoryProduct, GarmentRole.ACCESSORY);
        OutfitItem footwearItem = itemWithSlot(footwearProduct, GarmentRole.FOOTWEAR);
        OutfitItem outerwearItem = itemWithSlot(outerwearProduct, GarmentRole.OUTERWEAR);
        OutfitItem topItem = itemWithSlot(topProduct, GarmentRole.TOP);
        OutfitItem bottomItem = itemWithSlot(bottomProduct, GarmentRole.BOTTOM);

        when(outfitItemRepository.findByOutfitId(outfitId))
                .thenReturn(List.of(accessoryItem, footwearItem, outerwearItem, topItem, bottomItem));

        List<Product> result = service.findProductsForTryon(outfitId);

        assertThat(result).containsExactly(
                bottomProduct, topProduct, outerwearProduct, footwearProduct, accessoryProduct);
    }

    @Test
    void findProductsForTryon_fullBodyOutfitWithAccessory_fullBodyGoesFirst() {
        when(outfitRepository.findById(outfitId)).thenReturn(Optional.of(Outfit.builder().id(outfitId).build()));
        Product accessoryProduct = productWithId();
        Product fullBodyProduct = productWithId();

        OutfitItem accessoryItem = itemWithSlot(accessoryProduct, GarmentRole.ACCESSORY);
        OutfitItem fullBodyItem = itemWithSlot(fullBodyProduct, GarmentRole.FULL_BODY);

        when(outfitItemRepository.findByOutfitId(outfitId)).thenReturn(List.of(accessoryItem, fullBodyItem));

        List<Product> result = service.findProductsForTryon(outfitId);

        assertThat(result).containsExactly(fullBodyProduct, accessoryProduct);
    }

    @Test
    void getReference_delegatesToOutfitRepositoryGetReferenceById() {
        Outfit reference = Outfit.builder().id(outfitId).build();
        when(outfitRepository.getReferenceById(outfitId)).thenReturn(reference);

        Outfit result = service.getReference(outfitId);

        assertThat(result).isEqualTo(reference);
    }

    private Product productWithId() {
        return Product.builder().id(UUID.randomUUID()).build();
    }

    private OutfitItem itemWithSlot(Product product, GarmentRole slot) {
        return OutfitItem.builder().id(UUID.randomUUID()).product(product).slot(slot).build();
    }
}