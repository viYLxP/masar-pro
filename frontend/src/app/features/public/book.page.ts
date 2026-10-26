import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { BookingCreated } from '../../core/api/models';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';
import { BookingFormComponent } from '../../shared/booking-form.component';

/**
 * Public booking page. After sending, the form is replaced by the reference number the customer needs to track.
 * صفحة الحجز العامة. بعد الإرسال، النموذج يتبدل برقم الطلب اللي يحتاجه العميل للتتبع.
 */
@Component({
  selector: 'app-book',
  imports: [RouterLink, TranslatePipe, IconComponent, BookingFormComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-narrow">
      @if (result(); as r) {
        <section class="success-card" data-testid="booking-success" tabindex="-1">
          <span class="success-card__icon" aria-hidden="true"><app-icon name="check" /></span>
          <h1>{{ 'booking.successTitle' | t }}</h1>
          <p>{{ 'booking.successText' | t }}</p>
          <p class="reference">
            <span class="muted">{{ 'booking.reference' | t }}</span>
            <strong dir="ltr" data-testid="booking-reference">{{ r.reference }}</strong>
            <button type="button" class="btn btn--small" (click)="copy(r.reference)">
              <app-icon name="copy" /> {{ 'booking.copy' | t }}
            </button>
          </p>
          <div class="success-card__actions">
            <a class="btn btn--primary" routerLink="/track" [queryParams]="{ ref: r.reference }">{{
              'booking.trackNow' | t
            }}</a>
            <button type="button" class="btn" (click)="result.set(null)">
              {{ 'booking.another' | t }}
            </button>
          </div>
        </section>
      } @else {
        <header class="page-header page-header--center">
          <div>
            <h1>{{ 'booking.title' | t }}</h1>
            <p>{{ 'booking.subtitle' | t }}</p>
          </div>
        </header>
        <app-booking-form mode="public" [serviceId]="preselected()" (created)="onCreated($event)" />
      }
    </div>
  `,
})
export class BookPage {
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);

  // ?service=3 from the home page's "Book …" buttons. / ‎?service=3 من أزرار "احجز …" في الرئيسية.
  readonly service = input<string | undefined>();
  protected readonly preselected = computed(() => {
    const id = Number(this.service());
    return Number.isInteger(id) && id > 0 ? id : null;
  });
  protected readonly result = signal<BookingCreated | null>(null);

  protected onCreated(result: BookingCreated): void {
    this.result.set(result);
    // Bring the reference into view and focus, so screen readers announce the success right away.
    // نعرض رقم الطلب ونركّز عليه، حتى قارئ الشاشة يعلن النجاح مباشرة.
    queueMicrotask(() => {
      window.scrollTo({ top: 0 });
      document.querySelector<HTMLElement>('[data-testid="booking-success"]')?.focus();
    });
  }

  protected copy(reference: string): void {
    navigator.clipboard?.writeText(reference).then(
      () => this.toast.success(this.i18n.t('booking.copied')),
      // Clipboard can be blocked (http, permissions); the reference is still on screen to copy by hand.
      // الحافظة ممكن تكون محجوبة (http أو صلاحيات)؛ ورقم الطلب ظاهر على الشاشة للنسخ يدويًا.
      () => undefined,
    );
  }
}
