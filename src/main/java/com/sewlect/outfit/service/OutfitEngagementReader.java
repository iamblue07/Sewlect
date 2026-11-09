package com.sewlect.outfit.service;

import com.sewlect.outfit.domain.OutfitEngagement;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface OutfitEngagementReader {

    Map<UUID, OutfitEngagement> read(Collection<UUID> outfitIds, UUID viewerId);

    Map<UUID, Long> readLikeCounts(Collection<UUID> outfitIds);
}
