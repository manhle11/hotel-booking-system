-- Repair requests left pending by older customer cancellation flows; retain their audit history.
UPDATE booking_change_requests r
SET status = 'REJECTED',
    decided_at = CURRENT_TIMESTAMP,
    decision_note = 'Đã kết thúc yêu cầu đổi lịch vì khách đã hủy booking/đơn.',
    snapshot = COALESCE(r.snapshot, '{}'::jsonb)
        || '{"autoClosed":true,"closureReason":"BOOKING_CANCELLED"}'::jsonb,
    version = r.version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE r.type = 'RESCHEDULE' AND r.status = 'PENDING'
  AND EXISTS (
      SELECT 1 FROM bookings b
      WHERE b.status = 'CANCELLED'
        AND (r.scope_key = 'BOOKING:' || b.id::text
          OR r.scope_key = 'ORDER:' || b.order_id::text)
  );
