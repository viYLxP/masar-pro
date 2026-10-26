import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { MAIN_FLOW, RequestStatus } from '../core/api/models';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { IconComponent } from '../core/ui/icon.component';

/**
 * The six steps of a request with the current one highlighted. A rejected or cancelled request shows
 * the steps it reached, then the exit as the last step, so the customer sees where it stopped.
 * الخطوات الست للطلب مع تمييز الخطوة الحالية. الطلب المرفوض أو الملغي يعرض الخطوات اللي وصلها،
 * ثم المخرج كآخر خطوة، حتى يشوف العميل وين وقف.
 */
@Component({
  selector: 'app-status-stepper',
  imports: [TranslatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="stepper" [attr.aria-label]="label() | t">
      @for (step of steps(); track step.status) {
        <li
          [class.is-done]="step.state === 'done'"
          [class.is-exit]="step.state === 'exit'"
          [attr.aria-current]="step.state === 'current' || step.state === 'exit' ? 'step' : null"
        >
          <span class="stepper__dot" aria-hidden="true">
            @if (step.state === 'done') {
              <app-icon name="check" />
            } @else if (step.state === 'exit') {
              <app-icon name="x" />
            }
          </span>
          <span>{{ 'status.' + step.status | t }}</span>
        </li>
      }
    </ol>
  `,
})
export class StatusStepperComponent {
  readonly status = input.required<RequestStatus>();
  // The furthest main-flow status the request reached (taken from its history) before it was rejected/cancelled.
  // أبعد حالة في المسار الطبيعي وصلها الطلب (من سجله) قبل ما ينرفض أو ينلغي.
  readonly reached = input<RequestStatus[]>([]);
  readonly label = input('requests.progress');

  protected readonly steps = computed(() => {
    const status = this.status();
    if (status === 'REJECTED' || status === 'CANCELLED') {
      const reached = this.reached();
      const last = Math.max(0, ...reached.map((s) => MAIN_FLOW.indexOf(s)));
      return [
        ...MAIN_FLOW.slice(0, last + 1).map((s) => ({ status: s, state: 'done' as const })),
        { status, state: 'exit' as const },
      ];
    }
    const current = MAIN_FLOW.indexOf(status);
    return MAIN_FLOW.map((s, i) => ({
      status: s,
      // The final step is "done" rather than "current" once the job is completed.
      // الخطوة الأخيرة تصير "منجزة" بدل "حالية" لما يكتمل الشغل.
      state:
        i < current || (status === 'COMPLETED' && i === current)
          ? ('done' as const)
          : i === current
            ? ('current' as const)
            : ('todo' as const),
    }));
  });
}
