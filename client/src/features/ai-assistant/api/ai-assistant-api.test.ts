import { beforeEach, describe, expect, it, vi } from 'vitest';

import { tokenStorage } from '../../../lib/storage';
import { streamAiMessage } from './ai-assistant-api';

describe('streamAiMessage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(tokenStorage, 'getAccessToken').mockReturnValue('access-token');
  });

  it('forwards backend recommendation payloads instead of parsing cards from model prose', async () => {
    const recommendations = {
      status: 'NEEDS_CLARIFICATION',
      criteria: {},
      evaluatedAt: '2026-11-25T08:00:00Z',
      currency: 'VND',
      priceScope: 'ROOM_ONLY',
      scoringVersion: 'v1',
      recommendations: [],
      questions: ['Ngày nhận phòng?'],
      rejections: [],
      suggestions: [],
      warnings: [],
    };
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            `data:${JSON.stringify({ type: 'recommendations', content: '', recommendations })}\n\ndata:{"type":"done","content":""}\n\n`,
          ),
        ),
    );
    const onRecommendations = vi.fn();
    await streamAiMessage(
      { conversationId: 'conversation', message: 'Tìm phòng' },
      { onDelta: vi.fn(), onRecommendations },
    );
    expect(onRecommendations).toHaveBeenCalledWith(recommendations);
  });

  it('forwards progress and follow-up suggestions separately from assistant text', async () => {
    const onStatus = vi.fn(),
      onSuggestions = vi.fn(),
      onDelta = vi.fn();
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          [
            { type: 'status', content: 'Mình đang kiểm tra lịch phòng…' },
            { type: 'delta', content: 'Bạn muốn mình so sánh không?' },
            { type: 'suggestions', content: '', suggestions: ['So sánh hai phòng đầu giúp tôi'] },
            { type: 'done', content: '' },
          ]
            .map((event) => `data:${JSON.stringify(event)}\n\n`)
            .join(''),
        ),
      ),
    );
    await streamAiMessage(
      { conversationId: 'conversation', message: 'Tìm phòng' },
      { onDelta, onStatus, onSuggestions },
    );
    expect(onStatus).toHaveBeenCalledWith('Mình đang kiểm tra lịch phòng…');
    expect(onDelta).toHaveBeenCalledTimes(1);
    expect(onSuggestions).toHaveBeenCalledWith(['So sánh hai phòng đầu giúp tôi']);
  });

  it('rejects non-text follow-up suggestions', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response('data:{"type":"suggestions","content":"","suggestions":[{}]}\n\n'),
        ),
    );
    await expect(
      streamAiMessage(
        { conversationId: 'conversation', message: 'Tìm phòng' },
        { onDelta: vi.fn() },
      ),
    ).rejects.toThrow('Gợi ý từ trợ lý AI không hợp lệ');
  });

  it('rejects malformed recommendation payloads before rendering', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response('data:{"type":"recommendations","content":"","recommendations":{}}\n\n'),
        ),
    );
    await expect(
      streamAiMessage(
        { conversationId: 'conversation', message: 'Tìm phòng' },
        { onDelta: vi.fn() },
      ),
    ).rejects.toThrow('Dữ liệu tư vấn phòng không hợp lệ');
  });

  it('emits deltas from SSE frames split across network chunks', async () => {
    const encoder = new TextEncoder();
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('event:delta\ndata:{"type":"delta","content":"Xin '));
        controller.enqueue(
          encoder.encode('chào"}\n\nevent:done\ndata:{"type":"done","content":""}\n\n'),
        );
        controller.close();
      },
    });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body, { status: 200 })));
    const deltas: string[] = [];

    await streamAiMessage(
      { conversationId: '11111111-1111-4111-8111-111111111111', message: 'Xin chào' },
      { onDelta: (delta) => deltas.push(delta) },
    );

    expect(deltas).toEqual(['Xin chào']);
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/ai/chat/stream'),
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ Authorization: 'Bearer access-token' }),
      }),
    );
  });

  it('rejects an error event returned after streaming has started', async () => {
    const payload = 'data:{"type":"error","content":"AI unavailable"}\n\n';
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(payload));
        controller.close();
      },
    });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body, { status: 200 })));

    await expect(
      streamAiMessage(
        { conversationId: '11111111-1111-4111-8111-111111111111', message: 'Test' },
        { onDelta: vi.fn() },
      ),
    ).rejects.toThrow('AI unavailable');
  });

  it('forwards a whitelisted UI action event', async () => {
    const action = {
      type: 'CONTINUE_BOOKING' as const,
      label: 'Tiếp tục đặt phòng',
      params: {
        roomTypeId: '11111111-1111-4111-8111-111111111111',
        checkIn: '2026-09-20',
        checkOut: '2026-09-22',
        adults: 2,
        children: 1,
      },
    };
    const payload = `event:action\ndata:${JSON.stringify({ type: 'action', content: '', action })}\n\nevent:done\ndata:{"type":"done","content":""}\n\n`;
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(payload));
        controller.close();
      },
    });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body, { status: 200 })));
    const onAction = vi.fn();

    await streamAiMessage(
      { conversationId: '11111111-1111-4111-8111-111111111111', message: 'Đặt phòng đó' },
      { onDelta: vi.fn(), onAction },
    );

    expect(onAction).toHaveBeenCalledWith(action);
  });

  it('normalizes malformed SSE JSON instead of leaking a SyntaxError', async () => {
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('data:{not-json}\n\n'));
        controller.close();
      },
    });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body, { status: 200 })));

    await expect(
      streamAiMessage(
        { conversationId: '11111111-1111-4111-8111-111111111111', message: 'Test' },
        { onDelta: vi.fn() },
      ),
    ).rejects.toThrow('Phản hồi từ trợ lý AI không đúng định dạng');
  });

  it('rejects a stream that closes without a done event', async () => {
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('event: ping\n\n'));
        controller.close();
      },
    });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body, { status: 200 })));

    await expect(
      streamAiMessage(
        { conversationId: '11111111-1111-4111-8111-111111111111', message: 'Test' },
        { onDelta: vi.fn() },
      ),
    ).rejects.toThrow('không trả về nội dung');
  });

  it('forwards structured policy sources', async () => {
    const sources = [
      { title: 'Chính sách nhận phòng', category: 'CHECK_IN', section: 'Giờ nhận phòng' },
    ];
    const payload = `data:${JSON.stringify({ type: 'sources', content: '', sources })}\n\ndata:{"type":"done","content":""}\n\n`;
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(payload));
        controller.close();
      },
    });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body, { status: 200 })));
    const onSources = vi.fn();

    await streamAiMessage(
      { conversationId: '11111111-1111-4111-8111-111111111111', message: 'Check-in?' },
      { onDelta: vi.fn(), onSources },
    );

    expect(onSources).toHaveBeenCalledWith(sources);
  });
});
