import { TestBed } from '@angular/core/testing';

import { PaymentMethod, PaymentSummary } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { PaymentPanelComponent } from './payment-panel.component';

const unpaid: PaymentSummary = {
  status: 'UNPAID',
  payable: true,
  amountDue: 345,
  methods: ['CARD', 'APPLE_PAY', 'TABBY', 'TAMARA'],
  instalments: [
    { number: 1, amount: 86.25, dueDate: '2026-10-27' },
    { number: 2, amount: 86.25, dueDate: '2026-11-26' },
    { number: 3, amount: 86.25, dueDate: '2026-12-26' },
    { number: 4, amount: 86.25, dueDate: '2027-01-25' },
  ],
  method: null,
  cardBrand: null,
  cardLast4: null,
  paidAt: null,
};

function render(payment: PaymentSummary) {
  const fixture = TestBed.createComponent(PaymentPanelComponent);
  fixture.componentRef.setInput('payment', payment);
  const chosen: PaymentMethod[] = [];
  fixture.componentInstance.pay.subscribe((m) => chosen.push(m));
  fixture.detectChanges();
  const el = fixture.nativeElement as HTMLElement;
  const q = (id: string) => el.querySelector<HTMLElement>(`[data-testid="${id}"]`);
  return { fixture, el, q, chosen };
}

describe('PaymentPanelComponent', () => {
  beforeEach(() => TestBed.inject(I18nService).setLang('en'));

  it('offers the server methods, card first, and pays with the chosen one', () => {
    const { fixture, q, chosen } = render(unpaid);
    expect(q('method-CARD')).not.toBeNull();
    expect(q('method-CASH')).toBeNull();
    expect(q('payment-plan')).toBeNull();
    q('pay-now')!.click();
    q('method-TAMARA')!.click();
    fixture.detectChanges();
    q('pay-now')!.click();
    expect(chosen).toEqual(['CARD', 'TAMARA']);
  });

  it('shows the 4-payment plan for Tabby and Tamara', () => {
    const { fixture, q } = render(unpaid);
    q('method-TABBY')!.click();
    fixture.detectChanges();
    const plan = q('payment-plan')!;
    expect(plan.querySelectorAll('li')).toHaveLength(4);
    expect(plan.textContent).toContain('Today');
  });

  it('hides the methods the server stopped offering', () => {
    const { q } = render({ ...unpaid, methods: ['CARD', 'APPLE_PAY'], instalments: [] });
    expect(q('method-TABBY')).toBeNull();
    expect(q('method-APPLE_PAY')).not.toBeNull();
  });

  it('shows how a paid request was paid instead of the methods', () => {
    const { q } = render({
      ...unpaid,
      status: 'PAID',
      payable: false,
      methods: [],
      method: 'CARD',
      cardBrand: 'mada',
      cardLast4: '0003',
      paidAt: '2026-10-27T10:00:00Z',
    });
    expect(q('pay-now')).toBeNull();
    expect(q('payment-paid')!.textContent).toContain('mada ending in 0003');
  });

  it('explains that payment opens after the quote is accepted', () => {
    const { el, q } = render({ ...unpaid, payable: false, methods: [], instalments: [] });
    expect(q('pay-now')).toBeNull();
    expect(el.textContent).toContain('after you accept the quote');
  });
});
