package com.sewlect.stylist.entity;

import com.sewlect.common.persistence.BaseEntity;
import com.sewlect.stylist.enums.StylistSearchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Table(name = "stylist_searches",
        uniqueConstraints = @UniqueConstraint(name = "uq_stylist_searches_conversation_position",
                columnNames = {"conversation_id", "position"}))
public class StylistSearch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private StylistConversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_message_id", nullable = false)
    private StylistMessage proposalMessage;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String prompt;

    @Column(nullable = false)
    private boolean matchProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StylistSearchStatus status;

    @Column(name = "ai_prompt_query_id", unique = true)
    private UUID aiPromptQueryId;
}
