package com.sewlect.common.taxonomy.support;

import com.sewlect.common.taxonomy.enums.GarmentRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GarmentRoleResolverTest {

    private final GarmentRoleResolver resolver = new GarmentRoleResolver();

    @Test
    void resolve_topArticleType_returnsTop() {
        assertThat(resolver.resolve("Tshirts")).contains(GarmentRole.TOP);
    }

    @Test
    void resolve_bottomArticleType_returnsBottom() {
        assertThat(resolver.resolve("Jeans")).contains(GarmentRole.BOTTOM);
    }

    @Test
    void resolve_fullBodyArticleType_returnsFullBody() {
        assertThat(resolver.resolve("Dresses")).contains(GarmentRole.FULL_BODY);
    }

    @Test
    void resolve_footwearArticleType_returnsFootwear() {
        assertThat(resolver.resolve("Casual Shoes")).contains(GarmentRole.FOOTWEAR);
    }

    @Test
    void resolve_outerwearArticleType_returnsOuterwear() {
        assertThat(resolver.resolve("Jackets")).contains(GarmentRole.OUTERWEAR);
    }

    @Test
    void resolve_accessoryArticleType_returnsAccessory() {
        assertThat(resolver.resolve("Wallets")).contains(GarmentRole.ACCESSORY);
    }

    @Test
    void resolve_unmappedArticleType_returnsEmpty() {
        // "Socks" is explicitly one of the ~64 unmapped types, not accidentally omitted
        assertThat(resolver.resolve("Socks")).isEmpty();
    }

    @Test
    void resolve_nullArticleType_returnsEmptyRatherThanThrowing() {
        assertThat(resolver.resolve(null)).isEmpty();
    }

    @Test
    void resolve_isExactMatchOnly_doesNotNormalizeCasing() {
        // Deliberately unlike the color resolver: article_type is a controlled dataset field,
        // so exact match is correct here and case-folding would mask a real data problem.
        assertThat(resolver.resolve("tshirts")).isEmpty();
    }

    @Test
    void articleTypesByRole_coversEveryRoleWithSortedArticleTypes() {
        Map<GarmentRole, List<String>> byRole = resolver.articleTypesByRole();

        assertThat(byRole).containsOnlyKeys(GarmentRole.values());
        byRole.values().forEach(articleTypes -> assertThat(articleTypes).isSorted().doesNotHaveDuplicates());
        assertThat(byRole.get(GarmentRole.TOP)).contains("Tshirts", "Shirts");
        assertThat(byRole.get(GarmentRole.FOOTWEAR)).contains("Casual Shoes", "Heels");
    }

    @Test
    void articleTypesByRole_agreesWithResolveForEveryArticleType() {
        resolver.articleTypesByRole().forEach((role, articleTypes) ->
                articleTypes.forEach(articleType -> assertThat(resolver.resolve(articleType)).contains(role)));
    }

    @Test
    void articleTypesByRole_isUnmodifiable() {
        Map<GarmentRole, List<String>> byRole = resolver.articleTypesByRole();

        assertThatThrownBy(() -> byRole.put(GarmentRole.TOP, List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> byRole.get(GarmentRole.TOP).add("Capes"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
