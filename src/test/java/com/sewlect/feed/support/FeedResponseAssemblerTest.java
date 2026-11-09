package com.sewlect.feed.support;

import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.feed.dto.FeedItemResponse;
import com.sewlect.feed.entity.FeedEntry;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.domain.OutfitEngagement;
import com.sewlect.outfit.domain.OutfitItemView;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.support.OutfitResponseAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedResponseAssemblerTest {

    @Mock
    private OutfitResponseAssembler outfitResponseAssembler;

    private FeedResponseAssembler assembler;

    private final UUID viewerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assembler = new FeedResponseAssembler(outfitResponseAssembler);
    }

    @Test
    void toResponses_mapsOutfitBreakdownFieldsOneToOne() {
        Outfit outfit = outfitWithId();
        CompatibilityScoreBreakdown breakdown = new CompatibilityScoreBreakdown(
                new BigDecimal("0.9000"), new BigDecimal("0.8000"), new BigDecimal("0.85"),
                new BigDecimal("0.7000"), new BigDecimal("0.8200"));
        FeedEntry entry = FeedEntry.builder().outfit(outfit).rankScore(new BigDecimal("0.9500")).build();
        stubResponse(outfit, breakdown, BigDecimal.ZERO, List.of());

        FeedItemResponse response = single(entry);

        assertThat(response.compatibilityBreakdown().colorScore()).isEqualByComparingTo("0.9000");
        assertThat(response.compatibilityBreakdown().layeringScore()).isEqualByComparingTo("0.8000");
        assertThat(response.compatibilityBreakdown().structuredScore()).isEqualByComparingTo("0.85");
        assertThat(response.compatibilityBreakdown().embeddingScore()).isEqualByComparingTo("0.7000");
        assertThat(response.compatibilityBreakdown().finalScore()).isEqualByComparingTo("0.8200");
    }

    @Test
    void toResponses_rankScoreComesFromEntryNotOutfit_neverConflatedWithCompatibilityScore() {
        Outfit outfit = outfitWithId();
        CompatibilityScoreBreakdown breakdown = new CompatibilityScoreBreakdown(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("0.5000"));
        FeedEntry entry = FeedEntry.builder().outfit(outfit).rankScore(new BigDecimal("0.9999")).build();
        stubResponse(outfit, breakdown, BigDecimal.ZERO, List.of());

        FeedItemResponse response = single(entry);

        assertThat(response.rankScore()).isEqualByComparingTo("0.9999");
        assertThat(response.compatibilityBreakdown().finalScore()).isEqualByComparingTo("0.5000");
    }

    @Test
    void toResponses_mapsEachItemViewToAFeedOutfitItemResponsePreservingOrder() {
        Outfit outfit = outfitWithId();
        OutfitItemView top = new OutfitItemView(UUID.randomUUID(), UUID.randomUUID(), "Blue Shirt", "http://img/1.jpg",
                new BigDecimal("40.00"), GarmentRole.TOP, false);
        OutfitItemView scarf = new OutfitItemView(UUID.randomUUID(), UUID.randomUUID(), "Red Scarf", "http://img/2.jpg",
                new BigDecimal("15.00"), GarmentRole.ACCESSORY, true);
        FeedEntry entry = FeedEntry.builder().outfit(outfit).rankScore(BigDecimal.ONE).build();
        stubResponse(outfit, fullBreakdown(), new BigDecimal("55.00"), List.of(top, scarf));

        FeedItemResponse response = single(entry);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).itemId()).isEqualTo(top.itemId());
        assertThat(response.items().get(0).productId()).isEqualTo(top.productId());
        assertThat(response.items().get(0).slot()).isEqualTo(GarmentRole.TOP);
        assertThat(response.items().get(0).removable()).isFalse();
        assertThat(response.items().get(1).itemId()).isEqualTo(scarf.itemId());
        assertThat(response.items().get(1).productId()).isEqualTo(scarf.productId());
        assertThat(response.items().get(1).slot()).isEqualTo(GarmentRole.ACCESSORY);
        assertThat(response.items().get(1).removable()).isTrue();
    }

    @Test
    void toResponses_totalPriceAndEngagementComeFromTheOutfitResponse() {
        Outfit outfit = outfitWithId();
        FeedEntry entry = FeedEntry.builder().outfit(outfit).rankScore(BigDecimal.ONE).build();
        OutfitEngagement engagement = new OutfitEngagement(7L, true, false);
        when(outfitResponseAssembler.toResponses(List.of(outfit), viewerId)).thenReturn(List.of(
                new OutfitResponse(outfit.getId(), fullBreakdown(), new BigDecimal("100.49"), List.of(), engagement)));

        FeedItemResponse response = single(entry);

        assertThat(response.totalPrice()).isEqualByComparingTo("100.49");
        assertThat(response.engagement()).isEqualTo(engagement);
    }

    @Test
    void toResponses_noItems_returnsEmptyItemList() {
        Outfit outfit = outfitWithId();
        FeedEntry entry = FeedEntry.builder().outfit(outfit).rankScore(BigDecimal.ONE).build();
        stubResponse(outfit, fullBreakdown(), BigDecimal.ZERO, List.of());

        FeedItemResponse response = single(entry);

        assertThat(response.totalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.items()).isEmpty();
    }

    @Test
    void toResponses_outfitId_isTheOutfitsOwnIdNotTheFeedEntrysId() {
        Outfit outfit = outfitWithId();
        FeedEntry entry = FeedEntry.builder().id(UUID.randomUUID()).outfit(outfit).rankScore(BigDecimal.ONE).build();
        stubResponse(outfit, fullBreakdown(), BigDecimal.ZERO, List.of());

        FeedItemResponse response = single(entry);

        assertThat(response.outfitId()).isEqualTo(outfit.getId());
        assertThat(response.outfitId()).isNotEqualTo(entry.getId());
    }

    private FeedItemResponse single(FeedEntry entry) {
        List<FeedItemResponse> responses = assembler.toResponses(List.of(entry), viewerId);
        assertThat(responses).hasSize(1);
        return responses.get(0);
    }

    private void stubResponse(Outfit outfit, CompatibilityScoreBreakdown breakdown, BigDecimal totalPrice,
                              List<OutfitItemView> items) {
        when(outfitResponseAssembler.toResponses(List.of(outfit), viewerId)).thenReturn(List.of(
                new OutfitResponse(outfit.getId(), breakdown, totalPrice, items, OutfitEngagement.none())));
    }

    private CompatibilityScoreBreakdown fullBreakdown() {
        return new CompatibilityScoreBreakdown(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
    }

    private Outfit outfitWithId() {
        return Outfit.builder().id(UUID.randomUUID()).build();
    }

    @Test
    void toResponses_wholePage_assemblesEveryOutfitInOneCallInEntryOrder() {
        Outfit first = outfitWithId();
        Outfit second = outfitWithId();
        Outfit third = outfitWithId();
        List<FeedEntry> entries = List.of(
                FeedEntry.builder().outfit(first).rankScore(new BigDecimal("0.9000")).build(),
                FeedEntry.builder().outfit(second).rankScore(new BigDecimal("0.8000")).build(),
                FeedEntry.builder().outfit(third).rankScore(new BigDecimal("0.7000")).build());
        when(outfitResponseAssembler.toResponses(List.of(first, second, third), viewerId)).thenReturn(List.of(
                new OutfitResponse(first.getId(), fullBreakdown(), BigDecimal.ONE, List.of(), new OutfitEngagement(1L, true, false)),
                new OutfitResponse(second.getId(), fullBreakdown(), BigDecimal.TEN, List.of(), OutfitEngagement.none()),
                new OutfitResponse(third.getId(), fullBreakdown(), BigDecimal.ZERO, List.of(), new OutfitEngagement(2L, false, true))));

        List<FeedItemResponse> responses = assembler.toResponses(entries, viewerId);

        verify(outfitResponseAssembler, times(1)).toResponses(anyList(), any());
        assertThat(responses).extracting(FeedItemResponse::outfitId)
                .containsExactly(first.getId(), second.getId(), third.getId());
        assertThat(responses).extracting(FeedItemResponse::rankScore)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("0.9000"), new BigDecimal("0.8000"), new BigDecimal("0.7000"));
        assertThat(responses).extracting(FeedItemResponse::engagement).containsExactly(
                new OutfitEngagement(1L, true, false), OutfitEngagement.none(), new OutfitEngagement(2L, false, true));
    }

    @Test
    void toResponses_emptyPage_returnsEmptyList() {
        assertThat(assembler.toResponses(List.of(), viewerId)).isEmpty();
    }
}
