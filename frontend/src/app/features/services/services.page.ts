import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { ServiceType } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { serviceIcon } from '../../core/masar';
import { ConfirmService } from '../../core/ui/confirm.service';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';

/**
 * Admin-only service catalogue. A service that was ever booked cannot be deleted (its requests point to it);
 * hiding it (active = off) removes it from the website while keeping the history.
 * قائمة الخدمات للمدير فقط. الخدمة اللي انحجزت قبل ما تنحذف (طلباتها تشير لها)؛
 * وإخفاؤها (نشطة = لا) يشيلها من الموقع ويحتفظ بالسجل.
 */
@Component({
  selector: 'app-services',
  imports: [ReactiveFormsModule, TranslatePipe, IconComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './services.page.html',
})
export class ServicesPage {
  private readonly api = inject(ApiService);
  protected readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly services = rxResource({
    stream: () => this.api.get<ServiceType[]>('/services'),
  });
  protected readonly icon = serviceIcon;

  protected readonly editing = signal<ServiceType | null>(null);
  protected readonly saving = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});

  protected readonly form = inject(NonNullableFormBuilder).group({
    code: ['', [Validators.required, Validators.maxLength(30), Validators.pattern(/^[A-Z0-9_]+$/)]],
    nameAr: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    nameEn: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    descriptionAr: ['', Validators.maxLength(500)],
    descriptionEn: ['', Validators.maxLength(500)],
    basePrice: [
      null as number | null,
      [Validators.required, Validators.min(0), Validators.max(99999999.99)],
    ],
    durationMinutes: [60, [Validators.required, Validators.min(30), Validators.max(480)]],
    sortOrder: [0, [Validators.min(0), Validators.max(999)]],
    active: [true],
  });

  protected open(service: ServiceType | null): void {
    this.editing.set(service);
    this.apiError.set(null);
    this.form.reset({
      code: service?.code ?? '',
      nameAr: service?.nameAr ?? '',
      nameEn: service?.nameEn ?? '',
      descriptionAr: service?.descriptionAr ?? '',
      descriptionEn: service?.descriptionEn ?? '',
      basePrice: service?.basePrice ?? null,
      durationMinutes: service?.durationMinutes ?? 60,
      sortOrder: service?.sortOrder ?? (this.services.value()?.length ?? 0) + 1,
      active: service?.active ?? true,
    });
    this.dialog().nativeElement.showModal();
  }

  protected close(): void {
    this.dialog().nativeElement.close();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const current = this.editing();
    const body = {
      ...v,
      code: v.code.trim(),
      nameAr: v.nameAr.trim(),
      nameEn: v.nameEn.trim(),
      descriptionAr: v.descriptionAr.trim() || null,
      descriptionEn: v.descriptionEn.trim() || null,
      basePrice: Number(v.basePrice),
      durationMinutes: Number(v.durationMinutes),
      sortOrder: Number(v.sortOrder) || 0,
      version: current?.version ?? null,
    };
    const call = current
      ? this.api.put<ServiceType>(`/services/${current.id}`, body)
      : this.api.post<ServiceType>('/services', body);
    this.saving.set(true);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(this.i18n.t('common.saved'));
        this.close();
        this.services.reload();
      },
      error: (err) => {
        this.saving.set(false);
        this.apiError.set(toApiError(err));
      },
    });
  }

  protected async remove(service: ServiceType): Promise<void> {
    const name = this.i18n.lang() === 'ar' ? service.nameAr : service.nameEn;
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message: this.i18n.t('common.deleteConfirm', { name }),
      confirmLabel: this.i18n.t('common.delete'),
      danger: true,
    });
    if (!ok) return;
    this.api.delete(`/services/${service.id}`).subscribe({
      next: () => {
        this.toast.success(this.i18n.t('common.deleted'));
        this.services.reload();
      },
      error: (err) => {
        const error = toApiError(err);
        // IN_USE carries how many requests use the service, which makes the "hide it instead" advice concrete.
        // IN_USE يحمل عدد الطلبات اللي تستخدم الخدمة، فتصير نصيحة "أخفها بدلًا من ذلك" واضحة.
        this.toast.error(
          error.code === 'IN_USE'
            ? this.i18n.t('services.inUse', { requests: Number(error.details['requests'] ?? 0) })
            : errorMessage(this.i18n, error),
        );
      },
    });
  }
}
