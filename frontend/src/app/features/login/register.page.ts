import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { SAUDI_MOBILE } from '../../core/masar';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';

function saudiMobile(control: AbstractControl): ValidationErrors | null {
  const value = String(control.value ?? '');
  return value === '' || SAUDI_MOBILE.test(value) ? null : { rule: 'Phone' };
}

/**
 * Customer sign-up: name, mobile (the sign-in name), optional email and a password. Signs in right away.
 * تسجيل عميل جديد: الاسم والجوال (اسم الدخول) وإيميل اختياري وكلمة مرور. ويدخل على طول.
 */
@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, FieldErrorComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './register.page.html',
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly i18n = inject(I18nService);

  readonly returnUrl = input<string | undefined>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    fullName: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(120)]],
    phone: ['', [Validators.required, saudiMobile, Validators.maxLength(20)]],
    email: ['', [Validators.email, Validators.maxLength(150)]],
    // Same bounds as the server: BCrypt only uses the first 72 bytes.
    // نفس حدود الخادم: BCrypt يستخدم أول 72 بايت فقط.
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    confirm: ['', [Validators.required, (c: AbstractControl) => this.matches(c)]],
  });
  protected readonly submitting = signal(false);
  protected readonly showPassword = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly error = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});
  protected readonly apiErrorCode = computed(() => this.apiError()?.code);

  constructor() {
    // Re-check "confirm" when the first password changes. / نعيد فحص "التأكيد" لما تتغير كلمة المرور الأولى.
    this.form.controls.password.valueChanges.subscribe(() =>
      this.form.controls.confirm.updateValueAndValidity({ emitEvent: false }),
    );
  }

  private matches(control: AbstractControl): ValidationErrors | null {
    const password = this.form?.controls.password.value ?? '';
    return control.value && control.value !== password ? { rule: 'Match' } : null;
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.submitting.set(true);
    this.apiError.set(null);
    this.auth
      .register({
        fullName: v.fullName.trim(),
        phone: v.phone.trim(),
        email: v.email.trim() || null,
        password: v.password,
      })
      .subscribe({
        next: () => {
          const url = this.returnUrl();
          // Only follow a path inside this site. / نتبع بس مسار داخل الموقع.
          const safe = url && url.startsWith('/') && !url.startsWith('//');
          void this.router.navigateByUrl(safe ? url : '/account');
        },
        error: (err: unknown) => {
          this.submitting.set(false);
          this.apiError.set(toApiError(err));
        },
      });
  }
}
