package com.masar.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masar.dashboard.DashboardDtos.DashboardResponse;

/**
 * The office dashboard. Technicians use "My jobs" instead.
 * لوحة تحكم المكتب. الفنيين يستخدمون "أعمالي" بدالها.
 */
@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardResponse get() {
        return service.build();
    }
}
