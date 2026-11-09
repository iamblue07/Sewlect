package com.sewlect.outfit.service;

import com.sewlect.outfit.domain.PromptQueryResult;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.entity.AiPromptQuery;
import com.sewlect.outfit.entity.AiPromptQueryOutfit;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.repository.AiPromptQueryOutfitRepository;
import com.sewlect.outfit.repository.AiPromptQueryRepository;
import com.sewlect.outfit.support.OutfitResponseAssembler;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@AllArgsConstructor
public class PromptResultQueryService {

    private final AiPromptQueryRepository aiPromptQueryRepository;
    private final AiPromptQueryOutfitRepository aiPromptQueryOutfitRepository;
    private final OutfitResponseAssembler outfitResponseAssembler;

    public Map<UUID, PromptQueryResult> findResults(Collection<UUID> queryIds, UUID viewerId) {
        if (queryIds.isEmpty()) {
            return Map.of();
        }

        List<AiPromptQuery> queries = aiPromptQueryRepository.findAllById(queryIds);
        List<AiPromptQueryOutfit> rankedOutfits = aiPromptQueryOutfitRepository.findRankedWithOutfitByQueryIdIn(queryIds);

        List<Outfit> outfits = rankedOutfits.stream().map(AiPromptQueryOutfit::getOutfit).toList();
        List<OutfitResponse> responses = outfitResponseAssembler.toResponses(outfits, viewerId);

        Map<UUID, List<OutfitResponse>> responsesByQueryId = new HashMap<>();
        for (int i = 0; i < rankedOutfits.size(); i++) {
            UUID queryId = rankedOutfits.get(i).getAiPromptQuery().getId();
            responsesByQueryId.computeIfAbsent(queryId, id -> new ArrayList<>()).add(responses.get(i));
        }

        Map<UUID, PromptQueryResult> results = new HashMap<>();
        for (AiPromptQuery query : queries) {
            results.put(query.getId(), new PromptQueryResult(query.getId(), query.getStatus(), query.getErrorMessage(),
                    responsesByQueryId.getOrDefault(query.getId(), List.of())));
        }
        return results;
    }
}
