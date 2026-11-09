package com.sewlect.common.ai.domain;

import java.util.List;

public record SafetyVerdict(
        boolean safe,
        List<String> categories
) {

    public static final SafetyVerdict SAFE = new SafetyVerdict(true, List.of());

    public static SafetyVerdict unsafe(List<String> categories) {
        return new SafetyVerdict(false, List.copyOf(categories));
    }
}
