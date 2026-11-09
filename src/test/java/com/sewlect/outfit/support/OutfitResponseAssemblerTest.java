package com.sewlect.outfit.support;

import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.domain.OutfitEngagement;
import com.sewlect.outfit.domain.OutfitView;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.service.OutfitEngagementReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitResponseAssemblerTest {

    @Mock
    private OutfitViewAssembler outfitViewAssembler;

    @Mock
    private OutfitEngagementReader outfitEngagementReader;

    private OutfitResponseAssembler assembler;

    private final UUID viewerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assembler = new OutfitResponseAssembler(outfitViewAssembler, outfitEngagementReader);
    }

    @Test
    void toResponse_mergesTheViewWithTheViewersEngagement() {
        Outfit outfit = outfit();
        OutfitView view = view(outfit.getId(), "84.50");
        OutfitEngagement engagement = new OutfitEngagement(6L, true, false);
        when(outfitViewAssembler.toView(outfit)).thenReturn(view);
        when(outfitEngagementReader.read(List.of(outfit.getId()), viewerId))
                .thenReturn(Map.of(outfit.getId(), engagement));

        OutfitResponse response = assembler.toResponse(outfit, viewerId);

        assertThat(response.outfitId()).isEqualTo(outfit.getId());
        assertThat(response.compatibilityBreakdown()).isEqualTo(view.compatibilityBreakdown());
        assertThat(response.totalPrice()).isEqualByComparingTo("84.50");
        assertThat(response.items()).isEqualTo(view.items());
        assertThat(response.engagement()).isEqualTo(engagement);
    }

    @Test
    void toResponse_outfitMissingFromTheEngagementResult_getsNone() {
        Outfit outfit = outfit();
        when(outfitViewAssembler.toView(outfit)).thenReturn(view(outfit.getId(), "10.00"));
        when(outfitEngagementReader.read(List.of(outfit.getId()), viewerId)).thenReturn(Map.of());

        assertThat(assembler.toResponse(outfit, viewerId).engagement()).isEqualTo(OutfitEngagement.none());
    }

    @Test
    void toResponses_emptyList_returnsEmptyWithoutTouchingViewsOrEngagement() {
        assertThat(assembler.toResponses(List.of(), viewerId)).isEmpty();
        verifyNoInteractions(outfitViewAssembler, outfitEngagementReader);
    }

    @Test
    void toResponses_preservesInputOrderAndMergesEachOutfitsOwnEngagement() {
        Outfit first = outfit();
        Outfit second = outfit();
        Outfit third = outfit();
        List<Outfit> outfits = List.of(first, second, third);
        when(outfitViewAssembler.toViews(outfits)).thenReturn(List.of(
                view(first.getId(), "10.00"), view(second.getId(), "20.00"), view(third.getId(), "30.00")));
        when(outfitEngagementReader.read(List.of(first.getId(), second.getId(), third.getId()), viewerId))
                .thenReturn(Map.of(
                        third.getId(), new OutfitEngagement(3L, false, true),
                        first.getId(), new OutfitEngagement(1L, true, false)));

        List<OutfitResponse> responses = assembler.toResponses(outfits, viewerId);

        assertThat(responses).extracting(OutfitResponse::outfitId)
                .containsExactly(first.getId(), second.getId(), third.getId());
        assertThat(responses).extracting(OutfitResponse::totalPrice)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("20.00"), new BigDecimal("30.00"));
        assertThat(responses).extracting(OutfitResponse::engagement).containsExactly(
                new OutfitEngagement(1L, true, false), OutfitEngagement.none(), new OutfitEngagement(3L, false, true));
    }

    @Test
    void toResponses_wholePage_callsTheViewAssemblerOnceAndTheEngagementReaderOnce() {
        List<Outfit> outfits = List.of(outfit(), outfit(), outfit(), outfit(), outfit());
        when(outfitViewAssembler.toViews(outfits)).thenReturn(
                outfits.stream().map(outfit -> view(outfit.getId(), "10.00")).toList());
        when(outfitEngagementReader.read(anyList(), any())).thenReturn(Map.of());

        assembler.toResponses(outfits, viewerId);

        verify(outfitViewAssembler, times(1)).toViews(anyList());
        verify(outfitViewAssembler, times(0)).toView(any());
        verify(outfitEngagementReader, times(1)).read(anyList(), any());
        verify(outfitEngagementReader, times(0)).readLikeCounts(any());
    }

    @Test
    void toResponses_passesTheViewerThroughToTheEngagementReader() {
        Outfit outfit = outfit();
        when(outfitViewAssembler.toViews(List.of(outfit))).thenReturn(List.of(view(outfit.getId(), "10.00")));
        when(outfitEngagementReader.read(List.of(outfit.getId()), viewerId)).thenReturn(Map.of());

        assembler.toResponses(List.of(outfit), viewerId);

        verify(outfitEngagementReader).read(List.of(outfit.getId()), viewerId);
    }

    private Outfit outfit() {
        return Outfit.builder().id(UUID.randomUUID()).build();
    }

    private OutfitView view(UUID outfitId, String totalPrice) {
        BigDecimal score = new BigDecimal("0.8");
        return new OutfitView(outfitId, new CompatibilityScoreBreakdown(score, score, score, score, score),
                new BigDecimal(totalPrice), List.of());
    }
}
