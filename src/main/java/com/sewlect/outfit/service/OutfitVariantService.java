package com.sewlect.outfit.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.enums.OutfitShapeViolation;
import com.sewlect.outfit.enums.OutfitSource;
import com.sewlect.outfit.support.OutfitItemSetHasher;
import com.sewlect.outfit.support.OutfitShapeValidator;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class OutfitVariantService {

    private final OutfitShapeValidator shapeValidator;
    private final OutfitCompatibilityScorer compatibilityScorer;
    private final OutfitItemSetHasher itemSetHasher;
    private final OutfitPersistenceService outfitPersistenceService;

    public Outfit createVariant(List<Product> products, OutfitSource source) {
        List<GarmentRole> roles = products.stream().map(Product::getGarmentRole).toList();
        Optional<OutfitShapeViolation> violation = shapeValidator.findViolation(roles);
        if (violation.isPresent()) {
            throw new BadRequestException(violation.get().description() + ": " + roles);
        }

        CompatibilityScoreBreakdown breakdown = compatibilityScorer.score(products);
        String itemSetHash = itemSetHasher.hash(products);
        return outfitPersistenceService.saveOrReuse(products, breakdown, itemSetHash, source);
    }
}
