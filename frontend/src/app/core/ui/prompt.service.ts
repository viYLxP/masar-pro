import { Injectable, signal } from '@angular/core';

export interface PromptRequest {
  title: string;
  message: string;
  label: string;
  confirmLabel: string;
  maxLength: number;
  danger?: boolean;
}

/**
 * Asks the user for one required line of text, e.g. why a deal was lost:
 * `const reason = await prompt.ask({...}); if (reason !== null) {...}`.
 * يطلب من المستخدم سطر نص واحد مطلوب، مثل سبب خسارة الصفقة.
 * Resolves to the trimmed text, or null when cancelled.
 * يرجّع النص بدون مسافات زائدة، أو null لو ألغى.
 */
@Injectable({ providedIn: 'root' })
export class PromptService {
  readonly current = signal<PromptRequest | null>(null);
  private resolver: ((value: string | null) => void) | null = null;

  ask(request: PromptRequest): Promise<string | null> {
    // A new prompt cancels any one still open. / أي طلب جديد يلغي المفتوح.
    this.resolver?.(null);
    this.current.set(request);
    return new Promise<string | null>((resolve) => (this.resolver = resolve));
  }

  answer(value: string | null): void {
    this.resolver?.(value);
    this.resolver = null;
    this.current.set(null);
  }
}
