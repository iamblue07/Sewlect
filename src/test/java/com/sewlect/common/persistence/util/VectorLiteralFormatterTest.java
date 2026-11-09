package com.sewlect.common.persistence.util;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class VectorLiteralFormatterTest {

    @Test
    void format_writesValuesBracketedAndCommaSeparatedWithoutSpaces() {
        assertThat(VectorLiteralFormatter.format(new float[]{1f, -0.5f, 0.25f})).isEqualTo("[1.0,-0.5,0.25]");
    }

    @Test
    void format_emptyVector_writesEmptyBrackets() {
        assertThat(VectorLiteralFormatter.format(new float[0])).isEqualTo("[]");
    }

    @Test
    void format_defaultLocaleUsesDecimalComma_stillWritesDecimalPoints() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);

            assertThat(VectorLiteralFormatter.format(new float[]{0.5f, 1.25f})).isEqualTo("[0.5,1.25]");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void format_everyValueParsesBackToTheSameFloat() {
        float[] vector = {0.1f, -0.000012345f, 1.0E-8f, 0.33333334f, -1f, 123456.79f};

        String literal = VectorLiteralFormatter.format(vector);
        String[] values = literal.substring(1, literal.length() - 1).split(",");

        float[] parsed = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            parsed[i] = Float.parseFloat(values[i]);
        }
        assertThat(parsed).containsExactly(vector);
    }
}
