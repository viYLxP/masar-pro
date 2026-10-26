import { Page, expect, test } from '@playwright/test';

import { ADMIN, FAHAD, expectNoHorizontalScroll, login, staffHeaders } from './helpers';

const PUBLIC = ['/', '/book', '/track'];
const OFFICE = [
  '/app/dashboard',
  '/app/requests',
  '/app/requests/new',
  '/app/schedule',
  '/app/services',
  '/app/users',
];

function collectErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push(msg.text());
  });
  page.on('pageerror', (err) => errors.push(err.message));
  return errors;
}

async function checkPage(page: Page, path: string): Promise<void> {
  await page.goto(path);
  await expect(page.locator('h1').first()).toBeVisible();
  await expect(page.locator('.loading')).toHaveCount(0);
  await expectNoHorizontalScroll(page);
}

for (const lang of ['ar', 'en'] as const) {
  test(`every page renders without horizontal scroll or console errors (${lang})`, async ({
    page,
    request,
  }) => {
    const errors = collectErrors(page);
    await page.addInitScript((l) => localStorage.setItem('masar.lang', l), lang);
    // A scheduled demo request, to check the busiest detail page too. / طلب مجدول من البيانات التجريبية.
    const headers = await staffHeaders(request, ADMIN);
    const scheduled = await (await request.get('/api/requests?status=SCHEDULED', { headers })).json();

    for (const path of PUBLIC) await checkPage(page, path);
    await login(page, ADMIN);
    for (const path of [...OFFICE, `/app/requests/${scheduled.content[0].id}`]) {
      await checkPage(page, path);
    }
    await expect(page.locator('html')).toHaveAttribute('dir', lang === 'ar' ? 'rtl' : 'ltr');
    expect(errors).toEqual([]);
  });
}

test('switching language keeps typed input and flips direction', async ({ page }) => {
  await page.goto('/book');
  await page.locator('#b-name').fill('Sara Al-Otaibi');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await page.getByTestId('lang-switch').first().click();
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
  await expect(page.locator('h1')).toHaveText('Book a service');
  await expect(page.locator('#b-name')).toHaveValue('Sara Al-Otaibi');
  // The choice survives a reload. / الاختيار يبقى بعد إعادة التحميل.
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
});

test('the technician sees only their jobs and cannot open office pages', async ({ page }) => {
  const errors = collectErrors(page);
  await login(page, FAHAD);
  await expect(page).toHaveURL(/\/app\/jobs$/);
  await expectNoHorizontalScroll(page);
  await expect(page.locator('.sidebar a[href="/app/requests"]')).toHaveCount(0);
  // Typing an office URL sends them back to their own page. / كتابة رابط للمكتب يرجّعه لصفحته.
  await page.goto('/app/dashboard');
  await expect(page).toHaveURL(/\/app\/jobs$/);
  expect(errors).toEqual([]);
});

test('menus collapse into a drawer on phones', async ({ page }, info) => {
  await page.goto('/');
  const toggle = page.locator('.site-header__toggle');
  if (info.project.name === 'desktop') {
    await expect(toggle).toBeHidden();
    await expect(page.getByTestId('nav-book')).toBeVisible();
    return;
  }
  await expect(page.getByTestId('nav-book')).toBeHidden();
  await toggle.click();
  await page.getByTestId('nav-book').click();
  await expect(page).toHaveURL(/\/book$/);
  // Navigating closes the menu again. / التنقل يقفل القائمة.
  await expect(page.getByTestId('nav-book')).toBeHidden();

  await login(page, ADMIN);
  await expect(page.locator('.sidebar')).toBeHidden();
  await page.locator('.topbar__menu').click();
  await page.locator('.sidebar a[href="/app/schedule"]').click();
  await expect(page).toHaveURL(/\/app\/schedule$/);
  await expect(page.locator('.sidebar')).toBeHidden();
});
