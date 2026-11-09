package com.sewlect.outfit.repository;

import com.sewlect.outfit.entity.OutfitItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OutfitItemRepository extends JpaRepository<OutfitItem, UUID> {

    @Query("SELECT oi.outfit.id AS outfitId, oi.product.id AS productId FROM OutfitItem oi "
            + "WHERE oi.outfit.id IN :outfitIds")
    List<OutfitProductId> findProductIdsByOutfitIdIn(@Param("outfitIds") Collection<UUID> outfitIds);

    @Query("SELECT oi FROM OutfitItem oi JOIN FETCH oi.product WHERE oi.outfit.id = :outfitId")
    List<OutfitItem> findByOutfitId(@Param("outfitId") UUID outfitId);

    @Query("SELECT oi FROM OutfitItem oi JOIN FETCH oi.product WHERE oi.outfit.id IN :outfitIds")
    List<OutfitItem> findByOutfitIdIn(@Param("outfitIds") Collection<UUID> outfitIds);

    @Query("SELECT COALESCE(SUM(oi.product.basePrice), 0) FROM OutfitItem oi WHERE oi.outfit.id = :outfitId")
    BigDecimal sumBasePriceByOutfitId(@Param("outfitId") UUID outfitId);

    @Query("SELECT oi.outfit.id AS outfitId, COALESCE(SUM(oi.product.basePrice), 0) AS totalBasePrice "
            + "FROM OutfitItem oi WHERE oi.outfit.id IN :outfitIds GROUP BY oi.outfit.id")
    List<OutfitBasePriceTotal> sumBasePriceByOutfitIdIn(@Param("outfitIds") Collection<UUID> outfitIds);

    interface OutfitProductId {
        UUID getOutfitId();

        UUID getProductId();
    }

    interface OutfitBasePriceTotal {
        UUID getOutfitId();

        BigDecimal getTotalBasePrice();
    }
}