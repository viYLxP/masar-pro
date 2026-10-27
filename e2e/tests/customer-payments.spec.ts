import { APIRequestContext, Page, expect, test } from '@playwright/test';

import {
  ADMIN,
  bookViaApi,
  freeDay,
  login,
  randomPhone,
  riyadhDate,
  staffHeaders,
  unique,
} from './helpers';

/** On phones the site links sit behind the menu button. / في الجوال روابط الموقع ورا زر القائمة. */
async function openMenu(page: Page): Promise<void> {
  const toggle = page.locator('.site-header__toggle');
  if (await toggle.isVisible()) await toggle.click();
}

const CUSTOMER_PASSWORD = process.env['CUSTOMER_PASSWORD'] ?? 'Customer@12345';

/** The office writes a one-line quote and sends it. / المكتب يكتب عرض ببند واحد ويرسله. */
async function quote(request: APIRequestContext, reference: string, price: number): Promise<number> {
  const headers = await staffHeaders(request);
  const list = await (await request.get(`/api/requests?q=${reference}`, { headers })).json();
  const id = list.content[0].id as number;
  let detail = await (await request.get(`/api/requests/${id}`, { headers })).json();
  const saved = await request.put(`/api/requests/${id}/quote`, {
    headers,
    data: {
      lines: [{ description: 'Visit and repair', quantity: 1, unitPrice: price }],
      validUntil: riyadhDate(7),
      version: detail.version,
    },
  });
  expect(saved.ok()).toBeTruthy();
  detail = await saved.json();
  const sent = await request.post(`/api/requests/${id}/quote/send`, {
    headers,
    data: { version: detail.version },
  });
  expect(sent.ok()).toBeTruthy();
  return id;
}

test('a visitor signs up, books, accepts the quote and pays with Tabby instalments', async ({
  page,
  request,
}) => {
  const phone = randomPhone();
  await page.goto('/');
  await openMenu(page);
  await page.getByTestId('nav-register').click();
  await page.getByTestId('register-name').fill(`عميل ${unique()}`);
  await page.getByTestId('register-phone').fill(phone);
  await page.getByTestId('register-password').fill('Secret@123');
  await page.getByTestId('register-confirm').fill('Secret@124');
  await page.getByTestId('register-submit').click();
  await expect(page.locator('#r-confirm-err')).toContainText('غير متطابقتين');
  await page.getByTestId('register-confirm').fill('Secret@123');
  await page.getByTestId('register-submit').click();
  await expect(page).toHaveURL(/\/account$/);
  await expect(page.getByTestId('account-requests')).toContainText('لا توجد لديك طلبات');

  // Booking from the account: the mobile number is the account's and cannot be changed.
  // الحجز من الحساب: رقم الجوال هو رقم الحساب وما يتغير.
  await page.getByTestId('account-book').click();
  await expect(page.locator('#b-phone')).toBeDisabled();
  await expect(page.locator('#b-phone')).toHaveValue(phone);
  await page.locator('.choice', { hasText: 'سباكة' }).click();
  await page.locator('#b-district').fill('الياسمين');
  await page.locator('.segmented__item:not(.segmented__item--off)').last().click();
  await page.getByTestId('booking-submit').click();
  const reference = (await page.getByTestId('booking-reference').textContent())!.trim();

  await quote(request, reference, 300);
  await page.goto('/account');
  await page.getByRole('link', { name: 'عرض الطلب' }).first().click();
  await expect(page).toHaveURL(new RegExp(`/account/requests/${reference}$`));
  await page.getByTestId('quote-accept').click();
  await page.getByTestId('confirm-yes').click();
  await expect(page.getByTestId('payment-due')).toContainText('345');

  await page.getByTestId('method-TABBY').check({ force: true });
  await expect(page.getByTestId('payment-plan').locator('li')).toHaveCount(4);
  await page.getByTestId('pay-now').click();
  await expect(page).toHaveURL(/\/pay\/[0-9a-f-]{36}$/);
  await expect(page.getByTestId('sandbox-banner')).toBeVisible();
  await expect(page.getByTestId('bnpl-plan').locator('li')).toHaveCount(4);
  await page.getByTestId('bnpl-confirm').click();
  await expect(page.getByTestId('pay-success')).toContainText(reference);

  await page.getByRole('link', { name: 'العودة إلى طلبي' }).click();
  await expect(page.getByTestId('payment-paid')).toContainText('تابي');
  await page.goto('/account');
  await expect(page.getByTestId('account-requests')).toContainText('مدفوع');

  // A customer never gets into the staff app. / العميل ما يدخل تطبيق الموظفين أبدًا.
  await page.goto('/app/dashboard');
  await expect(page).toHaveURL(/\/account$/);
});

test('a guest pays by card from the tracking page: a declined card, then mada', async ({
  page,
  request,
}) => {
  const booking = await bookViaApi(request, `Guest Pay ${unique()}`);
  await quote(request, booking.reference, 200);
  await page.goto(`/track?ref=${booking.reference}`);
  await page.locator('#t-phone').fill(booking.phone);
  await page.getByTestId('track-submit').click();
  await page.getByTestId('quote-accept').click();
  await page.getByTestId('confirm-yes').click();
  await expect(page.getByTestId('payment-due')).toContainText('230');
  await page.getByTestId('pay-now').click();

  await page.getByTestId('use-card-0002').click();
  await page.getByTestId('card-submit').click();
  await expect(page.getByTestId('pay-failed')).toContainText('رُفضت البطاقة');

  await page.getByTestId('use-card-0003').click();
  await page.getByTestId('card-submit').click();
  await expect(page.getByTestId('pay-success')).toContainText('230');
  await expect(page.getByTestId('pay-success')).toContainText('mada');

  // The office sees both attempts and the request as paid. / المكتب يشوف المحاولتين والطلب مدفوع.
  await login(page, ADMIN);
  await page.goto(`/app/requests/${booking.id}`);
  await expect(page.getByTestId('detail-payment')).toContainText('مدفوع');
  await expect(page.getByTestId('payment-card').locator('li')).toHaveCount(1);
  await expect(page.getByTestId('payment-card')).toContainText('0003');
});

test('the office records a cash payment for a scheduled visit', async ({ page, request }) => {
  const booking = await bookViaApi(request, `Cash ${unique()}`);
  await quote(request, booking.reference, 100);
  const accepted = await request.post(`/api/public/requests/${booking.reference}/accept`, {
    data: { phone: booking.phone },
  });
  expect(accepted.ok()).toBeTruthy();
  const headers = await staffHeaders(request);
  const technicians = await (await request.get('/api/technicians?city=RIYADH', { headers })).json();
  const fahad = technicians.find((t: { username: string }) => t.username === 'fahad');
  const date = await freeDay(request, 'Fahad', 40);
  const detail = await (await request.get(`/api/requests/${booking.id}`, { headers })).json();
  const scheduled = await request.post(`/api/requests/${booking.id}/schedule`, {
    headers,
    data: { technicianId: fahad.id, date, startTime: '09:00', version: detail.version },
  });
  expect(scheduled.ok()).toBeTruthy();

  await login(page, ADMIN);
  await page.goto(`/app/requests/${booking.id}`);
  await expect(page.getByTestId('detail-payment')).toContainText('غير مدفوع');
  await page.getByTestId('record-cash').click();
  await page.getByTestId('confirm-yes').click();
  await expect(page.getByTestId('detail-payment')).toContainText('مدفوع');
  await expect(page.getByTestId('payment-card')).toContainText('نقدًا');
  await expect(page.getByTestId('record-cash')).toHaveCount(0);
});

test('the demo customer signs in with a mobile number and sees their requests', async ({
  page,
}) => {
  await page.goto('/login');
  // Any common way of writing the number works. / أي طريقة شائعة لكتابة الرقم تشتغل.
  await page.locator('#username').fill('+966 55 300 1010');
  await page.locator('#password').fill(CUSTOMER_PASSWORD);
  await page.locator('form button[type=submit]').click();
  await expect(page).toHaveURL(/\/account$/);
  const rows = page.getByTestId('account-requests').locator('.account-row');
  await expect(rows).not.toHaveCount(0);
  await expect(page.getByTestId('account-requests')).toContainText('مدفوع');
  await openMenu(page);
  await expect(page.getByTestId('nav-account')).toBeVisible();
});

test('the admin sees customers in their own tab and can disable one', async ({ page, request }) => {
  const phone = randomPhone();
  const res = await request.post('/api/auth/register', {
    data: { fullName: `Disable Me ${unique()}`, phone, email: null, password: 'Secret@123' },
  });
  expect(res.status()).toBe(201);
  const username = `966${phone.slice(1)}`;

  await login(page, ADMIN);
  await page.goto('/app/users');
  await expect(page.getByTestId('users-table')).not.toContainText(username);
  await page.getByTestId('tab-customers').click();
  await page.getByTestId(`toggle-${username}`).click();
  await expect(page.getByTestId(`toggle-${username}`)).toContainText('تفعيل');

  const blocked = await request.post('/api/auth/login', {
    data: { username: phone, password: 'Secret@123' },
  });
  expect(blocked.status()).toBe(401);
});
