package com.sewlect.outfit.service;

import com.sewlect.common.ai.properties.DeepInfraProperties;
import com.sewlect.common.exception.ExternalServiceException;
import com.sewlect.identity.service.UserReferenceQueryService;
import com.sewlect.outfit.entity.AiPromptQuery;
import com.sewlect.outfit.entity.AiPromptQueryOutfit;
import com.sewlect.outfit.enums.AiPromptQueryStatus;
import com.sewlect.outfit.repository.AiPromptQueryOutfitRepository;
import com.sewlect.outfit.repository.AiPromptQueryRepository;
import com.sewlect.outfit.repository.OutfitRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class AiPromptQueryService {

    private final AiPromptQueryRepository aiPromptQueryRepository;
    private final AiPromptQueryOutfitRepository aiPromptQueryOutfitRepository;
    private final OutfitRepository outfitRepository;
    private final UserReferenceQueryService userReferenceQueryService;
    private final JsonMapper jsonMapper;
    private final DeepInfraProperties deepInfraProperties;

    @Transactional
    public AiPromptQuery logSuccess(UUID userId, String rawPrompt, Object query, boolean matchProfile,
                                    List<UUID> resultingOutfitIds) {
        AiPromptQuery aiPromptQuery = AiPromptQuery.builder()
                .user(userReferenceQueryService.getReference(userId))
                .rawPrompt(rawPrompt)
                .structuredQuery(writeAsJson(query))
                .matchProfile(matchProfile)
                .status(AiPromptQueryStatus.SUCCESS)
                .model(deepInfraProperties.chatModel())
                .build();
        aiPromptQueryRepository.save(aiPromptQuery);

        List<AiPromptQueryOutfit> rankedOutfits = new ArrayList<>(resultingOutfitIds.size());
        for (int rank = 0; rank < resultingOutfitIds.size(); rank++) {
            rankedOutfits.add(AiPromptQueryOutfit.builder()
                    .aiPromptQuery(aiPromptQuery)
                    .outfit(outfitRepository.getReferenceById(resultingOutfitIds.get(rank)))
                    .rank(rank)
                    .build());
        }
        aiPromptQueryOutfitRepository.saveAll(rankedOutfits);

        return aiPromptQuery;
    }

    public AiPromptQuery logFailure(UUID userId, String rawPrompt, String errorMessage) {
        AiPromptQuery aiPromptQuery = AiPromptQuery.builder()
                .user(userReferenceQueryService.getReference(userId))
                .rawPrompt(rawPrompt)
                .status(AiPromptQueryStatus.FAILED)
                .model(deepInfraProperties.chatModel())
                .errorMessage(errorMessage)
                .build();
        return aiPromptQueryRepository.save(aiPromptQuery);
    }

    private String writeAsJson(Object query) {
        try {
            return jsonMapper.writeValueAsString(query);
        } catch (JacksonException e) {
            throw new ExternalServiceException("Failed to serialize structured prompt query: " + e.getMessage());
        }
    }
}