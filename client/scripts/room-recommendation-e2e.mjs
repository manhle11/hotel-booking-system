// Run against a local Vite server: node scripts/room-recommendation-e2e.mjs
// Every API is intercepted; this script never reads/writes the hotel database or calls OpenAI.
import assert from 'node:assert/strict';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { chromium } from 'playwright';

const baseUrl = process.env.RECOMMENDATION_E2E_URL ?? 'http://127.0.0.1:5175';
const room = {
  id: '11111111-1111-4111-8111-111111111111',
  code: 'DELUXE_TWIN',
  name: 'Deluxe Twin',
  description: 'Phòng mẫu kiểm thử',
  basePrice: 2500000,
  adultCapacity: 2,
  childCapacity: 1,
  maxGuests: 3,
  areaM2: 36,
  bedType: '2 SINGLE',
  beds: [{ bedType: 'SINGLE', quantity: 2 }],
  bedConfigurationStatus: 'CONFIGURED',
  viewType: 'SEA',
  hasBalcony: true,
  highFloor: true,
  imageUrl: '/login-hotel.jpg',
  availableRooms: 1,
  readyRooms: 0,
  totalNights: 2,
  roomSubtotal: 5000000,
  roomDiscount: 0,
  roomTotal: 5000000,
  nightlyPrices: [
    { stayDate: '2026-12-01', price: 2400000, adjustments: [] },
    { stayDate: '2026-12-02', price: 2600000, adjustments: [] },
  ],
};
const result = {
  status: 'RECOMMENDED',
  criteria: {
    checkIn: '2026-12-01',
    checkOut: '2026-12-03',
    adults: 2,
    children: 1,
    childAges: [5],
  },
  evaluatedAt: '2026-11-25T08:00:00Z',
  currency: 'VND',
  priceScope: 'ROOM_ONLY',
  scoringVersion: 'v1',
  recommendations: [
    {
      room,
      amenities: [{ id: 'wifi', name: 'Wi-Fi tốc độ cao', icon: 'wifi' }],
      score: 81.3,
      scoreBreakdown: [{ code: 'AREA', weight: 15, value: 1, explanation: 'Diện tích 36 m².' }],
      reasons: ['Đáp ứng cấu hình giường bắt buộc.', 'Hướng nhìn: biển.', 'Diện tích 36 m².'],
      unmetPreferences: [],
    },
  ],
  questions: [],
  rejections: [],
  suggestions: [],
  warnings: [
    'Giá chỉ gồm tiền phòng. Kết quả không giữ chỗ; giá và tồn được kiểm tra lại khi đặt.',
  ],
};
const superior = {
  ...room,
  id: '22222222-2222-4222-8222-222222222222',
  code: 'SUPERIOR_TWIN',
  name: 'Superior Twin',
  areaM2: 30,
  basePrice: 1700000,
  roomSubtotal: 3400000,
  roomTotal: 3400000,
  nightlyPrices: room.nightlyPrices.map((n) => ({ ...n, price: 1700000 })),
};
result.recommendations.unshift({ ...result.recommendations[0], room: superior });
const browser = await chromium.launch({
  headless: true,
  executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE,
});
let page;
try {
  page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  const errors = [];
  page.on('pageerror', (e) => {
    errors.push(e.message);
    console.error('Browser runtime error:', e.message);
  });
  page.on('requestfailed', (request) =>
    console.error(
      'Fixture request failed:',
      new URL(request.url()).pathname,
      request.failure()?.errorText,
    ),
  );
  const turns = [];
  let bookingWrites = 0;
  await page.addInitScript(() => {
    sessionStorage.setItem('access_token', 'fixture-token');
    sessionStorage.setItem('user_roles', '[]');
    sessionStorage.setItem('expires_at', String(Date.now() + 3600000));
    sessionStorage.setItem(
      'user_profile',
      JSON.stringify({
        id: 'fixture-guest',
        fullName: 'Khách kiểm thử',
        email: 'guest@example.test',
        phone: null,
      }),
    );
  });
  await page.route('**/api/v1/**', async (route) => {
    const req = route.request();
    const path = new URL(req.url()).pathname;
    let data = {};
    if (path.endsWith('/ai/chat/stream')) {
      const payload = req.postDataJSON();
      turns.push(payload);
      const message = payload.message;
      const events = [{ type: 'status', content: 'Mình đang đối chiếu dữ liệu phòng…' }];
      if (/tư vấn giúp/.test(message))
        events.push({
          type: 'delta',
          content:
            'Bạn dự định nhận và trả phòng ngày nào? Có bao nhiêu người lớn và trẻ em đi cùng bạn?',
        });
      else if (/So sánh/.test(message))
        events.push({
          type: 'delta',
          content:
            'Superior Twin tiết kiệm hơn 1.600.000 VND cho cả kỳ; Deluxe Twin rộng hơn 6 m².',
        });
      else if (/thứ hai/.test(message))
        events.push(
          {
            type: 'delta',
            content: 'Deluxe Twin có 2 giường đơn, Wi-Fi tốc độ cao và diện tích 36 m².',
          },
          { type: 'suggestions', content: '', suggestions: ['Tôi chọn phòng này'] },
        );
      else if (/chọn phòng/.test(message))
        events.push(
          {
            type: 'delta',
            content:
              'Mình vừa kiểm tra lại Deluxe Twin: 5.000.000 VND tiền phòng cho 2 đêm. Bạn có thể tiếp tục nhập thông tin đặt phòng.',
          },
          {
            type: 'action',
            content: '',
            action: {
              type: 'CONTINUE_BOOKING',
              label: 'Tiếp tục đặt phòng',
              params: { ...result.criteria, roomTypeId: room.id },
            },
          },
        );
      else
        events.push(
          {
            type: 'delta',
            content:
              'Với nhu cầu gia đình, mình gợi ý hai lựa chọn dưới đây. Bạn muốn so sánh trước không?',
          },
          { type: 'recommendations', content: '', recommendations: result },
          { type: 'suggestions', content: '', suggestions: ['So sánh hai phòng đầu giúp tôi'] },
        );
      events.push({ type: 'done', content: '' });
      await route.fulfill({
        contentType: 'text/event-stream',
        body: events.map((e) => `event:${e.type}\ndata:${JSON.stringify(e)}\n\n`).join(''),
      });
      return;
    }
    if (path.endsWith('/stay-policy'))
      data = {
        zone: 'Asia/Ho_Chi_Minh',
        checkInTime: '14:00:00',
        checkOutTime: '12:00:00',
        sameDayBookingCutoff: '23:59:59',
        lateArrivalCutoff: '06:00:00',
        paymentHoldMinutes: 15,
        hotelToday: '2026-11-25',
        serverNow: new Date().toISOString(),
        adultFromAge: 12,
        youngChildUnderAge: 6,
      };
    else if (path.includes('/room-types'))
      data = {
        content: [room],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      };
    else if (path.includes('/add-ons')) data = [];
    else if (req.method() !== 'GET' && /booking/.test(path)) bookingWrites++;
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data }),
    });
  });
  page.setDefaultTimeout(15000);
  await page.goto(baseUrl, { waitUntil: 'domcontentloaded' });
  console.log('Local app loaded');
  await page.getByRole('button', { name: 'Mở trợ lý HANBAY AI' }).click();
  const ask = async (message) => {
    await page.waitForFunction(
      () =>
        !document
          .querySelector('.ai-widget__input-row .ant-btn')
          ?.classList.contains('ant-btn-loading'),
    );
    await page.getByRole('textbox', { name: 'Tin nhắn cho trợ lý AI' }).fill(message);
    await page.getByRole('button', { name: 'Gửi tin nhắn', exact: true }).click();
  };
  await ask('Gia đình mình muốn đi nghỉ, bạn tư vấn giúp nhé');
  await page
    .getByText(
      'Bạn dự định nhận và trả phòng ngày nào? Có bao nhiêu người lớn và trẻ em đi cùng bạn?',
      { exact: true },
    )
    .waitFor();
  assert.equal(await page.getByRole('tab').count(), 0);
  assert.equal(
    await page.getByRole('button', { name: 'Tiếp tục đặt phòng', exact: true }).count(),
    0,
  );
  await ask(
    'Hai người lớn, một bé 5 tuổi, 01–03/12/2026, hai giường đơn, tối đa 6 triệu cho cả kỳ.',
  );
  await page.locator('.ai-widget__messages').getByText('Deluxe Twin', { exact: true }).waitFor();
  assert.equal(await page.locator('.ai-room-card').count(), 2);
  assert.equal(await page.getByText(/Phù hợp.*100|trọng số/).count(), 0);
  assert.equal(
    await page.getByRole('button', { name: 'Tiếp tục đặt phòng', exact: true }).count(),
    0,
  );
  await page.getByRole('button', { name: 'So sánh hai phòng đầu giúp tôi', exact: true }).click();
  await page
    .getByText('Superior Twin tiết kiệm hơn 1.600.000 VND cho cả kỳ; Deluxe Twin rộng hơn 6 m².', {
      exact: true,
    })
    .waitFor();
  await ask('Phòng thứ hai có tiện nghi gì?');
  await page
    .getByText('Deluxe Twin có 2 giường đơn, Wi-Fi tốc độ cao và diện tích 36 m².', { exact: true })
    .waitFor();
  await page.screenshot({ path: join(tmpdir(), 'hanbay-concierge-desktop.png') });
  await page.setViewportSize({ width: 390, height: 844 });
  await page
    .getByRole('button', { name: 'Tôi chọn phòng này', exact: true })
    .scrollIntoViewIfNeeded();
  await page.screenshot({ path: join(tmpdir(), 'hanbay-concierge-mobile.png') });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
  assert.equal(
    await page.locator('.ai-widget__messages').evaluate((el) => el.scrollWidth > el.clientWidth),
    false,
  );
  await page.getByRole('button', { name: 'Tôi chọn phòng này', exact: true }).click();
  await page.getByRole('button', { name: 'Tiếp tục đặt phòng', exact: true }).click();
  await page.waitForURL('**/bookings/new?**');
  const handoff = new URL(page.url());
  assert.equal(handoff.searchParams.get('childAges'), '5');
  assert.equal(handoff.searchParams.get('roomTypeId'), room.id);
  assert.equal(
    await page.getByRole('spinbutton', { name: 'Tuổi trẻ em 1 phòng 1' }).inputValue(),
    '5',
  );
  assert.equal(turns.length, 5);
  assert.equal(new Set(turns.map((t) => t.conversationId)).size, 1);
  assert.equal(bookingWrites, 0);
  assert.deepEqual(errors, []);
  console.log(
    'PASS: five-turn conversation, comparison, second-room details, explicit selection, desktop/mobile and checkout ages; no booking writes.',
  );
} catch (error) {
  if (page) {
    await page.screenshot({ path: join(tmpdir(), 'hanbay-recommendation-failure.png') });
    console.error(
      await page
        .locator('.ai-widget__messages')
        .ariaSnapshot()
        .catch(() => 'Chat unavailable'),
    );
  }
  throw error;
} finally {
  await browser.close();
}
