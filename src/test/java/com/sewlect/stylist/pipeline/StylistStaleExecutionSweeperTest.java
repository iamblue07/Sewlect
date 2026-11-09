package com.sewlect.stylist.pipeline;

import com.sewlect.stylist.entity.StylistConversation;
import com.sewlect.stylist.enums.ConversationStatus;
import com.sewlect.stylist.properties.StylistProperties;
import com.sewlect.stylist.repository.StylistConversationRepository;
import com.sewlect.stylist.service.StylistPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StylistStaleExecutionSweeperTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private StylistConversationRepository conversationRepository;

    @Mock
    private StylistPersistenceService persistenceService;

    private StylistStaleExecutionSweeper sweeper;

    @BeforeEach
    void setUp() {
        StylistProperties properties = new StylistProperties(
                15, 1000, 60, Duration.ofMinutes(10), Duration.ofMinutes(15), 300000L, 20, 100);
        sweeper = new StylistStaleExecutionSweeper(conversationRepository, persistenceService, properties, CLOCK);
    }

    @Test
    void sweep_failsEveryExecutingConversationOlderThanTheStaleThreshold() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        LocalDateTime cutoff = LocalDateTime.now(CLOCK).minusMinutes(15);
        when(conversationRepository.findByStatusAndUpdatedAtBefore(ConversationStatus.EXECUTING, cutoff))
                .thenReturn(List.of(conversation(first), conversation(second)));

        sweeper.sweepStaleExecutions();

        verify(persistenceService).failExecution(first);
        verify(persistenceService).failExecution(second);
    }

    @Test
    void sweep_nothingStale_failsNothing() {
        when(conversationRepository.findByStatusAndUpdatedAtBefore(any(), any())).thenReturn(List.of());

        sweeper.sweepStaleExecutions();

        verify(persistenceService, never()).failExecution(any());
    }

    private StylistConversation conversation(UUID id) {
        return StylistConversation.builder().id(id).status(ConversationStatus.EXECUTING).build();
    }
}
