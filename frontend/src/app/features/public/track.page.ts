import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { CheckoutResponse, PaymentMethod, PublicView } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { openCheckout } from '../../core/checkout';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { REFERENCE, SAUDI_MOBILE, normalizeReference } from '../../core/masar';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { ToastService } from '../../core/ui/toast.service';
import { CustomerRequestComponent } from '../../shared/customer-request.component';

function pattern(regex: RegExp, rule: string) {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = String(control.value ?? '');
    return value === '' || regex.test(value) ? null : { rule };
  };
}

/**
 * Guest tracking: reference + mobile number, then the request's progress, the quote (accept/decline), the
 * payment, the booked visit and the history. Both values are required, so a leaked reference alone shows nothing.
 * تتبع الزائر: رقم الطلب + رقم الجوال، ثم مراحل الطلب وعرض السعر (قبول/رفض) والدفع وموعد الزيارة والسجل.
 * القيمتين مطلوبة، فرقم الطلب لو تسرّب لحاله ما يعرض شي.
 */
@Component({
  selector: 'app-track',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslatePipe,
    FieldErrorComponent,
    CustomerRequestComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './track.page.html',
})
export class TrackPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly i18n = inject(I18nService);
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  // ?ref=MS-… from the booking success screen fills the reference for the customer.
  // ‎?ref=MS-… من شاشة نجاح الحجز يعبّي رقم الطلب للعميل.
  readonly ref = input<string | undefined>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    reference: ['', [Validators.required, pattern(REFERENCE, 'Reference')]],
    phone: ['', [Validators.required, pattern(SAUDI_MOBILE, 'Phone')]],
  });

  protected readonly view = signal<PublicView | null>(null);
  protected readonly busy = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  // The phone that found the request; accept/decline must send the same one.
  // الجوال اللي لقى الطلب؛ القبول والرفض لازم يرسلون نفسه.
  private phone = '';

  protected readonly errorText = computed(() => {
    const err = this.apiError();
    if (!err) return null;
    // NOT_FOUND gets the specific "check both values" text instead of the generic one.
    // NOT_FOUND ياخذ نص "تأكد من القيمتين" بدل الرسالة العامة.
    return err.code === 'NOT_FOUND' ? this.i18n.t('track.notFound') : errorMessage(this.i18n, err);
  });

  ngOnInit(): void {
    const ref = this.ref();
    if (ref) this.form.controls.reference.setValue(ref);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const reference = normalizeReference(this.form.controls.reference.value);
    const phone = this.form.controls.phone.value.trim();
    this.run(this.api.post<PublicView>('/public/requests/track', { reference, phone }), () => {
      this.phone = phone;
    });
  }

  protected refresh(): void {
    const v = this.view();
    if (v) {
      this.run(
        this.api.post<PublicView>('/public/requests/track', {
          reference: v.reference,
          phone: this.phone,
        }),
      );
    }
  }

  protected accept(): void {
    const v = this.view();
    if (!v) return;
    this.run(
      this.api.post<PublicView>(`/public/requests/${v.reference}/accept`, { phone: this.phone }),
      () => this.toast.success(this.i18n.t('track.accepted')),
    );
  }

  protected reject(reason: string): void {
    const v = this.view();
    if (!v) return;
    this.run(
      this.api.post<PublicView>(`/public/requests/${v.reference}/reject`, {
        phone: this.phone,
        reason,
      }),
      () => this.toast.success(this.i18n.t('track.rejected')),
    );
  }

  protected pay(method: PaymentMethod): void {
    const v = this.view();
    if (!v) return;
    this.busy.set(true);
    this.apiError.set(null);
    this.api
      .post<CheckoutResponse>(`/public/requests/${v.reference}/pay`, { phone: this.phone, method })
      .subscribe({
        next: (checkout) => openCheckout(this.router, checkout),
        error: (err) => {
          this.busy.set(false);
          this.apiError.set(toApiError(err));
        },
      });
  }

  protected reset(): void {
    this.view.set(null);
    this.apiError.set(null);
    this.phone = '';
    this.form.reset();
  }

  private run(request: Observable<PublicView>, onSuccess?: () => void): void {
    this.busy.set(true);
    this.apiError.set(null);
    request.subscribe({
      next: (view) => {
        this.busy.set(false);
        this.view.set(view);
        onSuccess?.();
      },
      error: (err) => {
        this.busy.set(false);
        this.apiError.set(toApiError(err));
      },
    });
  }
}
