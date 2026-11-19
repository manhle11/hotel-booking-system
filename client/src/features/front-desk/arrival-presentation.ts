import type {
  ArrivalReason,
  ContactChannel,
  ContactOutcome,
  ArrivalProgress,
} from './types/arrival-handling';
export const reasonLabels: Record<ArrivalReason, string> = {
  RECEPTION_EXPIRED: 'Hết hạn tiếp nhận',
  HOLD_REQUEST: 'Chờ duyệt nhận sau hạn',
  CHANGE_REQUEST: 'Chờ duyệt hủy / đổi lịch',
  ETA_AFTER_DEADLINE: 'Giờ hẹn vượt hạn tiếp nhận',
  PAST_ETA: 'Quá giờ khách hẹn',
};
export const channelLabels: Record<ContactChannel, string> = {
  PHONE: 'Điện thoại',
  EMAIL: 'Email',
  MESSAGE: 'Tin nhắn',
  IN_PERSON: 'Trực tiếp',
  OTHER: 'Khác',
};
export const outcomeLabels: Record<ContactOutcome, string> = {
  STILL_COMING: 'Khách vẫn đến',
  NO_ANSWER: 'Không nghe máy / chưa phản hồi',
  CANCELLATION: 'Khách báo hủy',
  RESCHEDULE: 'Khách xin đổi lịch',
  UNDECIDED: 'Chưa xác định',
};
export function nextArrivalAction(progress?: ArrivalProgress) {
  if (!progress) return 'Xem tình trạng';
  if (progress.group === 'RESOLVED') return 'Xem kết quả xử lý';
  if (progress.reasons.includes('CHANGE_REQUEST')) return 'Quản lý xem yêu cầu hủy / đổi lịch';
  if (progress.holdPending) return 'Quản lý duyệt hoặc từ chối nhận sau hạn';
  if (progress.reasons.includes('RECEPTION_EXPIRED'))
    return 'Liên hệ khách; xem xét nhận sau hạn hoặc không đến';
  if (progress.reasons.includes('ETA_AFTER_DEADLINE'))
    return 'Xin nhận sau hạn hoặc điều chỉnh kỳ ở';
  if (progress.reasons.includes('PAST_ETA')) return 'Liên hệ và cập nhật giờ khách đến';
  return 'Chờ khách đến; nhận phòng khi đủ điều kiện';
}
