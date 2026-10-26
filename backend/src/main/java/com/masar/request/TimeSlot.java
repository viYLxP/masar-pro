package com.masar.request;

import java.time.LocalTime;

/**
 * The part of the day a customer prefers. Together the slots cover the working hours (08:00 to 20:00).
 * الفترة اللي يفضلها العميل من اليوم. الفترات مع بعض تغطي ساعات العمل (من 8 الصبح لين 8 الليل).
 */
public enum TimeSlot {
    MORNING(LocalTime.of(8, 0), LocalTime.of(12, 0)),
    AFTERNOON(LocalTime.of(12, 0), LocalTime.of(16, 0)),
    EVENING(LocalTime.of(16, 0), LocalTime.of(20, 0));

    public static final LocalTime DAY_START = LocalTime.of(8, 0);
    public static final LocalTime DAY_END = LocalTime.of(20, 0);

    private final LocalTime start;
    private final LocalTime end;

    TimeSlot(LocalTime start, LocalTime end) {
        this.start = start;
        this.end = end;
    }

    public LocalTime start() {
        return start;
    }

    public LocalTime end() {
        return end;
    }
}
