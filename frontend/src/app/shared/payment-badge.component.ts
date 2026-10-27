import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { PaymentStatus } from '../core/api/models';
import { TranslatePipe } from '../core/i18n/translate.pipe';

/**
 * Paid / unpaid / refunded pill, next to the request status.
 * شارة مدفوع / غير مدفوع / مسترد، جنب حالة الطلب.
 */
@Component({
  selector: 'app-payment-badge',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span [class]="'badge badge--' + tone()" [attr.data-payment]="status()">{{
    'payment.status.' + status() | t
  }}</span>`,
})
export class PaymentBadgeComponent {
  readonly status = input.required<PaymentStatus>();
  protected readonly tone = computed(() =>
    this.status() === 'PAID' ? 'success' : this.status() === 'REFUNDED' ? 'info' : 'warn',
  );
}
