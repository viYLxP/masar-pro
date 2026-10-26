import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  inject,
  viewChild,
} from '@angular/core';

import { TranslatePipe } from '../i18n/translate.pipe';
import { ConfirmService } from './confirm.service';

/**
 * Uses the native <dialog> element: showModal() gives focus trapping, Escape to close and a backdrop for free,
 * which are hard to get right by hand for accessibility.
 * يستخدم عنصر <dialog> الأصلي: showModal() يعطي حصر التركيز وإغلاق بزر Escape وخلفية معتمة مجانًا،
 * وهذي أشياء صعب تنفيذها يدويًا بشكل صحيح لإمكانية الوصول.
 */
@Component({
  selector: 'app-confirm-dialog',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog
      #dialog
      class="dialog dialog--small"
      (close)="onClose()"
      aria-labelledby="confirm-title"
    >
      @if (confirm.current(); as request) {
        <h2 id="confirm-title">{{ request.title }}</h2>
        <p>{{ request.message }}</p>
        <div class="dialog__actions">
          <button type="button" class="btn" (click)="confirm.answer(false)">
            {{ 'common.cancel' | t }}
          </button>
          <button
            type="button"
            class="btn"
            [class.btn--danger]="request.danger"
            [class.btn--primary]="!request.danger"
            (click)="confirm.answer(true)"
            data-testid="confirm-yes"
          >
            {{ request.confirmLabel }}
          </button>
        </div>
      }
    </dialog>
  `,
})
export class ConfirmDialogComponent {
  protected readonly confirm = inject(ConfirmService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    // Open or close the native dialog whenever the service state changes.
    // نفتح أو نقفل النافذة الأصلية كل ما تغيرت حالة الخدمة.
    effect(() => {
      const element = this.dialog().nativeElement;
      if (this.confirm.current() && !element.open) {
        element.showModal();
      } else if (!this.confirm.current() && element.open) {
        element.close();
      }
    });
  }

  // Pressing Escape closes the dialog natively; treat that as "cancel".
  // زر Escape يقفل النافذة تلقائيًا، ونعتبره "إلغاء".
  protected onClose(): void {
    if (this.confirm.current()) {
      this.confirm.answer(false);
    }
  }
}
