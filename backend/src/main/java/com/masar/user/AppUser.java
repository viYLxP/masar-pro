package com.masar.user;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import com.masar.catalog.ServiceType;
import com.masar.common.City;

/**
 * A user account that can log in to the system.
 * حساب مستخدم يقدر يسجّل دخول للنظام.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    // Only the BCrypt hash is stored, never the plain password.
    // نخزّن ناتج BCrypt فقط، ولا نخزّن كلمة المرور الأصلية أبدًا.
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    // Stored as text ("ADMIN") so adding a new role later does not shift existing values.
    // نخزّنها كنص ("ADMIN") حتى لا تتغير القيم القديمة لو أضفنا دورًا جديدًا لاحقًا.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // Normalised Saudi mobile (see Phones). / جوال سعودي بالشكل الموحّد (شوف Phones).
    @Column(length = 12)
    private String phone;

    // Where a technician works; jobs are only offered to technicians of the same city.
    // مدينة شغل الفني؛ الأعمال تنعرض فقط على فنيين نفس المدينة.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private City city;

    // Services a technician is qualified for. Empty for other roles.
    // الخدمات اللي الفني مؤهل لها. فاضية للأدوار الثانية.
    @ManyToMany
    @JoinTable(name = "technician_skill",
            joinColumns = @JoinColumn(name = "technician_id"),
            inverseJoinColumns = @JoinColumn(name = "service_type_id"))
    private Set<ServiceType> skills = new HashSet<>();

    // Disabled users cannot log in, but their history (quotes, jobs) stays intact.
    // المستخدم الموقوف لا يقدر يدخل، لكن سجله (العروض والأعمال) يبقى محفوظًا.
    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
        // Required by JPA.
        // مطلوب من JPA.
    }

    public AppUser(String username, String fullName, String passwordHash, Role role) {
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public City getCity() {
        return city;
    }

    public void setCity(City city) {
        this.city = city;
    }

    public Set<ServiceType> getSkills() {
        return skills;
    }

    /**
     * Whether this account can be sent to do the given service in the given city.
     * هل هذا الحساب ينفع يتعيّن على هذي الخدمة في هذي المدينة.
     */
    public boolean canServe(ServiceType service, City requestCity) {
        return active && role == Role.TECHNICIAN && city == requestCity
                && skills.stream().anyMatch(s -> s.getId().equals(service.getId()));
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
