package com.sewlect.outfit.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.service.ProductSearchService;
import com.sewlect.catalog.service.ProductStyleTagQueryService;
import com.sewlect.common.ai.util.EmbeddingVectorCentroid;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.service.UserStylePreferenceQueryService;
import com.sewlect.outfit.properties.OutfitDiversityProperties;
import com.sewlect.outfit.properties.OutfitGenerationProperties;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.enums.OutfitSource;
import com.sewlect.outfit.support.BudgetCeilingResolver;
import com.sewlect.outfit.support.OutfitGenderFilterResolver;
import com.sewlect.outfit.support.OutfitItemSetHasher;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Vector;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
@EnableConfigurationProperties({OutfitGenerationProperties.class, OutfitDiversityProperties.class})
public class OutfitCandidateGenerator {

    private static final double TOP_BOTTOM_PROBABILITY = 0.7;
    private static final double PREFERRED_ANCHOR_PROBABILITY = 0.8;

    private static final Set<Month> OUTERWEAR_MONTHS = EnumSet.of(
            Month.OCTOBER, Month.NOVEMBER, Month.DECEMBER, Month.JANUARY, Month.FEBRUARY, Month.MARCH);

    private final ProductSearchService productSearchService;
    private final ProductStyleTagQueryService productStyleTagQueryService;
    private final UserStylePreferenceQueryService userStylePreferenceQueryService;
    private final OutfitBatchPersistenceService outfitBatchPersistenceService;
    private final OutfitCompatibilityScorer compatibilityScorer;
    private final OutfitGenerationProperties properties;

    private final Random random;
    private final Clock clock;

    private final OutfitGenderFilterResolver genderFilterResolver;
    private final OutfitItemSetHasher itemSetHasher;
    private final BudgetCeilingResolver budgetCeilingResolver;
    private final OutfitDiversityProperties diversityProperties;

    public List<Outfit> generate(UserProfile profile) {
        Set<String> genders = genderFilterResolver.allowedGenders(profile.getSex());
        BigDecimal priceCeiling = budgetCeilingResolver.resolve(profile.getAverageBudgetPerOutfit());

        List<Product> topPool = productSearchService.findEligible(GarmentRole.TOP, genders, priceCeiling);
        List<Product> fullBodyPool = productSearchService.findEligible(GarmentRole.FULL_BODY, genders, priceCeiling);
        Set<UUID> preferredProductIds = resolvePreferredProductIds(profile.getUserId());

        GenerationContext context = new GenerationContext(
                genders, priceCeiling, topPool, fullBodyPool, preferredProductIds, new HashMap<>());

        List<OutfitPersistenceService.PersistenceCandidate> candidates = new ArrayList<>();
        int attempts = 0;
        while (candidates.size() < properties.batchSize() && attempts < properties.maxGenerationAttempts()) {
            attempts++;
            runCycle(context).ifPresent(candidates::add);
        }

        if (attempts >= properties.maxGenerationAttempts() && candidates.size() < properties.batchSize()) {
            log.warn("Reached max generation attempts ({}) with {}/{} outfits collected for user {}",
                    properties.maxGenerationAttempts(), candidates.size(), properties.batchSize(), profile.getUserId());
        }

        return outfitBatchPersistenceService.persistWithSingleRetry(candidates);
    }

    private Set<UUID> resolvePreferredProductIds(UUID userId) {
        Set<UUID> preferredStyleTagIds = userStylePreferenceQueryService.findPreferredStyleTagIds(userId);
        if (preferredStyleTagIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(productStyleTagQueryService.findProductIdsByStyleTagIds(preferredStyleTagIds));
    }

    private Optional<OutfitPersistenceService.PersistenceCandidate> runCycle(GenerationContext context) {
        List<Product> availableTop = filterAvailable(context.topPool(), context.productUsageCounts());
        List<Product> availableFullBody = filterAvailable(context.fullBodyPool(), context.productUsageCounts());

        CoreShape coreShape = rollCoreShape(availableTop, availableFullBody);
        if (coreShape == null) {
            return Optional.empty();
        }

        List<Product> anchorPool = coreShape == CoreShape.TOP_BOTTOM ? availableTop : availableFullBody;
        Product anchor = sampleAnchor(anchorPool, context.preferredProductIds());
        String anchorOccasion = anchor.getOccasion();

        List<BeamPath> beams = List.of(new BeamPath(List.of(anchor), BigDecimal.ZERO, new LinkedHashMap<>()));

        if (coreShape == CoreShape.TOP_BOTTOM) {
            beams = expandBeams(beams, GarmentRole.BOTTOM, anchorOccasion, context, false);
            if (beams.isEmpty()) {
                return Optional.empty();
            }
        }

        beams = expandBeams(beams, GarmentRole.FOOTWEAR, anchorOccasion, context, false);
        if (beams.isEmpty()) {
            return Optional.empty();
        }

        if (isOuterwearSeason()) {
            beams = expandBeams(beams, GarmentRole.OUTERWEAR, anchorOccasion, context, true);
        }

        beams = expandAccessoryIfAffordable(beams, anchorOccasion, context);

        BeamPath winner = beams.stream().max(Comparator.comparing(BeamPath::score)).orElseThrow();
        List<Product> polished = polish(winner);

        CompatibilityScoreBreakdown breakdown = compatibilityScorer.score(polished);
        String itemSetHash = itemSetHasher.hash(polished);
        recordUsage(polished, context.productUsageCounts());

        return Optional.of(new OutfitPersistenceService.PersistenceCandidate(
                polished, breakdown, itemSetHash, OutfitSource.PROFILE_GENERATED));
    }

    private List<BeamPath> expandBeams(List<BeamPath> beams, GarmentRole role, String anchorOccasion,
                                       GenerationContext context, boolean optional) {
        List<BeamPath> nextGeneration = new ArrayList<>();
        for (BeamPath beam : beams) {
            Vector reference = Vector.of(EmbeddingVectorCentroid.compute(
                    beam.selected().stream().map(Product::getTextEmbedding).toList()));
            List<Product> candidates = fetchSlotCandidates(
                    role, context.genders(), context.priceCeiling(), reference, anchorOccasion);
            candidates = filterAvailable(candidates, context.productUsageCounts());
            if (candidates.isEmpty()) {
                if (optional) {
                    nextGeneration.add(beam);
                }
                continue;
            }
            for (Product candidate : candidates) {
                nextGeneration.add(expandWith(beam, role, candidate, candidates));
            }
        }
        return topBeams(nextGeneration, properties.beamWidth());
    }

    private List<BeamPath> expandAccessoryIfAffordable(List<BeamPath> beams, String anchorOccasion, GenerationContext context) {
        List<BeamPath> affordable = new ArrayList<>();
        List<BeamPath> notAffordable = new ArrayList<>();
        for (BeamPath beam : beams) {
            if (context.priceCeiling().subtract(requiredSpend(beam)).compareTo(BigDecimal.ZERO) > 0) {
                affordable.add(beam);
            } else {
                notAffordable.add(beam);
            }
        }
        if (affordable.isEmpty()) {
            return beams;
        }
        List<BeamPath> expanded = expandBeams(affordable, GarmentRole.ACCESSORY, anchorOccasion, context, true);
        List<BeamPath> combined = new ArrayList<>(expanded);
        combined.addAll(notAffordable);
        return topBeams(combined, properties.beamWidth());
    }

    private BeamPath expandWith(BeamPath beam, GarmentRole role, Product candidate, List<Product> slotCandidates) {
        List<Product> selected = new ArrayList<>(beam.selected());
        selected.add(candidate);
        BigDecimal score = compatibilityScorer.score(selected).finalScore();

        Map<GarmentRole, List<Product>> slotCandidatesByRole = new LinkedHashMap<>(beam.slotCandidates());
        slotCandidatesByRole.put(role, slotCandidates);

        return new BeamPath(selected, score, slotCandidatesByRole);
    }

    private List<BeamPath> topBeams(List<BeamPath> candidates, int width) {
        return candidates.stream()
                .sorted(Comparator.comparing(BeamPath::score).reversed())
                .limit(width)
                .toList();
    }

    private BigDecimal requiredSpend(BeamPath beam) {
        return beam.selected().stream()
                .filter(p -> p.getGarmentRole() != GarmentRole.OUTERWEAR)
                .map(Product::getBasePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Product> fetchSlotCandidates(GarmentRole role, Set<String> genders, BigDecimal priceCeiling,
                                              Vector referenceVector, String occasion) {
        int limit = properties.topKPerSlot();

        List<Product> candidates = productSearchService.findNearestByOccasion(
                role, genders, priceCeiling, occasion, referenceVector, limit);
        if (!candidates.isEmpty()) {
            return candidates;
        }

        return productSearchService.findNearest(role, genders, priceCeiling, referenceVector, limit);
    }

    private List<Product> polish(BeamPath winner) {
        List<Product> current = new ArrayList<>(winner.selected());
        BigDecimal currentScore = winner.score();

        for (Map.Entry<GarmentRole, List<Product>> entry : winner.slotCandidates().entrySet()) {
            int index = indexOfRole(current, entry.getKey());
            if (index < 0) {
                continue;
            }
            UUID currentPickId = current.get(index).getId();
            for (Product candidate : entry.getValue()) {
                if (candidate.getId().equals(currentPickId)) {
                    continue;
                }
                List<Product> trial = new ArrayList<>(current);
                trial.set(index, candidate);
                BigDecimal trialScore = compatibilityScorer.score(trial).finalScore();
                if (trialScore.compareTo(currentScore) > 0) {
                    current = trial;
                    currentScore = trialScore;
                    currentPickId = candidate.getId();
                }
            }
        }
        return current;
    }

    private int indexOfRole(List<Product> products, GarmentRole role) {
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).getGarmentRole() == role) {
                return i;
            }
        }
        return -1;
    }

    private Product sampleAnchor(List<Product> pool, Set<UUID> preferredProductIds) {
        List<Product> preferredSubset = pool.stream()
                .filter(p -> preferredProductIds.contains(p.getId()))
                .toList();
        boolean drawFromPreferred = !preferredSubset.isEmpty() && random.nextDouble() < PREFERRED_ANCHOR_PROBABILITY;
        List<Product> drawPool = drawFromPreferred ? preferredSubset : pool;
        return drawPool.get(random.nextInt(drawPool.size()));
    }

    private CoreShape rollCoreShape(List<Product> availableTop, List<Product> availableFullBody) {
        if (availableTop.isEmpty() && availableFullBody.isEmpty()) {
            return null;
        }
        boolean rollTopBottom = random.nextDouble() < TOP_BOTTOM_PROBABILITY;
        if (rollTopBottom && !availableTop.isEmpty()) {
            return CoreShape.TOP_BOTTOM;
        }
        if (!rollTopBottom && !availableFullBody.isEmpty()) {
            return CoreShape.FULL_BODY;
        }
        return availableTop.isEmpty() ? CoreShape.FULL_BODY : CoreShape.TOP_BOTTOM;
    }

    private boolean isOuterwearSeason() {
        return OUTERWEAR_MONTHS.contains(LocalDate.now(clock).getMonth());
    }

    private List<Product> filterAvailable(List<Product> pool, Map<UUID, Integer> usageCounts) {
        int cap = diversityProperties.maxProductRepetitions();
        return pool.stream().filter(p -> usageCounts.getOrDefault(p.getId(), 0) < cap).toList();
    }

    private void recordUsage(List<Product> products, Map<UUID, Integer> usageCounts) {
        for (Product product : products) {
            usageCounts.merge(product.getId(), 1, Integer::sum);
        }
    }

    private enum CoreShape {
        TOP_BOTTOM, FULL_BODY
    }

    private record BeamPath(List<Product> selected, BigDecimal score, Map<GarmentRole, List<Product>> slotCandidates) {
    }

    private record GenerationContext(
            Set<String> genders,
            BigDecimal priceCeiling,
            List<Product> topPool,
            List<Product> fullBodyPool,
            Set<UUID> preferredProductIds,
            Map<UUID, Integer> productUsageCounts
    ) {
    }
}