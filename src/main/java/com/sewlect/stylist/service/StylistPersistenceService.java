package com.sewlect.stylist.service;

import com.sewlect.common.exception.ConflictException;
import com.sewlect.identity.service.UserReferenceQueryService;
import com.sewlect.outfit.domain.PromptQueryOutcome;
import com.sewlect.outfit.enums.AiPromptQueryStatus;
import com.sewlect.stylist.domain.AssistantTurn;
import com.sewlect.stylist.domain.SearchProposal;
import com.sewlect.stylist.domain.TurnRecord;
import com.sewlect.stylist.entity.StylistConversation;
import com.sewlect.stylist.entity.StylistMessage;
import com.sewlect.stylist.entity.StylistSearch;
import com.sewlect.stylist.enums.AssistantTurnState;
import com.sewlect.stylist.enums.ConversationStatus;
import com.sewlect.stylist.enums.MessageRole;
import com.sewlect.stylist.enums.StylistSearchStatus;
import com.sewlect.stylist.repository.StylistConversationRepository;
import com.sewlect.stylist.repository.StylistMessageRepository;
import com.sewlect.stylist.repository.StylistSearchRepository;
import com.sewlect.stylist.support.SearchProposalCodec;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@AllArgsConstructor
public class StylistPersistenceService {

    private final StylistConversationRepository conversationRepository;
    private final StylistMessageRepository messageRepository;
    private final StylistSearchRepository searchRepository;
    private final UserReferenceQueryService userReferenceQueryService;
    private final SearchProposalCodec searchProposalCodec;
    private final Clock clock;

    @Transactional
    public TurnRecord createWithTurn(UUID userId, String userContent, AssistantTurn turn) {
        StylistConversation conversation = conversationRepository.save(StylistConversation.builder()
                .user(userReferenceQueryService.getReference(userId))
                .title(proposalTitleOf(turn))
                .status(ConversationStatus.OPEN)
                .build());

        StylistMessage userMessage = messageRepository.save(userMessage(conversation, 0, userContent));
        StylistMessage assistantMessage = messageRepository.save(assistantMessage(conversation, 1, turn));
        return new TurnRecord(userMessage, assistantMessage);
    }

    @Transactional
    public TurnRecord appendTurn(UUID conversationId, int nextSequence, String userContentOrNull, AssistantTurn turn) {
        LocalDateTime now = LocalDateTime.now(clock);
        String title = proposalTitleOf(turn);
        int touched = title == null
                ? conversationRepository.touch(conversationId, ConversationStatus.OPEN, now)
                : conversationRepository.touchAndRetitle(conversationId, ConversationStatus.OPEN, title, now);
        if (touched == 0) {
            throw new ConflictException("The conversation is no longer open");
        }

        StylistConversation conversation = conversationRepository.getReferenceById(conversationId);
        int sequence = nextSequence;
        try {
            StylistMessage userMessage = null;
            if (userContentOrNull != null) {
                userMessage = messageRepository.save(userMessage(conversation, sequence++, userContentOrNull));
            }
            StylistMessage assistantMessage = messageRepository.save(assistantMessage(conversation, sequence, turn));
            messageRepository.flush();
            return new TurnRecord(userMessage, assistantMessage);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("The conversation changed while this turn was being written; reload it");
        }
    }

    @Transactional
    public StylistSearch startExecution(UUID conversationId, UUID proposalMessageId, SearchProposal proposal) {
        if (conversationRepository.transitionStatus(conversationId, ConversationStatus.OPEN,
                ConversationStatus.EXECUTING, LocalDateTime.now(clock)) == 0) {
            throw new ConflictException("The conversation is no longer open");
        }

        return searchRepository.save(StylistSearch.builder()
                .conversation(conversationRepository.getReferenceById(conversationId))
                .proposalMessage(messageRepository.getReferenceById(proposalMessageId))
                .position(0)
                .title(proposal.title())
                .prompt(proposal.prompt())
                .matchProfile(proposal.matchProfile())
                .status(StylistSearchStatus.PENDING)
                .build());
    }

    @Transactional
    public void revertToOpen(UUID conversationId) {
        searchRepository.deleteByConversationIdAndStatus(conversationId, StylistSearchStatus.PENDING);
        conversationRepository.transitionStatus(conversationId, ConversationStatus.EXECUTING,
                ConversationStatus.OPEN, LocalDateTime.now(clock));
    }

    @Transactional
    public boolean recordSearchOutcome(UUID searchId, PromptQueryOutcome outcome) {
        StylistSearchStatus target = outcome.status() == AiPromptQueryStatus.SUCCESS
                ? StylistSearchStatus.SUCCEEDED
                : StylistSearchStatus.FAILED;
        return searchRepository.recordOutcome(searchId, StylistSearchStatus.PENDING, target, outcome.queryId()) == 1;
    }

    @Transactional
    public boolean touchExecuting(UUID conversationId) {
        return conversationRepository.touch(conversationId, ConversationStatus.EXECUTING, LocalDateTime.now(clock)) == 1;
    }

    @Transactional
    public boolean finishExecution(UUID conversationId) {
        ConversationStatus target = searchRepository.existsByConversationIdAndStatus(
                conversationId, StylistSearchStatus.SUCCEEDED) ? ConversationStatus.COMPLETE : ConversationStatus.FAILED;
        return closeExecution(conversationId, target);
    }

    @Transactional
    public boolean failExecution(UUID conversationId) {
        return closeExecution(conversationId, ConversationStatus.FAILED);
    }

    @Transactional
    public boolean deleteUnlessExecuting(UUID conversationId) {
        return conversationRepository.deleteUnlessStatus(conversationId, ConversationStatus.EXECUTING) == 1;
    }

    private boolean closeExecution(UUID conversationId, ConversationStatus target) {
        if (conversationRepository.completeIfStatus(conversationId, ConversationStatus.EXECUTING, target,
                LocalDateTime.now(clock)) == 0) {
            return false;
        }
        searchRepository.transitionStatusByConversationId(
                conversationId, StylistSearchStatus.PENDING, StylistSearchStatus.FAILED);
        return true;
    }

    private String proposalTitleOf(AssistantTurn turn) {
        return turn.state() == AssistantTurnState.PROPOSING ? turn.title() : null;
    }

    private StylistMessage userMessage(StylistConversation conversation, int sequence, String content) {
        return StylistMessage.builder()
                .conversation(conversation)
                .sequence(sequence)
                .role(MessageRole.USER)
                .content(content)
                .build();
    }

    private StylistMessage assistantMessage(StylistConversation conversation, int sequence, AssistantTurn turn) {
        String proposal = turn.state() == AssistantTurnState.PROPOSING
                ? searchProposalCodec.encode(turn.toProposal())
                : null;
        return StylistMessage.builder()
                .conversation(conversation)
                .sequence(sequence)
                .role(MessageRole.ASSISTANT)
                .content(turn.reply() == null ? "" : turn.reply())
                .proposal(proposal)
                .build();
    }
}
