import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { BookingCreated } from '../../core/api/models';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';
import { BookingFormComponent } from '../../shared/booking-form.component';

/**
 * Office booking (e.g. a customer on the phone). Same form and rules as the public site.
 * حجز من المكتب (مثلًا عميل على الهاتف). نفس النموذج والقواعد في الموقع العام.
 */
@Component({
  selector: 'app-request-new',
  imports: [RouterLink, TranslatePipe, IconComponent, BookingFormComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="breadcrumb" routerLink="/app/requests">
      <app-icon name="chevron" class="icon--back" [flip]="true" /> {{ 'nav.requests' | t }}
    </a>
    <header class="page-header">
      <div>
        <h1>{{ 'booking.officeTitle' | t }}</h1>
        <p>{{ 'booking.officeSubtitle' | t }}</p>
      </div>
    </header>
    <div class="card">
      <app-booking-form mode="office" (created)="onCreated($event)" />
    </div>
  `,
})
export class RequestNewPage {
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(I18nService);

  // The create call returns only the reference, so we open the list filtered to that one request.
  // طلب الإنشاء يرجّع رقم الطلب فقط، فنفتح القائمة مفلترة على هذا الطلب.
  protected onCreated(result: BookingCreated): void {
    this.toast.success(this.i18n.t('booking.officeCreated', { reference: result.reference }));
    void this.router.navigate(['/app/requests'], { queryParams: { q: result.reference } });
  }
}
