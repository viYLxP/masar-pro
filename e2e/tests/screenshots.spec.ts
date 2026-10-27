import { Page, test } from '@playwright/test';

import { ADMIN, FAHAD, bookViaApi, login, riyadhDate, staffHeaders } from './helpers';

// Captures README screenshots. Run with SCREENSHOTS=1; skipped otherwise so normal runs stay fast.
// يلتقط صور README. شغّله مع SCREENSHOTS=1؛ وإلا يتم تخطيه حتى يبقى التشغيل العادي سريع.
test.skip(!process.env['SCREENSHOTS'], 'set SCREENSHOTS=1 to capture README images');

async function shot(page: Page, path: string, file: string, fullPage: boolean): Promise<void> {
  await page.goto(path);
  await page.locator('.loading').waitFor({ state: 'detached' }).catch(() => undefined);
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: file, fullPage });
}

for (const lang of ['ar', 'en'] as const) {
  test(`screenshots (${lang})`, async ({ page, browser, request }, info) => {
    const dir = `../docs/screenshots/${info.project.name}`;
    const full = info.project.name === 'desktop';
    await page.addInitScript((l) => localStorage.setItem('masar.lang', l), lang);
    const headers = await staffHeaders(request, ADMIN);
    const scheduled = await (await request.get('/api/requests?status=SCHEDULED', { headers })).json();

    await shot(page, '/', `${dir}/home-${lang}.png`, full);
    await shot(page, '/book', `${dir}/book-${lang}.png`, full);
    await login(page, ADMIN);
    for (const [name, path] of [
      ['dashboard', '/app/dashboard'],
      ['requests', '/app/requests'],
      ['request', `/app/requests/${scheduled.content[0].id}`],
      ['schedule', '/app/schedule'],
    ] as const) {
      await shot(page, path, `${dir}/${name}-${lang}.png`, full);
    }

    const tech = await browser.newPage({ ...info.project.use, locale: 'ar-SA' });
    await tech.addInitScript((l) => localStorage.setItem('masar.lang', l), lang);
    await login(tech, FAHAD);
    await shot(tech, '/app/jobs', `${dir}/jobs-${lang}.png`, full);
    await tech.close();

    // Customer side: the demo account, and a Tamara instalment page for a fresh accepted quote.
    // جهة العميل: الحساب التجريبي، وصفحة تقسيط تمارا لعرض مقبول جديد.
    const booking = await bookViaApi(request, 'Screenshot Customer');
    const quoted = await (
      await request.put(`/api/requests/${booking.id}/quote`, {
        headers,
        data: {
          lines: [{ description: 'Visit', quantity: 1, unitPrice: 300 }],
          validUntil: riyadhDate(7),
          version: 0,
        },
      })
    ).json();
    await request.post(`/api/requests/${booking.id}/quote/send`, {
      headers,
      data: { version: quoted.version },
    });
    await request.post(`/api/public/requests/${booking.reference}/accept`, {
      data: { phone: booking.phone },
    });
    const pay = await (
      await request.post(`/api/public/requests/${booking.reference}/pay`, {
        data: { phone: booking.phone, method: 'TAMARA' },
      })
    ).json();

    const customer = await browser.newPage({ ...info.project.use, locale: 'ar-SA' });
    await customer.addInitScript((l) => localStorage.setItem('masar.lang', l), lang);
    await shot(customer, `/pay/${pay.paymentId}`, `${dir}/pay-${lang}.png`, full);
    await customer.goto('/login');
    await customer.locator('#username').fill('0553001010');
    await customer.locator('#password').fill(process.env['CUSTOMER_PASSWORD'] ?? 'Customer@12345');
    await customer.locator('form button[type=submit]').click();
    await customer.waitForURL(/\/account$/);
    await shot(customer, '/account', `${dir}/account-${lang}.png`, full);
    const rows = await (
      await request.post('/api/auth/login', {
        data: { username: '0553001010', password: process.env['CUSTOMER_PASSWORD'] ?? 'Customer@12345' },
      })
    ).json();
    const mine = await (
      await request.get('/api/account/requests', { headers: { Authorization: `Bearer ${rows.token}` } })
    ).json();
    const payable = mine.find((r: { payable: boolean }) => r.payable) ?? mine[0];
    await shot(customer, `/account/requests/${payable.reference}`, `${dir}/account-request-${lang}.png`, full);
    await customer.close();
  });
}
