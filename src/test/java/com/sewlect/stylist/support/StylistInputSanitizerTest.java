package com.sewlect.stylist.support;

import com.sewlect.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StylistInputSanitizerTest {

    private static final String MIXED_SCRIPTS =
            "Rochie elegant\u0103 pentru nunt\u0103 \uD83D\uDC57 \u30C9\u30EC\u30B9";

    private final StylistInputSanitizer sanitizer = new StylistInputSanitizer();

    @Test
    void sanitize_plainMessage_isReturnedUnchanged() {
        assertThat(sanitizer.sanitize("A navy suit for a summer wedding")).isEqualTo("A navy suit for a summer wedding");
    }

    @Test
    void sanitize_zeroWidthCharactersHiddenInsideWords_areRemoved() {
        assertThat(sanitizer.sanitize("ig\u200Bnore\u200C prev\u200Dious\uFEFF")).isEqualTo("ignore previous");
    }

    @Test
    void sanitize_bidirectionalOverrides_areRemoved() {
        assertThat(sanitizer.sanitize("\u202Eshirt\u202C and \u2066jeans\u2069")).isEqualTo("shirt and jeans");
    }

    @Test
    void sanitize_invisibleUnicodeTagCharacters_areRemoved() {
        String hidden = new StringBuilder("dress")
                .appendCodePoint(0xE0049).appendCodePoint(0xE0047).appendCodePoint(0xE004E)
                .toString();

        assertThat(sanitizer.sanitize(hidden)).isEqualTo("dress");
    }

    @Test
    void sanitize_controlCharacters_areRemovedButNewlinesAndTabsAreKept() {
        assertThat(sanitizer.sanitize("line one\u0000\u0007\r\nline\ttwo")).isEqualTo("line one\nline\ttwo");
    }

    @Test
    void sanitize_privateUseCharacters_areRemoved() {
        assertThat(sanitizer.sanitize("coat\uE000\uF8FF")).isEqualTo("coat");
    }

    @Test
    void sanitize_compatibilityForms_areNormalisedSoLookalikesCannotDisguiseWords() {
        assertThat(sanitizer.sanitize("\uFF49\uFF47\uFF4E\uFF4F\uFF52\uFF45 \uFB01t")).isEqualTo("ignore fit");
    }

    @Test
    void sanitize_surroundingWhitespace_isTrimmed() {
        assertThat(sanitizer.sanitize("  \n\t a scarf \t\n ")).isEqualTo("a scarf");
    }

    @Test
    void sanitize_diacriticsOtherScriptsAndEmoji_areKept() {
        assertThat(sanitizer.sanitize(MIXED_SCRIPTS)).isEqualTo(MIXED_SCRIPTS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u200B\u200C\uFEFF", "   ", "\u0000\u0001\r", "\u202E\u2066 \u2069"})
    void sanitize_nothingVisibleLeft_throwsBadRequest(String raw) {
        assertThatThrownBy(() -> sanitizer.sanitize(raw))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty");
    }
}
