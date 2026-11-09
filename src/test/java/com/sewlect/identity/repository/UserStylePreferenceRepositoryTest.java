package com.sewlect.identity.repository;

import com.sewlect.common.persistence.config.JpaAuditingConfig;
import com.sewlect.common.taxonomy.entity.StyleTag;
import com.sewlect.common.taxonomy.repository.StyleTagRepository;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.entity.UserStylePreference;
import com.sewlect.identity.enums.Role;
import com.sewlect.support.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class UserStylePreferenceRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserStylePreferenceRepository userStylePreferenceRepository;

    @Autowired
    private StyleTagRepository styleTagRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findStyleTagNamesByUserId_returnsOnlyThatUsersTagNamesAlphabetically() {
        User user = persistUser();
        User otherUser = persistUser();
        prefer(user, "streetwear");
        prefer(user, "classic");
        prefer(user, "minimalist");
        prefer(otherUser, "bohemian");
        entityManager.flush();
        entityManager.clear();

        assertThat(userStylePreferenceRepository.findStyleTagNamesByUserId(user.getId()))
                .containsExactly("classic", "minimalist", "streetwear");
    }

    @Test
    void findStyleTagNamesByUserId_noPreferences_returnsEmpty() {
        User user = persistUser();
        entityManager.flush();
        entityManager.clear();

        assertThat(userStylePreferenceRepository.findStyleTagNamesByUserId(user.getId())).isEmpty();
    }

    private void prefer(User user, String tagName) {
        StyleTag tag = styleTagRepository.findAllByNameIn(List.of(tagName)).get(0);
        userStylePreferenceRepository.save(UserStylePreference.builder().user(user).styleTag(tag).build());
    }

    private User persistUser() {
        return userRepository.save(User.builder()
                .email("prefs-" + UUID.randomUUID() + "@example.com")
                .passwordHash("{noop}irrelevant")
                .role(Role.USER)
                .build());
    }
}
