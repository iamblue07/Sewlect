package com.sewlect.outfit.support;

import com.sewlect.common.cache.properties.CacheProperties;
import com.sewlect.common.cache.support.CacheSwitch;
import com.sewlect.common.properties.RedisProperties;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.config.OutfitViewCacheConfig;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.domain.OutfitItemView;
import com.sewlect.outfit.domain.OutfitView;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.properties.OutfitViewCacheProperties;
import com.sewlect.outfit.service.OutfitItemQueryService;
import com.sewlect.support.AbstractRedisIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitViewCacheTest extends AbstractRedisIntegrationTest {

    @Mock
    private OutfitItemQueryService outfitItemQueryService;

    private OutfitViewCache cache;
    private OutfitViewAssembler assembler;

    @BeforeEach
    void setUp() {
        CacheSwitch cacheSwitch = new CacheSwitch(new CacheProperties(true), new RedisProperties(true));
        RedisTemplate<String, OutfitView> template =
                new OutfitViewCacheConfig().outfitViewRedisTemplate(CONNECTION_FACTORY, JsonMapper.builder().build());
        template.afterPropertiesSet();

        cache = new OutfitViewCache(template, cacheSwitch, new OutfitViewCacheProperties(Duration.ofHours(12)));
        assembler = new OutfitViewAssembler(outfitItemQueryService, cache);
    }

    @Test
    void getAll_partialHit_returnsOnlyTheCachedOutfits() {
        OutfitView first = response(UUID.randomUUID(), "84.50");
        OutfitView third = response(UUID.randomUUID(), "120.00");
        UUID missing = UUID.randomUUID();
        cache.putAll(Map.of(first.outfitId(), first, third.outfitId(), third));

        Map<UUID, OutfitView> hits = cache.getAll(List.of(first.outfitId(), missing, third.outfitId()));

        assertThat(hits).containsOnlyKeys(first.outfitId(), third.outfitId());
        assertSameView(hits.get(first.outfitId()), first);
        assertSameView(hits.get(third.outfitId()), third);
    }

    @Test
    void toViews_cacheReturnsHitsOutOfOrder_resultFollowsTheRequestedOrder() {
        Outfit o1 = outfit();
        Outfit o2 = outfit();
        Outfit o3 = outfit();
        Outfit o4 = outfit();
        cache.putAll(Map.of(
                o3.getId(), response(o3.getId(), "30.00"),
                o1.getId(), response(o1.getId(), "10.00")));

        List<UUID> misses = List.of(o2.getId(), o4.getId());
        when(outfitItemQueryService.findItemViewsForOutfits(misses))
                .thenReturn(Map.of(o2.getId(), List.of(), o4.getId(), List.of()));
        when(outfitItemQueryService.sumBasePriceForOutfits(misses))
                .thenReturn(Map.of(o2.getId(), new BigDecimal("20.00"), o4.getId(), new BigDecimal("40.00")));

        List<OutfitView> responses = assembler.toViews(List.of(o1, o2, o3, o4));

        assertThat(responses).extracting(OutfitView::outfitId)
                .containsExactly(o1.getId(), o2.getId(), o3.getId(), o4.getId());
        assertThat(responses).extracting(OutfitView::totalPrice)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("20.00"),
                        new BigDecimal("30.00"), new BigDecimal("40.00"));
        assertThat(cache.getAll(List.of(o1.getId(), o2.getId(), o3.getId(), o4.getId()))).hasSize(4);
    }

    private static void assertSameView(OutfitView actual, OutfitView expected) {
        assertThat(actual).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    private static Outfit outfit() {
        return Outfit.builder().id(UUID.randomUUID()).build();
    }

    private static OutfitView response(UUID outfitId, String totalPrice) {
        CompatibilityScoreBreakdown breakdown = new CompatibilityScoreBreakdown(
                new BigDecimal("0.9100"), new BigDecimal("0.8200"), new BigDecimal("0.8650"),
                new BigDecimal("0.7300"), new BigDecimal("0.7975"));
        OutfitItemView item = new OutfitItemView(
                UUID.randomUUID(), UUID.randomUUID(), "Linen shirt", "https://img.test/linen.jpg",
                new BigDecimal(totalPrice), GarmentRole.TOP, false);
        return new OutfitView(outfitId, breakdown, new BigDecimal(totalPrice), List.of(item));
    }
}