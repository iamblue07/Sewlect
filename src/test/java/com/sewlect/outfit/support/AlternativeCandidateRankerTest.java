package com.sewlect.outfit.support;

import com.sewlect.catalog.entity.Product;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.dto.AlternativeCandidateResponse;
import com.sewlect.outfit.service.OutfitCompatibilityScorer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlternativeCandidateRankerTest {

    @Mock
    private OutfitCompatibilityScorer compatibilityScorer;

    private AlternativeCandidateRanker ranker;

    private Product top;
    private Product bottom;

    @BeforeEach
    void setUp() {
        ranker = new AlternativeCandidateRanker(compatibilityScorer);
        top = product("Top", "40");
        bottom = product("Bottom", "50");
    }

    @Test
    void rank_ordersCandidatesByProjectedFinalScoreDescending() {
        Product weak = product("Weak shoes", "30");
        Product strong = product("Strong shoes", "35");
        Product middle = product("Middle shoes", "25");
        when(compatibilityScorer.score(List.of(top, bottom, weak))).thenReturn(breakdown("0.20"));
        when(compatibilityScorer.score(List.of(top, bottom, strong))).thenReturn(breakdown("0.90"));
        when(compatibilityScorer.score(List.of(top, bottom, middle))).thenReturn(breakdown("0.55"));

        List<AlternativeCandidateResponse> ranked = ranker.rank(List.of(top, bottom), List.of(weak, strong, middle));

        assertThat(ranked).extracting(AlternativeCandidateResponse::productId)
                .containsExactly(strong.getId(), middle.getId(), weak.getId());
    }

    @Test
    void rank_mapsTheCandidateAndItsProjectedBreakdownIntoTheResponse() {
        Product shoes = product("Leather loafers", "75");
        CompatibilityScoreBreakdown projected = breakdown("0.61");
        when(compatibilityScorer.score(List.of(top, bottom, shoes))).thenReturn(projected);

        AlternativeCandidateResponse response = ranker.rank(List.of(top, bottom), List.of(shoes)).get(0);

        assertThat(response.productId()).isEqualTo(shoes.getId());
        assertThat(response.productDisplayName()).isEqualTo("Leather loafers");
        assertThat(response.imageUrl()).isEqualTo("https://img/Leather loafers.jpg");
        assertThat(response.basePrice()).isEqualByComparingTo("75");
        assertThat(response.projectedBreakdown()).isSameAs(projected);
    }

    @Test
    void rank_scoresEachCandidateAgainstTheOtherProductsOnlyWithoutAccumulatingEarlierCandidates() {
        Product first = product("First", "10");
        Product second = product("Second", "20");
        when(compatibilityScorer.score(List.of(top, first))).thenReturn(breakdown("0.5"));
        when(compatibilityScorer.score(List.of(top, second))).thenReturn(breakdown("0.6"));

        ranker.rank(List.of(top), List.of(first, second));

        verify(compatibilityScorer).score(List.of(top, first));
        verify(compatibilityScorer).score(List.of(top, second));
    }

    @Test
    void rank_noCandidates_returnsAnEmptyListWithoutScoring() {
        assertThat(ranker.rank(List.of(top, bottom), List.of())).isEmpty();
        verifyNoInteractions(compatibilityScorer);
    }

    private Product product(String name, String price) {
        return Product.builder().id(UUID.randomUUID()).productDisplayName(name)
                .imageUrl("https://img/" + name + ".jpg").basePrice(new BigDecimal(price)).build();
    }

    private CompatibilityScoreBreakdown breakdown(String finalScore) {
        BigDecimal score = new BigDecimal(finalScore);
        return new CompatibilityScoreBreakdown(score, score, score, score, score);
    }
}
