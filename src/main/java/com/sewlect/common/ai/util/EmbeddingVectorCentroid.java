package com.sewlect.common.ai.util;

import java.util.List;

public final class EmbeddingVectorCentroid {

    private EmbeddingVectorCentroid() {
    }

    public static float[] compute(List<float[]> vectors) {
        int dimensions = vectors.get(0).length;
        float[] centroid = new float[dimensions];
        for (float[] vector : vectors) {
            for (int i = 0; i < dimensions; i++) {
                centroid[i] += vector[i];
            }
        }
        for (int i = 0; i < dimensions; i++) {
            centroid[i] /= vectors.size();
        }
        return centroid;
    }
}
