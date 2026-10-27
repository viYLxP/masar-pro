package com.masar.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.masar.catalog.ServiceType;
import com.masar.user.AppUser;

/**
 * Database access for requests. Lists load the service and technician in the same query (no N+1).
 * الوصول لجدول الطلبات. القوائم تحمّل الخدمة والفني بنفس الاستعلام (بدون مشكلة N+1).
 */
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long>,
        JpaSpecificationExecutor<ServiceRequest> {

    long countByServiceType(ServiceType serviceType);

    boolean existsByReference(String reference);

    Optional<ServiceRequest> findByReference(String reference);

    // The customer's "My requests", newest first. / "طلباتي" للعميل، الأحدث أول.
    @EntityGraph(attributePaths = {"serviceType", "quote"})
    List<ServiceRequest> findByCustomerOrderByCreatedAtDesc(AppUser customer);

    /**
     * Jobs of one technician that overlap [start, end). Two windows overlap when each starts before the other ends,
     * so a visit ending at 10:00 and one starting at 10:00 do not clash.
     * أعمال فني واحد اللي تتداخل مع [start, end). الفترتين يتداخلون لو كل وحدة تبدأ قبل ما تخلص الثانية،
     * فزيارة تخلص 10:00 وزيارة تبدأ 10:00 ما يتعارضون.
     */
    @Query("""
            select r from ServiceRequest r
            where r.technician.id = :technicianId and r.status in :booked and r.id <> :excludeId
              and r.scheduledStart < :end and r.scheduledEnd > :start
            """)
    List<ServiceRequest> overlapping(@Param("technicianId") Long technicianId,
                                     @Param("booked") Collection<RequestStatus> booked,
                                     @Param("excludeId") Long excludeId,
                                     @Param("start") Instant start, @Param("end") Instant end);

    // Everything on the calendar in [from, to), for the day board. / كل شي في الجدول بين from و to، للوحة اليوم.
    @EntityGraph(attributePaths = {"serviceType", "technician"})
    @Query("""
            select r from ServiceRequest r
            where r.technician is not null and r.status in :statuses
              and r.scheduledStart >= :from and r.scheduledStart < :to
            order by r.scheduledStart
            """)
    List<ServiceRequest> onCalendar(@Param("statuses") Collection<RequestStatus> statuses,
                                    @Param("from") Instant from, @Param("to") Instant to);

    /**
     * A technician's open jobs, plus the ones finished since a given time (so "done today" stays visible).
     * أعمال الفني المفتوحة، مع اللي خلصت من وقت معيّن (حتى يبقى "خلصت اليوم" ظاهر).
     */
    @EntityGraph(attributePaths = {"serviceType"})
    @Query("""
            select r from ServiceRequest r
            where r.technician.id = :technicianId
              and (r.status in :open or (r.status = :done and r.completedAt >= :since))
            order by r.scheduledStart
            """)
    List<ServiceRequest> jobsOf(@Param("technicianId") Long technicianId,
                                @Param("open") Collection<RequestStatus> open,
                                @Param("done") RequestStatus done, @Param("since") Instant since);

    // ----- Dashboard / لوحة التحكم -----

    long countByStatusAndPaymentStatus(RequestStatus status, PaymentStatus paymentStatus);

    @Query("select r.status, count(r) from ServiceRequest r group by r.status")
    List<Object[]> countByStatus();

    @Query("""
            select coalesce(sum(q.total), 0) from ServiceRequest r join r.quote q
            where r.status = :done and r.completedAt >= :from and r.completedAt < :to
            """)
    BigDecimal revenue(@Param("done") RequestStatus done, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(r) from ServiceRequest r
            where r.status = :done and r.completedAt >= :from and r.completedAt < :to
            """)
    long completedBetween(@Param("done") RequestStatus done, @Param("from") Instant from, @Param("to") Instant to);

    // Sent quotes that run out within a few days, so the office can remind the customer.
    // العروض المرسلة اللي بتنتهي خلال أيام، حتى المكتب يذكّر العميل.
    @EntityGraph(attributePaths = {"serviceType", "quote"})
    @Query("""
            select r from ServiceRequest r join r.quote q
            where r.status = :quoted and q.validUntil <= :limit
            order by q.validUntil, r.id
            """)
    List<ServiceRequest> expiringQuotes(@Param("quoted") RequestStatus quoted, @Param("limit") LocalDate limit,
                                        Pageable page);

    @EntityGraph(attributePaths = {"serviceType"})
    List<ServiceRequest> findByStatusOrderByCreatedAtAsc(RequestStatus status, Pageable page);

    @Query("""
            select r.serviceType.id, count(r) from ServiceRequest r
            where r.createdAt >= :since group by r.serviceType.id order by count(r) desc
            """)
    List<Object[]> countByServiceSince(@Param("since") Instant since);
}
