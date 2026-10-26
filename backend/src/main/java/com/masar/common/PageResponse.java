package com.masar.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * A stable JSON shape for paginated lists.
 * شكل JSON ثابت لنتائج القوائم المقسّمة إلى صفحات.
 *
 * <p>We do not return Spring's {@code Page} directly because its serialized shape is an internal
 * detail that may change between versions and break the frontend.</p>
 * <p>لا نرجّع {@code Page} من Spring مباشرة لأن شكله الداخلي قد يتغير بين الإصدارات ويكسر الواجهة.</p>
 *
 * @param content       items of the current page / عناصر الصفحة الحالية
 * @param page          zero-based page index / رقم الصفحة ويبدأ من صفر
 * @param size          page size / حجم الصفحة
 * @param totalElements total matches / العدد الكلي للنتائج
 * @param totalPages    total pages / عدد الصفحات الكلي
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    /**
     * Maps a page of entities to a page of DTOs using the given mapper.
     * يحوّل صفحة كيانات إلى صفحة DTO باستخدام دالة التحويل المعطاة.
     */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
