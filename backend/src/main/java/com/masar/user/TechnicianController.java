package com.masar.user;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.masar.common.City;
import com.masar.user.UserDtos.UserResponse;

/**
 * Active technicians for the dispatcher's pickers. Separate from /api/users, which only admins may open.
 * الفنيين النشطين لقوائم اختيار الموزّع. منفصل عن /api/users اللي للمدير فقط.
 */
@RestController
@RequestMapping("/api/technicians")
@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
public class TechnicianController {

    private final UserService service;

    public TechnicianController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) City city,
                                   @RequestParam(required = false) Long serviceTypeId) {
        return service.technicians(city, serviceTypeId);
    }
}
