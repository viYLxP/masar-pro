import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { TranslatePipe } from '../i18n/translate.pipe';
import { ToastService } from './toast.service';

/**
 * Renders the toast list. aria-live="polite" makes screen readers announce new messages.
 * يعرض قائمة الإشعارات. aria-live="polite" يخلي قارئ الشاشة يعلن الرسائل الجديدة.
 */
@Component({
  selector: 'app-toast-host',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toasts" aria-live="polite" role="status">
      @for (toast of toasts.toasts(); track toast.id) {
        <div class="toast" [class.toast--error]="toast.kind === 'error'">
          <span>{{ toast.text }}</span>
          <button
            type="button"
            class="icon-btn"
            (click)="toasts.dismiss(toast.id)"
            [attr.aria-label]="'common.close' | t"
          >
            ×
          </button>
        </div>
      }
    </div>
  `,
})
export class ToastHostComponent {
  protected readonly toasts = inject(ToastService);
}
