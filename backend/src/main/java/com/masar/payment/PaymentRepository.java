package com.masar.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.masar.request.ServiceRequest;

/**
 * Database access for payments.
 * الوصول لجدول المدفوعات.
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByRequestOrderByCreatedAtAsc(ServiceRequest request);

    Optional<Payment> findFirstByRequestAndStatus(ServiceRequest request, Payment.Status status);

    List<Payment> findByRequestAndStatus(ServiceRequest request, Payment.Status status);

    // The payment page needs the request, its service and quote in one go. / صفحة الدفع تحتاج الطلب وخدمته وعرضه مرة وحدة.
    @EntityGraph(attributePaths = {"request", "request.serviceType"})
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findWithRequest(@Param("id") UUID id);

    // Money actually received in a period (refunds excluded), for the dashboard.
    // الفلوس اللي وصلت فعلًا في فترة (بدون المسترجع)، للوحة التحكم.
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = com.masar.payment.Payment.Status.PAID and p.paidAt >= :from and p.paidAt < :to
            """)
    BigDecimal collected(@Param("from") Instant from, @Param("to") Instant to);
}
