package com.sewlect.identity.service;

import com.sewlect.identity.repository.UserStylePreferenceRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class UserStylePreferenceQueryService {

    private final UserStylePreferenceRepository userStylePreferenceRepository;

    public Set<UUID> findPreferredStyleTagIds(UUID userId) {
        return userStylePreferenceRepository.findAllByUserId(userId).stream()
                .map(pref -> pref.getStyleTag().getId())
                .collect(Collectors.toSet());
    }

    public List<String> findPreferredStyleTagNames(UUID userId) {
        return userStylePreferenceRepository.findStyleTagNamesByUserId(userId);
    }
}
