package com.sewlect.outfit.support;

import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.enums.OutfitShapeViolation;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Component
public class OutfitShapeValidator {

    private static final Set<GarmentRole> OPTIONAL_ROLES =
            Collections.unmodifiableSet(EnumSet.of(GarmentRole.OUTERWEAR, GarmentRole.ACCESSORY));

    public Optional<OutfitShapeViolation> findViolation(Collection<GarmentRole> roles) {
        Set<GarmentRole> roleSet = new HashSet<>(roles);

        if (roleSet.size() != roles.size()) {
            return Optional.of(OutfitShapeViolation.DUPLICATE_ROLE);
        }

        boolean hasTop = roleSet.contains(GarmentRole.TOP);
        boolean hasBottom = roleSet.contains(GarmentRole.BOTTOM);
        boolean hasFullBody = roleSet.contains(GarmentRole.FULL_BODY);
        boolean validCore = (hasTop && hasBottom && !hasFullBody) || (hasFullBody && !hasTop && !hasBottom);

        if (!validCore) {
            return Optional.of(OutfitShapeViolation.INVALID_CORE);
        }

        if (!roleSet.contains(GarmentRole.FOOTWEAR)) {
            return Optional.of(OutfitShapeViolation.MISSING_FOOTWEAR);
        }

        return Optional.empty();
    }

    public boolean isOptional(GarmentRole role) {
        return OPTIONAL_ROLES.contains(role);
    }

    public Set<GarmentRole> optionalRoles() {
        return OPTIONAL_ROLES;
    }
}
