package com.sewlect.catalog.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.repository.ProductRepository;
import com.sewlect.common.persistence.util.VectorLiteralFormatter;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Vector;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ProductSearchService {

    private final ProductRepository productRepository;

    public List<UUID> findEligibleIds(GarmentRole role, Set<String> genders, BigDecimal priceCeiling) {
        return productRepository.findEligibleIds(role, genders, priceCeiling);
    }

    public List<Product> findNearest(GarmentRole role, Set<String> genders, BigDecimal priceCeiling,
                                     Vector reference, int limit) {
        return productRepository.findNearestByRole(
                role.name(), genders, priceCeiling, VectorLiteralFormatter.format(reference.toFloatArray()), limit);
    }

    public List<Product> findNearestByOccasion(GarmentRole role, Set<String> genders, BigDecimal priceCeiling,
                                               String occasion, Vector reference, int limit) {
        return productRepository.findNearestByRoleAndOccasion(
                role.name(), genders, priceCeiling, occasion, VectorLiteralFormatter.format(reference.toFloatArray()),
                limit);
    }

    public List<Product> findNearestOfArticleType(String articleType, Set<String> genders, BigDecimal priceCeiling,
                                                  UUID excludedProductId, Vector reference, int limit) {
        return productRepository.findNearestOfArticleType(
                articleType, genders, priceCeiling, excludedProductId,
                VectorLiteralFormatter.format(reference.toFloatArray()), limit);
    }

    public Optional<Product> findById(UUID productId) {
        return productRepository.findById(productId);
    }
}
