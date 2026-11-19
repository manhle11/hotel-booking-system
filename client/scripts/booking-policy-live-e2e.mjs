import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { access, readdir } from 'node:fs/promises';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { chromium } from 'playwright';

// Only the isolated Spring/Testcontainers test supplies these signed test sessions.
const backend = process.env.POLICY_TEST_BACKEND;
if (!backend)
  throw new Error(
    'Run ./mvnw -Dbooking.policy.e2e=true -Dtest=BookingPolicyBrowserIntegrationTest test',
  );
const frontend = 'http://127.0.0.1:5182';
const server = spawn(
  process.execPath,
  [
    'node_modules/vite/bin/vite.js',
    'preview',
    '--host',
    '127.0.0.1',
    '--port',
    '5182',
    '--strictPort',
  ],
  { stdio: 'ignore' },
);
let browser;
let page;
try {
  let ready = false;
  for (let i = 0; i < 80; i++) {
    try {
      if ((await fetch(frontend)).ok) {
        ready = true;
        break;
      }
    } catch {
      /* preview startup */
    }
    assert.equal(server.exitCode, null, 'Port 5182 must be free; run npm run build first');
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  assert(ready, 'Frontend preview did not start');
  let executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE;
  if (!executablePath) {
    try {
      await access(chromium.executablePath());
    } catch {
      if (process.platform === 'darwin') {
        const cache = join(homedir(), 'Library/Caches/ms-playwright');
        for (const entry of (await readdir(cache))
          .filter((n) => n.startsWith('chromium_headless_shell-'))
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
            /* next browser */
          }
        }
      }
    }
  }
  browser = await chromium.launch({
    headless: true,
    ...(executablePath ? { executablePath } : {}),
  });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1100 } });
  await context.route('**/api/v1/**', async (route) => {
    const original = new URL(route.request().url());
    const response = await route.fetch({ url: `${backend}${original.pathname}${original.search}` });
    await route.fulfill({ response });
  });
  page = await context.newPage();
  page.setDefaultTimeout(12000);
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto(frontend);
  const session = async (staff) => {
    await page.evaluate(
      ({ token, roles, id }) => {
        localStorage.setItem('access_token', token);
        localStorage.setItem('expires_at', String(Date.now() + 3600000));
        localStorage.setItem('user_roles', JSON.stringify(roles));
        localStorage.setItem(
          'user_profile',
          JSON.stringify({
            id,
            fullName: 'Policy QA',
            email: 'qa-policy@example.test',
            phone: null,
          }),
        );
      },
      {
        token: staff ? process.env.POLICY_TEST_STAFF_TOKEN : process.env.POLICY_TEST_GUEST_TOKEN,
        roles: staff ? ['RECEPTIONIST'] : [],
        id: staff ? process.env.POLICY_TEST_STAFF_ID : process.env.POLICY_TEST_GUEST_ID,
      },
    );
  };
  const lookup = async (code) => {
    await page.goto(`${frontend}/front-desk/arrivals/today`);
    await page.getByLabel('Tra cứu xử lý khách chưa đến theo mã booking').fill(code);
    await page.getByRole('button', { name: 'Tra cứu xử lý', exact: true }).click();
    await page.getByRole('dialog').waitFor();
  };
  await session(true);
  await lookup(process.env.POLICY_TEST_DEBT);
  await page.getByRole('button', { name: 'Khách báo hủy', exact: true }).click();
  const cancellation = page.getByRole('dialog').filter({ hasText: 'Khách chủ động báo hủy' });
  await cancellation.getByLabel('Yêu cầu khách').fill('Khách QA gọi xác nhận hủy kỳ ở.');
  await cancellation.getByRole('button', { name: 'Khách xác nhận hủy, giải phóng phòng' }).click();
  await cancellation.getByText('Đã ghi nhận yêu cầu của khách', { exact: true }).waitFor();

  await session(false);
  await page.goto(`${frontend}/account/bookings/${process.env.POLICY_TEST_DEBT}`);
  await page.getByRole('button', { name: 'Thanh toán phí còn thiếu' }).click();
  await page.waitForURL(/\/payments\/[^/]+\/result/);
  await page.getByText('Thanh toán phí hủy / không đến', { exact: true }).waitFor();
  const paymentId = new URL(page.url()).pathname.split('/')[2];
  const status = await context.request.get(`${backend}/api/v1/payments/${paymentId}/status`, {
    headers: { Authorization: `Bearer ${process.env.POLICY_TEST_GUEST_TOKEN}` },
  });
  assert(status.ok(), 'Owner must access fee payment status');
  const payment = (await status.json()).data;
  assert.equal(payment.amount, 500000);
  assert.equal(payment.policyFeePayment, true);

  await session(true);
  await lookup(process.env.POLICY_TEST_DEBT);
  const collect = async (amount) => {
    await page.getByLabel('Tiền mặt phí đã nhận').fill(String(amount));
    await page.getByLabel('Ghi chú thu phí').fill(`Phiếu QA ${amount}`);
    await page.getByRole('checkbox', { name: 'Đã nhận đủ số tiền mặt nhập ở trên' }).check();
    const response = page.waitForResponse(
      (r) => r.url().endsWith('/policy-fee-collection') && r.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'Ghi nhận đã thu tiền mặt', exact: true }).click();
    assert((await response).ok(), 'Cash collection must succeed');
  };
  await collect(200000);
  await page.getByText('Còn phải thu phí: 300.000đ', { exact: true }).waitFor();
  await collect(300000);
  await page.getByLabel('Tiền mặt phí đã nhận').waitFor({ state: 'detached' });
  await session(false);
  await page.goto(`${frontend}/account/bookings/${process.env.POLICY_TEST_DEBT}`);
  await page.getByText('Quyết toán khách chủ động hủy', { exact: true }).waitFor();
  assert.equal(await page.getByRole('button', { name: 'Thanh toán phí còn thiếu' }).count(), 0);

  await session(true);
  await lookup(process.env.POLICY_TEST_NOSHOW);
  await page.getByRole('button', { name: 'Xử lý không đến', exact: true }).click();
  await page.getByLabel('Kết quả liên hệ').click();
  await page.getByTitle('Không nghe máy / chưa phản hồi', { exact: true }).click();
  await page.getByLabel('Ghi chú liên hệ').fill('Đã gọi hai lần, khách không trả lời.');
  await page.getByRole('button', { name: 'Xác nhận không đến', exact: true }).click();
  await page
    .getByText('Đã xác nhận khách không đến; giữ chỗ đã được giải phóng', { exact: true })
    .waitFor();
  assert.deepEqual(errors, [], 'Browser must not report uncaught errors');
  console.log(
    'Booking policy browser checks passed: cancellation, owner fee QR, partial cash, no-show.',
  );
} catch (error) {
  if (page) {
    await page
      .screenshot({ path: '/private/tmp/hanbay-booking-policy-browser-error.png', fullPage: true })
      .catch(() => {});
    console.error((await page.locator('body').innerText()).slice(-8000));
  }
  throw error;
} finally {
  if (browser) await browser.close();
  server.kill('SIGTERM');
}
