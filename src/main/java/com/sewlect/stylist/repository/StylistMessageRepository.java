package com.sewlect.stylist.repository;

import com.sewlect.stylist.entity.StylistMessage;
import com.sewlect.stylist.enums.MessageRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StylistMessageRepository extends JpaRepository<StylistMessage, UUID> {

    List<StylistMessage> findByConversationIdOrderBySequenceAsc(UUID conversationId);

    long countByConversationIdAndRole(UUID conversationId, MessageRole role);

    Optional<StylistMessage> findFirstByConversationIdOrderBySequenceDesc(UUID conversationId);
}
