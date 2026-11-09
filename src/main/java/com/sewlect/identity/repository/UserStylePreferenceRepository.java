package com.sewlect.identity.repository;

import com.sewlect.common.taxonomy.entity.StyleTag;
import com.sewlect.identity.entity.UserStylePreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserStylePreferenceRepository extends JpaRepository<UserStylePreference, UUID> {

    List<UserStylePreference> findAllByUserId(UUID userId);

    @Query("SELECT t.name FROM UserStylePreference p JOIN p.styleTag t WHERE p.user.id = :userId ORDER BY t.name")
    List<String> findStyleTagNamesByUserId(@Param("userId") UUID userId);

    @Query("SELECT t FROM UserStylePreference p JOIN p.styleTag t WHERE p.user.id = :userId ORDER BY t.name")
    List<StyleTag> findStyleTagsByUserId(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserStylePreference p WHERE p.user.id = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
