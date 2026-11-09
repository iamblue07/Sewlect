package com.sewlect.catalog.service;

import com.sewlect.catalog.config.CatalogVocabularyCacheConfig;
import com.sewlect.catalog.repository.ProductRepository;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class CatalogVocabularyQueryService {

    private final ProductRepository productRepository;

    @Cacheable(CatalogVocabularyCacheConfig.CATALOG_OCCASIONS_CACHE)
    @Transactional(readOnly = true)
    public List<String> findOccasions() {
        return productRepository.findDistinctOccasions();
    }
}
