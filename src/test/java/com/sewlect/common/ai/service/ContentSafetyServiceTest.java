package com.sewlect.common.ai.service;

import com.sewlect.common.ai.domain.SafetyVerdict;
import com.sewlect.common.exception.ServiceUnavailableException;
import com.sewlect.common.logging.enums.ExternalCallOutcome;
import com.sewlect.common.logging.support.ExternalCallLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentSafetyServiceTest {

    @Mock
    private OpenAiChatModel guardModel;

    @Mock
    private ExternalCallLogger externalCallLogger;

    private ContentSafetyService service;

    @BeforeEach
    void setUp() {
        service = new ContentSafetyService(guardModel, externalCallLogger);
    }

    @Test
    void checkUserMessage_safeVerdict_isSafeWithoutCategoriesAndLoggedAsSuccess() {
        stubGuard("safe");

        SafetyVerdict verdict = service.checkUserMessage("a navy suit for a wedding");

        assertThat(verdict.safe()).isTrue();
        assertThat(verdict.categories()).isEmpty();
        verify(externalCallLogger).logCall(eq("deepinfra"), eq("content-safety"), anyLong(),
                eq(ExternalCallOutcome.SUCCESS));
    }

    @Test
    void checkUserMessage_unsafeVerdictWithCategories_returnsEveryCategory() {
        stubGuard("unsafe\nS1,S10");

        SafetyVerdict verdict = service.checkUserMessage("harmful text");

        assertThat(verdict.safe()).isFalse();
        assertThat(verdict.categories()).containsExactly("S1", "S10");
    }

    @Test
    void checkUserMessage_unsafeVerdictInOddCaseAndSpacing_isStillParsed() {
        stubGuard("  Unsafe \n s12 , s12 ");

        SafetyVerdict verdict = service.checkUserMessage("harmful text");

        assertThat(verdict.safe()).isFalse();
        assertThat(verdict.categories()).containsExactly("S12");
    }

    @Test
    void checkUserMessage_unsafeVerdictWithoutCategories_isUnsafeWithAnEmptyCategoryList() {
        stubGuard("unsafe");

        SafetyVerdict verdict = service.checkUserMessage("harmful text");

        assertThat(verdict.safe()).isFalse();
        assertThat(verdict.categories()).isEmpty();
    }

    @Test
    void checkUserMessage_sendsOnlyTheUserMessageToTheGuard() {
        stubGuard("safe");

        service.checkUserMessage("a navy suit for a wedding");

        List<Message> sent = capturePrompt().getInstructions();
        assertThat(sent).extracting(Message::getMessageType).containsExactly(MessageType.USER);
        assertThat(sent.get(0).getText()).isEqualTo("a navy suit for a wedding");
    }

    @Test
    void checkAssistantReply_sendsTheUserTurnThenTheAssistantTurnSoTheReplyIsClassified() {
        stubGuard("safe");

        service.checkAssistantReply("what should I wear?", "Try a linen shirt with chinos.");

        List<Message> sent = capturePrompt().getInstructions();
        assertThat(sent).extracting(Message::getMessageType).containsExactly(MessageType.USER, MessageType.ASSISTANT);
        assertThat(sent.get(0).getText()).isEqualTo("what should I wear?");
        assertThat(sent.get(1).getText()).isEqualTo("Try a linen shirt with chinos.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"maybe", "", "I cannot classify this"})
    void checkUserMessage_unrecognisedVerdict_failsClosedWith503(String output) {
        stubGuard(output);

        assertThatThrownBy(() -> service.checkUserMessage("text"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("content safety check is unavailable");
        verify(externalCallLogger).logCall(eq("deepinfra"), eq("content-safety"), anyLong(),
                eq(ExternalCallOutcome.PERMANENT_FAILURE));
    }

    @Test
    void checkUserMessage_guardCallFails_failsClosedWith503() {
        when(guardModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("connection refused"));

        assertThatThrownBy(() -> service.checkUserMessage("text"))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(externalCallLogger).logCall(eq("deepinfra"), eq("content-safety"), anyLong(),
                eq(ExternalCallOutcome.PERMANENT_FAILURE));
    }

    @Test
    void checkUserMessage_guardReturnsNoGeneration_failsClosedWith503() {
        when(guardModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));

        assertThatThrownBy(() -> service.checkUserMessage("text"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void unsafe_copiesTheCategoriesSoLaterChangesCannotLeakIn() {
        List<String> categories = new ArrayList<>(List.of("S1"));

        SafetyVerdict verdict = SafetyVerdict.unsafe(categories);
        categories.add("S2");

        assertThat(verdict.categories()).containsExactly("S1");
    }

    private void stubGuard(String output) {
        when(guardModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(output)))));
    }

    private Prompt capturePrompt() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(guardModel).call(captor.capture());
        return captor.getValue();
    }
}
