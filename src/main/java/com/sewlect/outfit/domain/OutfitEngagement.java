package com.sewlect.outfit.domain;

public record OutfitEngagement(
        long likeCount,
        boolean likedByViewer,
        boolean savedByViewer
) {

    public static OutfitEngagement none() {
        return new OutfitEngagement(0L, false, false);
    }
}
