import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { AccountRequestRow, PublicView } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { REFERENCE, normalizeReference, serviceName } from '../../core/masar';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';
import { PaymentBadgeComponent } from '../../shared/payment-badge.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

function reference(control: AbstractControl): ValidationErrors | null {
  const value = String(control.value ?? '');
  return value === '' || REFERENCE.test(value) ? null : { rule: 'Reference' };
}

/**
 * The customer's home: every request on their account, newest first, and a form to add one booked as a guest.
 * صفحة العميل الرئيسية: كل الطلبات في حسابه من الأحدث، ونموذج لإضافة طلب انحجز كزائر.
 */
@Component({
  selector: 'app-account',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    IconComponent,
    FieldErrorComponent,
    StatusBadgeComponent,
    PaymentBadgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './account.page.html',
})
export class AccountPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);
  protected readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);

  protected readonly requests = rxResource({
    stream: () => this.api.get<AccountRequestRow[]>('/account/requests'),
  });

  protected readonly claimForm = inject(NonNullableFormBuilder).group({
    reference: ['', [Validators.required, reference]],
  });
  protected readonly claiming = signal(false);
  private readonly claimError = signal<ApiError | null>(null);
  protected readonly claimErrorText = computed(() => {
    const err = this.claimError();
    if (!err) return null;
    // NOT_FOUND here means: no such reference with this account's mobile number.
    // NOT_FOUND هنا يعني: ما فيه طلب بهالرقم وبجوال هالحساب.
    return err.code === 'NOT_FOUND' ? this.i18n.t('track.notFound') : errorMessage(this.i18n, err);
  });

  protected name(row: AccountRequestRow): string {
    return serviceName(row.service, this.i18n.lang());
  }

  protected claim(): void {
    if (this.claimForm.invalid) {
      this.claimForm.markAllAsTouched();
      return;
    }
    const ref = normalizeReference(this.claimForm.controls.reference.value);
    this.claiming.set(true);
    this.claimError.set(null);
    this.api.post<PublicView>('/account/requests/claim', { reference: ref }).subscribe({
      next: (view) => {
        this.claiming.set(false);
        this.claimForm.reset();
        this.toast.success(this.i18n.t('account.claimed', { reference: view.reference }));
        void this.router.navigate(['/account/requests', view.reference]);
      },
      error: (err) => {
        this.claiming.set(false);
        this.claimError.set(toApiError(err));
      },
    });
  }
}
