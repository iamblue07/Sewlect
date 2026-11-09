package com.sewlect.stylist.support;

import com.sewlect.catalog.service.CatalogVocabularyQueryService;
import com.sewlect.common.taxonomy.support.GarmentRoleResolver;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.service.UserProfileQueryService;
import com.sewlect.identity.service.UserStylePreferenceQueryService;
import com.sewlect.stylist.domain.StylistPromptContext;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@AllArgsConstructor
public class StylistPromptContextFactory {

    private final UserProfileQueryService userProfileQueryService;
    private final UserStylePreferenceQueryService userStylePreferenceQueryService;
    private final GarmentRoleResolver garmentRoleResolver;
    private final CatalogVocabularyQueryService catalogVocabularyQueryService;

    public StylistPromptContext build(UUID userId) {
        UserProfile profile = userProfileQueryService.getById(userId);
        return new StylistPromptContext(
                profile.getSex(),
                profile.getAverageBudgetPerOutfit(),
                userStylePreferenceQueryService.findPreferredStyleTagNames(userId),
                garmentRoleResolver.articleTypesByRole(),
                catalogVocabularyQueryService.findOccasions());
    }
}
