package com.sewlect.social.dto;

import com.sewlect.outfit.domain.OutfitEngagement;

public record InteractionStateResponse(boolean active, OutfitEngagement engagement) {
}