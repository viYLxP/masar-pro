import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { AbstractControl } from '@angular/forms';

import { I18nService } from '../i18n/i18n.service';

/**
 * Shows one translated error under a form field: client-side validators first, then the server's field error.
 * يعرض خطأ واحد مترجم تحت حقل النموذج: أخطاء التحقق في المتصفح أولًا، ثم خطأ الحقل القادم من الخادم.
 *
 * The message only appears after the user touched the field (or tried to submit), so an empty form is not all red.
 * الرسالة تظهر فقط بعد ما يلمس المستخدم الحقل (أو يحاول الإرسال)، حتى ما يكون النموذج الفاضي كله أحمر.
 *
 * The id is placed on the host element (<app-field-error id="sku-err">), which the input's aria-describedby
 * points to; the inner message must not repeat it, or the page would contain duplicate ids.
 * الـ id يوضع على العنصر نفسه (<app-field-error id="sku-err">) واللي يشير له aria-describedby في الحقل؛
 * والرسالة الداخلية ما تكرره، وإلا يصير في الصفحة id مكرر.
 *
 * Uses Default change detection on purpose: form state lives in the control (not in a signal), so this small
 * component must be re-checked whenever its parent form is.
 * نستخدم Default change detection عن قصد: حالة النموذج داخل الـ control (مو signal)، فلازم يتفحص
 * هذا المكوّن الصغير كل ما تفحّص النموذج الأب.
 */
@Component({
  selector: 'app-field-error',
  changeDetection: ChangeDetectionStrategy.Default,
  template: `@if (message(); as text) {
    <p class="field__error" role="alert">{{ text }}</p>
  }`,
})
export class FieldErrorComponent {
  private readonly i18n = inject(I18nService);

  readonly control = input.required<AbstractControl>();
  // Server rule name for this field, e.g. "Pattern" (from ApiError.fields).
  // اسم قاعدة الخادم لهذا الحقل، مثل "Pattern" (من ApiError.fields).
  readonly server = input<string | undefined>();
  // Translation key for a "pattern" error, because each field has its own format rule.
  // مفتاح الترجمة لخطأ "pattern"، لأن لكل حقل قاعدة صيغة خاصة.
  readonly patternKey = input('validation.Pattern');

  protected message(): string | null {
    const control = this.control();
    if (control.invalid && (control.touched || control.dirty)) {
      const errors = control.errors ?? {};
      // Custom validators report { rule: 'Phone' } etc., which maps straight to a validation.* text.
      // المدققات الخاصة ترجع { rule: 'Phone' } وغيرها، وتنربط مباشرة بنص validation.*.
      if (typeof errors['rule'] === 'string') return this.i18n.t(`validation.${errors['rule']}`);
      if (errors['required']) return this.i18n.t('validation.NotBlank');
      if (errors['email']) return this.i18n.t('validation.Email');
      if (errors['pattern']) return this.i18n.t(this.patternKey());
      if (errors['minlength'])
        return this.i18n.t('validation.minLength', { min: errors['minlength'].requiredLength });
      if (errors['maxlength']) return this.i18n.t('validation.Size');
      // A negative number gets the clearer "cannot be negative" message.
      // الرقم السالب ياخذ الرسالة الأوضح "لا يمكن أن تكون سالبة".
      if (errors['min'])
        return this.i18n.t(Number(control.value) < 0 ? 'validation.DecimalMin' : 'validation.Min');
      if (errors['max']) return this.i18n.t('validation.Max');
    }
    // Server-side error: shown until the user edits the field (the form clears it on change).
    // خطأ من الخادم: يظهر لين يعدّل المستخدم الحقل (النموذج يمسحه عند التغيير).
    const server = this.server();
    return server ? this.i18n.t(`validation.${server}`) : null;
  }
}
