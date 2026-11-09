package com.sewlect.outfit.support;

import com.sewlect.catalog.entity.Product;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.dto.AlternativeCandidateResponse;
import com.sewlect.outfit.service.OutfitCompatibilityScorer;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@AllArgsConstructor
public class AlternativeCandidateRanker {

    private final OutfitCompatibilityScorer compatibilityScorer;

    public List<AlternativeCandidateResponse> rank(List<Product> otherProducts, List<Product> candidates) {
        List<AlternativeCandidateResponse> ranked = new ArrayList<>(candidates.size());
        for (Product candidate : candidates) {
            List<Product> trial = new ArrayList<>(otherProducts);
            trial.add(candidate);
            CompatibilityScoreBreakdown projected = compatibilityScorer.score(trial);
            ranked.add(new AlternativeCandidateResponse(
                    candidate.getId(), candidate.getProductDisplayName(), candidate.getImageUrl(),
                    candidate.getBasePrice(), projected));
        }
        ranked.sort(Comparator.comparing(
                (AlternativeCandidateResponse response) -> response.projectedBreakdown().finalScore()).reversed());
        return ranked;
    }
}
