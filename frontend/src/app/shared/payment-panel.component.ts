import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';

import { PaymentMethod, PaymentSummary } from '../core/api/models';
import { FormatService } from '../core/i18n/format.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { IconComponent } from '../core/ui/icon.component';
import { PaymentBadgeComponent } from './payment-badge.component';

const ICONS: Record<PaymentMethod, string> = {
  CARD: 'card',
  APPLE_PAY: 'phone',
  TABBY: 'split',
  TAMARA: 'split',
  CASH: 'cash',
};

/**
 * The customer's payment card: what is due, the methods the server offers for this request, the Tabby/Tamara
 * plan, and how it was paid once it is. The parent starts the payment (guest and account use different links).
 * بطاقة الدفع عند العميل: كم المطلوب، والطرق اللي يعرضها الخادم لهالطلب، وخطة تابي/تمارا، وكيف انْدفع
 * بعد الدفع. والأب هو اللي يبدأ الدفع (الزائر والحساب لهم روابط مختلفة).
 */
@Component({
  selector: 'app-payment-panel',
  imports: [TranslatePipe, IconComponent, PaymentBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card" aria-labelledby="pay-title" data-testid="payment-panel">
      <div class="card__header">
        <h2 id="pay-title">{{ 'payment.title' | t }}</h2>
        <app-payment-badge [status]="payment().status" />
      </div>
      @let p = payment();
      @if (p.status === 'PAID') {
        <p class="alert alert--success" data-testid="payment-paid">
          {{
            'payment.paidWith'
              | t
                : {
                    amount: fmt.money(p.amountDue),
                    method: methodText(),
                    date: fmt.dateTime(p.paidAt),
                  }
          }}
        </p>
      } @else if (p.status === 'REFUNDED') {
        <p class="alert alert--info">{{ 'payment.refunded' | t }}</p>
      } @else if (p.payable) {
        <p class="pay-due">
          <span class="muted">{{ 'payment.due' | t }}</span>
          <strong data-testid="payment-due">{{ fmt.money(p.amountDue) }}</strong>
        </p>
        <fieldset class="field">
          <legend class="field__label">{{ 'payment.choose' | t }}</legend>
          <div class="choice-grid" role="radiogroup">
            @for (m of p.methods; track m) {
              <label class="choice" [class.choice--on]="method() === m">
                <input
                  type="radio"
                  name="pay-method"
                  [value]="m"
                  [checked]="method() === m"
                  (change)="method.set(m)"
                  [attr.data-testid]="'method-' + m"
                />
                <span class="choice__title"
                  ><app-icon [name]="icon(m)" /> {{ 'payment.method.' + m | t }}</span
                >
                <small class="muted">{{ 'payment.methodHint.' + m | t }}</small>
              </label>
            }
          </div>
        </fieldset>
        @if (instalmentMethod() && p.instalments.length) {
          <div class="pay-plan" data-testid="payment-plan">
            <strong>{{
              'payment.plan'
                | t
                  : {
                      count: fmt.number(p.instalments.length),
                      amount: fmt.money(p.instalments[1]?.amount ?? p.instalments[0].amount),
                    }
            }}</strong>
            <ol class="pay-plan__list">
              @for (i of p.instalments; track i.number) {
                <li>
                  <span>{{ 'payment.instalment' | t: { n: fmt.number(i.number) } }}</span>
                  <span class="muted">{{
                    $first ? ('payment.dueToday' | t) : fmt.plainDate(i.dueDate)
                  }}</span>
                  <strong class="num">{{ fmt.money(i.amount) }}</strong>
                </li>
              }
            </ol>
          </div>
        }
        <button
          type="button"
          class="btn btn--accent btn--block"
          [disabled]="busy() || !method()"
          (click)="choose()"
          data-testid="pay-now"
        >
          <app-icon name="lock" />
          {{ (busy() ? 'payment.paying' : 'payment.pay') | t: { amount: fmt.money(p.amountDue) } }}
        </button>
        <p class="hint">{{ 'payment.cashNote' | t }}</p>
      } @else {
        <p class="muted">{{ 'payment.notYet' | t }}</p>
      }
    </section>
  `,
})
export class PaymentPanelComponent {
  protected readonly fmt = inject(FormatService);
  private readonly i18n = inject(I18nService);

  readonly payment = input.required<PaymentSummary>();
  readonly busy = input(false);
  readonly pay = output<PaymentMethod>();

  protected readonly method = signal<PaymentMethod | null>(null);
  protected readonly instalmentMethod = computed(
    () => this.method() === 'TABBY' || this.method() === 'TAMARA',
  );

  // "mada ending in 0003" for cards, the method name for the rest.
  // "مدى تنتهي بـ 0003" للبطاقات، واسم الطريقة للباقي.
  protected readonly methodText = computed(() => {
    const p = this.payment();
    const name = p.method ? this.i18n.t(`payment.method.${p.method}`) : '';
    return p.cardLast4
      ? `${name} (${this.i18n.t('payment.card', { brand: p.cardBrand ?? '', last4: p.cardLast4 })})`
      : name;
  });

  constructor() {
    // Pre-select the first offered method, and drop a choice the server no longer offers.
    // نختار أول طريقة متاحة، ونشيل الاختيار لو الخادم ما عاد يعرضه.
    effect(() => {
      const methods = this.payment().methods;
      const current = this.method();
      if (!current || !methods.includes(current)) this.method.set(methods[0] ?? null);
    });
  }

  protected icon(method: PaymentMethod): string {
    return ICONS[method];
  }

  protected choose(): void {
    const m = this.method();
    if (m) this.pay.emit(m);
  }
}
