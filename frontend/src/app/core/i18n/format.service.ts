import { Injectable, computed, inject } from '@angular/core';

import { I18nService } from './i18n.service';

/**
 * Locale-aware number, money and date formatting with the browser's Intl API.
 * تنسيق الأرقام والمبالغ والتواريخ حسب اللغة باستخدام Intl المدمج في المتصفح.
 *
 * Formatters are created once per language (computed) because building Intl objects is relatively expensive.
 * ننشئ أدوات التنسيق مرة وحدة لكل لغة (computed) لأن إنشاء كائنات Intl مكلف نسبيًا.
 */
@Injectable({ providedIn: 'root' })
export class FormatService {
  private readonly i18n = inject(I18nService);

  private readonly moneyFmt = computed(
    () =>
      new Intl.NumberFormat(this.i18n.locale(), {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }),
  );
  private readonly numberFmt = computed(() => new Intl.NumberFormat(this.i18n.locale()));
  private readonly percentFmt = computed(
    () => new Intl.NumberFormat(this.i18n.locale(), { style: 'percent', maximumFractionDigits: 0 }),
  );
  private readonly dateFmt = computed(
    () =>
      new Intl.DateTimeFormat(this.i18n.locale(), { dateStyle: 'medium', timeZone: 'Asia/Riyadh' }),
  );
  private readonly dateTimeFmt = computed(
    () =>
      new Intl.DateTimeFormat(this.i18n.locale(), {
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone: 'Asia/Riyadh',
      }),
  );
  private readonly timeFmt = computed(
    () =>
      new Intl.DateTimeFormat(this.i18n.locale(), {
        hour: 'numeric',
        minute: '2-digit',
        timeZone: 'Asia/Riyadh',
      }),
  );
  private readonly clockFmt = computed(
    () =>
      new Intl.DateTimeFormat(this.i18n.locale(), {
        hour: 'numeric',
        minute: '2-digit',
        timeZone: 'UTC',
      }),
  );
  // Plain calendar dates ("2026-10-26") are formatted in UTC so no time zone can move them to another day.
  // التواريخ المجردة ("2026-10-26") تُنسّق بتوقيت UTC حتى ما تنقلها أي منطقة زمنية ليوم ثاني.
  private readonly plainDateFmt = computed(
    () =>
      new Intl.DateTimeFormat(this.i18n.locale(), {
        weekday: 'short',
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        timeZone: 'UTC',
      }),
  );
  private readonly longDayFmt = computed(
    () =>
      new Intl.DateTimeFormat(this.i18n.locale(), {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric',
        timeZone: 'UTC',
      }),
  );

  /** "1,234.50 SAR" / "1,234.50 ر.س" */
  money(value: number | null | undefined): string {
    return `${this.moneyFmt().format(value ?? 0)} ${this.i18n.t('app.currency')}`;
  }

  /** Number with 2 decimals, without currency (for table cells). / رقم بمنزلتين بدون عملة (لخانات الجداول). */
  amount(value: number | null | undefined): string {
    return this.moneyFmt().format(value ?? 0);
  }

  /**
   * Whole percentage such as "75%", placed the way the current language expects.
   * نسبة مئوية مثل "75%"، بالشكل اللي تتوقعه اللغة الحالية.
   */
  percent(value: number): string {
    return this.percentFmt().format(value / 100);
  }

  number(value: number | null | undefined): string {
    return this.numberFmt().format(value ?? 0);
  }

  /** Server timestamps are UTC; we always show them in Riyadh time. / أوقات الخادم بتوقيت UTC ونعرضها دائمًا بتوقيت الرياض. */
  date(iso: string | null | undefined): string {
    return iso ? this.dateFmt().format(new Date(iso)) : '—';
  }

  dateTime(iso: string | null | undefined): string {
    return iso ? this.dateTimeFmt().format(new Date(iso)) : '—';
  }

  /** "Mon, 26 Oct 2026" for a plain date such as a preferred day or a quote's "valid until". */
  /** "الاثنين، 26 أكتوبر 2026" لتاريخ مجرد مثل اليوم المفضّل أو صلاحية العرض. */
  plainDate(isoDate: string | null | undefined): string {
    return isoDate ? this.plainDateFmt().format(new Date(`${isoDate}T00:00:00Z`)) : '—';
  }

  /** Full weekday and date, for the schedule page heading. / اليوم والتاريخ كاملين لعنوان صفحة الجدول. */
  longDay(isoDate: string): string {
    return this.longDayFmt().format(new Date(`${isoDate}T00:00:00Z`));
  }

  /** Clock time in Riyadh, e.g. "2:30 PM" / "2:30 م". / الساعة بتوقيت الرياض. */
  time(iso: string | null | undefined): string {
    return iso ? this.timeFmt().format(new Date(iso)) : '—';
  }

  /** "2:00 – 3:30 PM" for a booked job. / "2:00 – 3:30 م" لموعد محجوز. */
  timeRange(start: string | null | undefined, end: string | null | undefined): string {
    if (!start || !end) return '—';
    const range = this.timeFmt().formatRange(new Date(start), new Date(end));
    // In Arabic pages the Latin digits of "1:30–2:30 م" would be reordered by the bidi algorithm and read
    // as "2:30–1:30"; a left-to-right isolate (LRI…PDI) keeps the range in order.
    // في الصفحات العربية خوارزمية الاتجاه تعكس أرقام "1:30–2:30 م" فتنقرأ "2:30–1:30"؛
    // والعزل من اليسار لليمين (LRI…PDI) يحافظ على ترتيب المدى.
    return this.i18n.lang() === 'ar' ? `\u2066${range}\u2069` : range;
  }

  /** A "HH:mm" clock string from a time picker, shown in the user's language. / وقت "HH:mm" بلغة المستخدم. */
  clock(hhmm: string): string {
    const [h, m] = hhmm.split(':').map(Number);
    // Any fixed day works: we only format the hour and minute, in UTC so nothing shifts.
    // أي يوم ثابت ينفع: ننسّق الساعة والدقيقة فقط، بتوقيت UTC حتى ما يتزحزح شي.
    return this.clockFmt().format(Date.UTC(2000, 0, 1, h, m));
  }

  /** 90 → "1 h 30 min", 60 → "1 h", 45 → "45 min". */
  duration(minutes: number): string {
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h === 0) return this.i18n.t('common.minutes', { count: m });
    if (m === 0) return this.i18n.t('common.hours', { count: h });
    return this.i18n.t('common.hoursMinutes', { h, m });
  }
}
