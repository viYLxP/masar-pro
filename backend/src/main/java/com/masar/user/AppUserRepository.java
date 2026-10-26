package com.masar.user;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Database access for users. Spring Data generates the SQL from the method names.
 * الوصول لجدول المستخدمين. Spring Data يولّد استعلامات SQL من أسماء الدوال.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // Case-insensitive so "Admin" and "admin" are treated as the same account.
    // بدون حساسية لحالة الأحرف حتى يُعامل "Admin" و "admin" كحساب واحد.
    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countByRoleAndActiveTrue(Role role);

    // Loads the skills in the same query, so listing technicians is one query instead of one per technician.
    // يحمّل المهارات بنفس الاستعلام، فعرض الفنيين يصير استعلام واحد بدل استعلام لكل فني.
    @EntityGraph(attributePaths = "skills")
    List<AppUser> findByRoleAndActiveTrueOrderByFullName(Role role);

    /**
     * Locks the technician's row until the transaction ends. Two dispatchers booking the same technician at the same
     * moment then run one after the other, so the overlap check of the second one sees the first booking.
     * يقفل صف الفني لين تنتهي المعاملة. لو موزّعين حجزوا نفس الفني بنفس اللحظة، يمشون واحد بعد الثاني،
     * ففحص التعارض عند الثاني يشوف حجز الأول.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> lockById(@Param("id") Long id);
}
