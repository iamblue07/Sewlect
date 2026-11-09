package com.sewlect.outfit.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.service.ProductSearchService;
import com.sewlect.common.exception.ExternalServiceException;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.service.UserProfileQueryService;
import com.sewlect.outfit.properties.OutfitDiversityProperties;
import com.sewlect.outfit.properties.OutfitPromptProperties;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.domain.OutfitBlueprint;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.domain.PromptInferredQuery;
import com.sewlect.outfit.domain.PromptQueryOutcome;
import com.sewlect.outfit.domain.SlotDescription;
import com.sewlect.outfit.domain.StructuredPromptQuery;
import com.sewlect.outfit.entity.AiPromptQuery;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.enums.AiPromptQueryStatus;
import com.sewlect.outfit.enums.OutfitSource;
import com.sewlect.outfit.support.BudgetCeilingResolver;
import com.sewlect.outfit.support.OutfitGenderFilterResolver;
import com.sewlect.outfit.support.OutfitItemSetHasher;
import com.sewlect.outfit.support.OutfitResponseAssembler;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Vector;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
@EnableConfigurationProperties(OutfitPromptProperties.class)
public class PromptOutfitGenerationService {

    private final PromptExtractionService promptExtractionService;
    private final PromptQueryEmbeddingService promptQueryEmbeddingService;
    private final ProductSearchService productSearchService;
    private final OutfitCompatibilityScorer outfitCompatibilityScorer;
    private final OutfitGenderFilterResolver outfitGenderFilterResolver;
    private final OutfitItemSetHasher outfitItemSetHasher;
    private final OutfitBatchPersistenceService outfitBatchPersistenceService;
    private final OutfitResponseAssembler outfitResponseAssembler;
    private final UserProfileQueryService userProfileQueryService;
    private final AiPromptQueryService aiPromptQueryService;
    private final BudgetCeilingResolver budgetCeilingResolver;
    private final OutfitPromptProperties properties;
    private final OutfitDiversityProperties diversityProperties;

    public List<OutfitResponse> generate(UUID userId, String rawPrompt, boolean matchProfile) {
        PromptQueryOutcome outcome = execute(userId, rawPrompt, matchProfile);
        if (outcome.status() == AiPromptQueryStatus.FAILED) {
            throw new ExternalServiceException(outcome.errorMessage());
        }
        return outfitResponseAssembler.toResponses(outcome.outfits(), userId);
    }

    public PromptQueryOutcome execute(UUID userId, String rawPrompt, boolean matchProfile) {
        try {
            return matchProfile
                    ? executeAgainstProfile(userId, rawPrompt)
                    : executeWithInferredFilters(userId, rawPrompt);
        } catch (RuntimeException e) {
            AiPromptQuery failure = logFailurePreservingCause(userId, rawPrompt, e);
            return new PromptQueryOutcome(failure.getId(), AiPromptQueryStatus.FAILED, e.getMessage(), List.of());
        }
    }

    private AiPromptQuery logFailurePreservingCause(UUID userId, String rawPrompt, RuntimeException cause) {
        try {
            return aiPromptQueryService.logFailure(userId, rawPrompt, cause.getMessage());
        } catch (RuntimeException loggingFailure) {
            loggingFailure.addSuppressed(cause);
            throw loggingFailure;
        }
    }

    private PromptQueryOutcome executeAgainstProfile(UUID userId, String rawPrompt) {
        UserProfile profile = userProfileQueryService.getById(userId);
        BigDecimal priceCeiling = budgetCeilingResolver.resolve(profile.getAverageBudgetPerOutfit());

        if (profile.getSex() == null) {
            PromptInferredQuery query = promptExtractionService.extractWithInferredFilters(rawPrompt);
            return buildOutcome(userId, rawPrompt, query.blueprints(), query.genders(), priceCeiling, query, true);
        }

        StructuredPromptQuery query = promptExtractionService.extract(rawPrompt);
        Set<String> genders = outfitGenderFilterResolver.allowedGenders(profile.getSex());
        return buildOutcome(userId, rawPrompt, query.blueprints(), genders, priceCeiling, query, true);
    }

    private PromptQueryOutcome executeWithInferredFilters(UUID userId, String rawPrompt) {
        PromptInferredQuery query = promptExtractionService.extractWithInferredFilters(rawPrompt);

        Set<String> genders = query.genders();
        BigDecimal priceCeiling = budgetCeilingResolver.resolve(query.budget());

        return buildOutcome(userId, rawPrompt, query.blueprints(), genders, priceCeiling, query, false);
    }

    private PromptQueryOutcome buildOutcome(UUID userId, String rawPrompt, List<OutfitBlueprint> blueprints,
                                            Set<String> genders, BigDecimal priceCeiling, Object loggedQuery,
                                            boolean matchProfile) {
        List<BlueprintResult> pool = new ArrayList<>();
        for (OutfitBlueprint blueprint : blueprints) {
            if (pool.size() >= properties.generationPoolSize()) {
                break;
            }
            collectCombinationsFor(blueprint, genders, priceCeiling, pool);
        }

        if (pool.isEmpty()) {
            throw new ExternalServiceException("No viable product combination was found for any extracted blueprint");
        }

        List<BlueprintResult> sortedPool = pool.stream()
                .sorted(Comparator.comparing((BlueprintResult result) -> result.breakdown().finalScore()).reversed())
                .toList();

        List<WinningOutfit> winners = selectTopUniqueCombinations(sortedPool);

        List<OutfitPersistenceService.PersistenceCandidate> candidates = winners.stream()
                .map(winner -> new OutfitPersistenceService.PersistenceCandidate(
                        winner.products(), winner.breakdown(), winner.itemSetHash(), OutfitSource.AI_PROMPT))
                .toList();
        List<Outfit> outfits = outfitBatchPersistenceService.persistWithSingleRetry(candidates);

        List<UUID> outfitIds = outfits.stream().map(Outfit::getId).toList();
        AiPromptQuery success = aiPromptQueryService.logSuccess(userId, rawPrompt, loggedQuery, matchProfile, outfitIds);

        return new PromptQueryOutcome(success.getId(), AiPromptQueryStatus.SUCCESS, null, outfits);
    }

    private List<WinningOutfit> selectTopUniqueCombinations(List<BlueprintResult> sortedPool) {
        List<WinningOutfit> winners = new ArrayList<>();
        Set<String> seenHashes = new HashSet<>();
        Map<UUID, Integer> productUsageCounts = new HashMap<>();
        int maxRepetitions = diversityProperties.maxProductRepetitions();

        for (BlueprintResult candidate : sortedPool) {
            if (winners.size() >= properties.batchSize()) {
                break;
            }
            String hash = outfitItemSetHasher.hash(candidate.products());
            if (seenHashes.contains(hash)) {
                continue;
            }
            if (!withinRepetitionCap(candidate.products(), productUsageCounts, maxRepetitions)) {
                continue;
            }
            seenHashes.add(hash);
            recordUsage(candidate.products(), productUsageCounts);
            winners.add(new WinningOutfit(candidate.products(), candidate.breakdown(), hash));
        }
        return winners;
    }

    private boolean withinRepetitionCap(List<Product> products, Map<UUID, Integer> usageCounts, int maxRepetitions) {
        for (Product product : products) {
            if (usageCounts.getOrDefault(product.getId(), 0) >= maxRepetitions) {
                return false;
            }
        }
        return true;
    }

    private void recordUsage(List<Product> products, Map<UUID, Integer> usageCounts) {
        for (Product product : products) {
            usageCounts.merge(product.getId(), 1, Integer::sum);
        }
    }

    private void collectCombinationsFor(OutfitBlueprint blueprint, Set<String> genders, BigDecimal priceCeiling,
                                        List<BlueprintResult> pool) {
        List<List<Product>> candidatesBySlot = new ArrayList<>();
        for (SlotDescription slot : blueprint.slots()) {
            List<Product> candidates = fetchSlotCandidates(slot, genders, priceCeiling);
            if (candidates.isEmpty()) {
                return;
            }
            candidatesBySlot.add(candidates);
        }

        for (List<Product> combination : cartesianProduct(candidatesBySlot)) {
            if (pool.size() >= properties.generationPoolSize()) {
                return;
            }
            CompatibilityScoreBreakdown breakdown = outfitCompatibilityScorer.score(combination);
            pool.add(new BlueprintResult(combination, breakdown));
        }
    }

    private List<Product> fetchSlotCandidates(SlotDescription slot, Set<String> genders, BigDecimal priceCeiling) {
        Vector referenceEmbedding = promptQueryEmbeddingService.embed(slot.description());
        return productSearchService.findNearest(
                slot.role(), genders, priceCeiling, referenceEmbedding, properties.topKPerSlot());
    }

    private List<List<Product>> cartesianProduct(List<List<Product>> candidatesBySlot) {
        List<List<Product>> combinations = new ArrayList<>();
        combinations.add(new ArrayList<>());
        for (List<Product> slotCandidates : candidatesBySlot) {
            List<List<Product>> expanded = new ArrayList<>();
            for (List<Product> partial : combinations) {
                for (Product candidate : slotCandidates) {
                    List<Product> next = new ArrayList<>(partial);
                    next.add(candidate);
                    expanded.add(next);
                }
            }
            combinations = expanded;
        }
        return combinations;
    }

    private record BlueprintResult(List<Product> products, CompatibilityScoreBreakdown breakdown) {
    }

    private record WinningOutfit(List<Product> products, CompatibilityScoreBreakdown breakdown, String itemSetHash) {
    }
}