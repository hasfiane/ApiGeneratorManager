package com.api.generator.account.repo;

import com.api.generator.account.ApiProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApiProjectRepository extends JpaRepository<ApiProject, UUID> {
    List<ApiProject> findByOwner_IdOrderByCreatedAtDesc(UUID ownerId);

    java.util.Optional<ApiProject> findByJobIdAndOwner_EmailIgnoreCase(String jobId, String email);

}
