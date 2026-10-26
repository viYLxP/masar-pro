import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { RequestDetail } from '../../core/api/models';
import { I18nService } from '../../core/i18n/i18n.service';
import { QuoteEditorComponent } from './quote-editor.component';

const REQUEST = {
  id: 7,
  version: 3,
  status: 'NEW',
  service: { id: 1, code: 'AC', nameAr: 'صيانة مكيفات', nameEn: 'AC maintenance' },
  quote: null,
} as unknown as RequestDetail;

describe('QuoteEditorComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.inject(I18nService).setLang('en');
  });

  function create() {
    const fixture = TestBed.createComponent(QuoteEditorComponent);
    fixture.componentRef.setInput('request', REQUEST);
    fixture.detectChanges();
    return fixture;
  }

  it('starts a new quote with the service name as its first item', () => {
    const fixture = create();
    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(
      '#q-desc-0',
    );
    expect(input?.value).toBe('AC maintenance');
  });

  it('previews VAT at 15% with half-up rounding in halalas', () => {
    const fixture = create();
    const form = fixture.componentInstance['form'];
    // 3 × 33.35 = 100.05; VAT 15.0075 rounds to 15.01. / الضريبة 15.0075 تتقرب إلى 15.01.
    form.controls.lines.at(0).patchValue({ quantity: 3, unitPrice: 33.35 });
    fixture.detectChanges();
    const totals = fixture.componentInstance['totals']();
    expect(totals).toEqual({ subtotal: 100.05, vat: 15.01, total: 115.06 });
  });

  it('sends the lines with the loaded version and does not submit an invalid form', () => {
    const fixture = create();
    const http = TestBed.inject(HttpTestingController);
    const form = fixture.componentInstance['form'];
    // No price yet: nothing is sent. / ما فيه سعر: ما ينرسل شي.
    fixture.componentInstance['submit']();
    http.expectNone('/api/requests/7/quote');

    form.controls.lines.at(0).patchValue({ unitPrice: 150 });
    fixture.componentInstance['submit']();
    const req = http.expectOne('/api/requests/7/quote');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toMatchObject({
      version: 3,
      lines: [{ description: 'AC maintenance', quantity: 1, unitPrice: 150 }],
    });
    http.verify();
  });
});
