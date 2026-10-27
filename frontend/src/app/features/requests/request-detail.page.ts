import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { PaymentView, RequestDetail } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { localPhone, serviceName } from '../../core/masar';
import { ConfirmService } from '../../core/ui/confirm.service';
import { IconComponent } from '../../core/ui/icon.component';
import { PromptService } from '../../core/ui/prompt.service';
import { ToastService } from '../../core/ui/toast.service';
import { PaymentBadgeComponent } from '../../shared/payment-badge.component';
import { QuoteViewComponent } from '../../shared/quote-view.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { StatusStepperComponent } from '../../shared/status-stepper.component';
import { QuoteEditorComponent } from './quote-editor.component';
import { ScheduleDialogComponent } from './schedule-dialog.component';

/**
 * One request from the office's (or the assigned technician's) point of view, with only the actions
 * its current status allows. Every action sends the version the page loaded, so two people working on
 * the same request cannot overwrite each other.
 * طلب واحد من وجهة نظر المكتب (أو الفني المعيّن)، مع الإجراءات اللي تسمح بها حالته فقط.
 * كل إجراء يرسل رقم النسخة اللي حمّلتها الصفحة، فشخصين يشتغلون على نفس الطلب ما يكتبون فوق بعض.
 */
@Component({
  selector: 'app-request-detail',
  imports: [
    RouterLink,
    FormsModule,
    TranslatePipe,
    IconComponent,
    StatusBadgeComponent,
    StatusStepperComponent,
    PaymentBadgeComponent,
    QuoteViewComponent,
    QuoteEditorComponent,
    ScheduleDialogComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './request-detail.page.html',
})
export class RequestDetailPage {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  protected readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);
  private readonly confirm = inject(ConfirmService);
  private readonly prompt = inject(PromptService);
  private readonly toast = inject(ToastService);
  private readonly scheduleDialog = viewChild(ScheduleDialogComponent);

  // Route parameter :id. / باراميتر الرابط ‎:id.
  readonly id = input.required<string>();

  protected readonly request = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.get<RequestDetail>(`/requests/${params}`),
  });
  protected readonly loadErrorText = computed(() => {
    const err = this.request.error();
    return err ? errorMessage(this.i18n, toApiError(err)) : null;
  });

  protected readonly editingQuote = signal(false);
  protected readonly busy = signal(false);
  protected readonly completeNote = signal('');
  protected readonly cashCollected = signal(false);

  protected readonly serviceLabel = computed(() =>
    serviceName(this.request.value()?.service, this.i18n.lang()),
  );
  protected readonly reached = computed(
    () => this.request.value()?.history.map((h) => h.toStatus) ?? [],
  );
  protected readonly phone = localPhone;

  protected openSchedule(): void {
    this.scheduleDialog()?.open();
  }

  /** The server's fresh copy replaces the page's one after every action. / نسخة الخادم الجديدة تحل محل نسخة الصفحة بعد كل إجراء. */
  protected update(detail: RequestDetail, message?: string): void {
    this.request.set(detail);
    this.editingQuote.set(false);
    if (message) this.toast.success(this.i18n.t(message));
  }

  /** Someone else changed the request: reload it so the next try uses the new version. */
  /** أحد ثاني غيّر الطلب: نعيد التحميل حتى المحاولة الجاية تستخدم النسخة الجديدة. */
  protected onConflict(): void {
    this.editingQuote.set(false);
    this.request.reload();
  }

  protected async sendQuote(r: RequestDetail): Promise<void> {
    const ok = await this.confirm.ask({
      title: this.i18n.t('quote.send'),
      message: this.i18n.t('quote.sendConfirm', { total: this.fmt.money(r.quote?.total) }),
      confirmLabel: this.i18n.t('quote.send'),
    });
    if (ok) this.act(`/requests/${r.id}/quote/send`, { version: r.version }, 'quote.sent');
  }

  protected async revise(r: RequestDetail): Promise<void> {
    const reopening = r.status === 'REJECTED';
    const ok = await this.confirm.ask({
      title: this.i18n.t(reopening ? 'requests.reopen' : 'requests.revise'),
      message: this.i18n.t(reopening ? 'requests.reopenConfirm' : 'requests.reviseConfirm'),
      confirmLabel: this.i18n.t(reopening ? 'requests.reopen' : 'requests.revise'),
    });
    if (ok) this.act(`/requests/${r.id}/revise`, { version: r.version }, 'requests.revised');
  }

  protected async cancel(r: RequestDetail): Promise<void> {
    const reason = await this.prompt.ask({
      title: this.i18n.t('requests.cancelTitle'),
      message: this.i18n.t('requests.cancelPrompt'),
      label: this.i18n.t('common.reason'),
      confirmLabel: this.i18n.t('requests.cancel'),
      maxLength: 255,
      danger: true,
    });
    if (reason !== null) {
      this.act(`/requests/${r.id}/cancel`, { reason, version: r.version }, 'requests.cancelled');
    }
  }

  protected async start(r: RequestDetail): Promise<void> {
    const ok = await this.confirm.ask({
      title: this.i18n.t('requests.start'),
      message: this.i18n.t('requests.startConfirm', { reference: r.reference }),
      confirmLabel: this.i18n.t('requests.start'),
    });
    if (ok) this.act(`/requests/${r.id}/start`, { version: r.version }, 'requests.started');
  }

  protected complete(r: RequestDetail): void {
    this.act(
      `/requests/${r.id}/complete`,
      {
        note: this.completeNote().trim() || null,
        cashCollected: this.cashCollected() && this.canTakeCash(r),
        version: r.version,
      },
      'requests.completed',
    );
  }

  // Same rule as PaymentService.recordCash: once scheduled, unpaid, with a quote to collect.
  // نفس قاعدة PaymentService.recordCash: بعد الجدولة، غير مدفوع، وفيه عرض سعر نحصّله.
  protected canTakeCash(r: RequestDetail): boolean {
    return (
      ['SCHEDULED', 'IN_PROGRESS', 'COMPLETED'].includes(r.status) &&
      r.paymentStatus === 'UNPAID' &&
      !!r.quote &&
      r.quote.total > 0
    );
  }

  protected async recordCash(r: RequestDetail): Promise<void> {
    const ok = await this.confirm.ask({
      title: this.i18n.t('payment.recordCash'),
      message: this.i18n.t('payment.recordCashConfirm', {
        amount: this.fmt.money(r.quote?.total),
        reference: r.reference,
      }),
      confirmLabel: this.i18n.t('payment.recordCash'),
    });
    if (ok) {
      this.act(`/requests/${r.id}/payments/cash`, { version: r.version }, 'payment.cashRecorded');
    }
  }

  // Known reasons are translated; a gateway's own code is shown as it came. / الأسباب المعروفة تتترجم؛ ورمز البوابة يظهر كما هو.
  protected reasonText(p: PaymentView): string | null {
    if (!p.failureReason) return null;
    const key = `payment.reason.${p.failureReason}`;
    const text = this.i18n.t(key);
    return text === key ? p.failureReason : text;
  }

  // "mada ••0003", or who recorded a cash payment. / "مدى ••0003"، أو مين سجّل الدفعة النقدية.
  protected paymentDetail(p: PaymentView): string {
    if (p.cardLast4) return `${p.cardBrand ?? ''} ••${p.cardLast4}`;
    return p.method === 'CASH' ? this.i18n.t('payment.by', { name: p.recordedBy }) : '';
  }

  private act(path: string, body: unknown, message: string): void {
    this.busy.set(true);
    const call: Observable<RequestDetail> = this.api.post<RequestDetail>(path, body);
    call.subscribe({
      next: (detail) => {
        this.busy.set(false);
        this.completeNote.set('');
        this.cashCollected.set(false);
        this.update(detail, message);
      },
      error: (err) => {
        this.busy.set(false);
        const error = toApiError(err);
        this.toast.error(errorMessage(this.i18n, error));
        if (error.code === 'CONCURRENT_UPDATE' || error.code === 'INVALID_STATUS') {
          this.request.reload();
        }
      },
    });
  }

  protected actorLabel(actor: string): string {
    return actor === 'customer' ? this.i18n.t('requests.customerActor') : actor;
  }
}
