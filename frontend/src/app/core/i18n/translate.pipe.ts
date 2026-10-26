import { Pipe, PipeTransform, inject } from '@angular/core';

import { I18nService } from './i18n.service';

/**
 * Template usage: {{ 'products.title' | t }} or {{ 'common.pageOf' | t: { page: 1, pages: 3 } }}.
 * طريقة الاستخدام في القالب: {{ 'products.title' | t }} أو مع باراميترات كما في المثال.
 *
 * pure: false lets Angular re-run the pipe when the language signal changes; the lookup is a cheap
 * object walk, so this costs very little.
 * pure: false يخلي Angular يعيد تشغيل الـ pipe لما تتغير اللغة؛ والبحث مجرد مرور سريع على كائن، فالتكلفة بسيطة.
 */
@Pipe({ name: 't', pure: false })
export class TranslatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(key: string, params?: Record<string, string | number>): string {
    return this.i18n.t(key, params);
  }
}
