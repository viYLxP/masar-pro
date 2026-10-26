import { Injectable, signal } from '@angular/core';

export interface ConfirmRequest {
  title: string;
  message: string;
  confirmLabel: string;
  danger?: boolean;
}

/**
 * Promise-based confirmation dialog: `if (await confirm.ask({...})) { ... }`.
 * Replaces window.confirm(), which cannot be styled, translated consistently or tested reliably.
 * نافذة تأكيد تعتمد على Promise: `if (await confirm.ask({...})) { ... }`.
 * بديل لـ window.confirm() اللي ما يمكن تنسيقه أو ترجمته بشكل موحّد أو اختباره بثبات.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly current = signal<ConfirmRequest | null>(null);
  private resolver: ((value: boolean) => void) | null = null;

  ask(request: ConfirmRequest): Promise<boolean> {
    // If a dialog is already open, answer it "no" before showing the new one.
    // لو فيه نافذة مفتوحة، نرد عليها بـ "لا" قبل نعرض الجديدة.
    this.resolver?.(false);
    this.current.set(request);
    return new Promise<boolean>((resolve) => (this.resolver = resolve));
  }

  answer(value: boolean): void {
    this.resolver?.(value);
    this.resolver = null;
    this.current.set(null);
  }
}
