import { expect, test } from '@playwright/test';

import { ADMIN, NOURA, login } from './helpers';

test('wrong password shows a clear error and staff pages need a login', async ({ page }) => {
  await page.goto('/app/requests');
  await expect(page).toHaveURL(/\/login$/);
  await page.locator('#username').fill('admin');
  await page.locator('#password').fill('wrong-password');
  await page.locator('form button[type=submit]').click();
  await expect(page.getByTestId('login-error')).toContainText('غير صحيحة');
});

test('a dispatcher works requests but has no admin pages', async ({ page }) => {
  await login(page, NOURA);
  await expect(page).toHaveURL(/\/app\/dashboard$/);
  await expect(page.locator('.sidebar a[href="/app/users"]')).toHaveCount(0);
  await page.goto('/app/users');
  await expect(page).toHaveURL(/\/app\/dashboard$/);
});

test('logging out ends the session', async ({ page }) => {
  await login(page, ADMIN);
  await page.getByTestId('logout').click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/app/dashboard');
  await expect(page).toHaveURL(/\/login$/);
});
