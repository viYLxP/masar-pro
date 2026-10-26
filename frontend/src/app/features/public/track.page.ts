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
import { Observable } from 'rxjs';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { PublicView } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { REFERENCE, SAUDI_MOBILE, normalizeReference, serviceName } from '../../core/masar';
import { ConfirmService } from '../../core/ui/confirm.service';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';
import { PromptService } from '../../core/ui/prompt.service';
import { ToastService } from '../../core/ui/toast.service';
import { QuoteViewComponent } from '../../shared/quote-view.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { StatusStepperComponent } from '../../shared/status-stepper.component';

function pattern(regex: RegExp, rule: string) {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = String(control.value ?? '');
    return value === '' || regex.test(value) ? null : { rule };
  };
}

/**
 * Customer tracking: reference + mobile number, then the request's progress, the quote (accept/decline),
 * the booked visit and the history. Both values are required, so a leaked reference alone shows nothing.
 * تتبع العميل: رقم الطلب + رقم الجوال، ثم مراحل الطلب وعرض السعر (قبول/رفض) وموعد الزيارة والسجل.
 * القيمتين مطلوبة، فرقم الطلب لو تسرّب لحاله ما يعرض شي.
 */
@Component({
  selector: 'app-track',
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    IconComponent,
    FieldErrorComponent,
    QuoteViewComponent,
    StatusBadgeComponent,
    StatusStepperComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './track.page.html',
})
export class TrackPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);
  private readonly confirm = inject(ConfirmService);
  private readonly prompt = inject(PromptService);
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

  protected readonly reached = computed(() => this.view()?.history.map((h) => h.status) ?? []);
  protected readonly serviceLabel = computed(() =>
    serviceName(this.view()?.service, this.i18n.lang()),
  );

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

  protected async accept(): Promise<void> {
    const v = this.view();
    if (!v?.quote) return;
    const ok = await this.confirm.ask({
      title: this.i18n.t('track.accept'),
      message: this.i18n.t('track.acceptConfirm', { total: this.fmt.money(v.quote.total) }),
      confirmLabel: this.i18n.t('track.accept'),
    });
    if (!ok) return;
    this.run(
      this.api.post<PublicView>(`/public/requests/${v.reference}/accept`, { phone: this.phone }),
      () => this.toast.success(this.i18n.t('track.accepted')),
    );
  }

  protected async reject(): Promise<void> {
    const v = this.view();
    if (!v) return;
    const reason = await this.prompt.ask({
      title: this.i18n.t('track.rejectTitle'),
      message: this.i18n.t('track.rejectPrompt'),
      label: this.i18n.t('common.reason'),
      confirmLabel: this.i18n.t('track.reject'),
      maxLength: 255,
      danger: true,
    });
    if (reason === null) return;
    this.run(
      this.api.post<PublicView>(`/public/requests/${v.reference}/reject`, {
        phone: this.phone,
        reason,
      }),
      () => this.toast.success(this.i18n.t('track.rejected')),
    );
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
