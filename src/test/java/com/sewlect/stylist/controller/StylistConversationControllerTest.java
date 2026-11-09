package com.sewlect.stylist.controller;

import com.sewlect.common.exception.ConflictException;
import com.sewlect.common.exception.RateLimitExceededException;
import com.sewlect.common.exception.ContentPolicyViolationException;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.exception.ServiceUnavailableException;
import com.sewlect.stylist.dto.ConversationPageResponse;
import com.sewlect.stylist.dto.ConversationSummaryResponse;
import com.sewlect.stylist.dto.MessageResponse;
import com.sewlect.stylist.dto.TurnResponse;
import com.sewlect.stylist.enums.ConversationStatus;
import com.sewlect.stylist.enums.MessageRole;
import com.sewlect.stylist.service.StylistConversationQueryService;
import com.sewlect.stylist.service.StylistConversationService;
import com.sewlect.stylist.service.StylistExecutionService;
import com.sewlect.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StylistConversationController.class)
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY,
        WebSliceTestConfig.SUBJECT_HASH_SECRET_PROPERTY
})
class StylistConversationControllerTest {

    private static final String BASE = "/api/v1/stylist/conversations";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StylistConversationService stylistConversationService;

    @MockitoBean
    private StylistExecutionService stylistExecutionService;

    @MockitoBean
    private StylistConversationQueryService stylistConversationQueryService;

    @Test
    void start_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"message\": \"hello\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(stylistConversationService, stylistExecutionService, stylistConversationQueryService);
    }

    @Test
    void start_validRequest_returns201WithTheTurnForTheJwtSubject() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(stylistConversationService.start(userId, "I need an outfit for a summer wedding")).thenReturn(new TurnResponse(
                conversationId, ConversationStatus.OPEN, 14,
                message(0, MessageRole.USER, "I need an outfit for a summer wedding"),
                message(1, MessageRole.ASSISTANT, "Who is it for?")));

        mockMvc.perform(post(BASE).header(HttpHeaders.AUTHORIZATION, bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"I need an outfit for a summer wedding\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversationId").value(conversationId.toString()))
                .andExpect(jsonPath("$.userTurnsRemaining").value(14))
                .andExpect(jsonPath("$.assistantMessage.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.assistantMessage.content").value("Who is it for?"));
    }

    @Test
    void start_blankMessage_returns400AndNeverInvokesService() throws Exception {
        mockMvc.perform(post(BASE).header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.message").exists());

        verifyNoInteractions(stylistConversationService, stylistExecutionService, stylistConversationQueryService);
    }

    @Test
    void list_noParameters_usesDefaultPageAndSize() throws Exception {
        UUID userId = UUID.randomUUID();
        when(stylistConversationQueryService.list(userId, 0, 20)).thenReturn(new ConversationPageResponse(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));

        verify(stylistConversationQueryService).list(userId, 0, 20);
    }

    @Test
    void get_anotherUsersConversation_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(stylistConversationQueryService.get(userId, conversationId))
                .thenThrow(new ResourceNotFoundException("Conversation not found: " + conversationId));

        mockMvc.perform(get(BASE + "/{id}", conversationId).header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void sendMessage_blankContent_returns400AndNeverInvokesService() throws Exception {
        mockMvc.perform(post(BASE + "/{id}/messages", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(stylistConversationService, stylistExecutionService, stylistConversationQueryService);
    }

    @Test
    void sendMessage_closedConversation_returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(stylistConversationService.sendMessage(userId, conversationId, "hi"))
                .thenThrow(new ConflictException("The conversation is closed - start a new chat"));

        mockMvc.perform(post(BASE + "/{id}/messages", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"hi\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void requestProposal_takesNoBodyAndReturns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(stylistConversationService.requestProposal(userId, conversationId)).thenReturn(new TurnResponse(
                conversationId, ConversationStatus.OPEN, 13, null, message(4, MessageRole.ASSISTANT, "Ideas")));

        mockMvc.perform(post(BASE + "/{id}/proposal", conversationId).header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userMessage").doesNotExist())
                .andExpect(jsonPath("$.assistantMessage.sequence").value(4));
    }

    @Test
    void confirm_validRequest_returns202WithTheSummary() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(stylistExecutionService.confirm(userId, conversationId, messageId)).thenReturn(
                new ConversationSummaryResponse(conversationId, "Wedding looks", ConversationStatus.EXECUTING,
                        LocalDateTime.now(), LocalDateTime.now()));

        mockMvc.perform(post(BASE + "/{id}/confirm", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\": \"%s\"}".formatted(messageId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("EXECUTING"))
                .andExpect(jsonPath("$.title").value("Wedding looks"));
    }

    @Test
    void confirm_missingMessageId_returns400AndNeverInvokesService() throws Exception {
        mockMvc.perform(post(BASE + "/{id}/confirm", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(stylistConversationService, stylistExecutionService, stylistConversationQueryService);
    }

    @Test
    void confirm_queueFull_returns503() throws Exception {
        when(stylistExecutionService.confirm(any(), any(), any()))
                .thenThrow(new ServiceUnavailableException("The stylist is busy right now"));

        mockMvc.perform(post(BASE + "/{id}/confirm", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\": \"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void delete_returns204() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        mockMvc.perform(delete(BASE + "/{id}", conversationId).header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andExpect(status().isNoContent());

        verify(stylistConversationService).delete(userId, conversationId);
    }

    @Test
    void delete_whileExecuting_returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        doThrow(new ConflictException("The conversation is running its searches and cannot be deleted yet"))
                .when(stylistConversationService).delete(userId, conversationId);

        mockMvc.perform(delete(BASE + "/{id}", conversationId).header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andExpect(status().isConflict());
    }

    private String bearer(UUID userId) {
        return "Bearer " + WebSliceTestConfig.accessToken(userId);
    }

    private MessageResponse message(int sequence, MessageRole role, String content) {
        return new MessageResponse(UUID.randomUUID(), sequence, role, content, null, LocalDateTime.now());
    }

    @Test
    void start_messageBlockedBySafetyCheck_returns422WithTheMessage() throws Exception {
        UUID userId = UUID.randomUUID();
        when(stylistConversationService.start(userId, "harmful")).thenThrow(new ContentPolicyViolationException(
                "This message can't be sent to the stylist - please keep the chat about outfits and styling"));

        mockMvc.perform(post(BASE).header(HttpHeaders.AUTHORIZATION, bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"harmful\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(
                        "This message can't be sent to the stylist - please keep the chat about outfits and styling"));
    }

    @Test
    void sendMessage_safetyCheckUnavailable_returns503() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(stylistConversationService.sendMessage(userId, conversationId, "hi"))
                .thenThrow(new ServiceUnavailableException("The content safety check is unavailable - try again later"));

        mockMvc.perform(post(BASE + "/{id}/messages", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"hi\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void requestProposal_pausedAccount_returns429() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(stylistConversationService.requestProposal(userId, conversationId)).thenThrow(new RateLimitExceededException(
                "The stylist is paused for this account after repeated blocked messages - try again later"));

        mockMvc.perform(post(BASE + "/{id}/proposal", conversationId).header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andExpect(status().isTooManyRequests());
    }
}
