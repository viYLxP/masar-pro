import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';

import { PaymentMethod, PublicView } from '../core/api/models';
import { FormatService } from '../core/i18n/format.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { serviceName } from '../core/masar';
import { ConfirmService } from '../core/ui/confirm.service';
import { IconComponent } from '../core/ui/icon.component';
import { PromptService } from '../core/ui/prompt.service';
import { PaymentPanelComponent } from './payment-panel.component';
import { QuoteViewComponent } from './quote-view.component';
import { StatusBadgeComponent } from './status-badge.component';
import { StatusStepperComponent } from './status-stepper.component';

/**
 * One request as the customer sees it: progress, quote (accept / decline), payment, visit and updates.
 * Shared by the guest tracking page and the customer account, which differ only in how they call the server.
 * طلب واحد مثل ما يشوفه العميل: المراحل والعرض (قبول / رفض) والدفع والزيارة والتحديثات.
 * مشترك بين صفحة التتبع للزائر وحساب العميل، والفرق بينهم بس في طريقة الاتصال بالخادم.
 */
@Component({
  selector: 'app-customer-request',
  imports: [
    TranslatePipe,
    IconComponent,
    PaymentPanelComponent,
    QuoteViewComponent,
    StatusBadgeComponent,
    StatusStepperComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './customer-request.component.html',
})
export class CustomerRequestComponent {
  protected readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);
  private readonly confirm = inject(ConfirmService);
  private readonly prompt = inject(PromptService);

  readonly view = input.required<PublicView>();
  readonly busy = input(false);
  // Shown under the header, e.g. a failed action. / يظهر تحت العنوان، مثل إجراء فشل.
  readonly error = input<string | null>(null);
  readonly accepted = output<void>();
  readonly rejected = output<string>();
  readonly paid = output<PaymentMethod>();
  readonly refreshed = output<void>();

  protected readonly reached = computed(() => this.view().history.map((h) => h.status));
  protected readonly serviceLabel = computed(() =>
    serviceName(this.view().service, this.i18n.lang()),
  );

  protected async accept(): Promise<void> {
    const quote = this.view().quote;
    if (!quote) return;
    const ok = await this.confirm.ask({
      title: this.i18n.t('track.accept'),
      message: this.i18n.t('track.acceptConfirm', { total: this.fmt.money(quote.total) }),
      confirmLabel: this.i18n.t('track.accept'),
    });
    if (ok) this.accepted.emit();
  }

  protected async reject(): Promise<void> {
    const reason = await this.prompt.ask({
      title: this.i18n.t('track.rejectTitle'),
      message: this.i18n.t('track.rejectPrompt'),
      label: this.i18n.t('common.reason'),
      confirmLabel: this.i18n.t('track.reject'),
      maxLength: 255,
      danger: true,
    });
    if (reason !== null) this.rejected.emit(reason);
  }
}
