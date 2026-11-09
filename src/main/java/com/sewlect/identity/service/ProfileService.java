package com.sewlect.identity.service;

import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.taxonomy.repository.StyleTagRepository;
import com.sewlect.identity.dto.StyleTagResponse;
import com.sewlect.identity.dto.UserProfileResponse;
import com.sewlect.identity.dto.UserProfileUpdateRequest;
import com.sewlect.common.taxonomy.entity.StyleTag;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.entity.UserStylePreference;
import com.sewlect.identity.enums.Sex;
import com.sewlect.identity.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class ProfileService {
   private final UserRepository userRepository;
   private final UserProfileRepository userProfileRepository;
   private final StyleTagRepository styleTagRepository;
   private final UserStylePreferenceRepository userStylePreferenceRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        UserProfile profile = loadProfile(userId);
        return toResponse(profile, currentStyleTags(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UserProfileUpdateRequest request) {
        UserProfile profile = loadProfile(userId);

        if (request.birthDate() != null) {
            profile.setBirthDate(request.birthDate());
        }
        if (request.sex() != null) {
            applySex(profile, request.sex());
        }
        if (request.heightCm() != null) {
            profile.setHeightCm(request.heightCm());
        }
        if (request.weightKg() != null) {
            profile.setWeightKg(request.weightKg());
        }
        if (request.footLengthCm() != null) {
            profile.setFootLengthCm(request.footLengthCm());
        }
        if (request.averageBudgetPerOutfit() != null) {
            profile.setAverageBudgetPerOutfit(request.averageBudgetPerOutfit());
        }
        if (request.currency() != null) {
            profile.setCurrency(request.currency());
        }

        userProfileRepository.save(profile);

        return toResponse(profile, currentStyleTags(userId));
    }

    @Transactional
    public List<StyleTagResponse> updateStylePreferences(UUID userId, List<UUID> styleTagIds) {
        Set<UUID> requestedIds = new LinkedHashSet<>(styleTagIds);

        List<StyleTag> foundTags = styleTagRepository.findAllById(requestedIds);
        if (foundTags.size() != requestedIds.size()) {
            Set<UUID> foundIds = foundTags.stream().map(StyleTag::getId).collect(Collectors.toSet());
            Set<UUID> missingIds = new LinkedHashSet<>(requestedIds);
            missingIds.removeAll(foundIds);
            throw new BadRequestException("Unknown style tag ids: " + missingIds);
        }

        userStylePreferenceRepository.deleteAllByUserId(userId);

        User userRef = userRepository.getReferenceById(userId);
        List<UserStylePreference> preferences = foundTags.stream()
                .map(tag -> toPreference(userRef, tag))
                .toList();
        userStylePreferenceRepository.saveAll(preferences);

        return foundTags.stream()
                .map(tag -> new StyleTagResponse(tag.getId(), tag.getName()))
                .toList();
    }

    private UserStylePreference toPreference(User userRef, StyleTag tag) {
        return UserStylePreference.builder()
                .user(userRef)
                .styleTag(tag)
                .build();
    }

    private void applySex(UserProfile profile, Sex requested) {
        if (profile.getSex() == null) {
            profile.setSex(requested);
            return;
        }
        if (profile.getSex() != requested) {
            throw new BadRequestException("Sex can only be set once and cannot be changed afterwards");
        }
    }

    private UserProfile loadProfile(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user has no profile row: " + userId));
    }

    private List<StyleTagResponse> currentStyleTags(UUID userId) {
        return userStylePreferenceRepository.findStyleTagsByUserId(userId).stream()
                .map(tag -> new StyleTagResponse(tag.getId(), tag.getName()))
                .toList();
    }

    private UserProfileResponse toResponse(UserProfile profile, List<StyleTagResponse> styleTags) {
        return new UserProfileResponse(
                profile.getBirthDate(),
                profile.getSex(),
                profile.getHeightCm(),
                profile.getWeightKg(),
                profile.getFootLengthCm(),
                profile.getAverageBudgetPerOutfit(),
                profile.getCurrency(),
                styleTags
        );
    }


}
