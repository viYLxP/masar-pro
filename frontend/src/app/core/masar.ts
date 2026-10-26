import { Lang } from './i18n/i18n.service';
import { RequestStatus, ServiceRef, TimeSlot } from './api/models';

// Small domain helpers shared by the public site and the staff app.
// أدوات صغيرة خاصة بالمجال يستخدمها الموقع العام وتطبيق الموظفين.

export const ZONE = 'Asia/Riyadh';

// Same rule as the server's Phones.normalize: 05XXXXXXXX, 5XXXXXXXX, +9665…, 009665… with optional spaces/dashes.
// نفس قاعدة Phones.normalize في الخادم: 05XXXXXXXX أو 5XXXXXXXX أو ‎+9665… أو 009665… مع مسافات أو شرطات اختيارية.
// The server still checks it; this only gives instant feedback while typing.
// الخادم يتحقق منه برضو؛ هذا بس يعطي تنبيه فوري وقت الكتابة.
export const SAUDI_MOBILE = /^\s*(?:\+966|00966|966|0)?[\s-]*5(?:[\s-]*\d){8}\s*$/;

// Reference shape "MS-" + 8 characters from the server's alphabet (no 0/O/1/I/L look-alikes).
// شكل المرجع "MS-" + 8 خانات من حروف الخادم (بدون المتشابهات 0/O/1/I/L).
export const REFERENCE = /^\s*MS-?[A-HJ-KM-NP-Z2-9]{8}\s*$/i;

/** "966512345678" → "0512345678", the way Saudis write and read their mobile numbers. */
/** "966512345678" ← "0512345678"، بالشكل اللي يكتب ويقرأ فيه السعوديون جوالاتهم. */
export function localPhone(phone: string | null | undefined): string {
  if (!phone) return '';
  return phone.startsWith('966') ? `0${phone.slice(3)}` : phone;
}

/** Upper-cases and adds the dash, so "ms7k3qx9pa" still finds "MS-7K3QX9PA". */
/** يحوّل للأحرف الكبيرة ويضيف الشرطة، فـ "ms7k3qx9pa" يلقى "MS-7K3QX9PA". */
export function normalizeReference(raw: string): string {
  const compact = raw.trim().toUpperCase().replace(/^MS-?/, '');
  return `MS-${compact}`;
}

/** The service name in the current language. / اسم الخدمة باللغة الحالية. */
export function serviceName(service: ServiceRef | null | undefined, lang: Lang): string {
  if (!service) return '';
  return lang === 'ar' ? service.nameAr : service.nameEn;
}

// An icon per known service code; anything new added by the admin gets the generic tool icon.
// أيقونة لكل رمز خدمة معروف؛ وأي خدمة جديدة يضيفها المدير تاخذ أيقونة العدة العامة.
const SERVICE_ICONS: Record<string, string> = {
  AC: 'snow',
  CLEANING: 'sparkle',
  PLUMBING: 'drop',
  ELECTRICAL: 'bolt',
  PEST: 'bug',
  MOVING: 'truck',
};
export function serviceIcon(code: string): string {
  return SERVICE_ICONS[code] ?? 'tool';
}

// Badge colour group per status, so the CSS has 4 colours instead of 8 rules.
// مجموعة لون الشارة لكل حالة، حتى يكون في الـ CSS أربعة ألوان بدل ثمان قواعد.
export function statusTone(
  status: RequestStatus,
): 'info' | 'warn' | 'success' | 'muted' | 'danger' {
  switch (status) {
    case 'NEW':
    case 'QUOTED':
      return 'warn';
    case 'ACCEPTED':
    case 'SCHEDULED':
    case 'IN_PROGRESS':
      return 'info';
    case 'COMPLETED':
      return 'success';
    case 'REJECTED':
      return 'danger';
    default:
      return 'muted';
  }
}

/** Today's date in Riyadh as "YYYY-MM-DD" (the browser may be in another time zone). */
/** تاريخ اليوم في الرياض بصيغة "YYYY-MM-DD" (المتصفح ممكن يكون في منطقة زمنية ثانية). */
export function riyadhToday(now: Date = new Date()): string {
  // en-CA formats dates as YYYY-MM-DD, which is exactly the ISO shape the API expects.
  // en-CA ينسّق التاريخ بصيغة YYYY-MM-DD، وهي نفس صيغة ISO اللي يتوقعها الـ API.
  return new Intl.DateTimeFormat('en-CA', { timeZone: ZONE }).format(now);
}

/** Adds whole days to a "YYYY-MM-DD" date without any time-zone shift. */
/** يضيف أيام كاملة لتاريخ "YYYY-MM-DD" بدون أي إزاحة توقيت. */
export function addDays(isoDate: string, days: number): string {
  const d = new Date(`${isoDate}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** The current Riyadh time as minutes since midnight. / الوقت الحالي في الرياض بالدقائق من منتصف الليل. */
export function riyadhMinutesNow(now: Date = new Date()): number {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: ZONE,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(now);
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value ?? 0);
  return get('hour') * 60 + get('minute');
}

// Working day 08:00–20:00 and the end hour of each preferred slot (mirrors TimeSlot.java).
// يوم العمل 08:00–20:00 وساعة نهاية كل فترة مفضلة (مطابق لـ TimeSlot.java).
export const DAY_START_MIN = 8 * 60;
export const DAY_END_MIN = 20 * 60;
export const SLOT_END_MIN: Record<TimeSlot, number> = {
  MORNING: 12 * 60,
  AFTERNOON: 16 * 60,
  EVENING: 20 * 60,
};

/**
 * Half-hour start times a job of this length can use (it must finish by 20:00).
 * أوقات البداية كل نص ساعة اللي يقدر يستخدمها شغل بهالمدة (لازم يخلص قبل 8 الليل).
 */
export function startTimes(durationMinutes: number): string[] {
  const times: string[] = [];
  for (let m = DAY_START_MIN; m + durationMinutes <= DAY_END_MIN; m += 30) {
    times.push(`${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`);
  }
  return times;
}

/** Minutes since Riyadh midnight for an ISO instant (used to place jobs on the day board). */
/** الدقائق من منتصف ليل الرياض لوقت ISO (نستخدمها لوضع الأعمال على لوحة اليوم). */
export function riyadhMinutesOf(iso: string): number {
  return riyadhMinutesNow(new Date(iso));
}
