package com.masar.user;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads a user for Spring Security during login.
 * يحمّل بيانات المستخدم لـ Spring Security أثناء تسجيل الدخول.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUser user = users.findByUsernameIgnoreCase(username)
                // Spring turns this into BadCredentialsException, so the response does not reveal that the user is missing.
                // Spring يحوّل هذا إلى BadCredentialsException، فلا يكشف الرد أن المستخدم غير موجود.
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                // A disabled account is rejected by Spring with DisabledException.
                // الحساب الموقوف يرفضه Spring بـ DisabledException.
                .disabled(!user.isActive())
                .build();
    }
}
