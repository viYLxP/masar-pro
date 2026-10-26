import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { I18nService } from '../i18n/i18n.service';
import { AuthService } from './auth.service';

/**
 * Adds "Authorization: Bearer <token>" and "Accept-Language" to every API call, and logs the user out
 * when the server answers 401 (expired or invalid token).
 * يضيف "Authorization: Bearer <token>" و"Accept-Language" لكل طلب API، ويسجّل خروج المستخدم
 * لما يرد الخادم بـ 401 (رمز منتهي أو غير صالح).
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const i18n = inject(I18nService);
  const token = auth.token();
  const isLogin = request.url.endsWith('/auth/login');
  // The public site never sends the staff token: an expired token there would be refused with 401
  // even though the page needs no login, and would wrongly sign the staff member out.
  // الموقع العام ما يرسل رمز الموظف أبدًا: الرمز المنتهي هناك بينرفض بـ 401 مع إن الصفحة
  // ما تحتاج دخول، وبيطلّع الموظف من حسابه بالغلط.
  const isPublic = request.url.startsWith('/api/public/');

  const headers: Record<string, string> = { 'Accept-Language': i18n.lang() };
  // Never send a token to the login endpoint itself.
  // ما نرسل رمز لرابط تسجيل الدخول نفسه.
  if (token && !isLogin && !isPublic) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  return next(request.clone({ setHeaders: headers })).pipe(
    catchError((error: unknown) => {
      // A 401 on login just means wrong credentials, which the login form shows itself.
      // 401 في تسجيل الدخول يعني بيانات خاطئة فقط، ونموذج الدخول يعرضها بنفسه.
      if (error instanceof HttpErrorResponse && error.status === 401 && !isLogin && !isPublic) {
        auth.sessionExpired();
      }
      return throwError(() => error);
    }),
  );
};
