import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { CheckoutPage } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { serviceName } from '../../core/masar';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';

// The built-in test gateway's cards (same list as SandboxCards.java). / بطاقات البوابة التجريبية (نفس SandboxCards.java).
const TEST_CARDS = [
  { number: '5888 4500 0000 0003', brand: 'mada', result: 'approves' },
  { number: '4111 1111 1111 1111', brand: 'Visa', result: 'approves' },
  { number: '5105 1051 0510 5100', brand: 'Mastercard', result: 'approves' },
  { number: '4000 0000 0000 0002', brand: 'Visa', result: 'declines' },
  { number: '4000 0000 0000 9995', brand: 'Visa', result: 'insufficient' },
] as const;

/**
 * The payment page every online payment goes through (/pay/:id).
 * صفحة الدفع اللي تمر فيها كل الدفعات الإلكترونية (‎/pay/:id).
 *
 * With a real gateway (Moyasar, Stripe, Tabby, Tamara) it sends the customer to the gateway's page and, when
 * they come back (?returned=1), asks the server to check the result with the gateway. Without keys it is the
 * built-in test gateway: test cards, an Apple Pay sheet and Tabby/Tamara plan screens, no real money.
 * مع بوابة حقيقية (ميسّر، Stripe، تابي، تمارا) تنقل العميل لصفحة البوابة، ولما يرجع (?returned=1)
 * تطلب من الخادم يتأكد من النتيجة عند البوابة. وبدون مفاتيح هي البوابة التجريبية المدمجة: بطاقات
 * تجريبية وشاشة Apple Pay وشاشات خطط تابي وتمارا، بدون فلوس حقيقية.
 */
@Component({
  selector: 'app-pay',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, IconComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pay.page.html',
})
export class PayPage {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  protected readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);

  // Route parameter :id and ?returned=1 added to the gateway's success URL.
  // باراميتر الرابط ‎:id و‎?returned=1 المضاف لرابط النجاح عند البوابة.
  readonly id = input.required<string>();
  readonly returned = input<string | undefined>();

  protected readonly payment = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.get<CheckoutPage>(`/public/payments/${params}`),
  });
  protected readonly loadErrorText = computed(() => {
    const err = this.payment.error();
    if (!err) return null;
    const apiError = toApiError(err);
    // A bad or unknown id: the link itself is wrong. / رقم غلط أو مو موجود: الرابط نفسه غلط.
    return apiError.code === 'NOT_FOUND' || apiError.status === 400
      ? this.i18n.t('checkout.notFound')
      : errorMessage(this.i18n, apiError);
  });

  protected readonly testCards = TEST_CARDS;
  protected readonly busy = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});

  protected readonly card = inject(NonNullableFormBuilder).group({
    cardNumber: ['', [Validators.required, Validators.pattern(/^[\d\s-]{12,23}$/)]],
    expMonth: ['', [Validators.required, Validators.pattern(/^(0?[1-9]|1[0-2])$/)]],
    expYear: ['', [Validators.required, Validators.pattern(/^(\d{2}|\d{4})$/)]],
    cvc: ['', [Validators.required, Validators.pattern(/^\d{3,4}$/)]],
    cardHolder: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
  });

  protected readonly serviceLabel = computed(() =>
    serviceName(this.payment.value()?.service, this.i18n.lang()),
  );
  // A replaced attempt cannot be paid any more; the customer starts again from the request.
  // المحاولة المستبدلة ما عاد تندفع؛ والعميل يبدأ من جديد من الطلب.
  protected readonly closed = computed(() => this.payment.value()?.failureReason === 'SUPERSEDED');
  protected readonly failureText = computed(() => {
    const p = this.payment.value();
    if (p?.status !== 'FAILED' || !p.failureReason) return null;
    const key = `payment.reason.${p.failureReason}`;
    const known = this.i18n.t(key);
    return known === key ? this.i18n.t('checkout.failed') : known;
  });
  protected readonly providerName = computed(() =>
    this.i18n.t(`payment.provider.${this.payment.value()?.provider ?? 'SANDBOX'}`),
  );

  /** Back to the request: the account page for a signed-in customer, tracking for a guest. */
  /** الرجوع للطلب: صفحة الحساب للعميل المسجّل، والتتبع للزائر. */
  protected backLink(reference: string): { link: string[]; query: Record<string, string> } {
    return this.auth.isCustomer()
      ? { link: ['/account/requests', reference], query: {} }
      : { link: ['/track'], query: { ref: reference } };
  }

  protected useCard(number: string): void {
    // Any future date works; 3 years ahead is always valid. / أي تاريخ مستقبلي يشتغل؛ بعد 3 سنين دايم صالح.
    const year = String((new Date().getFullYear() + 3) % 100).padStart(2, '0');
    this.card.setValue({
      cardNumber: number,
      expMonth: '12',
      expYear: year,
      cvc: '123',
      cardHolder: this.auth.user()?.fullName ?? 'Test Customer',
    });
    this.apiError.set(null);
  }

  protected payByCard(p: CheckoutPage): void {
    if (this.card.invalid) {
      this.card.markAllAsTouched();
      return;
    }
    const v = this.card.getRawValue();
    this.send(p, {
      cardNumber: v.cardNumber,
      expMonth: Number(v.expMonth),
      expYear: Number(v.expYear),
      cvc: v.cvc,
      cardHolder: v.cardHolder.trim(),
    });
  }

  // Apple Pay, Tabby and Tamara: confirm (true) or cancel (false), like on the provider's own screen.
  // Apple Pay وتابي وتمارا: تأكيد (true) أو إلغاء (false)، مثل شاشة المزوّد نفسه.
  protected answer(p: CheckoutPage, approve: boolean): void {
    this.send(p, { approve });
  }

  protected continueToGateway(p: CheckoutPage): void {
    if (p.checkoutUrl) window.location.assign(p.checkoutUrl);
  }

  private send(p: CheckoutPage, body: Record<string, unknown>): void {
    this.busy.set(true);
    this.apiError.set(null);
    this.api.post<CheckoutPage>(`/public/payments/${p.id}/sandbox`, body).subscribe({
      next: (page) => {
        this.busy.set(false);
        this.payment.set(page);
        // The card number is not kept in the page after a try. / رقم البطاقة ما يبقى في الصفحة بعد المحاولة.
        if (page.status === 'PAID') this.card.reset();
      },
      error: (err) => {
        this.busy.set(false);
        const error = toApiError(err);
        this.apiError.set(error);
        if (error.code === 'PAYMENT_NOT_ALLOWED') this.payment.reload();
      },
    });
  }
}
