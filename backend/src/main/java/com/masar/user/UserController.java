package com.masar.user;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.masar.user.UserDtos.CreateUserRequest;
import com.masar.user.UserDtos.ResetPasswordRequest;
import com.masar.user.UserDtos.UpdateUserRequest;
import com.masar.user.UserDtos.UserResponse;

/**
 * User management endpoints. SecurityConfig restricts /api/users/** to ADMIN.
 * روابط إدارة المستخدمين. SecurityConfig يحصر /api/users/** على المدير.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserResponse> list() {
        return service.list();
    }

    // 201 Created is the correct HTTP status for a new resource.
    // 201 Created هو الرد الصحيح عند إنشاء مورد جديد.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(id, request);
    }

    // 204 No Content: success with nothing to return.
    // 204 No Content: نجاح بدون محتوى للرد.
    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(id, request.password());
    }
}
