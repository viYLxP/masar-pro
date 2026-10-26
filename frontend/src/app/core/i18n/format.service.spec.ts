import { TestBed } from '@angular/core/testing';

import { FormatService } from './format.service';
import { I18nService } from './i18n.service';

describe('FormatService', () => {
  let fmt: FormatService;
  let i18n: I18nService;

  beforeEach(() => {
    fmt = TestBed.inject(FormatService);
    i18n = TestBed.inject(I18nService);
  });

  it('shows job times in Riyadh time whatever the browser zone', () => {
    i18n.setLang('en');
    // 07:00–08:30 UTC is 10:00–11:30 in Riyadh. / 07:00–08:30 UTC تساوي 10:00–11:30 في الرياض.
    // Intl puts a narrow no-break space before AM; compare with plain spaces.
    // Intl يحط مسافة ضيقة قبل AM؛ نقارن بمسافات عادية.
    const range = fmt.timeRange('2026-11-01T07:00:00Z', '2026-11-01T08:30:00Z');
    expect(range.replace(/\s/g, ' ')).toBe('10:00 – 11:30 AM');
  });

  it('isolates Arabic time ranges so the bidi algorithm cannot swap them', () => {
    i18n.setLang('ar');
    const range = fmt.timeRange('2026-11-01T07:00:00Z', '2026-11-01T08:30:00Z');
    expect(range.startsWith('⁦') && range.endsWith('⁩')).toBe(true);
    expect(range.indexOf('10:00')).toBeLessThan(range.indexOf('11:30'));
  });

  it('formats durations in words', () => {
    i18n.setLang('en');
    expect(fmt.duration(45)).toBe('45 min');
    expect(fmt.duration(60)).toBe('1 h');
    expect(fmt.duration(90)).toBe('1 h 30 min');
  });

  it('never moves a plain date to another day', () => {
    i18n.setLang('en');
    expect(fmt.plainDate('2026-12-31')).toContain('Dec 31, 2026');
  });
});
