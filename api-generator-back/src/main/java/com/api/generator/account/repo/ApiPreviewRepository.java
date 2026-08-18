package com.api.generator.account.repo;

import com.api.generator.account.ApiPreview;
import com.api.generator.account.PreviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiPreviewRepository extends JpaRepository<ApiPreview, UUID> {
    Optional<ApiPreview> findByGeneratedApi_Id(UUID generatedApiId);

    List<ApiPreview> findByStatusIn(Collection<PreviewStatus> statuses);

    List<ApiPreview> findAllByGeneratedApi_User_Id(UUID userId);

    List<ApiPreview> findByGeneratedApi_User_IdAndStatusOrderByStoppedAtDesc(UUID userId, PreviewStatus status, Pageable pageable);

    long countByGeneratedApi_User_IdAndStatus(UUID userId, PreviewStatus status);

    long countByGeneratedApi_User_IdAndStatusIn(UUID userId, Collection<PreviewStatus> statuses);

    long countByStatus(PreviewStatus status);

    long countByStatusIn(Collection<PreviewStatus> statuses);

    long countByCreatedAtAfter(Instant createdAt);

    @EntityGraph(attributePaths = {"generatedApi", "generatedApi.user"})
    @Query("""
            select p
              from ApiPreview p
             where (p.errorMessage is not null and p.errorMessage <> '')
                or (p.errorHint is not null and p.errorHint <> '')
             order by coalesce(p.stoppedAt, p.startedAt, p.createdAt) desc
            """)
    List<ApiPreview> findRecentErroredWithApiAndUser(Pageable pageable);

    @Query("""
            select p.status as status, count(p) as total
              from ApiPreview p
             group by p.status
            """)
    List<PreviewStatusCount> countByStatusGroup();

    interface PreviewStatusCount {
        PreviewStatus getStatus();
        long getTotal();
    }
}
