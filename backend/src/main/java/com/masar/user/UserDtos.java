package com.masar.user;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.masar.catalog.ServiceTypeDtos.ServiceRef;
import com.masar.common.City;

/**
 * Request/response shapes for user management. Records are immutable, which suits DTOs.
 * أشكال الطلب والرد لإدارة المستخدمين. الـ records غير قابلة للتعديل وهذا مناسب للـ DTOs.
 */
public final class UserDtos {

    private UserDtos() {
    }

    // Public view of a user: never includes the password hash.
    // العرض العام للمستخدم: لا يحتوي أبدًا على ناتج تشفير كلمة المرور.
    public record UserResponse(Long id, String username, String fullName, Role role, String phone, City city,
                               List<ServiceRef> skills, boolean active, Instant createdAt) {

        public static UserResponse from(AppUser user) {
            List<ServiceRef> skills = user.getSkills().stream()
                    .sorted(Comparator.comparing(s -> s.getSortOrder()))
                    .map(ServiceRef::of).toList();
            return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getRole(),
                    user.getPhone(), user.getCity(), skills, user.isActive(), user.getCreatedAt());
        }
    }

    // Username: letters, digits, dot, dash, underscore only, so it is safe in URLs and logs.
    // اسم المستخدم: حروف وأرقام ونقطة وشرطة وشرطة سفلية فقط، حتى يكون آمنًا في الروابط والسجلات.
    // Password: at least 8 chars; BCrypt ignores anything past 72 bytes, so we cap it there.
    // كلمة المرور: 8 أحرف على الأقل، وBCrypt يتجاهل ما بعد 72 بايت فنحدّها عندها.
    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Role role,
            @Size(max = 20) String phone,
            City city,
            Set<Long> skillIds) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotNull Role role,
            boolean active,
            @Size(max = 20) String phone,
            City city,
            Set<Long> skillIds) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 72) String password) {
    }
}
