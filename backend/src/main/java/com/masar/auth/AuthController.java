package com.masar.auth;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masar.common.ApiException;
import com.masar.common.ErrorCode;
import com.masar.user.AppUser;
import com.masar.user.AppUserRepository;
import com.masar.user.UserDtos.UserResponse;
import com.masar.user.UserService;

/**
 * Login and "who am I" endpoints.
 * روابط تسجيل الدخول ومعرفة المستخدم الحالي.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final TokenService tokenService;
    private final UserService userService;

    public AuthController(AuthenticationManager authenticationManager, AppUserRepository users,
                          TokenService tokenService, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.tokenService = tokenService;
        this.userService = userService;
    }

    public record LoginRequest(@NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 72) String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserResponse user) {
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        // Throws BadCredentialsException / DisabledException on failure, mapped to 401 BAD_CREDENTIALS.
        // يرمي BadCredentialsException أو DisabledException عند الفشل، وتتحول إلى 401 BAD_CREDENTIALS.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));
        AppUser user = users.findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_CREDENTIALS));
        TokenService.IssuedToken token = tokenService.issue(user);
        // Built inside a transaction by the service, because the response includes the technician's skills (lazy).
        // يُبنى داخل معاملة في الخدمة، لأن الرد فيه مهارات الفني (تُحمّل عند الطلب).
        return new LoginResponse(token.value(), token.expiresAt(), userService.byUsername(user.getUsername()));
    }

    // The UI calls this on page reload to restore the session from a saved token.
    // الواجهة تستدعيه عند إعادة تحميل الصفحة لاسترجاع الجلسة من الرمز المحفوظ.
    @GetMapping("/me")
    public UserResponse me() {
        return userService.current();
    }
}
