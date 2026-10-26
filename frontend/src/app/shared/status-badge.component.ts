import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { RequestStatus } from '../core/api/models';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { statusTone } from '../core/masar';

/**
 * Coloured status pill. The colour is a hint only; the text always says the status, so it works without colour.
 * شارة الحالة الملونة. اللون للتلميح فقط، والنص دائمًا يقول الحالة، فتشتغل حتى بدون ألوان.
 */
@Component({
  selector: 'app-status-badge',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span [class]="'badge badge--' + tone()" [attr.data-status]="status()">{{
    'status.' + status() | t
  }}</span>`,
})
export class StatusBadgeComponent {
  readonly status = input.required<RequestStatus>();
  protected readonly tone = computed(() => statusTone(this.status()));
}
