import { Page, expect, test } from '@playwright/test';

import { FAHAD, NOURA, bookViaApi, freeDay, login, riyadhDate, unique } from './helpers';

// The whole life of a request across three people: office, customer and technician, each in their own browser.
// حياة الطلب كاملة بين ثلاثة أشخاص: المكتب والعميل والفني، كل واحد في متصفحه.
test.describe('request lifecycle', () => {
  test.skip(({ isMobile }) => isMobile, 'one full run is enough; layouts are checked in i18n-layout');

  test('quote → customer accepts → schedule → technician starts and completes', async ({
    page,
    browser,
    request,
  }) => {
    const booking = await bookViaApi(request, `Flow Customer ${unique()}`);
    const status = (p: Page) => p.getByTestId('detail-status');

    // 1) Office prepares a two-line quote and sends it. / المكتب يجهّز عرض ببندين ويرسله.
    await login(page, NOURA);
    await page.goto(`/app/requests/${booking.id}`);
    await expect(page.getByTestId('detail-reference')).toHaveText(booking.reference);
    await page.getByTestId('create-quote').click();
    await page.locator('#q-price-0').fill('180');
    await page.getByTestId('quote-add-line').click();
    await page.locator('#q-desc-1').fill('Gas refill');
    await page.locator('#q-price-1').fill('120');
    // (180 + 120) + 15% VAT = 345.00
    await expect(page.getByTestId('quote-editor-total')).toContainText('345.00');
    await page.getByTestId('quote-save').click();
    await expect(page.getByTestId('quote-total')).toContainText('345.00');
    await expect(page.getByTestId('quote-card')).toContainText('مسودة');

    await page.getByTestId('send-quote').click();
    await page.getByTestId('confirm-yes').click();
    await expect(status(page)).toHaveText('أُرسل العرض');

    // 2) The customer sees the same total and accepts. / العميل يشوف نفس الإجمالي ويقبل.
    const customer = await browser.newPage();
    await customer.goto(`/track?ref=${booking.reference}`);
    await customer.locator('#t-phone').fill(booking.phone);
    await customer.getByTestId('track-submit').click();
    await expect(customer.getByTestId('quote-total')).toContainText('345.00');
    await customer.getByTestId('quote-accept').click();
    await customer.getByTestId('confirm-yes').click();
    await expect(customer.getByTestId('track-status-text')).toContainText('تم قبول العرض');

    // 3) Office books Fahad (AC, Riyadh) on a free day; the proposal shows on his line first.
    // 3) المكتب يحجز فهد (مكيفات، الرياض) في يوم فاضي؛ والاقتراح يظهر على سطره أول.
    await page.reload();
    await expect(status(page)).toHaveText('مقبول');
    await page.getByTestId('open-schedule').click();
    const dialog = page.getByTestId('schedule-dialog');
    await dialog.getByTestId('schedule-technician').selectOption({ label: 'Fahad Al-Qahtani' });
    await dialog.locator('#s-date').fill(await freeDay(request, 'Fahad', 10));
    await dialog.getByTestId('schedule-time').selectOption('10:00');
    await expect(dialog.getByTestId('timeline-proposal')).toBeVisible();
    await dialog.getByTestId('schedule-submit').click();
    await expect(status(page)).toHaveText('مجدول');
    await expect(page.getByTestId('visit-technician')).toContainText('Fahad Al-Qahtani');

    // 4) Fahad finds it in "My jobs", starts and completes it. / فهد يلقاه في "أعمالي"، يبدأ وينهي.
    const techContext = await browser.newContext();
    const tech = await techContext.newPage();
    await login(tech, FAHAD);
    await expect(tech).toHaveURL(/\/app\/jobs$/);
    const card = tech.locator('.job-card', { hasText: 'Flow Customer' }).filter({
      has: tech.locator(`a[href="/app/requests/${booking.id}"]`),
    });
    await card.getByRole('link', { name: 'فتح العمل' }).click();
    await tech.getByTestId('start-job').click();
    await tech.getByTestId('confirm-yes').click();
    await expect(status(tech)).toHaveText('قيد التنفيذ');
    await tech.getByTestId('complete-note').fill('Cleaned filters and refilled gas.');
    await tech.getByTestId('complete-job').click();
    await expect(status(tech)).toHaveText('مكتمل');

    // 5) The customer's page shows the job as done. / صفحة العميل تبيّن إن الشغل خلص.
    await customer.getByRole('button', { name: 'تحديث' }).click();
    await expect(customer.getByTestId('track-status-text')).toContainText('تم إنجاز العمل');
    await techContext.close();
  });

  test('the customer can decline with a reason, and the office reopens it', async ({
    page,
    request,
  }) => {
    const booking = await bookViaApi(request, `Decline Customer ${unique()}`);
    await login(page, NOURA);
    await page.goto(`/app/requests/${booking.id}`);
    await page.getByTestId('create-quote').click();
    await page.locator('#q-price-0').fill('500');
    await page.getByTestId('quote-save').click();
    await page.getByTestId('send-quote').click();
    await page.getByTestId('confirm-yes').click();
    await expect(page.getByTestId('detail-status')).toHaveText('أُرسل العرض');

    await page.goto(`/track?ref=${booking.reference}`);
    await page.locator('#t-phone').fill(booking.phone);
    await page.getByTestId('track-submit').click();
    await page.getByTestId('quote-reject').click();
    // The reason is required. / السبب مطلوب.
    await page.getByTestId('prompt-ok').click();
    await expect(page.locator('#prompt-error')).toContainText('مطلوب');
    await page.getByTestId('prompt-input').fill('السعر أعلى من المتوقع');
    await page.getByTestId('prompt-ok').click();
    await expect(page.getByTestId('track-status-text')).toContainText('رفضت عرض السعر');

    await page.goto(`/app/requests/${booking.id}`);
    await expect(page.getByText('رفض العميل: السعر أعلى من المتوقع')).toBeVisible();
    await page.getByRole('button', { name: 'تجهيز عرض جديد' }).click();
    await page.getByTestId('confirm-yes').click();
    await expect(page.getByTestId('detail-status')).toHaveText('جديد');
    // The old quote is back as an editable draft. / العرض القديم رجع مسودة قابلة للتعديل.
    await expect(page.getByTestId('edit-quote')).toBeVisible();
  });

  test('a busy technician cannot be double-booked', async ({ page, request }) => {
    const headers = { Authorization: '' };
    const first = await bookViaApi(request, `Busy A ${unique()}`);
    const second = await bookViaApi(request, `Busy B ${unique()}`);
    const auth = await request.post('/api/auth/login', { data: NOURA });
    headers.Authorization = `Bearer ${(await auth.json()).token}`;
    const day = await freeDay(request, 'Fahad', 70);
    const fahad = (await (await request.get('/api/technicians?city=RIYADH', { headers })).json()).find(
      (u: { username: string }) => u.username === 'fahad',
    );
    // Put the first request through quote + accept + schedule at 14:00 via the API.
    // نمشّي الطلب الأول عبر العرض والقبول والجدولة الساعة 14:00 عن طريق الـ API.
    let detail = await (
      await request.put(`/api/requests/${first.id}/quote`, {
        headers,
        data: { lines: [{ description: 'Visit', quantity: 1, unitPrice: 150 }], validUntil: riyadhDate(7), version: 0 },
      })
    ).json();
    detail = await (await request.post(`/api/requests/${first.id}/quote/send`, { headers, data: { version: detail.version } })).json();
    await request.post(`/api/public/requests/${first.reference}/accept`, { data: { phone: first.phone } });
    detail = await (await request.get(`/api/requests/${first.id}`, { headers })).json();
    const booked = await request.post(`/api/requests/${first.id}/schedule`, {
      headers,
      data: { technicianId: fahad.id, date: day, startTime: '14:00', version: detail.version },
    });
    expect(booked.ok()).toBeTruthy();

    detail = await (
      await request.put(`/api/requests/${second.id}/quote`, {
        headers,
        data: { lines: [{ description: 'Visit', quantity: 1, unitPrice: 150 }], validUntil: riyadhDate(7), version: 0 },
      })
    ).json();
    await request.post(`/api/requests/${second.id}/quote/send`, { headers, data: { version: detail.version } });
    await request.post(`/api/public/requests/${second.reference}/accept`, { data: { phone: second.phone } });

    // In the UI, 14:30 overlaps the 90-minute job at 14:00, and the server says so in plain words.
    // في الواجهة، 14:30 يتداخل مع شغل الـ 90 دقيقة الساعة 14:00، والخادم يوضح ذلك بكلام واضح.
    await login(page, NOURA);
    await page.goto(`/app/requests/${second.id}`);
    await page.getByTestId('open-schedule').click();
    const dialog = page.getByTestId('schedule-dialog');
    await dialog.getByTestId('schedule-technician').selectOption({ label: 'Fahad Al-Qahtani' });
    await dialog.locator('#s-date').fill(day);
    await dialog.getByTestId('schedule-time').selectOption('14:30');
    // Fahad's existing job is drawn on the board next to the proposal.
    // شغل فهد الموجود مرسوم على اللوحة جنب الاقتراح.
    await expect(dialog.locator('.timeline-row--on .timeline-job--info')).toHaveCount(1);
    await dialog.getByTestId('schedule-submit').click();
    await expect(dialog.getByTestId('schedule-error')).toContainText(first.reference);
    await expect(page.getByTestId('detail-status')).toHaveText('مقبول');
  });
});
