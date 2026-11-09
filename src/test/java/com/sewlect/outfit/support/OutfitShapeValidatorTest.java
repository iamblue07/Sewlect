package com.sewlect.outfit.support;

import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.enums.OutfitShapeViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static com.sewlect.common.taxonomy.enums.GarmentRole.ACCESSORY;
import static com.sewlect.common.taxonomy.enums.GarmentRole.BOTTOM;
import static com.sewlect.common.taxonomy.enums.GarmentRole.FOOTWEAR;
import static com.sewlect.common.taxonomy.enums.GarmentRole.FULL_BODY;
import static com.sewlect.common.taxonomy.enums.GarmentRole.OUTERWEAR;
import static com.sewlect.common.taxonomy.enums.GarmentRole.TOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutfitShapeValidatorTest {

    private final OutfitShapeValidator validator = new OutfitShapeValidator();

    @Test
    void findViolation_topBottomFootwear_isValid() {
        assertThat(validator.findViolation(List.of(TOP, BOTTOM, FOOTWEAR))).isEmpty();
    }

    @Test
    void findViolation_fullBodyFootwear_isValid() {
        assertThat(validator.findViolation(List.of(FULL_BODY, FOOTWEAR))).isEmpty();
    }

    @Test
    void findViolation_everyOptionalRoleAdded_isStillValid() {
        assertThat(validator.findViolation(List.of(TOP, BOTTOM, FOOTWEAR, OUTERWEAR, ACCESSORY))).isEmpty();
        assertThat(validator.findViolation(List.of(FULL_BODY, FOOTWEAR, OUTERWEAR, ACCESSORY))).isEmpty();
    }

    @Test
    void findViolation_duplicateRole_isDuplicateRole() {
        assertThat(validator.findViolation(List.of(TOP, BOTTOM, FOOTWEAR, ACCESSORY, ACCESSORY)))
                .contains(OutfitShapeViolation.DUPLICATE_ROLE);
    }

    @Test
    void findViolation_duplicateRoleIsReportedBeforeAnInvalidCore() {
        assertThat(validator.findViolation(List.of(TOP, TOP, FOOTWEAR)))
                .contains(OutfitShapeViolation.DUPLICATE_ROLE);
    }

    @Test
    void findViolation_topWithoutBottom_isInvalidCore() {
        assertThat(validator.findViolation(List.of(TOP, FOOTWEAR))).contains(OutfitShapeViolation.INVALID_CORE);
    }

    @Test
    void findViolation_bottomWithoutTop_isInvalidCore() {
        assertThat(validator.findViolation(List.of(BOTTOM, FOOTWEAR))).contains(OutfitShapeViolation.INVALID_CORE);
    }

    @Test
    void findViolation_fullBodyCombinedWithTop_isInvalidCore() {
        assertThat(validator.findViolation(List.of(FULL_BODY, TOP, FOOTWEAR)))
                .contains(OutfitShapeViolation.INVALID_CORE);
    }

    @Test
    void findViolation_fullBodyCombinedWithTopAndBottom_isInvalidCore() {
        assertThat(validator.findViolation(List.of(FULL_BODY, TOP, BOTTOM, FOOTWEAR)))
                .contains(OutfitShapeViolation.INVALID_CORE);
    }

    @Test
    void findViolation_noCoreAtAll_isInvalidCore() {
        assertThat(validator.findViolation(List.of(FOOTWEAR, ACCESSORY))).contains(OutfitShapeViolation.INVALID_CORE);
    }

    @Test
    void findViolation_validCoreWithoutFootwear_isMissingFootwear() {
        assertThat(validator.findViolation(List.of(TOP, BOTTOM, OUTERWEAR)))
                .contains(OutfitShapeViolation.MISSING_FOOTWEAR);
        assertThat(validator.findViolation(List.of(FULL_BODY))).contains(OutfitShapeViolation.MISSING_FOOTWEAR);
    }

    @Test
    void findViolation_invalidCoreIsReportedBeforeMissingFootwear() {
        assertThat(validator.findViolation(List.of(TOP))).contains(OutfitShapeViolation.INVALID_CORE);
    }

    @ParameterizedTest
    @EnumSource(value = GarmentRole.class, names = {"OUTERWEAR", "ACCESSORY"})
    void isOptional_outerwearAndAccessory_areOptional(GarmentRole role) {
        assertThat(validator.isOptional(role)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = GarmentRole.class, names = {"TOP", "BOTTOM", "FULL_BODY", "FOOTWEAR"})
    void isOptional_coreRolesAndFootwear_areMandatory(GarmentRole role) {
        assertThat(validator.isOptional(role)).isFalse();
    }

    @Test
    void everyViolation_carriesANonBlankDescription() {
        for (OutfitShapeViolation violation : OutfitShapeViolation.values()) {
            assertThat(violation.description()).isNotBlank();
        }
    }

    @Test
    void optionalRoles_isExactlyOuterwearAndAccessory() {
        assertThat(validator.optionalRoles()).containsExactlyInAnyOrder(OUTERWEAR, ACCESSORY);
    }

    @Test
    void optionalRoles_agreesWithIsOptionalForEveryRole() {
        for (GarmentRole role : GarmentRole.values()) {
            assertThat(validator.optionalRoles().contains(role)).isEqualTo(validator.isOptional(role));
        }
    }

    @Test
    void optionalRoles_cannotBeModifiedByCallers() {
        assertThatThrownBy(() -> validator.optionalRoles().add(TOP))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(validator.isOptional(TOP)).isFalse();
    }
}
