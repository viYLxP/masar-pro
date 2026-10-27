import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { CheckoutResponse, PaymentMethod, PublicView } from '../../core/api/models';
import { openCheckout } from '../../core/checkout';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';
import { CustomerRequestComponent } from '../../shared/customer-request.component';

/**
 * One of the customer's own requests. Same view as guest tracking, but the account proves ownership,
 * so no mobile number is asked for.
 * طلب من طلبات العميل نفسه. نفس عرض تتبع الزائر، بس الحساب يثبت الملكية فما نطلب رقم الجوال.
 */
@Component({
  selector: 'app-account-request',
  imports: [RouterLink, TranslatePipe, IconComponent, CustomerRequestComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-narrow">
      <a class="breadcrumb" routerLink="/account">
        <app-icon name="chevron" class="icon--back" [flip]="true" /> {{ 'account.back' | t }}
      </a>
      @if (request.error()) {
        <div class="alert alert--danger" role="alert">
          {{ loadErrorText() }}
          <button type="button" class="btn btn--small" (click)="request.reload()">
            {{ 'app.retry' | t }}
          </button>
        </div>
      } @else if (request.isLoading() && !request.hasValue()) {
        <div class="loading" role="status">
          <span class="spinner"></span>{{ 'app.loading' | t }}
        </div>
      } @else if (request.value(); as v) {
        <app-customer-request
          [view]="v"
          [busy]="busy() || request.isLoading()"
          [error]="errorText()"
          (accepted)="accept(v)"
          (rejected)="reject(v, $event)"
          (paid)="pay(v, $event)"
          (refreshed)="request.reload()"
        />
      }
    </div>
  `,
})
export class AccountRequestPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(I18nService);

  // Route parameter :reference. / باراميتر الرابط ‎:reference.
  readonly reference = input.required<string>();

  protected readonly request = rxResource({
    params: () => this.reference(),
    stream: ({ params }) => this.api.get<PublicView>(`/account/requests/${params}`),
  });
  protected readonly loadErrorText = computed(() => {
    const err = this.request.error();
    return err ? errorMessage(this.i18n, toApiError(err)) : null;
  });

  protected readonly busy = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err ? errorMessage(this.i18n, err) : null;
  });

  protected accept(v: PublicView): void {
    this.run(
      this.api.post<PublicView>(`/account/requests/${v.reference}/accept`),
      'track.accepted',
    );
  }

  protected reject(v: PublicView, reason: string): void {
    this.run(
      this.api.post<PublicView>(`/account/requests/${v.reference}/reject`, { reason }),
      'track.rejected',
    );
  }

  protected pay(v: PublicView, method: PaymentMethod): void {
    this.busy.set(true);
    this.apiError.set(null);
    this.api.post<CheckoutResponse>(`/account/requests/${v.reference}/pay`, { method }).subscribe({
      next: (checkout) => openCheckout(this.router, checkout),
      error: (err) => {
        this.busy.set(false);
        this.apiError.set(toApiError(err));
      },
    });
  }

  private run(call: Observable<PublicView>, message: string): void {
    this.busy.set(true);
    this.apiError.set(null);
    call.subscribe({
      next: (view) => {
        this.busy.set(false);
        this.request.set(view);
        this.toast.success(this.i18n.t(message));
      },
      error: (err) => {
        this.busy.set(false);
        this.apiError.set(toApiError(err));
      },
    });
  }
}
