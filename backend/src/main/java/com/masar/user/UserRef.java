package com.masar.user;

/**
 * The short form of a user shown on other records (for example the technician on a job).
 * الشكل المختصر للمستخدم اللي يظهر على السجلات الثانية (مثل الفني على الشغل).
 */
public record UserRef(Long id, String fullName, String phone) {

    public static UserRef of(AppUser user) {
        return user == null ? null : new UserRef(user.getId(), user.getFullName(), user.getPhone());
    }
}
