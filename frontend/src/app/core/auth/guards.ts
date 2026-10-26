import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

// Guards only hide pages in the UI; the real protection is the server's role check on every endpoint.
// الحراس يخفون الصفحات في الواجهة فقط؛ والحماية الحقيقية هي فحص الدور في الخادم على كل رابط.

/**
 * Lets the route open only for a logged-in user; otherwise redirects to /login.
 * يسمح بفتح الصفحة للمستخدم المسجّل فقط، وإلا يحوّله لصفحة الدخول.
 */
export const authGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return (await auth.restore()) ? true : router.createUrlTree(['/login']);
};

/**
 * Builds a guard that allows one group of roles and sends everyone else to their own home page.
 * يبني حارس يسمح لمجموعة أدوار، ويرجّع الباقين لصفحتهم الرئيسية.
 */
function roleGuard(allowed: (auth: AuthService) => boolean): CanActivateFn {
  return async () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!(await auth.restore())) return router.createUrlTree(['/login']);
    return allowed(auth) ? true : router.parseUrl(auth.home());
  };
}

export const adminGuard = roleGuard((auth) => auth.isAdmin());
export const officeGuard = roleGuard((auth) => auth.isOffice());
export const technicianGuard = roleGuard((auth) => auth.isTechnician());

/**
 * /app on its own: open the right first page for the user's role.
 * /app لحاله: يفتح أول صفحة مناسبة لدور المستخدم.
 */
export const homeRedirect: CanActivateFn = async () => {
  const auth = inject(AuthService);
  await auth.restore();
  return inject(Router).parseUrl(auth.home());
};

/**
 * Keeps logged-in users away from the login page.
 * يمنع المستخدم المسجّل من فتح صفحة الدخول مرة ثانية.
 */
export const guestGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return (await auth.restore()) ? router.parseUrl(auth.home()) : true;
};
