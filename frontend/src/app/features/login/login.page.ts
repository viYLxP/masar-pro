import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';

/**
 * Sign-in page.
 * صفحة تسجيل الدخول.
 */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, FieldErrorComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.page.html',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly i18n = inject(I18nService);

  // "?expired=1" is added by the interceptor when the session ended, so we can explain why the user is here.
  // "?expired=1" يضيفه الـ interceptor لما تنتهي الجلسة، حتى نوضح للمستخدم ليش رجع هنا.
  readonly expired = input<string | undefined>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    username: ['', [Validators.required, Validators.maxLength(50)]],
    password: ['', [Validators.required, Validators.maxLength(72)]],
  });
  protected readonly submitting = signal(false);
  // We store the raw error, not the text, so the message re-translates if the user switches language.
  // نخزّن الخطأ الخام مو النص، حتى تتترجم الرسالة من جديد لو بدّل المستخدم اللغة.
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly error = computed(() => {
    const err = this.apiError();
    return err ? errorMessage(this.i18n, err) : null;
  });
  protected readonly showPassword = signal(false);

  protected submit(): void {
    // Show all field errors at once if the user submits an incomplete form.
    // نعرض كل أخطاء الحقول مرة وحدة لو أرسل المستخدم نموذج ناقص.
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.apiError.set(null);
    const { username, password } = this.form.getRawValue();
    this.auth.login(username.trim(), password).subscribe({
      next: () => void this.router.navigateByUrl(this.auth.home()),
      error: (err: unknown) => {
        this.submitting.set(false);
        // Translate the server error code (e.g. BAD_CREDENTIALS) into the current language.
        // نترجم رمز الخطأ القادم من الخادم (مثل BAD_CREDENTIALS) إلى اللغة الحالية.
        this.apiError.set(toApiError(err));
      },
    });
  }
}
