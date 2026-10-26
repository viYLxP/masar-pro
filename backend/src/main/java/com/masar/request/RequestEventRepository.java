package com.masar.request;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Database access for the status history.
 * الوصول لسجل تغييرات الحالة.
 */
public interface RequestEventRepository extends JpaRepository<RequestEvent, Long> {

    List<RequestEvent> findByRequestOrderByCreatedAtAscIdAsc(ServiceRequest request);

    // How many requests reached each of the given statuses since a time (e.g. accepted vs rejected quotes).
    // كم طلب وصل لكل حالة من الحالات المعطاة من وقت معيّن (مثل العروض المقبولة مقابل المرفوضة).
    @Query("""
            select e.toStatus, count(e) from RequestEvent e
            where e.toStatus in :statuses and e.createdAt >= :since group by e.toStatus
            """)
    List<Object[]> countReached(@Param("statuses") Collection<RequestStatus> statuses, @Param("since") Instant since);
}
