package com.sewlect.stylist.service;

import com.sewlect.common.exception.ConflictException;
import com.sewlect.common.exception.ServiceUnavailableException;
import com.sewlect.outfit.service.PromptGenerationQuotaService;
import com.sewlect.stylist.domain.SearchProposal;
import com.sewlect.stylist.dto.ConversationSummaryResponse;
import com.sewlect.stylist.entity.StylistMessage;
import com.sewlect.stylist.enums.MessageRole;
import com.sewlect.stylist.repository.StylistMessageRepository;
import com.sewlect.stylist.support.SearchProposalCodec;
import com.sewlect.stylist.support.StylistResponseAssembler;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class StylistExecutionService {

    private static final String STALE_PROPOSAL_MESSAGE = "Proposal is no longer current";
    private static final int SEARCHES_PER_CONFIRMATION = 1;

    private final StylistConversationQueryService conversationQueryService;
    private final StylistMessageRepository messageRepository;
    private final StylistPersistenceService stylistPersistenceService;
    private final StylistSearchExecutor stylistSearchExecutor;
    private final PromptGenerationQuotaService promptGenerationQuotaService;
    private final SearchProposalCodec searchProposalCodec;
    private final StylistResponseAssembler responseAssembler;
    @Qualifier("stylistExecutor")
    private final AsyncTaskExecutor stylistExecutor;

    public ConversationSummaryResponse confirm(UUID userId, UUID conversationId, UUID messageId) {
        conversationQueryService.getOwnedOpen(userId, conversationId);
        StylistMessage latest = messageRepository.findFirstByConversationIdOrderBySequenceDesc(conversationId)
                .orElseThrow(() -> new ConflictException(STALE_PROPOSAL_MESSAGE));
        if (!latest.getId().equals(messageId) || latest.getRole() != MessageRole.ASSISTANT
                || latest.getProposal() == null) {
            throw new ConflictException(STALE_PROPOSAL_MESSAGE);
        }

        SearchProposal proposal = searchProposalCodec.decode(latest.getProposal());
        promptGenerationQuotaService.consumeGeneration(userId, SEARCHES_PER_CONFIRMATION);
        stylistPersistenceService.startExecution(conversationId, messageId, proposal);
        submit(conversationId);

        return responseAssembler.toSummary(conversationQueryService.getOwned(userId, conversationId));
    }

    private void submit(UUID conversationId) {
        try {
            stylistExecutor.submit(() -> {
                try {
                    stylistSearchExecutor.run(conversationId);
                } catch (RuntimeException e) {
                    log.error("Background stylist execution failed for conversation {}", conversationId, e);
                    stylistPersistenceService.failExecution(conversationId);
                }
            });
        } catch (TaskRejectedException e) {
            stylistPersistenceService.revertToOpen(conversationId);
            throw new ServiceUnavailableException("The stylist is busy right now - confirm the proposal again in a moment");
        }
    }
}
