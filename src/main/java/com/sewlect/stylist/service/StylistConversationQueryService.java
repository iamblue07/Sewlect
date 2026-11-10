package com.sewlect.stylist.service;

import com.sewlect.common.exception.ConflictException;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.persistence.util.PageRequests;
import com.sewlect.outfit.domain.PromptQueryResult;
import com.sewlect.outfit.service.PromptResultQueryService;
import com.sewlect.stylist.dto.ConversationPageResponse;
import com.sewlect.stylist.dto.ConversationResponse;
import com.sewlect.stylist.dto.ConversationResultsResponse;
import com.sewlect.stylist.dto.SearchResultResponse;
import com.sewlect.stylist.entity.StylistConversation;
import com.sewlect.stylist.entity.StylistSearch;
import com.sewlect.stylist.enums.ConversationStatus;
import com.sewlect.stylist.properties.StylistProperties;
import com.sewlect.stylist.repository.StylistConversationRepository;
import com.sewlect.stylist.repository.StylistMessageRepository;
import com.sewlect.stylist.repository.StylistSearchRepository;
import com.sewlect.stylist.support.StylistResponseAssembler;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@AllArgsConstructor
public class StylistConversationQueryService {

    private final StylistConversationRepository conversationRepository;
    private final StylistMessageRepository messageRepository;
    private final StylistSearchRepository searchRepository;
    private final PromptResultQueryService promptResultQueryService;
    private final StylistResponseAssembler responseAssembler;
    private final StylistProperties properties;

    public StylistConversation getOwned(UUID userId, UUID conversationId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));
    }

    public StylistConversation getOwnedOpen(UUID userId, UUID conversationId) {
        StylistConversation conversation = getOwned(userId, conversationId);
        if (conversation.getStatus() != ConversationStatus.OPEN) {
            throw new ConflictException("The conversation is closed - start a new chat");
        }
        return conversation;
    }

    public ConversationPageResponse list(UUID userId, int page, int size) {
        Page<StylistConversation> conversations = conversationRepository.findPageByUserId(
                userId, PageRequests.of(page, clampSize(size)));
        return new ConversationPageResponse(
                conversations.getContent().stream().map(responseAssembler::toSummary).toList(),
                conversations.getNumber(), conversations.getSize(),
                conversations.getTotalElements(), conversations.getTotalPages());
    }

    public ConversationResponse get(UUID userId, UUID conversationId) {
        StylistConversation conversation = getOwned(userId, conversationId);
        return responseAssembler.toConversationResponse(
                conversation, messageRepository.findByConversationIdOrderBySequenceAsc(conversationId));
    }

    public ConversationResultsResponse results(UUID userId, UUID conversationId) {
        StylistConversation conversation = getOwned(userId, conversationId);
        List<StylistSearch> searches = searchRepository.findByConversationIdOrderByPositionAsc(conversationId);
        List<UUID> queryIds = searches.stream().map(StylistSearch::getAiPromptQueryId).filter(Objects::nonNull).toList();
        Map<UUID, PromptQueryResult> resultsByQueryId = promptResultQueryService.findResults(queryIds, userId);

        List<SearchResultResponse> results = new ArrayList<>(searches.size());
        for (StylistSearch search : searches) {
            PromptQueryResult result = search.getAiPromptQueryId() == null
                    ? null
                    : resultsByQueryId.get(search.getAiPromptQueryId());
            results.add(new SearchResultResponse(search.getId(), search.getPosition(), search.getTitle(),
                    search.isMatchProfile(), search.getStatus(),
                    result == null ? null : result.errorMessage(),
                    search.getAiPromptQueryId(),
                    result == null ? List.of() : result.outfits()));
        }
        return new ConversationResultsResponse(conversation.getId(), conversation.getStatus(), results);
    }

    private int clampSize(int size) {
        if (size < 1) {
            return properties.defaultPageSize();
        }
        return Math.min(size, properties.maxPageSize());
    }
}
