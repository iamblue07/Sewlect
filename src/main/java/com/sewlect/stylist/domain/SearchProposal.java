package com.sewlect.stylist.domain;

public record SearchProposal(
        String title,
        String summary,
        String prompt,
        boolean matchProfile
) {
}
