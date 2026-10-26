package com.masar.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.masar.common.ApiException;
import com.masar.common.ErrorCode;

/**
 * Who is calling. Services use it to apply the "technicians only touch their own jobs" rule.
 * مين المستخدم الحالي. الخدمات تستخدمه لتطبيق قاعدة "الفني يتعامل بس مع أعماله".
 */
@Service
public class CurrentUserService {

    private final AppUserRepository users;

    public CurrentUserService(AppUserRepository users) {
        this.users = users;
    }

    /**
     * The logged-in user's account. The JWT subject is the username.
     * حساب المستخدم المسجّل. حقل subject في JWT هو اسم المستخدم.
     */
    public AppUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return users.findByUsernameIgnoreCase(auth.getName())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));
    }

    /**
     * True for admins and dispatchers, who can see and handle every request.
     * صح للمدير والموزّع، لأنهم يشوفون ويتعاملون مع كل الطلبات.
     */
    public static boolean isOffice(AppUser user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.DISPATCHER;
    }
}
