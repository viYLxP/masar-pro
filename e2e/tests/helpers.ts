import { APIRequestContext, Page, expect } from '@playwright/test';

// Demo accounts created by the seeder (passwords come from .env; these are the documented defaults).
// الحسابات التجريبية اللي ينشئها الـ seeder (كلمات المرور من .env، وهذي القيم الافتراضية الموثقة).
const STAFF_PASSWORD = process.env['STAFF_PASSWORD'] ?? 'Staff@12345';
export const ADMIN = { username: 'admin', password: process.env['ADMIN_PASSWORD'] ?? 'Admin@12345' };
export const NOURA = { username: 'noura', password: STAFF_PASSWORD };
export const FAHAD = { username: 'fahad', password: STAFF_PASSWORD };
export const OMAR = { username: 'omar', password: STAFF_PASSWORD };
type Account = typeof ADMIN;

/** Signs in through the real form and waits for the role's home page. / يدخل من النموذج الحقيقي وينتظر صفحة الدور. */
export async function login(page: Page, user: Account = ADMIN): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(user.username);
  await page.locator('#password').fill(user.password);
  await page.locator('form button[type=submit]').click();
  await expect(page).toHaveURL(/\/app\/(dashboard|jobs)$/);
}

export async function staffHeaders(request: APIRequestContext, user: Account = NOURA) {
  const auth = await request.post('/api/auth/login', { data: user });
  expect(auth.ok()).toBeTruthy();
  return { Authorization: `Bearer ${(await auth.json()).token}` };
}

/** Today in Riyadh, then +days, as YYYY-MM-DD. / اليوم في الرياض مضاف له أيام، بصيغة YYYY-MM-DD. */
export function riyadhDate(days = 0): string {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Riyadh' }).format(new Date());
  const d = new Date(`${today}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** A random Saudi mobile, so each run tracks its own request. / جوال سعودي عشوائي حتى كل تشغيل يتتبع طلبه. */
export function randomPhone(): string {
  return `05${String(Math.floor(Math.random() * 1e8)).padStart(8, '0')}`;
}

export interface Booking {
  reference: string;
  phone: string;
  id: number;
}

/**
 * Books an AC visit in Riyadh through the public API and finds its id as the office, for tests that
 * start later in the flow.
 * يحجز زيارة مكيفات في الرياض عن طريق الـ API العام ويجيب رقمه الداخلي كموظف مكتب، للاختبارات
 * اللي تبدأ من مرحلة متأخرة.
 */
export async function bookViaApi(request: APIRequestContext, name: string): Promise<Booking> {
  const services = await (await request.get('/api/public/services')).json();
  const ac = services.find((s: { code: string }) => s.code === 'AC');
  const phone = randomPhone();
  const res = await request.post('/api/public/requests', {
    data: {
      customerName: name,
      phone,
      city: 'RIYADH',
      district: 'Al Olaya',
      propertyType: 'APARTMENT',
      serviceTypeId: ac.id,
      preferredDate: riyadhDate(3),
      preferredSlot: 'MORNING',
    },
  });
  expect(res.status()).toBe(201);
  const { reference } = await res.json();
  const headers = await staffHeaders(request);
  const list = await (await request.get(`/api/requests?q=${reference}`, { headers })).json();
  return { reference, phone, id: list.content[0].id };
}

/**
 * First day from `fromDays` ahead on which the technician has no jobs at all, read from the real schedule.
 * Reruns on the same database therefore never collide with bookings made by earlier runs.
 * أول يوم بعد `fromDays` ما عند الفني فيه أي شغل، من الجدول الحقيقي.
 * فإعادة التشغيل على نفس قاعدة البيانات ما تتعارض أبدًا مع حجوزات التشغيلات السابقة.
 */
export async function freeDay(
  request: APIRequestContext,
  technician: string,
  fromDays: number,
): Promise<string> {
  const headers = await staffHeaders(request);
  for (let offset = fromDays; offset < fromDays + 60; offset++) {
    const date = riyadhDate(offset);
    const board = await (await request.get(`/api/schedule?date=${date}&city=RIYADH`, { headers })).json();
    const row = board.technicians.find((t: { fullName: string }) => t.fullName.startsWith(technician));
    if (row && row.jobs.length === 0) return date;
  }
  throw new Error(`no free day for ${technician}`);
}

/**
 * Asserts the page has no horizontal scroll, the most common RTL/mobile layout bug.
 * يتأكد إن الصفحة ما فيها تمرير أفقي، وهو أشهر خلل في التصميم مع RTL والجوال.
 */
export async function expectNoHorizontalScroll(page: Page): Promise<void> {
  // Compare with screen.width, not innerWidth: a mobile browser widens the layout viewport (and innerWidth)
  // to fit overflowing content, which would hide the bug.
  // نقارن مع screen.width وليس innerWidth: متصفح الجوال يوسّع منطقة العرض (و innerWidth) لتناسب المحتوى الفائض،
  // وهذا كان بيخفي الخلل.
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - Math.min(window.innerWidth, window.screen.width),
  );
  expect(overflow, 'page is wider than the viewport').toBeLessThanOrEqual(1);
}

// Unique suffix so repeated runs never collide on names.
// لاحقة فريدة حتى التشغيل المتكرر ما يتعارض في الأسماء.
export const unique = () => Date.now().toString(36).toUpperCase();
