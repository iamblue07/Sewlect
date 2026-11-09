package com.sewlect.catalog.repository;

import com.sewlect.catalog.entity.Product;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    String HALFVEC_TYPE = "halfvec(" + Product.TEXT_EMBEDDING_DIMENSIONS + ")";

    String NEAREST_TO_REFERENCE = "ORDER BY CAST(p.text_embedding AS " + HALFVEC_TYPE + ")"
            + " <=> CAST(:reference AS " + HALFVEC_TYPE + ") LIMIT :limit";

    @Query("SELECT p.externalId FROM Product p")
    List<String> findAllExternalIds();

    @Query("SELECT DISTINCT p.occasion FROM Product p WHERE p.occasion IS NOT NULL ORDER BY p.occasion")
    List<String> findDistinctOccasions();

    Optional<Product> findFirstByDescriptionIsNullOrderByCreatedAtAsc();

    Optional<Product> findFirstByDescriptionIsNullAndIdNotInOrderByCreatedAtAsc(Collection<UUID> excludedIds);

    long countByDescriptionIsNotNull();

    List<Product> findAllByDescriptionIsNotNullAndTextEmbeddingIsNull(Limit limit);

    List<Product> findAllByDescriptionIsNotNullAndTextEmbeddingIsNullAndIdNotIn(Collection<UUID> excludedIds, Limit limit);

    long countByTextEmbeddingIsNotNull();

    List<Product> findAllByGarmentRoleIsNull(Limit limit);

    List<Product> findAllByGarmentRoleIsNullAndIdNotIn(Collection<UUID> excludedIds, Limit limit);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.garmentRole = :role WHERE p.id IN :ids")
    int updateGarmentRoleByIdIn(@Param("role") GarmentRole role, @Param("ids") Collection<UUID> ids);

    List<Product> findByGarmentRoleAndGenderInAndBasePriceLessThanEqualAndPrimaryColorIsNotNullAndTextEmbeddingIsNotNull(
            GarmentRole garmentRole, Collection<String> genders, BigDecimal priceCeiling
    );

    @Query(value = """
            SELECT p.* FROM products p
            WHERE p.garment_role = :role
              AND p.gender IN (:genders)
              AND p.base_price <= :priceCeiling
              AND p.text_embedding IS NOT NULL
              AND p.primary_color IS NOT NULL
            """ + NEAREST_TO_REFERENCE, nativeQuery = true)
    List<Product> findNearestByRole(@Param("role") String role,
                                    @Param("genders") Collection<String> genders,
                                    @Param("priceCeiling") BigDecimal priceCeiling,
                                    @Param("reference") String reference,
                                    @Param("limit") int limit);

    @Query(value = """
            SELECT p.* FROM products p
            WHERE p.garment_role = :role
              AND p.gender IN (:genders)
              AND p.base_price <= :priceCeiling
              AND p.occasion = :occasion
              AND p.text_embedding IS NOT NULL
              AND p.primary_color IS NOT NULL
            """ + NEAREST_TO_REFERENCE, nativeQuery = true)
    List<Product> findNearestByRoleAndOccasion(@Param("role") String role,
                                               @Param("genders") Collection<String> genders,
                                               @Param("priceCeiling") BigDecimal priceCeiling,
                                               @Param("occasion") String occasion,
                                               @Param("reference") String reference,
                                               @Param("limit") int limit);

    @Query(value = """
            SELECT p.* FROM products p
            WHERE p.article_type = :articleType
              AND p.gender IN (:genders)
              AND p.base_price <= :priceCeiling
              AND p.id <> :excludedProductId
              AND p.text_embedding IS NOT NULL
              AND p.primary_color IS NOT NULL
            """ + NEAREST_TO_REFERENCE, nativeQuery = true)
    List<Product> findNearestOfArticleType(@Param("articleType") String articleType,
                                           @Param("genders") Collection<String> genders,
                                           @Param("priceCeiling") BigDecimal priceCeiling,
                                           @Param("excludedProductId") UUID excludedProductId,
                                           @Param("reference") String reference,
                                           @Param("limit") int limit);
}
