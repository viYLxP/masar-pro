import { Page, test } from '@playwright/test';

import { ADMIN, FAHAD, login, staffHeaders } from './helpers';

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
  });
}
