import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { Quote } from '../core/api/models';
import { FormatService } from '../core/i18n/format.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';

/**
 * Read-only quote: items, subtotal, VAT and total. Used on the customer tracking page and in the office.
 * عرض السعر للقراءة فقط: البنود والمجموع والضريبة والإجمالي. يُستخدم في صفحة تتبع العميل وفي المكتب.
 */
@Component({
  selector: 'app-quote-view',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="table-wrap table-wrap--flat">
      <table class="quote-table" data-testid="quote-table">
        <thead>
          <tr>
            <th scope="col">{{ 'quote.description' | t }}</th>
            <th scope="col" class="num">{{ 'quote.quantity' | t }}</th>
            <th scope="col" class="num">{{ 'quote.unitPrice' | t }}</th>
            <th scope="col" class="num">{{ 'quote.lineTotal' | t }}</th>
          </tr>
        </thead>
        <tbody>
          @for (line of quote().lines; track $index) {
            <tr>
              <td class="wrap">
                <bdi>{{ line.description }}</bdi>
              </td>
              <td class="num">{{ fmt.number(line.quantity) }}</td>
              <td class="num">{{ fmt.amount(line.unitPrice) }}</td>
              <td class="num">{{ fmt.amount(line.lineTotal) }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
    <dl class="totals">
      <div>
        <dt>{{ 'quote.subtotal' | t }}</dt>
        <dd>{{ fmt.money(quote().subtotal) }}</dd>
      </div>
      <div>
        <dt>{{ 'quote.vat' | t }}</dt>
        <dd>{{ fmt.money(quote().vat) }}</dd>
      </div>
      <div class="totals__grand">
        <dt>{{ 'quote.total' | t }}</dt>
        <dd data-testid="quote-total">{{ fmt.money(quote().total) }}</dd>
      </div>
    </dl>
    @if (quote().notes) {
      <p class="quote-notes" dir="auto">{{ quote().notes }}</p>
    }
  `,
})
export class QuoteViewComponent {
  protected readonly fmt = inject(FormatService);
  readonly quote = input.required<Quote>();
}
