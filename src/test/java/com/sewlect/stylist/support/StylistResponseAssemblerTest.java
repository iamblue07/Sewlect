package com.sewlect.stylist.support;

import com.sewlect.stylist.domain.SearchProposal;
import com.sewlect.stylist.domain.TurnRecord;
import com.sewlect.stylist.dto.ConversationResponse;
import com.sewlect.stylist.dto.ConversationSummaryResponse;
import com.sewlect.stylist.dto.MessageResponse;
import com.sewlect.stylist.dto.TurnResponse;
import com.sewlect.stylist.entity.StylistConversation;
import com.sewlect.stylist.entity.StylistMessage;
import com.sewlect.stylist.enums.ConversationStatus;
import com.sewlect.stylist.enums.MessageRole;
import com.sewlect.stylist.properties.StylistProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StylistResponseAssemblerTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 5, 1, 10, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 5, 1, 10, 5);
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 5, 1, 10, 9);

    private SearchProposalCodec codec;
    private StylistResponseAssembler assembler;

    private final UUID conversationId = UUID.randomUUID();
    private StylistConversation conversation;

    @BeforeEach
    void setUp() {
        codec = new SearchProposalCodec(JsonMapper.builder().build());
        StylistProperties properties = new StylistProperties(
                15, 1000, 60, Duration.ofMinutes(10), Duration.ofMinutes(15), 300000L, 20, 100);
        assembler = new StylistResponseAssembler(codec, properties);
        conversation = StylistConversation.builder().id(conversationId).title("Wedding looks")
                .status(ConversationStatus.COMPLETE).createdAt(CREATED_AT).updatedAt(UPDATED_AT)
                .completedAt(COMPLETED_AT).build();
    }

    @Test
    void toTurnResponse_mapsBothMessagesAndReportsTheRemainingTurns() {
        StylistMessage user = message(4, MessageRole.USER, "for my brother", null);
        StylistMessage assistant = message(5, MessageRole.ASSISTANT, "What is the occasion?", null);

        TurnResponse response = assembler.toTurnResponse(conversationId, new TurnRecord(user, assistant), 3);

        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(response.status()).isEqualTo(ConversationStatus.OPEN);
        assertThat(response.userTurnsRemaining()).isEqualTo(12);
        assertThat(response.userMessage().messageId()).isEqualTo(user.getId());
        assertThat(response.userMessage().sequence()).isEqualTo(4);
        assertThat(response.userMessage().role()).isEqualTo(MessageRole.USER);
        assertThat(response.userMessage().content()).isEqualTo("for my brother");
        assertThat(response.userMessage().createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.assistantMessage().messageId()).isEqualTo(assistant.getId());
        assertThat(response.assistantMessage().content()).isEqualTo("What is the occasion?");
        assertThat(response.assistantMessage().proposal()).isNull();
    }

    @Test
    void toTurnResponse_withoutAUserMessage_leavesItNull() {
        StylistMessage assistant = message(2, MessageRole.ASSISTANT, "Ideas", codec.encode(proposal()));

        TurnResponse response = assembler.toTurnResponse(conversationId, new TurnRecord(null, assistant), 1);

        assertThat(response.userMessage()).isNull();
        assertThat(response.assistantMessage().sequence()).isEqualTo(2);
    }

    @Test
    void toTurnResponse_moreTurnsUsedThanAllowed_floorsTheRemainingTurnsAtZero() {
        StylistMessage assistant = message(40, MessageRole.ASSISTANT, "Ideas", null);

        TurnResponse response = assembler.toTurnResponse(conversationId, new TurnRecord(null, assistant), 20);

        assertThat(response.userTurnsRemaining()).isZero();
    }

    @Test
    void toTurnResponse_decodesTheProposalIntoItsTitleAndSummaryOnly() {
        StylistMessage assistant = message(1, MessageRole.ASSISTANT, "Ideas", codec.encode(proposal()));

        TurnResponse response = assembler.toTurnResponse(conversationId, new TurnRecord(null, assistant), 1);

        assertThat(response.assistantMessage().proposal().title()).isEqualTo("Wedding looks");
        assertThat(response.assistantMessage().proposal().summary()).isEqualTo("A blue linen look for a summer wedding.");
    }

    @Test
    void toConversationResponse_countsOnlyUserMessagesTowardsTheTurnLimit() {
        List<StylistMessage> messages = List.of(
                message(0, MessageRole.USER, "a", null),
                message(1, MessageRole.ASSISTANT, "b", null),
                message(2, MessageRole.USER, "c", null),
                message(3, MessageRole.ASSISTANT, "d", codec.encode(proposal())));

        ConversationResponse response = assembler.toConversationResponse(conversation, messages);

        assertThat(response.userTurnsRemaining()).isEqualTo(13);
    }

    @Test
    void toConversationResponse_mapsTheConversationAndKeepsTheMessageOrder() {
        List<StylistMessage> messages = List.of(
                message(0, MessageRole.USER, "a", null),
                message(1, MessageRole.ASSISTANT, "b", codec.encode(proposal())));

        ConversationResponse response = assembler.toConversationResponse(conversation, messages);

        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(response.title()).isEqualTo("Wedding looks");
        assertThat(response.status()).isEqualTo(ConversationStatus.COMPLETE);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.updatedAt()).isEqualTo(UPDATED_AT);
        assertThat(response.completedAt()).isEqualTo(COMPLETED_AT);
        assertThat(response.messages()).extracting(MessageResponse::sequence).containsExactly(0, 1);
        assertThat(response.messages().get(0).proposal()).isNull();
        assertThat(response.messages().get(1).proposal().summary()).isEqualTo("A blue linen look for a summer wedding.");
    }

    @Test
    void toConversationResponse_noMessages_reportsTheFullTurnAllowance() {
        ConversationResponse response = assembler.toConversationResponse(conversation, List.of());

        assertThat(response.userTurnsRemaining()).isEqualTo(15);
        assertThat(response.messages()).isEmpty();
    }

    @Test
    void toSummary_mapsIdTitleStatusAndTimestamps() {
        ConversationSummaryResponse summary = assembler.toSummary(conversation);

        assertThat(summary.conversationId()).isEqualTo(conversationId);
        assertThat(summary.title()).isEqualTo("Wedding looks");
        assertThat(summary.status()).isEqualTo(ConversationStatus.COMPLETE);
        assertThat(summary.createdAt()).isEqualTo(CREATED_AT);
        assertThat(summary.updatedAt()).isEqualTo(UPDATED_AT);
    }

    private StylistMessage message(int sequence, MessageRole role, String content, String proposal) {
        return StylistMessage.builder().id(UUID.randomUUID()).conversation(conversation).sequence(sequence)
                .role(role).content(content).proposal(proposal).createdAt(CREATED_AT).build();
    }

    private SearchProposal proposal() {
        return new SearchProposal("Wedding looks", "A blue linen look for a summer wedding.", "a blue linen dress for a summer wedding", true);
    }
}
