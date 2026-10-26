package com.masar.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.masar.config.AppProperties;

/**
 * Dates and times in the business time zone (Asia/Riyadh), wherever the server runs.
 * التواريخ والأوقات بتوقيت النشاط (الرياض)، مهما كان مكان الخادم.
 *
 * <p>The server and database store UTC instants. "Today", working hours and quote expiry are about the local day,
 * so they always go through this class. A server in UTC would otherwise think it is still yesterday until 03:00.</p>
 * <p>الخادم وقاعدة البيانات يحفظون أوقات UTC. أما "اليوم" وساعات العمل وانتهاء العروض فهي حسب اليوم المحلي،
 * فتمر دائمًا عبر هذا الكلاس. وإلا الخادم اللي على UTC بيحسب إنه لسا أمس لين الساعة 3 الفجر.</p>
 */
@Component
public class BusinessTime {

    private final ZoneId zone;

    public BusinessTime(AppProperties properties) {
        this.zone = properties.timeZone();
    }

    public ZoneId zone() {
        return zone;
    }

    public Instant now() {
        return Instant.now();
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public Instant at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(zone).toInstant();
    }

    public Instant startOf(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    public LocalDate dateOf(Instant instant) {
        return instant.atZone(zone).toLocalDate();
    }

    public LocalTime timeOf(Instant instant) {
        return instant.atZone(zone).toLocalTime();
    }
}
