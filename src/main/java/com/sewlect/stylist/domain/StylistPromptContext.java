package com.sewlect.stylist.domain;

import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.identity.enums.Sex;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record StylistPromptContext(
        Sex sex,
        BigDecimal budget,
        List<String> styleTags,
        Map<GarmentRole, List<String>> articleTypesByRole,
        List<String> occasions
) {
}
