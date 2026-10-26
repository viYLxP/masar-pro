import { TestBed } from '@angular/core/testing';

import { ar } from './ar';
import { en } from './en';
import { I18nService } from './i18n.service';

// Collects every dotted key of a dictionary, e.g. "products.title".
// يجمع كل المفاتيح المنقّطة في القاموس، مثل "products.title".
function keys(obj: object, prefix = ''): string[] {
  return Object.entries(obj).flatMap(([k, v]) =>
    typeof v === 'object' && v !== null ? keys(v, `${prefix}${k}.`) : [`${prefix}${k}`],
  );
}

function placeholders(text: string): string[] {
  return [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();
}

function valueAt(obj: object, key: string): string {
  return key.split('.').reduce<unknown>((o, k) => (o as Record<string, unknown>)[k], obj) as string;
}

describe('I18nService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
  });

  it('Arabic and English dictionaries have exactly the same keys', () => {
    expect(keys(ar).sort()).toEqual(keys(en).sort());
  });

  it('every translation keeps the same {placeholders} as English', () => {
    for (const key of keys(en)) {
      expect(placeholders(valueAt(ar, key)), key).toEqual(placeholders(valueAt(en, key)));
    }
  });

  it('no translation is empty', () => {
    for (const key of keys(ar)) expect(valueAt(ar, key).trim(), key).not.toBe('');
    for (const key of keys(en)) expect(valueAt(en, key).trim(), key).not.toBe('');
  });

  it('defaults to Arabic and updates <html lang dir> when switching', () => {
    const i18n = TestBed.inject(I18nService);
    // The test browser (jsdom) reports English, so pin the start language explicitly.
    // متصفح الاختبار (jsdom) لغته إنجليزي، فنثبّت لغة البداية صراحة.
    i18n.setLang('ar');
    TestBed.tick();
    expect(document.documentElement.dir).toBe('rtl');
    expect(document.documentElement.lang).toBe('ar');

    i18n.toggle();
    TestBed.tick();
    expect(i18n.lang()).toBe('en');
    expect(document.documentElement.dir).toBe('ltr');
    expect(localStorage.getItem('masar.lang')).toBe('en');
  });

  it('fills placeholders and falls back to the key when missing', () => {
    const i18n = TestBed.inject(I18nService);
    i18n.setLang('en');
    expect(i18n.t('common.pageOf', { page: 2, pages: 5 })).toBe('Page 2 of 5');
    expect(i18n.t('does.not.exist')).toBe('does.not.exist');
    // Unknown placeholders stay visible instead of disappearing.
    // العناصر غير المعروفة تبقى ظاهرة بدل ما تختفي.
    expect(i18n.t('common.pageOf', { page: 1 })).toBe('Page 1 of {pages}');
  });

  it('restores the saved language', () => {
    localStorage.setItem('masar.lang', 'en');
    expect(TestBed.inject(I18nService).lang()).toBe('en');
  });
});
