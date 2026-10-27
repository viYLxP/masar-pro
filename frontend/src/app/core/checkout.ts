import { Router } from '@angular/router';

import { CheckoutResponse } from './api/models';

/**
 * Sends the customer to pay. The built-in test gateway's page is our own /pay/:id, so it opens inside the app
 * (this also works when PUBLIC_URL points at another port in development); a real gateway's page is a full redirect.
 * ينقل العميل للدفع. صفحة البوابة التجريبية هي صفحتنا ‎/pay/:id فتنفتح داخل التطبيق
 * (وهذا يشتغل حتى لو PUBLIC_URL يأشر على منفذ ثاني وقت التطوير)؛ وصفحة البوابة الحقيقية تحويل كامل.
 */
export function openCheckout(router: Router, checkout: CheckoutResponse): void {
  const own = `/pay/${checkout.paymentId}`;
  if (pathOf(checkout.checkoutUrl) === own) {
    void router.navigateByUrl(own);
  } else {
    window.location.assign(checkout.checkoutUrl);
  }
}

function pathOf(url: string): string | null {
  try {
    return new URL(url, window.location.origin).pathname;
  } catch {
    // Not a valid URL. / مو رابط صحيح.
    return null;
  }
}
