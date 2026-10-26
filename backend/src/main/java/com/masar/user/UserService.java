package com.masar.user;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.masar.catalog.ServiceType;
import com.masar.catalog.ServiceTypeRepository;
import com.masar.common.ApiException;
import com.masar.common.City;
import com.masar.common.CurrentUser;
import com.masar.common.ErrorCode;
import com.masar.common.Phones;
import com.masar.common.Text;
import com.masar.user.UserDtos.CreateUserRequest;
import com.masar.user.UserDtos.UpdateUserRequest;
import com.masar.user.UserDtos.UserResponse;

/**
 * Business rules for managing user accounts (admin only).
 * قواعد إدارة حسابات المستخدمين (للمدير فقط).
 */
@Service
public class UserService {

    private final AppUserRepository users;
    private final ServiceTypeRepository services;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository users, ServiceTypeRepository services, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.services = services;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll(Sort.by("username")).stream().map(UserResponse::from).toList();
    }

    /**
     * Active technicians, optionally only those who can do a service in a city (for the booking dialog).
     * الفنيين النشطين، واختياريًا فقط اللي يقدرون يسوون خدمة في مدينة (لنافذة الحجز).
     */
    @Transactional(readOnly = true)
    public List<UserResponse> technicians(City city, Long serviceTypeId) {
        return users.findByRoleAndActiveTrueOrderByFullName(Role.TECHNICIAN).stream()
                .filter(u -> city == null || u.getCity() == city)
                .filter(u -> serviceTypeId == null
                        || u.getSkills().stream().anyMatch(s -> s.getId().equals(serviceTypeId)))
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse current() {
        return byUsername(CurrentUser.username());
    }

    @Transactional(readOnly = true)
    public UserResponse byUsername(String username) {
        return UserResponse.from(findByUsername(username));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        // Check first to return a clear error code instead of a raw database constraint error.
        // نتحقق أولًا حتى نرجّع رمز خطأ واضح بدل خطأ قيد قاعدة البيانات.
        if (users.existsByUsernameIgnoreCase(request.username())) {
            throw new ApiException(ErrorCode.USERNAME_EXISTS);
        }
        AppUser user = new AppUser(request.username().trim(), request.fullName().trim(),
                passwordEncoder.encode(request.password()), request.role());
        applyProfile(user, request.role(), request.phone(), request.city(), request.skillIds());
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        AppUser user = users.findById(id).orElseThrow(() -> ApiException.notFound("user", id));
        // An admin cannot disable or demote themselves; otherwise they could lock everyone out.
        // المدير لا يقدر يوقف نفسه أو ينزّل صلاحيته، وإلا ممكن يقفل النظام على الجميع.
        boolean isSelf = user.getUsername().equalsIgnoreCase(CurrentUser.username());
        if (isSelf && (!request.active() || request.role() != user.getRole())) {
            throw new ApiException(ErrorCode.CANNOT_MODIFY_SELF);
        }
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setActive(request.active());
        applyProfile(user, request.role(), request.phone(), request.city(), request.skillIds());
        // No save() call needed: the entity is managed, and JPA writes changes when the transaction commits.
        // لا نحتاج save(): الكيان مُدار، وJPA يحفظ التغييرات تلقائيًا عند نهاية المعاملة.
        return UserResponse.from(user);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        AppUser user = users.findById(id).orElseThrow(() -> ApiException.notFound("user", id));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    /**
     * Phone, city and skills. A technician must have a city and at least one skill, or no job could ever match them.
     * الجوال والمدينة والمهارات. الفني لازم له مدينة ومهارة وحدة على الأقل، وإلا ما بيتطابق مع أي شغل.
     */
    private void applyProfile(AppUser user, Role role, String phone, City city, Set<Long> skillIds) {
        String cleanPhone = Text.blankToNull(phone);
        user.setPhone(cleanPhone == null ? null : Phones.require(cleanPhone, "phone"));
        user.setCity(city);
        Set<Long> ids = skillIds == null ? Set.of() : skillIds;
        if (role == Role.TECHNICIAN) {
            if (city == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("city", "NotNull")));
            }
            if (ids.isEmpty()) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("skillIds", "NotEmpty")));
            }
        }
        List<ServiceType> found = role == Role.TECHNICIAN ? services.findAllById(ids) : List.of();
        // An unknown id would otherwise be silently dropped. / الرقم غير الموجود كان بينحذف بصمت.
        if (found.size() != (role == Role.TECHNICIAN ? ids.size() : 0)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("skillIds", "NotFound")));
        }
        user.getSkills().clear();
        user.getSkills().addAll(new HashSet<>(found));
    }

    private AppUser findByUsername(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> ApiException.notFound("user", username));
    }
}
