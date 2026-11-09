package com.sewlect.identity.service;

import com.sewlect.common.exception.BadRequestException;
import com.sewlect.identity.dto.StyleTagResponse;
import com.sewlect.identity.dto.UserProfileResponse;
import com.sewlect.identity.dto.UserProfileUpdateRequest;
import com.sewlect.identity.enums.Sex;
import com.sewlect.common.taxonomy.entity.StyleTag;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.entity.UserStylePreference;
import com.sewlect.common.taxonomy.repository.StyleTagRepository;
import com.sewlect.identity.repository.UserProfileRepository;
import com.sewlect.identity.repository.UserRepository;
import com.sewlect.identity.repository.UserStylePreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserStylePreferenceRepository userStylePreferenceRepository;

    @Mock
    private StyleTagRepository styleTagRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProfileService profileService;

    @Test
    void getProfile_returnsCurrentFieldsAndStyleTags() {
        UUID userId = UUID.randomUUID();

        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .birthDate(LocalDate.of(1998, 4, 12))
                .sex(Sex.FEMALE)
                .heightCm(BigDecimal.valueOf(170))
                .weightKg(BigDecimal.valueOf(62))
                .footLengthCm(BigDecimal.valueOf(24.5))
                .averageBudgetPerOutfit(BigDecimal.valueOf(150))
                .currency("RON")
                .build();

        StyleTag minimalist = StyleTag.builder().id(UUID.randomUUID()).name("minimalist").build();
        StyleTag streetwear = StyleTag.builder().id(UUID.randomUUID()).name("streetwear").build();

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(userStylePreferenceRepository.findStyleTagsByUserId(userId)).thenReturn(List.of(minimalist, streetwear));

        UserProfileResponse response = profileService.getProfile(userId);

        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1998, 4, 12));
        assertThat(response.sex()).isEqualTo(Sex.FEMALE);
        assertThat(response.heightCm()).isEqualByComparingTo(BigDecimal.valueOf(170));
        assertThat(response.weightKg()).isEqualByComparingTo(BigDecimal.valueOf(62));
        assertThat(response.footLengthCm()).isEqualByComparingTo(BigDecimal.valueOf(24.5));
        assertThat(response.averageBudgetPerOutfit()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(response.currency()).isEqualTo("RON");
        assertThat(response.styleTags()).containsExactlyInAnyOrder(
                new StyleTagResponse(minimalist.getId(), "minimalist"),
                new StyleTagResponse(streetwear.getId(), "streetwear"));
    }

    @Test
    void updateProfile_onlyNonNullFieldsAreOverwritten() {
        UUID userId = UUID.randomUUID();

        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .birthDate(LocalDate.of(1998, 4, 12))
                .sex(Sex.FEMALE)
                .heightCm(BigDecimal.valueOf(170))
                .weightKg(BigDecimal.valueOf(62))
                .footLengthCm(BigDecimal.valueOf(24.5))
                .averageBudgetPerOutfit(BigDecimal.valueOf(150))
                .currency("RON")
                .build();

        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                null, null, BigDecimal.valueOf(175), null, null, null, "EUR");

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(userStylePreferenceRepository.findStyleTagsByUserId(userId)).thenReturn(List.of());

        UserProfileResponse response = profileService.updateProfile(userId, request);

        assertThat(response.heightCm()).isEqualByComparingTo(BigDecimal.valueOf(175));
        assertThat(response.currency()).isEqualTo("EUR");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1998, 4, 12));
        assertThat(response.sex()).isEqualTo(Sex.FEMALE);
        assertThat(response.weightKg()).isEqualByComparingTo(BigDecimal.valueOf(62));
        assertThat(response.footLengthCm()).isEqualByComparingTo(BigDecimal.valueOf(24.5));
        assertThat(response.averageBudgetPerOutfit()).isEqualByComparingTo(BigDecimal.valueOf(150));

        verify(userProfileRepository).save(profile);
    }

    @Test
    void updateProfile_allFieldsNull_profileUnchanged() {
        UUID userId = UUID.randomUUID();

        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .birthDate(LocalDate.of(1998, 4, 12))
                .sex(Sex.FEMALE)
                .heightCm(BigDecimal.valueOf(170))
                .weightKg(BigDecimal.valueOf(62))
                .footLengthCm(BigDecimal.valueOf(24.5))
                .averageBudgetPerOutfit(BigDecimal.valueOf(150))
                .currency("RON")
                .build();

        UserProfileUpdateRequest request =
                new UserProfileUpdateRequest(null, null, null, null, null, null, null);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(userStylePreferenceRepository.findStyleTagsByUserId(userId)).thenReturn(List.of());

        UserProfileResponse response = profileService.updateProfile(userId, request);

        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1998, 4, 12));
        assertThat(response.sex()).isEqualTo(Sex.FEMALE);
        assertThat(response.heightCm()).isEqualByComparingTo(BigDecimal.valueOf(170));
        assertThat(response.weightKg()).isEqualByComparingTo(BigDecimal.valueOf(62));
        assertThat(response.footLengthCm()).isEqualByComparingTo(BigDecimal.valueOf(24.5));
        assertThat(response.averageBudgetPerOutfit()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(response.currency()).isEqualTo("RON");
    }

    @Test
    void updateStylePreferences_validIds_replacesEntireSet() {
        UUID userId = UUID.randomUUID();
        UUID tag1Id = UUID.randomUUID();
        UUID tag2Id = UUID.randomUUID();

        StyleTag tag1 = StyleTag.builder().id(tag1Id).name("minimalist").build();
        StyleTag tag2 = StyleTag.builder().id(tag2Id).name("streetwear").build();
        User userRef = User.builder().id(userId).build();

        when(styleTagRepository.findAllById(Set.of(tag1Id, tag2Id))).thenReturn(List.of(tag1, tag2));
        when(userRepository.getReferenceById(userId)).thenReturn(userRef);

        List<StyleTagResponse> result = profileService.updateStylePreferences(userId, List.of(tag1Id, tag2Id));

        assertThat(result).containsExactlyInAnyOrder(
                new StyleTagResponse(tag1Id, "minimalist"),
                new StyleTagResponse(tag2Id, "streetwear"));

        InOrder inOrder = inOrder(userStylePreferenceRepository);
        inOrder.verify(userStylePreferenceRepository).deleteAllByUserId(userId);
        inOrder.verify(userStylePreferenceRepository).saveAll(any());

        ArgumentCaptor<List<UserStylePreference>> captor = ArgumentCaptor.captor();
        verify(userStylePreferenceRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).allSatisfy(pref -> assertThat(pref.getUser()).isEqualTo(userRef));
        assertThat(captor.getValue()).extracting(UserStylePreference::getStyleTag)
                .containsExactlyInAnyOrder(tag1, tag2);
    }

    @Test
    void updateStylePreferences_emptyList_clearsAllPreferences() {
        UUID userId = UUID.randomUUID();

        when(styleTagRepository.findAllById(Set.of())).thenReturn(List.of());
        when(userRepository.getReferenceById(userId)).thenReturn(User.builder().id(userId).build());

        List<StyleTagResponse> result = profileService.updateStylePreferences(userId, List.of());

        assertThat(result).isEmpty();
        verify(userStylePreferenceRepository).deleteAllByUserId(userId);
        verify(userStylePreferenceRepository).saveAll(List.of());
    }

    @Test
    void updateStylePreferences_unknownId_throwsBadRequestException() {
        UUID userId = UUID.randomUUID();
        UUID knownId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();

        StyleTag knownTag = StyleTag.builder().id(knownId).name("minimalist").build();

        when(styleTagRepository.findAllById(Set.of(knownId, unknownId))).thenReturn(List.of(knownTag));

        assertThatThrownBy(() -> profileService.updateStylePreferences(userId, List.of(knownId, unknownId)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(unknownId.toString());

        verify(userStylePreferenceRepository, never()).deleteAllByUserId(any());
        verify(userStylePreferenceRepository, never()).saveAll(any());
    }

    @Test
    void updateStylePreferences_bulkDeleteRunsBeforeInserts() {
        UUID userId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        StyleTag tag = StyleTag.builder().id(tagId).name("minimalist").build();

        when(styleTagRepository.findAllById(Set.of(tagId))).thenReturn(List.of(tag));
        when(userRepository.getReferenceById(userId)).thenReturn(User.builder().id(userId).build());

        profileService.updateStylePreferences(userId, List.of(tagId));

        InOrder inOrder = inOrder(userStylePreferenceRepository);
        inOrder.verify(userStylePreferenceRepository).deleteAllByUserId(userId);
        inOrder.verify(userStylePreferenceRepository).saveAll(any());
    }

    @Test
    void updateStylePreferences_duplicateIdsInRequest_deduplicatedBeforeValidation() {
        UUID userId = UUID.randomUUID();
        UUID tagAId = UUID.randomUUID();
        UUID tagBId = UUID.randomUUID();

        StyleTag tagA = StyleTag.builder().id(tagAId).name("minimalist").build();
        StyleTag tagB = StyleTag.builder().id(tagBId).name("streetwear").build();

        when(styleTagRepository.findAllById(Set.of(tagAId, tagBId))).thenReturn(List.of(tagA, tagB));
        when(userRepository.getReferenceById(userId)).thenReturn(User.builder().id(userId).build());

        // three raw ids, only two distinct — must not be rejected as "unknown ids"
        List<StyleTagResponse> result =
                profileService.updateStylePreferences(userId, List.of(tagAId, tagAId, tagBId));

        assertThat(result).containsExactlyInAnyOrder(
                new StyleTagResponse(tagAId, "minimalist"),
                new StyleTagResponse(tagBId, "streetwear"));
    }

    @Test
    void updateStylePreferences_allIdsUnknown_exceptionListsEveryMissingId() {
        UUID userId = UUID.randomUUID();
        UUID unknown1 = UUID.randomUUID();
        UUID unknown2 = UUID.randomUUID();

        when(styleTagRepository.findAllById(Set.of(unknown1, unknown2))).thenReturn(List.of());

        assertThatThrownBy(() -> profileService.updateStylePreferences(userId, List.of(unknown1, unknown2)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(unknown1.toString())
                .hasMessageContaining(unknown2.toString());

        verify(userStylePreferenceRepository, never()).deleteAllByUserId(any());
    }

    @Test
    void updateProfile_neverTouchesStylePreferences() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.builder().userId(userId).build();
        UserProfileUpdateRequest request =
                new UserProfileUpdateRequest(null, null, BigDecimal.valueOf(180), null, null, null, null);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(userStylePreferenceRepository.findStyleTagsByUserId(userId)).thenReturn(List.of());

        profileService.updateProfile(userId, request);

        verify(userStylePreferenceRepository, never()).deleteAllByUserId(any());
        verify(userStylePreferenceRepository, never()).saveAll(any());
        verify(styleTagRepository, never()).findAllById(any());
    }

    @Test
    void getProfile_noStylePreferencesYet_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.builder().userId(userId).build();

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(userStylePreferenceRepository.findStyleTagsByUserId(userId)).thenReturn(List.of());

        UserProfileResponse response = profileService.getProfile(userId);

        assertThat(response.styleTags()).isEmpty();
    }

    @Test
    void getProfile_profileRowMissing_throwsIllegalStateException() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getProfile(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(userId.toString());
    }

    @Test
    void updateProfile_sexNotYetSet_setsIt() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.builder().userId(userId).build();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));

        UserProfileResponse response = profileService.updateProfile(userId, sexOnly(Sex.FEMALE));

        assertThat(response.sex()).isEqualTo(Sex.FEMALE);
        verify(userProfileRepository).save(profile);
    }

    @Test
    void updateProfile_sexAlreadySetToADifferentValue_throwsBadRequestAndSavesNothing() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.builder().userId(userId).sex(Sex.FEMALE)
                .averageBudgetPerOutfit(BigDecimal.valueOf(150)).build();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                null, Sex.MALE, null, null, null, BigDecimal.valueOf(90), null);

        assertThatThrownBy(() -> profileService.updateProfile(userId, request))
                .isInstanceOf(BadRequestException.class);

        assertThat(profile.getSex()).isEqualTo(Sex.FEMALE);
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void updateProfile_sexAlreadySetToTheSameValue_isANoOpThatStillAppliesOtherFields() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.builder().userId(userId).sex(Sex.FEMALE)
                .averageBudgetPerOutfit(BigDecimal.valueOf(150)).build();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                null, Sex.FEMALE, null, null, null, BigDecimal.valueOf(90), null);

        UserProfileResponse response = profileService.updateProfile(userId, request);

        assertThat(response.sex()).isEqualTo(Sex.FEMALE);
        assertThat(response.averageBudgetPerOutfit()).isEqualByComparingTo(BigDecimal.valueOf(90));
        verify(userProfileRepository).save(profile);
    }

    @Test
    void updateProfile_sexOmitted_leavesTheStoredSexUnchanged() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.builder().userId(userId).sex(Sex.MALE).build();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                null, null, null, null, null, BigDecimal.valueOf(120), null);

        UserProfileResponse response = profileService.updateProfile(userId, request);

        assertThat(response.sex()).isEqualTo(Sex.MALE);
    }

    private UserProfileUpdateRequest sexOnly(Sex sex) {
        return new UserProfileUpdateRequest(null, sex, null, null, null, null, null);
    }
}
