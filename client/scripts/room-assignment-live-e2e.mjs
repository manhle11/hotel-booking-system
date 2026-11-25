import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { access, mkdir, readdir } from 'node:fs/promises';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { chromium } from 'playwright';

let phase = 'startup';
function failSafely(error) {
  const safe = String(error?.message ?? error).replace(
    /Bearer [A-Za-z0-9_.-]+/g,
    'Bearer [REDACTED]',
  );
  console.error(`Assignment E2E [${phase}]: ${safe.slice(0, 5000)}`);
  process.exit(1);
}
process.on('uncaughtException', failSafely);
process.on('unhandledRejection', failSafely);

// Test-only signed credentials arrive on stdin; never persist or print them.
let input = '';
for await (const chunk of process.stdin) input += chunk;
if (!input)
  throw new Error(
    'Run through Maven with -Dassignment.e2e=true -Dtest=RoomAssignmentBrowserIntegrationTest',
  );
const fixture = JSON.parse(input);
input = '';
const frontend = 'http://127.0.0.1:5183';
const output = '../output/room-assignment-validation';
const server = spawn(
  process.execPath,
  [
    'node_modules/vite/bin/vite.js',
    'preview',
    '--host',
    '127.0.0.1',
    '--port',
    '5183',
    '--strictPort',
  ],
  { stdio: 'ignore' },
);
let browser;
process.on('exit', () => server.kill());
try {
  let ready = false;
  for (let i = 0; i < 80; i++) {
    try {
      if ((await fetch(frontend)).ok) {
        ready = true;
        break;
      }
    } catch {
      /* starting */
    }
    if (server.exitCode !== null)
      throw new Error('Port 5183 must be free for the assignment browser test');
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  assert(ready, 'Build the frontend with npm run build first');
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
            /* next installed browser */
          }
        }
      }
    }
  }
  browser = await chromium.launch({
    headless: true,
    ...(executablePath ? { executablePath } : {}),
  });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  await context.route('**/api/v1/**', async (route) => {
    const original = new URL(route.request().url());
    const response = await route.fetch({
      url: `${fixture.backend}${original.pathname}${original.search}`,
    });
    await route.fulfill({ response });
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  async function session(token, role) {
    phase = `${role}: open page`;
    await page.goto(frontend);
    await page.evaluate(
      ({ token, role }) => {
        localStorage.setItem('access_token', token);
        localStorage.setItem('user_roles', JSON.stringify([role]));
        localStorage.setItem('expires_at', String(Date.now() + 3600000));
      },
      { token, role },
    );
    await page.goto(`${frontend}/front-desk/room-assignments`);
    await page.getByRole('heading', { name: 'Lịch gán phòng', exact: true }).waitFor();
    phase = `${role}: open type selector`;
    await page.locator('.assignment-calendar').waitFor();
    await page.getByRole('combobox', { name: 'Loại phòng', exact: true }).click();
    await page.getByRole('combobox', { name: 'Loại phòng', exact: true }).fill(fixture.typeName);
    phase = `${role}: choose fixture type`;
    await page.getByTitle(fixture.typeName, { exact: true }).last().click();
    phase = `${role}: locate initial booking`;
    await page.getByRole('button', { name: /Booking E2E-A/ }).waitFor();
  }
  const api = '/api/v1/front-desk/room-assignments';
  const from = fixture.today;
  const to = new Date(`${from}T00:00:00Z`);
  to.setUTCDate(to.getUTCDate() + 60);
  const calendarPath = `${api}?from=${from}&to=${to.toISOString().slice(0, 10)}&roomTypeId=${fixture.typeId}`;
  async function calendar() {
    const response = await context.request.get(`${fixture.backend}${calendarPath}`, {
      headers: { Authorization: `Bearer ${fixture.staffToken}` },
    });
    assert.equal(response.status(), 200);
    return (await response.json()).data;
  }
  await session(fixture.staffToken, 'RECEPTIONIST');
  phase = 'suggest draft';
  console.log('Assignment E2E: authenticated calendar loaded.');
  assert.equal((await calendar()).bookings.filter((b) => !b.roomId).length, 2);
  await page.getByRole('button', { name: 'Gợi ý xếp phòng', exact: true }).click();
  await page.getByText('Phương án đề xuất', { exact: true }).waitFor();
  assert.equal(
    (await calendar()).bookings.filter((b) => !b.roomId).length,
    2,
    'Suggest must not change the applied calendar',
  );
  const before = await calendar();
  phase = 'invalidate draft through manager update';
  const update = await context.request.put(
    `${fixture.backend}${api}/bookings/${fixture.bookingA}`,
    {
      headers: { Authorization: `Bearer ${fixture.managerToken}` },
      data: {
        roomId: fixture.firstRoom,
        locked: false,
        expectedVersion: before.version,
        reason: 'Khách đồng ý bỏ khóa phòng',
        from,
        to: to.toISOString().slice(0, 10),
      },
    },
  );
  assert.equal(update.status(), 200, 'Manager can unlock with a reason');
  await page.getByRole('button', { name: 'Làm mới lịch gán phòng', exact: true }).click();
  phase = 'confirm stale draft is disabled';
  await page
    .getByText('Lịch đã cập nhật hoặc gợi ý hết hạn. Vui lòng lấy gợi ý mới.', { exact: true })
    .waitFor();
  assert(await page.getByRole('button', { name: 'Áp dụng phương án', exact: true }).isDisabled());
  await page.getByRole('button', { name: 'Gợi ý xếp phòng', exact: true }).click();
  phase = 'apply fresh draft';
  const applyButton = page.getByRole('button', { name: 'Áp dụng phương án', exact: true });
  await applyButton.waitFor();
  for (let i = 0; i < 100 && (await applyButton.isDisabled()); i++) await page.waitForTimeout(100);
  assert(await applyButton.isEnabled(), 'Fresh plan can be applied');
  await applyButton.click();
  const applied = page.waitForResponse(
    (r) =>
      r.url().includes('/plans/') && r.url().endsWith('/apply') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Xác nhận áp dụng', exact: true }).click();
  assert.equal((await applied).status(), 200);
  await page.getByRole('button', { name: /Booking E2E-B/ }).waitFor();
  const saved = await calendar();
  assert(
    saved.bookings.every((b) => !!b.roomId),
    'Every confirmed booking has a tentative room',
  );
  assert.equal(
    saved.bookings.find((b) => b.id === fixture.bookingB).roomId,
    fixture.firstRoom,
    'Adjacent stays fit together',
  );
  await page.getByRole('button', { name: /Booking E2E-B/ }).click();
  phase = 'lock booking';
  await page.getByRole('checkbox', { name: 'Khóa phòng cụ thể cho booking này' }).check();
  const locked = page.waitForResponse(
    (r) => r.url().endsWith(`/bookings/${fixture.bookingB}`) && r.request().method() === 'PUT',
  );
  await page.getByRole('button', { name: 'Lưu gán phòng', exact: true }).click();
  assert.equal((await locked).status(), 200);
  await page.getByRole('button', { name: /Booking E2E-B/ }).click();
  phase = 'verify receptionist cannot unlock';
  assert(
    await page.getByRole('checkbox', { name: 'Khóa phòng cụ thể cho booking này' }).isDisabled(),
    'Receptionist cannot unlock',
  );
  await page.getByRole('button', { name: 'Đóng', exact: true }).click();
  await page.getByRole('dialog').waitFor({ state: 'hidden' });
  await page.getByText('Đã cập nhật gán phòng.', { exact: true }).waitFor({ state: 'hidden' });
  await page.getByText('Đã áp dụng lịch gán phòng.', { exact: true }).waitFor({ state: 'hidden' });
  await mkdir(output, { recursive: true });
  await page.screenshot({ path: `${output}/desktop.png`, fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  phase = 'verify mobile layout';
  await page.waitForTimeout(300);
  await page.screenshot({ path: `${output}/mobile.png`, fullPage: true });
  assert(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1),
    'Calendar must scroll inside its card on mobile',
  );
  await page.setViewportSize({ width: 1440, height: 1000 });
  await session(fixture.managerToken, 'MANAGER');
  phase = 'manager unlock';
  await page.getByRole('button', { name: /Booking E2E-B/ }).click();
  await page.getByRole('checkbox', { name: 'Khóa phòng cụ thể cho booking này' }).uncheck();
  assert(
    await page.getByRole('button', { name: 'Lưu gán phòng', exact: true }).isDisabled(),
    'Manager override requires a reason',
  );
  await page
    .getByRole('textbox', { name: 'Lý do thay đổi gán phòng' })
    .fill('Đồng ý điều chỉnh theo yêu cầu khách');
  const unlocked = page.waitForResponse(
    (r) => r.url().endsWith(`/bookings/${fixture.bookingB}`) && r.request().method() === 'PUT',
  );
  await page.getByRole('button', { name: 'Lưu gán phòng', exact: true }).click();
  assert.equal((await unlocked).status(), 200);
  await page.getByRole('dialog').waitFor({ state: 'hidden' });
  await page.getByRole('button', { name: 'Lịch sử gán phòng', exact: true }).click();
  await page
    .getByRole('dialog', { name: 'Lịch sử gán phòng' })
    .getByText('Đồng ý điều chỉnh theo yêu cầu khách', { exact: true })
    .waitFor();
  assert.equal(
    (await calendar()).bookings.find((b) => b.id === fixture.bookingB).allocationStatus,
    'TENTATIVE',
  );
  assert.deepEqual(errors, []);
  console.log(
    'Room assignment browser workflow passed: draft, stale protection, apply, lock, manager unlock, audit and mobile layout.',
  );
} finally {
  await browser?.close();
  server.kill();
}
