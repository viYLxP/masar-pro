import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';

import { ar } from './ar';
import { Dictionary, en } from './en';

export type Lang = 'ar' | 'en';

// localStorage key for the saved language choice.
// مفتاح localStorage لحفظ اللغة المختارة.
const STORAGE_KEY = 'masar.lang';
const DICTIONARIES: Record<Lang, Dictionary> = { ar, en };

/**
 * Holds the current language and translates keys. Built on signals, so every template that calls t()
 * re-renders automatically when the language changes, without reloading the page or losing form input.
 *
 * يحفظ اللغة الحالية ويترجم المفاتيح. مبني على الـ signals، فكل قالب يستدعي t() يتحدّث تلقائيًا
 * لما تتغير اللغة، بدون إعادة تحميل الصفحة ولا ضياع البيانات المكتوبة في النماذج.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly document = inject(DOCUMENT);

  // Current language; private writable signal, exposed read-only below.
  // اللغة الحالية؛ signal قابل للكتابة داخليًا فقط، ونعرضه للقراءة تحت.
  private readonly langSignal = signal<Lang>(this.initialLang());
  readonly lang = this.langSignal.asReadonly();
  readonly dir = computed(() => (this.lang() === 'ar' ? 'rtl' : 'ltr'));

  // Locale for Intl formatters: Saudi Arabic with Gregorian calendar and Latin digits (common in Saudi business apps).
  // اللغة المحلية لدوال التنسيق: عربي سعودي بالتقويم الميلادي والأرقام اللاتينية (الشائع في أنظمة الأعمال السعودية).
  readonly locale = computed(() => (this.lang() === 'ar' ? 'ar-SA-u-ca-gregory-nu-latn' : 'en-US'));

  constructor() {
    // Keep <html lang dir> and localStorage in sync with the signal; runs once now and on every change.
    // نخلي <html lang dir> وlocalStorage متزامنين مع الـ signal؛ يشتغل مرة الحين ومع كل تغيير.
    effect(() => {
      const lang = this.lang();
      const html = this.document.documentElement;
      html.lang = lang;
      html.dir = this.dir();
      this.document.title = DICTIONARIES[lang].app.name;
      try {
        localStorage.setItem(STORAGE_KEY, lang);
      } catch {
        // Private mode or blocked storage: the app still works, the choice just is not remembered.
        // وضع التصفح الخاص أو تخزين محجوب: التطبيق يشتغل عادي، بس ما ينحفظ الاختيار.
      }
    });
  }

  setLang(lang: Lang): void {
    this.langSignal.set(lang);
  }

  toggle(): void {
    this.setLang(this.lang() === 'ar' ? 'en' : 'ar');
  }

  /**
   * Translates a dotted key such as "products.title" and fills {placeholders} from params.
   * Falls back to English, then to the key itself, so a missing text is visible instead of blank.
   *
   * يترجم مفتاحًا مثل "products.title" ويعبّي {العناصر} من params.
   * لو ما لقى النص يرجع للإنجليزي ثم للمفتاح نفسه، حتى يكون النقص ظاهر بدل فراغ.
   */
  t(key: string, params?: Record<string, string | number>): string {
    const text = lookup(DICTIONARIES[this.lang()], key) ?? lookup(en, key) ?? key;
    if (!params) {
      return text;
    }
    // Replace each {name} with its value; unknown placeholders are left as-is.
    // نستبدل كل {name} بقيمته، والعناصر غير المعروفة تبقى كما هي.
    return text.replace(/\{(\w+)\}/g, (match, name: string) =>
      params[name] !== undefined ? String(params[name]) : match,
    );
  }

  /**
   * Order of preference: saved choice → browser language → Arabic (primary market).
   * ترتيب الأولوية: الاختيار المحفوظ ← لغة المتصفح ← العربية (السوق الأساسي).
   */
  private initialLang(): Lang {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved === 'ar' || saved === 'en') {
        return saved;
      }
    } catch {
      // Ignore storage errors and use the next option.
      // نتجاهل أخطاء التخزين وننتقل للخيار التالي.
    }
    const browser = this.document.defaultView?.navigator.language ?? '';
    return browser.toLowerCase().startsWith('en') ? 'en' : 'ar';
  }
}

// Walks the nested dictionary one key segment at a time; returns undefined if any segment is missing.
// يمشي في القاموس المتداخل جزء جزء من المفتاح، ويرجّع undefined لو نقص أي جزء.
function lookup(dictionary: unknown, key: string): string | undefined {
  let node: unknown = dictionary;
  for (const part of key.split('.')) {
    if (node === null || typeof node !== 'object') {
      return undefined;
    }
    node = (node as Record<string, unknown>)[part];
  }
  return typeof node === 'string' ? node : undefined;
}
