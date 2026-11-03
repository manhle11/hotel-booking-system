import { ApiClientError } from '../../../types/api';

const CHECK_IN_ERRORS: Record<string, string> = {
  BOOKING_NOT_FOUND: 'Không tìm thấy booking với mã đã nhập.',
  BOOKING_NOT_CONFIRMED: 'Booking chưa được xác nhận nên chưa thể check-in.',
  BOOKING_ALREADY_CHECKED_IN: 'Booking này đã được check-in trước đó.',
  CHECK_IN_DATE_NOT_ALLOWED:
    'Đã ngoài thời gian nhận phòng. Kiểm tra booking gốc hoặc điều chỉnh ngày đến.',
  ROOM_NOT_FOUND: 'Không tìm thấy phòng vật lý đã chọn.',
  ROOM_TYPE_MISMATCH: 'Phòng đã chọn không thuộc hạng phòng của booking.',
  ROOM_NOT_AVAILABLE: 'Phòng đã chọn không còn trống. Vui lòng chọn phòng khác.',
};

export function getCheckInErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError && error.code) {
    const reasonError = error.data?.errors?.earlyArrivalReason;
    if (reasonError) return Array.isArray(reasonError) ? reasonError.join(' ') : reasonError;
    return CHECK_IN_ERRORS[error.code] ?? error.message;
  }
  return error instanceof Error ? error.message : 'Không thể check-in. Vui lòng thử lại.';
}
