package com.sewlect.catalog.repository;

import com.sewlect.catalog.entity.Product;
import com.sewlect.common.persistence.config.JpaAuditingConfig;
import com.sewlect.common.persistence.util.VectorLiteralFormatter;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.support.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ProductRepositoryTest extends AbstractPostgresIntegrationTest {

    private static final Set<String> MEN_AND_UNISEX = Set.of("Men", "Unisex");
    private static final BigDecimal PRICE_CEILING = new BigDecimal("100.00");
    private static final String CASUAL = "casual";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findNearestByRole_returnsOnlyProductsPassingEveryFilterInAscendingCosineDistance() {
        Product nearest = persist(GarmentRole.TOP, "Men", "50.00", CASUAL, "black", embedding(1f, 0f));
        Product middle = persist(GarmentRole.TOP, "Unisex", "100.00", "work", "white", embedding(1f, 0.5f));
        Product farthest = persist(GarmentRole.TOP, "Men", "20.00", CASUAL, "navy", embedding(1f, 2f));
        persistExcludedByEveryFilter(CASUAL);
        flushAndClear();

        List<Product> results = productRepository.findNearestByRole(
                GarmentRole.TOP.name(), MEN_AND_UNISEX, PRICE_CEILING, reference(), 10);

        assertThat(results).extracting(Product::getId)
                .containsExactly(nearest.getId(), middle.getId(), farthest.getId());
    }

    @Test
    void findNearestByRole_limitKeepsOnlyTheClosestProducts() {
        Product nearest = persist(GarmentRole.TOP, "Men", "50.00", CASUAL, "black", embedding(1f, 0f));
        Product middle = persist(GarmentRole.TOP, "Men", "50.00", CASUAL, "white", embedding(1f, 0.5f));
        persist(GarmentRole.TOP, "Men", "50.00", CASUAL, "navy", embedding(1f, 2f));
        flushAndClear();

        List<Product> results = productRepository.findNearestByRole(
                GarmentRole.TOP.name(), MEN_AND_UNISEX, PRICE_CEILING, reference(), 2);

        assertThat(results).extracting(Product::getId).containsExactly(nearest.getId(), middle.getId());
    }

    @Test
    void findNearestByRoleAndOccasion_returnsOnlyProductsPassingEveryFilterInAscendingCosineDistance() {
        Product nearest = persist(GarmentRole.TOP, "Men", "50.00", CASUAL, "black", embedding(1f, 0f));
        persist(GarmentRole.TOP, "Men", "50.00", "work", "black", embedding(1f, 0.1f));
        Product middle = persist(GarmentRole.TOP, "Unisex", "100.00", CASUAL, "white", embedding(1f, 0.5f));
        Product farthest = persist(GarmentRole.TOP, "Men", "20.00", CASUAL, "navy", embedding(1f, 2f));
        persistExcludedByEveryFilter(CASUAL);
        flushAndClear();

        List<Product> results = productRepository.findNearestByRoleAndOccasion(
                GarmentRole.TOP.name(), MEN_AND_UNISEX, PRICE_CEILING, CASUAL, reference(), 10);

        assertThat(results).extracting(Product::getId)
                .containsExactly(nearest.getId(), middle.getId(), farthest.getId());
    }

    @Test
    void findNearestOfArticleType_returnsOnlyThatArticleTypeInAscendingDistanceExcludingTheGivenProduct() {
        Product excluded = persistOfType("Scarves", GarmentRole.ACCESSORY, "Men", "10.00", embedding(1f, 0f));
        Product nearest = persistOfType("Scarves", GarmentRole.ACCESSORY, "Unisex", "100.00", embedding(1f, 0.1f));
        Product farthest = persistOfType("Scarves", GarmentRole.ACCESSORY, "Men", "20.00", embedding(1f, 2f));
        persistOfType("Belts", GarmentRole.ACCESSORY, "Men", "10.00", embedding(1f, 0f));
        persistOfType("Scarves", GarmentRole.ACCESSORY, "Women", "10.00", embedding(1f, 0f));
        persistOfType("Scarves", GarmentRole.ACCESSORY, "Men", "100.01", embedding(1f, 0f));
        persistOfType("Scarves", GarmentRole.ACCESSORY, "Men", "10.00", null);
        flushAndClear();

        List<Product> results = productRepository.findNearestOfArticleType(
                "Scarves", MEN_AND_UNISEX, PRICE_CEILING, excluded.getId(), reference(), 10);

        assertThat(results).extracting(Product::getId).containsExactly(nearest.getId(), farthest.getId());
    }

    private Product persistOfType(String articleType, GarmentRole role, String gender, String basePrice,
                                  float[] textEmbedding) {
        return productRepository.save(Product.builder()
                .externalId(UUID.randomUUID().toString())
                .articleType(articleType)
                .garmentRole(role)
                .gender(gender)
                .basePrice(new BigDecimal(basePrice))
                .occasion(CASUAL)
                .primaryColor("black")
                .textEmbedding(textEmbedding)
                .build());
    }

    private void persistExcludedByEveryFilter(String occasion) {
        persist(GarmentRole.BOTTOM, "Men", "50.00", occasion, "black", embedding(1f, 0f));
        persist(GarmentRole.TOP, "Women", "50.00", occasion, "black", embedding(1f, 0f));
        persist(GarmentRole.TOP, "Men", "100.01", occasion, "black", embedding(1f, 0f));
        persist(GarmentRole.TOP, "Men", "50.00", occasion, null, embedding(1f, 0f));
        persist(GarmentRole.TOP, "Men", "50.00", occasion, "black", null);
    }

    private Product persist(GarmentRole role, String gender, String basePrice, String occasion,
                            String primaryColor, float[] textEmbedding) {
        return productRepository.save(Product.builder()
                .externalId(UUID.randomUUID().toString())
                .garmentRole(role)
                .gender(gender)
                .basePrice(new BigDecimal(basePrice))
                .occasion(occasion)
                .primaryColor(primaryColor)
                .textEmbedding(textEmbedding)
                .build());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private String reference() {
        return VectorLiteralFormatter.format(embedding(1f, 0f));
    }

    private float[] embedding(float first, float second) {
        float[] embedding = new float[Product.TEXT_EMBEDDING_DIMENSIONS];
        embedding[0] = first;
        embedding[1] = second;
        return embedding;
    }

    @Test
    void findDistinctOccasions_returnsEachNonNullOccasionOnceInAlphabeticalOrder() {
        persist(GarmentRole.TOP, "Men", "50.00", "work", "black", embedding(1f, 0f));
        persist(GarmentRole.TOP, "Men", "50.00", "casual", "black", embedding(1f, 0f));
        persist(GarmentRole.BOTTOM, "Women", "50.00", "casual", "black", embedding(1f, 0f));
        persist(GarmentRole.TOP, "Men", "50.00", null, "black", embedding(1f, 0f));
        flushAndClear();

        assertThat(productRepository.findDistinctOccasions()).containsExactly("casual", "work");
    }
}
