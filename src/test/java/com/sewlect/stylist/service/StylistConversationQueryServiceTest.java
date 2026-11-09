package com.sewlect.stylist.service;

import com.sewlect.outfit.domain.OutfitEngagement;
import com.sewlect.outfit.domain.PromptQueryResult;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.enums.AiPromptQueryStatus;
import com.sewlect.outfit.service.PromptResultQueryService;
import com.sewlect.stylist.domain.AssistantTurn;
import com.sewlect.stylist.dto.ConversationResultsResponse;
import com.sewlect.stylist.entity.StylistConversation;
import com.sewlect.stylist.entity.StylistSearch;
import com.sewlect.stylist.enums.AssistantTurnState;
import com.sewlect.stylist.enums.ConversationStatus;
import com.sewlect.stylist.enums.StylistSearchStatus;
import com.sewlect.stylist.properties.StylistProperties;
import com.sewlect.stylist.repository.StylistConversationRepository;
import com.sewlect.stylist.repository.StylistMessageRepository;
import com.sewlect.stylist.repository.StylistSearchRepository;
import com.sewlect.stylist.support.SearchProposalCodec;
import com.sewlect.stylist.support.StylistResponseAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StylistConversationQueryServiceTest {

    @Mock
    private StylistConversationRepository conversationRepository;
    @Mock
    private StylistMessageRepository messageRepository;
    @Mock
    private StylistSearchRepository searchRepository;
    @Mock
    private PromptResultQueryService resultQueryService;
    private StylistConversationQueryService service;
    private SearchProposalCodec codec;

    private final UUID userId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final AssistantTurn clarifying =
            AssistantTurn.withoutProposal(AssistantTurnState.CLARIFYING, "What is the occasion?");

    @BeforeEach
    void setUp() {
        codec = new SearchProposalCodec(JsonMapper.builder().build());
        StylistProperties properties = new StylistProperties(
                15, 1000, 60, Duration.ofMinutes(10), Duration.ofMinutes(15), 300000L, 20, 100);
        StylistResponseAssembler assembler = new StylistResponseAssembler(codec, properties);
        StylistConversationQueryService queryService = new StylistConversationQueryService(conversationRepository,
                messageRepository, searchRepository, resultQueryService, assembler, properties);
        service = queryService;
    }

    @Test
    void results_mergesSearchesInPositionOrderWithTheirQueryResults() {
        StylistConversation conversation = owned(ConversationStatus.COMPLETE);
        UUID succeededQuery = UUID.randomUUID();
        UUID failedQuery = UUID.randomUUID();
        StylistSearch first = search(conversation, 0, StylistSearchStatus.SUCCEEDED, succeededQuery);
        StylistSearch second = search(conversation, 1, StylistSearchStatus.FAILED, failedQuery);
        StylistSearch third = search(conversation, 2, StylistSearchStatus.FAILED, null);
        when(searchRepository.findByConversationIdOrderByPositionAsc(conversationId)).thenReturn(List.of(first, second, third));
        OutfitResponse outfit = new OutfitResponse(UUID.randomUUID(), null, null, List.of(), OutfitEngagement.none());
        when(resultQueryService.findResults(List.of(succeededQuery, failedQuery), userId)).thenReturn(Map.of(
                succeededQuery, new PromptQueryResult(succeededQuery, AiPromptQueryStatus.SUCCESS, null, List.of(outfit)),
                failedQuery, new PromptQueryResult(failedQuery, AiPromptQueryStatus.FAILED, "embedding failed", List.of())));

        ConversationResultsResponse results = service.results(userId, conversationId);

        assertThat(results.status()).isEqualTo(ConversationStatus.COMPLETE);
        assertThat(results.searches()).extracting(result -> result.position()).containsExactly(0, 1, 2);
        assertThat(results.searches().get(0).outfits()).containsExactly(outfit);
        assertThat(results.searches().get(1).errorMessage()).isEqualTo("embedding failed");
        assertThat(results.searches().get(2).queryId()).isNull();
        assertThat(results.searches().get(2).status()).isEqualTo(StylistSearchStatus.FAILED);
        assertThat(results.searches().get(2).outfits()).isEmpty();
    }

    @Test
    void list_clampsTheRequestedPageSizeToTheMaximum() {
        when(conversationRepository.findPageByUserId(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        service.list(userId, -3, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(conversationRepository).findPageByUserId(eq(userId), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    private StylistConversation owned(ConversationStatus status) {
        StylistConversation conversation = conversation(status);
        when(conversationRepository.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        return conversation;
    }

    private StylistConversation conversation(ConversationStatus status) {
        return StylistConversation.builder().id(conversationId).status(status).title("Wedding looks").build();
    }

    private StylistSearch search(StylistConversation conversation, int position, StylistSearchStatus status,
                                 UUID queryId) {
        return StylistSearch.builder().id(UUID.randomUUID()).conversation(conversation).position(position)
                .title("Look " + position).prompt("prompt " + position).matchProfile(true).status(status)
                .aiPromptQueryId(queryId).build();
    }

}
