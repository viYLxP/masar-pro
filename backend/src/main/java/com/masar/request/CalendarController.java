package com.masar.request;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.masar.common.City;
import com.masar.request.RequestDtos.DayBoard;
import com.masar.request.RequestDtos.JobCard;

/**
 * The calendar for the office and the job list for technicians.
 * الجدول للمكتب وقائمة الأعمال للفنيين.
 */
@RestController
public class CalendarController {

    private final SchedulingService scheduling;

    public CalendarController(SchedulingService scheduling) {
        this.scheduling = scheduling;
    }

    @GetMapping("/api/schedule")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
    public DayBoard day(@RequestParam LocalDate date,
                        @RequestParam(required = false) City city,
                        @RequestParam(required = false) Long serviceTypeId) {
        return scheduling.day(date, city, serviceTypeId);
    }

    @GetMapping("/api/jobs/mine")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public List<JobCard> myJobs() {
        return scheduling.myJobs();
    }
}
