package com.sewlect.outfit.service;

import com.sewlect.outfit.domain.OutfitEngagement;
import com.sewlect.outfit.domain.PromptQueryResult;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.entity.AiPromptQuery;
import com.sewlect.outfit.entity.AiPromptQueryOutfit;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.enums.AiPromptQueryStatus;
import com.sewlect.outfit.repository.AiPromptQueryOutfitRepository;
import com.sewlect.outfit.repository.AiPromptQueryRepository;
import com.sewlect.outfit.support.OutfitResponseAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class PromptResultQueryServiceTest {

    @Mock
    private AiPromptQueryRepository aiPromptQueryRepository;

    @Mock
    private AiPromptQueryOutfitRepository aiPromptQueryOutfitRepository;

    @Mock
    private OutfitResponseAssembler outfitResponseAssembler;

    private PromptResultQueryService service;

    private final UUID viewerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PromptResultQueryService(aiPromptQueryRepository, aiPromptQueryOutfitRepository,
                outfitResponseAssembler);
    }

    @Test
    void findResults_noQueryIds_returnsEmptyWithoutQuerying() {
        assertThat(service.findResults(List.of(), viewerId)).isEmpty();
        verifyNoInteractions(aiPromptQueryRepository, aiPromptQueryOutfitRepository, outfitResponseAssembler);
    }

    @Test
    void findResults_groupsOutfitsByQueryInRankOrderWithOneAssemblyCall() {
        AiPromptQuery first = query(AiPromptQueryStatus.SUCCESS, null);
        AiPromptQuery second = query(AiPromptQueryStatus.SUCCESS, null);
        Outfit a = outfit();
        Outfit b = outfit();
        Outfit c = outfit();
        List<UUID> ids = List.of(first.getId(), second.getId());
        when(aiPromptQueryRepository.findAllById(ids)).thenReturn(List.of(first, second));
        when(aiPromptQueryOutfitRepository.findRankedWithOutfitByQueryIdIn(ids)).thenReturn(List.of(
                link(first, a, 0), link(first, b, 1), link(second, c, 0)));
        when(outfitResponseAssembler.toResponses(List.of(a, b, c), viewerId))
                .thenReturn(List.of(response(a), response(b), response(c)));

        Map<UUID, PromptQueryResult> results = service.findResults(ids, viewerId);

        assertThat(results.get(first.getId()).outfits()).extracting(OutfitResponse::outfitId)
                .containsExactly(a.getId(), b.getId());
        assertThat(results.get(second.getId()).outfits()).extracting(OutfitResponse::outfitId)
                .containsExactly(c.getId());
        verify(outfitResponseAssembler, times(1)).toResponses(anyList(), any());
        verify(aiPromptQueryOutfitRepository, times(1)).findRankedWithOutfitByQueryIdIn(any());
        verify(aiPromptQueryRepository, times(1)).findAllById(any());
    }

    @Test
    void findResults_failedQueryCarriesItsStatusAndErrorWithNoOutfits() {
        AiPromptQuery failed = query(AiPromptQueryStatus.FAILED, "No viable product combination");
        List<UUID> ids = List.of(failed.getId());
        when(aiPromptQueryRepository.findAllById(ids)).thenReturn(List.of(failed));
        when(aiPromptQueryOutfitRepository.findRankedWithOutfitByQueryIdIn(ids)).thenReturn(List.of());
        when(outfitResponseAssembler.toResponses(List.of(), viewerId)).thenReturn(List.of());

        PromptQueryResult result = service.findResults(ids, viewerId).get(failed.getId());

        assertThat(result.queryId()).isEqualTo(failed.getId());
        assertThat(result.status()).isEqualTo(AiPromptQueryStatus.FAILED);
        assertThat(result.errorMessage()).isEqualTo("No viable product combination");
        assertThat(result.outfits()).isEmpty();
    }

    @Test
    void findResults_sameOutfitInTwoQueries_appearsUnderBoth() {
        AiPromptQuery first = query(AiPromptQueryStatus.SUCCESS, null);
        AiPromptQuery second = query(AiPromptQueryStatus.SUCCESS, null);
        Outfit shared = outfit();
        List<UUID> ids = List.of(first.getId(), second.getId());
        when(aiPromptQueryRepository.findAllById(ids)).thenReturn(List.of(first, second));
        when(aiPromptQueryOutfitRepository.findRankedWithOutfitByQueryIdIn(ids)).thenReturn(List.of(
                link(first, shared, 0), link(second, shared, 0)));
        when(outfitResponseAssembler.toResponses(List.of(shared, shared), viewerId))
                .thenReturn(List.of(response(shared), response(shared)));

        Map<UUID, PromptQueryResult> results = service.findResults(ids, viewerId);

        assertThat(results.get(first.getId()).outfits()).hasSize(1);
        assertThat(results.get(second.getId()).outfits()).hasSize(1);
    }

    private AiPromptQuery query(AiPromptQueryStatus status, String errorMessage) {
        return AiPromptQuery.builder().id(UUID.randomUUID()).status(status).errorMessage(errorMessage).build();
    }

    private Outfit outfit() {
        return Outfit.builder().id(UUID.randomUUID()).build();
    }

    private AiPromptQueryOutfit link(AiPromptQuery query, Outfit outfit, int rank) {
        return AiPromptQueryOutfit.builder().id(UUID.randomUUID()).aiPromptQuery(query).outfit(outfit).rank(rank).build();
    }

    private OutfitResponse response(Outfit outfit) {
        return new OutfitResponse(outfit.getId(), null, null, List.of(), OutfitEngagement.none());
    }
}
