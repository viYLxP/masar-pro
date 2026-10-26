import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  kind: 'success' | 'error';
  text: string;
}

/**
 * Short notifications in the corner of the screen. Messages are already translated by the caller.
 * إشعارات قصيرة في زاوية الشاشة. الرسائل تصل مترجمة من المستدعي.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  readonly toasts = signal<Toast[]>([]);

  success(text: string): void {
    this.push('success', text);
  }

  error(text: string): void {
    this.push('error', text);
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((toast) => toast.id !== id));
  }

  private push(kind: Toast['kind'], text: string): void {
    const id = this.nextId++;
    // Keep at most 4 toasts so a burst of errors does not cover the whole screen.
    // نخلي 4 إشعارات كحد أقصى حتى ما تغطي سلسلة أخطاء الشاشة كاملة.
    this.toasts.update((list) => [...list.slice(-3), { id, kind, text }]);
    // Errors stay longer than successes because they usually need to be read.
    // الأخطاء تبقى أطول من رسائل النجاح لأنها غالبًا تحتاج قراءة.
    setTimeout(() => this.dismiss(id), kind === 'error' ? 7000 : 3500);
  }
}
