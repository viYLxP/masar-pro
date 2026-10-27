import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { User } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { localPhone } from '../../core/masar';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';

/**
 * The customer's profile (name, email) and password. The mobile number is the sign-in name, so it stays fixed.
 * ملف العميل (الاسم والإيميل) وكلمة المرور. رقم الجوال هو اسم الدخول، فيبقى ثابت.
 */
@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, FieldErrorComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './profile.page.html',
})
export class ProfilePage {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);
  protected readonly i18n = inject(I18nService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly phone = computed(() => localPhone(this.auth.user()?.phone));

  protected readonly profile = this.fb.group({
    fullName: [
      this.auth.user()?.fullName ?? '',
      [Validators.required, Validators.pattern(/\S/), Validators.maxLength(120)],
    ],
    email: [this.auth.user()?.email ?? '', [Validators.email, Validators.maxLength(150)]],
  });
  protected readonly password = this.fb.group({
    currentPassword: ['', [Validators.required, Validators.maxLength(72)]],
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });

  protected readonly savingProfile = signal(false);
  protected readonly savingPassword = signal(false);
  private readonly profileError = signal<ApiError | null>(null);
  private readonly passwordError = signal<ApiError | null>(null);
  protected readonly profileErrorText = computed(() => this.text(this.profileError()));
  protected readonly passwordErrorText = computed(() => this.text(this.passwordError()));
  protected readonly profileFields = computed(() => this.profileError()?.fields ?? {});
  protected readonly passwordFields = computed(() => this.passwordError()?.fields ?? {});

  private text(err: ApiError | null): string | null {
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  }

  protected saveProfile(): void {
    if (this.profile.invalid) {
      this.profile.markAllAsTouched();
      return;
    }
    const v = this.profile.getRawValue();
    this.savingProfile.set(true);
    this.profileError.set(null);
    this.api
      .put<User>('/account/profile', { fullName: v.fullName.trim(), email: v.email.trim() || null })
      .subscribe({
        next: (user) => {
          this.savingProfile.set(false);
          this.auth.updateUser(user);
          this.toast.success(this.i18n.t('account.profileSaved'));
        },
        error: (err) => {
          this.savingProfile.set(false);
          this.profileError.set(toApiError(err));
        },
      });
  }

  protected changePassword(): void {
    if (this.password.invalid) {
      this.password.markAllAsTouched();
      return;
    }
    this.savingPassword.set(true);
    this.passwordError.set(null);
    this.api.put<void>('/account/password', this.password.getRawValue()).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.password.reset();
        this.toast.success(this.i18n.t('account.passwordChanged'));
      },
      error: (err) => {
        this.savingPassword.set(false);
        this.passwordError.set(toApiError(err));
      },
    });
  }
}
