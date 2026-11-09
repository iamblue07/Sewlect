package com.sewlect.stylist.service;

import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.outfit.domain.PromptQueryOutcome;
import com.sewlect.outfit.service.PromptOutfitGenerationService;
import com.sewlect.stylist.entity.StylistConversation;
import com.sewlect.stylist.entity.StylistSearch;
import com.sewlect.stylist.enums.StylistSearchStatus;
import com.sewlect.stylist.properties.StylistProperties;
import com.sewlect.stylist.repository.StylistConversationRepository;
import com.sewlect.stylist.repository.StylistSearchRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class StylistSearchExecutor {

    private final StylistConversationRepository conversationRepository;
    private final StylistSearchRepository searchRepository;
    private final StylistPersistenceService stylistPersistenceService;
    private final PromptOutfitGenerationService promptOutfitGenerationService;
    private final StylistProperties properties;
    private final Clock clock;

    public void run(UUID conversationId) {
        Instant deadline = clock.instant().plus(properties.executionTimeout());
        StylistConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));
        UUID userId = conversation.getUser().getId();

        List<StylistSearch> pending = searchRepository.findByConversationIdAndStatusOrderByPositionAsc(
                conversationId, StylistSearchStatus.PENDING);
        for (StylistSearch search : pending) {
            if (!clock.instant().isBefore(deadline)) {
                log.warn("Stylist execution {} hit its {} timeout; remaining searches are marked failed",
                        conversationId, properties.executionTimeout());
                break;
            }
            if (!stylistPersistenceService.touchExecuting(conversationId)) {
                log.warn("Stylist execution {} stopped: the conversation is no longer executing", conversationId);
                return;
            }
            PromptQueryOutcome outcome = promptOutfitGenerationService.execute(
                    userId, search.getPrompt(), search.isMatchProfile());
            stylistPersistenceService.recordSearchOutcome(search.getId(), outcome);
        }

        stylistPersistenceService.finishExecution(conversationId);
    }
}
