import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { access, readdir } from 'node:fs/promises';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { chromium } from 'playwright';

// Test-only signed JWTs are injected by ReviewBrowserIntegrationTest.
const backend = process.env.REVIEW_TEST_BACKEND;
if (!backend)
  throw new Error('Run through ./mvnw -Dreview.e2e=true -Dtest=ReviewBrowserIntegrationTest test');
const frontend = 'http://127.0.0.1:5181';
const server = spawn(
  process.execPath,
  [
    'node_modules/vite/bin/vite.js',
    'preview',
    '--host',
    '127.0.0.1',
    '--port',
    '5181',
    '--strictPort',
  ],
  { stdio: 'ignore' },
);
let browser;
try {
  let ready = false;
  for (let i = 0; i < 80; i++) {
    try {
      if ((await fetch(frontend)).ok) {
        ready = true;
        break;
      }
    } catch {
      /* wait for preview */
    }
    if (server.exitCode !== null)
      throw new Error('Frontend preview could not start (port 5181 must be free)');
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  assert(ready, 'Frontend build unavailable: run npm run build first');
  let executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE;
  if (!executablePath) {
    try {
      await access(chromium.executablePath());
    } catch {
      if (process.platform === 'darwin') {
        const cache = join(homedir(), 'Library/Caches/ms-playwright');
        for (const entry of (await readdir(cache))
          .filter((name) => name.startsWith('chromium_headless_shell-'))
          .reverse()) {
          const candidate = join(
            cache,
            entry,
            'chrome-headless-shell-mac-arm64/chrome-headless-shell',
          );
          try {
            await access(candidate);
            executablePath = candidate;
            break;
          } catch {
            /* try next installed browser */
          }
        }
      }
    }
  }
  browser = await chromium.launch({
    headless: true,
    ...(executablePath ? { executablePath } : {}),
  });
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
  // Proxy API requests without mocking responses: every request reaches the live Spring HTTP server.
  await context.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const original = new URL(request.url());
    const response = await route.fetch({ url: `${backend}${original.pathname}${original.search}` });
    await route.fulfill({ response });
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const guestId = process.env.REVIEW_TEST_GUEST_BOOKING;
  const anonymous = await context.request.post(`${backend}/api/v1/bookings/${guestId}/reviews`, {
    data: { rating: 5 },
    headers: {
      'X-Booking-Access-Token': process.env.REVIEW_TEST_GUEST_TOKEN,
      'X-Review-Access-Token': 'retired-token',
    },
  });
  assert.equal(anonymous.status(), 401, 'Guest access tokens must not grant review access');
  await page.goto(frontend);
  await page.evaluate(
    ({ order, token }) => sessionStorage.setItem(`booking-order-access:${order}`, token),
    { order: process.env.REVIEW_TEST_GUEST_ORDER, token: process.env.REVIEW_TEST_GUEST_TOKEN },
  );
  await page.goto(`${frontend}/booking-orders/${process.env.REVIEW_TEST_GUEST_ORDER}`);
  await page.getByRole('heading', { name: 'Đơn đặt phòng của bạn' }).waitFor();
  await page.getByText('Đã trả phòng', { exact: true }).waitFor();
  assert.equal(await page.getByRole('button', { name: /Đánh giá hạng phòng/ }).count(), 0);
  const setSession = async (token, roles, id) =>
    page.evaluate(
      ({ token, roles, id }) => {
        localStorage.setItem('access_token', token);
        localStorage.setItem('expires_at', String(Date.now() + 3600000));
        localStorage.setItem('user_roles', JSON.stringify(roles));
        localStorage.setItem(
          'user_profile',
          JSON.stringify({
            id,
            fullName: 'Trần Minh',
            email: 'customer@example.test',
            phone: null,
          }),
        );
      },
      { token, roles, id },
    );
  await setSession(process.env.REVIEW_TEST_CUSTOMER_TOKEN, [], process.env.REVIEW_TEST_CUSTOMER_ID);
  const stillGuest = await context.request.post(`${backend}/api/v1/bookings/${guestId}/reviews`, {
    data: { rating: 5 },
    headers: {
      Authorization: `Bearer ${process.env.REVIEW_TEST_CUSTOMER_TOKEN}`,
      'X-Booking-Access-Token': process.env.REVIEW_TEST_GUEST_TOKEN,
    },
  });
  assert.equal(stillGuest.status(), 404, 'Logging in must not claim a guest booking');
  await page.goto(`${frontend}/booking-orders/${process.env.REVIEW_TEST_ORDER_ID}`);
  await page.getByRole('button', { name: /Đánh giá hạng phòng/ }).click();
  await page.getByRole('dialog').locator('.ant-rate-star-second').nth(3).click();
  await page
    .getByRole('dialog')
    .getByRole('textbox')
    .fill('Phòng sạch, yên tĩnh và thoải mái. Một kỳ nghỉ đáng nhớ.');
  await page
    .getByRole('dialog')
    .screenshot({ path: '/private/tmp/hanbay-review-compose-mobile.png' });
  await page.getByRole('button', { name: 'Gửi đánh giá', exact: true }).click();
  await page.getByRole('button', { name: /Xem đánh giá của bạn/ }).waitFor();
  await page.goto(`${frontend}/room-types/${process.env.REVIEW_TEST_TYPE}`);
  await page
    .getByText('Phòng sạch, yên tĩnh và thoải mái. Một kỳ nghỉ đáng nhớ.', { exact: true })
    .waitFor();
  await setSession(process.env.REVIEW_TEST_MANAGER_TOKEN, ['MANAGER'], 'test-manager');
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto(`${frontend}/management/reviews?roomTypeId=${process.env.REVIEW_TEST_TYPE}`);
  await page.getByRole('button', { name: 'Chi tiết lưu trú', exact: true }).click();
  await page.getByText('Trả phòng thực tế', { exact: true }).waitFor();
  await page.locator('.ant-drawer-close').click();
  await page.getByRole('button', { name: 'Phản hồi', exact: true }).click();
  await page.getByLabel('Nội dung phản hồi').fill('Khách sạn đã ghi nhận và cải thiện cách âm.');
  await page.getByRole('button', { name: 'Lưu phản hồi', exact: true }).click();
  await page
    .getByText('Khách sạn: Khách sạn đã ghi nhận và cải thiện cách âm.', { exact: true })
    .waitFor();
  const managerFilters = page.getByRole('combobox', { name: 'Lọc trạng thái phản hồi' });
  await managerFilters.click();
  await page.getByTitle('Chưa phản hồi', { exact: true }).click();
  await page.getByText('Chưa có đánh giá phù hợp', { exact: true }).waitFor();
  await page.waitForFunction(
    () => document.querySelector('.review-management__total strong')?.textContent === '0',
  );
  await managerFilters.click();
  await page.getByTitle('Đã phản hồi', { exact: true }).click();
  await page.getByRole('button', { name: 'Sửa phản hồi', exact: true }).waitFor();
  await page
    .getByRole('searchbox', { name: 'Tìm khách hoặc mã đặt phòng' })
    .fill('không-có-khách-này');
  await page.getByRole('button', { name: 'Tìm', exact: true }).click();
  await page.getByText('Chưa có đánh giá phù hợp', { exact: true }).waitFor();
  await page.getByRole('searchbox', { name: 'Tìm khách hoặc mã đặt phòng' }).fill('tRẦN mINH');
  await page.getByRole('button', { name: 'Tìm', exact: true }).click();
  await page.getByRole('button', { name: 'Sửa phản hồi', exact: true }).waitFor();
  assert.equal(new URL(page.url()).searchParams.get('replied'), 'true');
  assert.equal(new URL(page.url()).searchParams.get('search'), 'tRẦN mINH');
  await page.reload();
  await page.getByRole('button', { name: 'Sửa phản hồi', exact: true }).waitFor();
  assert.equal(
    await page.getByRole('searchbox', { name: 'Tìm khách hoặc mã đặt phòng' }).inputValue(),
    'tRẦN mINH',
  );
  await page.getByRole('button', { name: 'Xóa bộ lọc', exact: true }).click();
  assert.equal(new URL(page.url()).search, '');
  await page.goto(`${frontend}/room-types/${process.env.REVIEW_TEST_TYPE}`);
  await page.getByText('Phản hồi từ HANBAY Hotel', { exact: true }).waitFor();
  await page
    .getByRole('region', { name: 'Đánh giá từ khách lưu trú' })
    .screenshot({ path: '/private/tmp/hanbay-reviews-public-desktop.png' });
  await page.setViewportSize({ width: 390, height: 844 });
  try {
    await page.waitForFunction(() => document.documentElement.scrollWidth <= innerWidth, null, {
      timeout: 5000,
    });
  } catch (error) {
    console.error(
      'Public mobile layout:',
      await page.evaluate(() => ({
        viewport: innerWidth,
        width: document.documentElement.scrollWidth,
        overflow: [...document.querySelectorAll('body *')]
          .filter((element) => element.getBoundingClientRect().right > innerWidth + 1)
          .slice(0, 12)
          .map((element) => ({
            tag: element.tagName,
            className: element.className,
            right: element.getBoundingClientRect().right,
          })),
      })),
    );
    await page.screenshot({ path: '/private/tmp/hanbay-reviews-mobile-layout-error.png' });
    throw error;
  }
  assert(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    'Public reviews overflow on mobile',
  );
  await page
    .getByRole('region', { name: 'Đánh giá từ khách lưu trú' })
    .screenshot({ path: '/private/tmp/hanbay-reviews-public-mobile.png' });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto(`${frontend}/management/reviews?roomTypeId=${process.env.REVIEW_TEST_TYPE}`);
  await page.getByRole('button', { name: 'Chi tiết lưu trú', exact: true }).waitFor();
  await page.waitForFunction(
    () => document.querySelector('.review-management__total strong')?.textContent === '1',
  );
  await page.screenshot({
    path: '/private/tmp/hanbay-reviews-manager-desktop.png',
    fullPage: true,
    animations: 'disabled',
  });
  await page.getByRole('button', { name: /Xóa$/ }).click();
  await page.getByLabel('Lý do xóa').fill('Khách yêu cầu gỡ đánh giá.');
  await page.getByRole('button', { name: 'Xóa đánh giá', exact: true }).click();
  await page.getByText('Chưa có đánh giá phù hợp', { exact: true }).waitFor();
  await page.getByRole('tab', { name: 'Lịch sử đã xóa', exact: true }).click();
  await page.getByText('Khách yêu cầu gỡ đánh giá.', { exact: true }).waitFor();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForFunction(
    () => document.querySelector('.ant-layout-sider').getBoundingClientRect().width <= 73,
  );
  await page.getByPlaceholder('Từ ngày', { exact: true }).click();
  await page.locator('.review-management-date-popup').waitFor();
  assert(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    'Date filter popup overflows on mobile',
  );
  await page.keyboard.press('Escape');
  assert(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    'Review management overflows on mobile',
  );
  await page.goto(`${frontend}/room-types/${process.env.REVIEW_TEST_TYPE}`);
  await page
    .getByRole('region', { name: 'Đánh giá từ khách lưu trú' })
    .getByText('Chưa có đánh giá', { exact: true })
    .waitFor();
  assert.equal(
    await page
      .getByText('Phòng sạch, yên tĩnh và thoải mái. Một kỳ nghỉ đáng nhớ.', { exact: true })
      .count(),
    0,
  );
  assert.deepEqual(errors, []);
  console.log(
    'PASS live HTTP + PostgreSQL: owner review; guest isolation; manager reply/search filters and global summary; URL persistence and reset; deletion audit; public score; desktop/mobile and date picker layout.',
  );
} finally {
  if (browser) await browser.close();
  server.kill();
}
