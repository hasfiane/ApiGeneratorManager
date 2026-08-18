package com.api.generator.account.repo;

import com.api.generator.account.AppUser;
import com.api.generator.account.GenerationStatus;
import com.api.generator.account.GeneratedApi;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeneratedApiRepository extends JpaRepository<GeneratedApi, UUID> {
    List<GeneratedApi> findByUserOrderByCreatedAtDesc(AppUser user);

    Optional<GeneratedApi> findByIdAndUser_Id(UUID id, UUID userId);

    Optional<GeneratedApi> findByJobIdAndUser_EmailIgnoreCase(String jobId, String email);

    List<GeneratedApi> findTop20ByStatusAndJobIdIsNotNullOrderByCreatedAtAsc(GenerationStatus status);

    long countByStatus(GenerationStatus status);

    long countByCreatedAtAfter(Instant createdAt);

    @EntityGraph(attributePaths = "user")
    List<GeneratedApi> findByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    List<GeneratedApi> findByJobIdIn(Collection<String> jobIds);

    @EntityGraph(attributePaths = "user")
    @Query("""
            select g
              from GeneratedApi g
             where g.errorMessage is not null
               and g.errorMessage <> ''
             order by coalesce(g.finishedAt, g.createdAt) desc
            """)
    List<GeneratedApi> findRecentErroredWithUser(Pageable pageable);

    @Query("""
            select g.createdAt as createdAt, g.finishedAt as finishedAt
              from GeneratedApi g
             where g.createdAt is not null
               and g.finishedAt is not null
             order by g.finishedAt desc
            """)
    List<GenerationTiming> findRecentFinishedGenerationTimings(Pageable pageable);

    @Query("""
            select g.status as status, count(g) as total
              from GeneratedApi g
             group by g.status
            """)
    List<GenerationStatusCount> countByStatusGroup();

    interface GenerationStatusCount {
        GenerationStatus getStatus();
        long getTotal();
    }

    interface GenerationTiming {
        Instant getCreatedAt();
        Instant getFinishedAt();
    }
}
