package com.sewlect.outfit.service;

import com.sewlect.outfit.entity.Outfit;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class OutfitBatchPersistenceService {

    private final OutfitPersistenceService outfitPersistenceService;

    public List<Outfit> persistWithSingleRetry(List<OutfitPersistenceService.PersistenceCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        try {
            return outfitPersistenceService.saveOrReuseBatch(candidates);
        } catch (DataIntegrityViolationException e) {
            return outfitPersistenceService.saveOrReuseBatch(candidates);
        }
    }
}
