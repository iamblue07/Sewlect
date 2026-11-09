package com.sewlect.catalog.repository;

import com.sewlect.catalog.entity.ProductStyleTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductStyleTagRepository extends JpaRepository<ProductStyleTag, UUID> {

    @Query("SELECT DISTINCT p.product.id FROM ProductStyleTag p WHERE p.styleTag.id IN :styleTagIds")
    List<UUID> findDistinctProductIdByStyleTagIdIn(@Param("styleTagIds") Collection<UUID> styleTagIds);

    @Query("SELECT p.product.id AS productId, p.styleTag.id AS styleTagId FROM ProductStyleTag p "
            + "WHERE p.product.id IN :productIds")
    List<ProductStyleTagLink> findLinksByProductIdIn(@Param("productIds") Collection<UUID> productIds);

    interface ProductStyleTagLink {
        UUID getProductId();

        UUID getStyleTagId();
    }
}