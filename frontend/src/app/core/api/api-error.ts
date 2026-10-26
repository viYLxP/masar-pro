import { HttpErrorResponse } from '@angular/common/http';

import { I18nService } from '../i18n/i18n.service';

/**
 * Normalised error read from the server's ProblemDetail body.
 * خطأ موحّد مقروء من جسم ProblemDetail القادم من الخادم.
 */
export interface ApiError {
  status: number;
  code: string;
  // Field name → validation rule (e.g. { phone: 'Phone' }).
  // اسم الحقل ← قاعدة التحقق (مثل { phone: 'Phone' }).
  fields: Record<string, string>;
  // Extra details such as { requests } for IN_USE or { status } for INVALID_STATUS.
  // تفاصيل إضافية مثل { requests } لخطأ IN_USE أو { status } لخطأ INVALID_STATUS.
  details: Record<string, unknown>;
}

/**
 * Converts any HttpClient error into an ApiError.
 * يحوّل أي خطأ من HttpClient إلى ApiError.
 */
export function toApiError(error: unknown): ApiError {
  if (error instanceof HttpErrorResponse) {
    // status 0 means the request never reached the server (offline, CORS, server down).
    // الحالة 0 تعني إن الطلب ما وصل للخادم أصلًا (بدون إنترنت، CORS، أو الخادم طافي).
    if (error.status === 0) {
      return { status: 0, code: 'NETWORK', fields: {}, details: {} };
    }
    const body = (error.error ?? {}) as Record<string, unknown>;
    return {
      status: error.status,
      code: typeof body['code'] === 'string' ? body['code'] : 'UNKNOWN',
      fields: (body['fields'] as Record<string, string>) ?? {},
      details: body,
    };
  }
  return { status: -1, code: 'UNKNOWN', fields: {}, details: {} };
}

/**
 * Builds the user-facing message for an error in the current language.
 * يبني رسالة الخطأ للمستخدم باللغة الحالية.
 */
export function errorMessage(i18n: I18nService, error: ApiError): string {
  const params: Record<string, string | number> = {};
  for (const [key, value] of Object.entries(error.details)) {
    if (typeof value === 'string' || typeof value === 'number') {
      params[key] = value;
    }
  }
  // Translate a request status inside the message too ("…while the request is Completed").
  // نترجم حالة الطلب داخل الرسالة أيضًا ("…والطلب في حالة مكتمل").
  if (typeof params['status'] === 'string') {
    params['status'] = i18n.t(`status.${params['status']}`);
  }
  // TECHNICIAN_BUSY carries the clashing job's UTC times; show them as Riyadh clock times.
  // TECHNICIAN_BUSY يحمل أوقات الموعد المتعارض بتوقيت UTC؛ نعرضها كساعة بتوقيت الرياض.
  for (const key of ['start', 'end']) {
    if (typeof params[key] === 'string') {
      params[key] = new Intl.DateTimeFormat(i18n.locale(), {
        hour: 'numeric',
        minute: '2-digit',
        timeZone: 'Asia/Riyadh',
      }).format(new Date(params[key]));
    }
  }
  // RATE_LIMITED says how many seconds to wait; people think in minutes (always at least 1).
  // RATE_LIMITED يقول كم ثانية ننتظر؛ والناس تفكر بالدقائق (دقيقة على الأقل دائمًا).
  if (typeof params['retryAfterSeconds'] === 'number') {
    params['minutes'] = Math.max(1, Math.ceil(params['retryAfterSeconds'] / 60));
  }
  const known = i18n.t(`errors.${error.code}`, params);
  return known === `errors.${error.code}` ? i18n.t('errors.UNKNOWN') : known;
}
