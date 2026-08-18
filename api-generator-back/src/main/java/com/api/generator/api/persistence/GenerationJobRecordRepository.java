package com.api.generator.api.persistence;

import com.api.generator.api.service.JobStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface GenerationJobRecordRepository extends JpaRepository<GenerationJobRecord, String> {

    List<GenerationJobRecord> findAllByStatusIn(Collection<JobStatus> statuses);

    List<GenerationJobRecord> findAllByCreatedAtBeforeAndStatusIn(Instant cutoff, Collection<JobStatus> statuses);

    List<GenerationJobRecord> findTop10ByStatusOrderByCreatedAtAsc(JobStatus status);

    long countByStatusIn(Collection<JobStatus> statuses);

    long countByCreatedAtAfter(Instant createdAt);

    @Query("""
            select r.status as status, count(r) as total
              from GenerationJobRecord r
             group by r.status
            """)
    List<JobStatusCount> countByStatusGroup();

    @Modifying
    @Query("""
            update GenerationJobRecord r
               set r.status = :nextStatus,
                   r.updatedAt = :updatedAt
             where r.jobId = :jobId
               and r.status = :expectedStatus
            """)
    int updateStatusIfCurrent(@Param("jobId") String jobId,
                              @Param("expectedStatus") JobStatus expectedStatus,
                              @Param("nextStatus") JobStatus nextStatus,
                              @Param("updatedAt") Instant updatedAt);

    interface JobStatusCount {
        JobStatus getStatus();
        long getTotal();
    }
}
