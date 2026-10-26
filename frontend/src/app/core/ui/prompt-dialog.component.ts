import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';

import { TranslatePipe } from '../i18n/translate.pipe';
import { PromptService } from './prompt.service';

/**
 * Native <dialog> with one required text field (focus trap, Escape and backdrop come from showModal()).
 * نافذة <dialog> أصلية فيها حقل نص واحد مطلوب (حصر التركيز وزر Escape والخلفية من showModal()).
 */
@Component({
  selector: 'app-prompt-dialog',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog class="dialog dialog--small" (close)="onClose()" aria-labelledby="prompt-title">
      @if (prompt.current(); as request) {
        <form (submit)="submit($event)" novalidate>
          <h2 id="prompt-title">{{ request.title }}</h2>
          <p class="muted" id="prompt-message">{{ request.message }}</p>
          <div class="field">
            <label for="prompt-input">{{ request.label }}</label>
            <input
              #input
              id="prompt-input"
              [value]="text()"
              (input)="text.set($any($event.target).value); touched.set(true)"
              [attr.maxlength]="request.maxLength"
              [attr.aria-invalid]="showError()"
              aria-describedby="prompt-message prompt-error"
              autocomplete="off"
              data-testid="prompt-input"
            />
            <div id="prompt-error">
              @if (showError()) {
                <p class="field__error" role="alert">{{ 'validation.NotBlank' | t }}</p>
              }
            </div>
          </div>
          <div class="dialog__actions">
            <button type="button" class="btn" (click)="prompt.answer(null)">
              {{ 'common.cancel' | t }}
            </button>
            <button
              type="submit"
              class="btn"
              [class.btn--danger]="request.danger"
              [class.btn--primary]="!request.danger"
              data-testid="prompt-ok"
            >
              {{ request.confirmLabel }}
            </button>
          </div>
        </form>
      }
    </dialog>
  `,
})
export class PromptDialogComponent {
  protected readonly prompt = inject(PromptService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly input = viewChild<ElementRef<HTMLInputElement>>('input');
  protected readonly text = signal('');
  protected readonly touched = signal(false);

  // The error appears only after a try, so the dialog does not open in red.
  // الخطأ يظهر بعد المحاولة فقط، حتى ما تنفتح النافذة باللون الأحمر.
  protected showError(): boolean {
    return this.touched() && this.text().trim() === '';
  }

  constructor() {
    effect(() => {
      const element = this.dialog().nativeElement;
      if (this.prompt.current() && !element.open) {
        this.text.set('');
        this.touched.set(false);
        element.showModal();
        // Focus the field once it is rendered. / نركّز على الحقل بعد ما يظهر.
        queueMicrotask(() => this.input()?.nativeElement.focus());
      } else if (!this.prompt.current() && element.open) {
        element.close();
      }
    });
  }

  protected submit(event: Event): void {
    event.preventDefault();
    this.touched.set(true);
    const value = this.text().trim();
    if (value) {
      this.prompt.answer(value);
    }
  }

  protected onClose(): void {
    if (this.prompt.current()) {
      this.prompt.answer(null);
    }
  }
}
