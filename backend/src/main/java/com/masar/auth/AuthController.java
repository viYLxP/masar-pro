package com.masar.auth;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.masar.account.AccountDtos.RegisterRequest;
import com.masar.account.AccountService;
import com.masar.common.ApiException;
import com.masar.common.ErrorCode;
import com.masar.common.Phones;
import com.masar.user.AppUser;
import com.masar.user.AppUserRepository;
import com.masar.user.UserDtos.UserResponse;
import com.masar.user.UserService;

/**
 * Login, customer sign-up and "who am I" endpoints.
 * روابط تسجيل الدخول، وتسجيل العملاء، ومعرفة المستخدم الحالي.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final TokenService tokenService;
    private final UserService userService;
    private final AccountService accountService;

    public AuthController(AuthenticationManager authenticationManager, AppUserRepository users,
                          TokenService tokenService, UserService userService, AccountService accountService) {
        this.accountService = accountService;
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
        // Customers log in with their mobile number, typed any common way ("055...", "+966 55..."); it is turned
        // into the stored form, which is their username.
        // العملاء يدخلون برقم الجوال بأي طريقة كتابة معتادة ("055..." أو "+966 55...")؛ ويتحول للشكل المحفوظ،
        // وهو اسم المستخدم حقهم.
        String typed = request.username().trim();
        String phone = Phones.normalize(typed);
        String username = phone != null ? phone : typed;
        // Throws BadCredentialsException / DisabledException on failure, mapped to 401 BAD_CREDENTIALS.
        // يرمي BadCredentialsException أو DisabledException عند الفشل، وتتحول إلى 401 BAD_CREDENTIALS.
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, request.password()));
        AppUser user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_CREDENTIALS));
        return issue(user);
    }

    /**
     * Customer sign-up. Returns a token right away, so the customer is logged in after registering.
     * تسجيل العميل. يرجّع رمز دخول على طول، فالعميل يصير داخل بعد التسجيل مباشرة.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        return issue(accountService.register(request));
    }

    private LoginResponse issue(AppUser user) {
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
