package com.sewlect.stylist.pipeline;

import com.sewlect.stylist.entity.StylistConversation;
import com.sewlect.stylist.enums.ConversationStatus;
import com.sewlect.stylist.properties.StylistProperties;
import com.sewlect.stylist.repository.StylistConversationRepository;
import com.sewlect.stylist.service.StylistPersistenceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@AllArgsConstructor
public class StylistStaleExecutionSweeper {

    private final StylistConversationRepository conversationRepository;
    private final StylistPersistenceService stylistPersistenceService;
    private final StylistProperties properties;
    private final Clock clock;

    @Scheduled(fixedRateString = "${stylist.stale-sweep-interval-ms}")
    public void sweepStaleExecutions() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(properties.staleExecutionThreshold());
        List<StylistConversation> stale =
                conversationRepository.findByStatusAndUpdatedAtBefore(ConversationStatus.EXECUTING, cutoff);
        for (StylistConversation conversation : stale) {
            log.warn("Sweeping abandoned stylist execution {} last updated at {}",
                    conversation.getId(), conversation.getUpdatedAt());
            stylistPersistenceService.failExecution(conversation.getId());
        }
    }
}
