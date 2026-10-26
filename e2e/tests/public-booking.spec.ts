import { expect, test } from '@playwright/test';

import { bookViaApi, randomPhone, riyadhDate, unique } from './helpers';

test('the home page lists the live services and links to booking', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  const cards = page.getByTestId('service-grid').locator('.service-card');
  // Six services come from the seeded database. / ست خدمات من قاعدة البيانات.
  await expect(cards).toHaveCount(6);
  await expect(cards.first()).toContainText('صيانة وتنظيف المكيفات');

  await cards.first().getByRole('link').click();
  await expect(page).toHaveURL(/\/book\?service=\d+/);
  // The service chosen on the home page is already selected in the form.
  // الخدمة المختارة في الرئيسية تكون محددة في النموذج مسبقًا.
  await expect(page.locator('.choice--on')).toContainText('صيانة وتنظيف المكيفات');
});

test('a customer books, gets a reference and tracks the request', async ({ page }) => {
  const phone = randomPhone();
  const name = `Test Customer ${unique()}`;
  await page.goto('/book');

  // Submitting empty shows the field errors instead of calling the server.
  // الإرسال وهو فاضي يعرض أخطاء الحقول بدل ما يكلم الخادم.
  await page.getByTestId('booking-submit').click();
  await expect(page.locator('.field__error').first()).toBeVisible();
  await expect(page.getByTestId('booking-success')).toHaveCount(0);

  await page.locator('.choice', { hasText: 'سباكة' }).click();
  await page.locator('#b-details').fill('تسريب تحت مغسلة المطبخ');
  await page.locator('#b-district').fill('الملقا');
  await page.locator('#b-date').fill(riyadhDate(2));
  await page.locator('.segmented__item', { hasText: 'المساء' }).click();
  await page.locator('#b-name').fill(name);
  // A landline is refused on the spot. / الرقم الثابت ينرفض فورًا.
  await page.locator('#b-phone').fill('0112345678');
  await page.locator('#b-phone').blur();
  await expect(page.locator('#b-phone-err')).toContainText('جوال سعودي');
  await page.locator('#b-phone').fill(phone);
  await page.getByTestId('booking-submit').click();

  await expect(page.getByTestId('booking-success')).toBeVisible();
  const reference = (await page.getByTestId('booking-reference').textContent())!.trim();
  expect(reference).toMatch(/^MS-[A-Z2-9]{8}$/);

  // "Track this request" carries the reference; the customer only types the phone.
  // "تتبّع هذا الطلب" ينقل رقم الطلب؛ والعميل يكتب الجوال بس.
  await page.getByRole('link', { name: 'تتبّع هذا الطلب' }).click();
  await expect(page.locator('#t-ref')).toHaveValue(reference);
  await page.locator('#t-phone').fill(phone);
  await page.getByTestId('track-submit').click();
  await expect(page.getByTestId('track-result')).toBeVisible();
  await expect(page.getByTestId('track-status-text')).toContainText('نجهّز عرض السعر');
  await expect(page.getByTestId('track-result')).toContainText('سباكة');
});

test('tracking needs the matching phone number', async ({ page, request }) => {
  const booking = await bookViaApi(request, `Wrong Phone ${unique()}`);
  await page.goto(`/track?ref=${booking.reference}`);
  await page.locator('#t-phone').fill('0550000000');
  await page.getByTestId('track-submit').click();
  // Same message as an unknown reference, so a guessed reference reveals nothing.
  // نفس رسالة رقم الطلب غير الموجود، فالرقم المخمّن ما يكشف شي.
  await expect(page.getByTestId('track-error')).toContainText('لا يوجد طلب');

  // The reference is accepted in lower case and without the dash too.
  // رقم الطلب ينقبل بحروف صغيرة وبدون شرطة برضو.
  await page.locator('#t-ref').fill(booking.reference.replace('MS-', 'ms'));
  await page.locator('#t-phone').fill(booking.phone);
  await page.getByTestId('track-submit').click();
  await expect(page.getByTestId('track-result')).toContainText(booking.reference);
});

test('the honeypot field is invisible to people', async ({ page }) => {
  await page.goto('/book');
  await expect(page.locator('#b-website')).not.toBeInViewport();
  await expect(page.locator('.honeypot')).toHaveAttribute('aria-hidden', 'true');
});
