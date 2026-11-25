import { env } from '../../../config/env';
import { tokenStorage } from '../../../lib/storage';
import { ApiClientError } from '../../../types/api';
import type { AiChatRequest, AiPolicySource, AiUiAction } from '../types/ai-assistant';
import type { RoomRecommendationResult } from '../types/room-recommendation';
import { parseRoomRecommendationResult } from './room-recommendation-schema';

interface AiChatStreamEvent {
  type:
    | 'delta'
    | 'action'
    | 'sources'
    | 'recommendations'
    | 'status'
    | 'suggestions'
    | 'done'
    | 'error';
  content: string;
  action?: AiUiAction;
  sources?: AiPolicySource[];
  recommendations?: RoomRecommendationResult;
  suggestions?: string[];
}

export interface StreamAiMessageOptions {
  signal?: AbortSignal;
  onDelta: (content: string) => void;
  onStatus?: (content: string) => void;
  onSuggestions?: (suggestions: string[]) => void;
  onAction?: (action: AiUiAction) => void;
  onSources?: (sources: AiPolicySource[]) => void;
  onRecommendations?: (result: RoomRecommendationResult) => void;
}

export async function streamAiMessage(
  payload: AiChatRequest,
  options: StreamAiMessageOptions,
): Promise<void> {
  const timeoutController = new AbortController();
  const timeout = globalThis.setTimeout(() => timeoutController.abort(), 60_000);
  const abort = () => timeoutController.abort();
  options.signal?.addEventListener('abort', abort, { once: true });
  try {
    await streamAiMessageOnce(payload, { ...options, signal: timeoutController.signal });
  } catch (error) {
    if (timeoutController.signal.aborted && !options.signal?.aborted) {
      throw new ApiClientError({
        message: 'Trợ lý AI phản hồi quá thời gian. Vui lòng thử lại.',
        kind: 'network',
      });
    }
    throw error;
  } finally {
    globalThis.clearTimeout(timeout);
    options.signal?.removeEventListener('abort', abort);
  }
}

async function streamAiMessageOnce(
  payload: AiChatRequest,
  options: StreamAiMessageOptions,
): Promise<void> {
  const accessToken = tokenStorage.getAccessToken();
  const response = await fetch(`${env.apiBaseUrl}/ai/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
    body: JSON.stringify(payload),
    signal: options.signal,
  });

  if (!response.ok || !response.body) {
    throw new ApiClientError({
      message:
        response.status === 401
          ? 'Phiên đăng nhập đã hết hạn.'
          : 'Không thể kết nối đến trợ lý AI.',
      kind: response.status ? 'api' : 'network',
      status: response.status,
    });
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let completed = false;
  let receivedPayload = false;

  while (true) {
    const { done, value } = await reader.read();
    buffer += decoder.decode(value, { stream: !done });
    const frames = buffer.split(/\r?\n\r?\n/);
    buffer = frames.pop() ?? '';
    frames.forEach((frame) => {
      const type = processSseFrame(frame, options);
      if (type) receivedPayload = true;
      if (type === 'done') completed = true;
    });

    if (done) {
      if (buffer.trim()) {
        const type = processSseFrame(buffer, options);
        if (type) receivedPayload = true;
        if (type === 'done') completed = true;
      }
      break;
    }
  }
  if (!completed) {
    throw new ApiClientError({
      message: receivedPayload
        ? 'Kết nối với trợ lý AI bị gián đoạn. Vui lòng thử lại.'
        : 'Trợ lý AI không trả về nội dung. Vui lòng thử lại.',
      kind: 'api',
    });
  }
}

function processSseFrame(frame: string, options: StreamAiMessageOptions) {
  const data = frame
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n');
  if (!data) return undefined;

  let event: AiChatStreamEvent;
  try {
    event = JSON.parse(data) as AiChatStreamEvent;
  } catch {
    throw new ApiClientError({
      message: 'Phản hồi từ trợ lý AI không đúng định dạng. Vui lòng thử lại.',
      kind: 'api',
    });
  }
  if (
    !event ||
    ![
      'delta',
      'action',
      'sources',
      'recommendations',
      'status',
      'suggestions',
      'done',
      'error',
    ].includes(event.type)
  ) {
    throw new ApiClientError({ message: 'Phản hồi từ trợ lý AI không hợp lệ.', kind: 'api' });
  }
  if (typeof event.content !== 'string') {
    throw new ApiClientError({ message: 'Phản hồi từ trợ lý AI không hợp lệ.', kind: 'api' });
  }
  if (event.type === 'status') options.onStatus?.(event.content);
  if (event.type === 'suggestions') {
    if (
      !Array.isArray(event.suggestions) ||
      event.suggestions.length > 3 ||
      event.suggestions.some((s) => typeof s !== 'string' || !s.trim() || s.length > 200)
    ) {
      throw new ApiClientError({ message: 'Gợi ý từ trợ lý AI không hợp lệ.', kind: 'api' });
    }
    options.onSuggestions?.(event.suggestions);
  }
  if (event.type === 'delta') options.onDelta(event.content);
  if (event.type === 'action' && event.action) options.onAction?.(event.action);
  if (event.type === 'sources' && Array.isArray(event.sources)) options.onSources?.(event.sources);
  if (event.type === 'recommendations') {
    const result = parseRoomRecommendationResult(event.recommendations);
    options.onRecommendations?.(result);
  }
  if (event.type === 'error') {
    throw new ApiClientError({ message: event.content, kind: 'api' });
  }
  return event.type;
}
