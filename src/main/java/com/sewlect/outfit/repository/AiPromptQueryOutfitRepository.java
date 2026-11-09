package com.sewlect.outfit.repository;

import com.sewlect.outfit.entity.AiPromptQueryOutfit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AiPromptQueryOutfitRepository extends JpaRepository<AiPromptQueryOutfit, UUID> {

    @Query("SELECT q FROM AiPromptQueryOutfit q JOIN FETCH q.outfit "
            + "WHERE q.aiPromptQuery.id IN :queryIds ORDER BY q.aiPromptQuery.id, q.rank")
    List<AiPromptQueryOutfit> findRankedWithOutfitByQueryIdIn(@Param("queryIds") Collection<UUID> queryIds);
}
