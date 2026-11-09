package com.sewlect.catalog.service;

import com.sewlect.catalog.repository.ProductStyleTagRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ProductStyleTagQueryService {

    private final ProductStyleTagRepository productStyleTagRepository;

    public List<UUID> findProductIdsByStyleTagIds(Collection<UUID> styleTagIds) {
        return productStyleTagRepository.findDistinctProductIdByStyleTagIdIn(styleTagIds);
    }

    public Map<UUID, Set<UUID>> findStyleTagIdsForProducts(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<UUID>> styleTagIdsByProductId = new HashMap<>();
        for (ProductStyleTagRepository.ProductStyleTagLink link
                : productStyleTagRepository.findLinksByProductIdIn(productIds)) {
            styleTagIdsByProductId.computeIfAbsent(link.getProductId(), id -> new HashSet<>()).add(link.getStyleTagId());
        }
        return styleTagIdsByProductId;
    }
}