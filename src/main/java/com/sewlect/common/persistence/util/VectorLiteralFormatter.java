package com.sewlect.common.persistence.util;

public final class VectorLiteralFormatter {

    private VectorLiteralFormatter() {
    }

    public static String format(float[] vector) {
        StringBuilder literal = new StringBuilder(vector.length * 12 + 2).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(vector[i]);
        }
        return literal.append(']').toString();
    }
}
