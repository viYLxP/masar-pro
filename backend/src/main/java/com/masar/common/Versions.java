package com.masar.common;

import java.util.Objects;

/**
 * Manual optimistic-lock check for edits made in a form.
 * فحص القفل المتفائل يدويًا للتعديلات اللي تصير من نموذج.
 *
 * <p>The client sends back the version it loaded. If the stored version is different, someone else saved
 * the record in the meantime, so we refuse instead of silently overwriting their change.</p>
 * <p>العميل يرجّع رقم النسخة اللي حمّلها. لو النسخة المحفوظة مختلفة يعني شخص ثاني حفظ السجل
 * في هذا الوقت، فنرفض بدل ما نمسح تعديله بصمت.</p>
 */
public final class Versions {

    private Versions() {
    }

    public static void check(Long current, Long fromClient) {
        if (!Objects.equals(current, fromClient)) {
            throw new ApiException(ErrorCode.CONCURRENT_UPDATE);
        }
    }
}
