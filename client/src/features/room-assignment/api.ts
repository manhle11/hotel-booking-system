import { apiClient } from '../../lib/axios';
import type { ApiResponse } from '../../types/api';
import type { RoomStatus } from '../room-map/api';

export interface AssignmentRoom {
  id: string;
  roomTypeId: string;
  roomTypeName: string;
  roomNumber: string;
  floor: number;
  status: RoomStatus;
}
export interface AssignmentBooking {
  id: string;
  bookingCode: string;
  roomTypeId: string;
  start: string;
  end: string;
  status: 'PENDING' | 'CONFIRMED' | 'CHECKED_IN';
  stayRevision: number;
  orderId: string | null;
  orderRoomNumber: number | null;
  guestName: string;
  expiresAt: string | null;
  roomId: string | null;
  allocationStatus: 'TENTATIVE' | 'LOCKED' | 'OCCUPIED' | null;
  overdue: boolean;
  scheduledEnd: string;
}
export interface AssignmentCalendar {
  from: string;
  to: string;
  today: string;
  version: string;
  serverNow: string;
  rooms: AssignmentRoom[];
  bookings: AssignmentBooking[];
  warnings: string[];
}
export interface AssignmentMetrics {
  orphanOne: number;
  orphanTwo: number;
  changed: number;
  unassigned: number;
  windowsThree: number;
  windowsFive: number;
  windowsSeven: number;
}
export interface AssignmentChange {
  bookingId: string;
  bookingCode: string;
  fromRoomId: string | null;
  fromRoomNumber: string | null;
  toRoomId: string;
  toRoomNumber: string;
  start: string;
  end: string;
  changed: boolean;
  reason: string;
}
export interface AssignmentPlan {
  id: string;
  roomTypeId: string;
  from: string;
  to: string;
  sourceVersion: string;
  createdAt: string;
  validUntil: string;
  status: string;
  algorithm: string;
  before: AssignmentMetrics;
  after: AssignmentMetrics | null;
  changes: AssignmentChange[];
  warnings: string[];
  assignments: Record<string, string>;
  applied: boolean;
}
export interface AssignmentHistory {
  id: string;
  bookingId: string | null;
  action: string;
  actorId: string | null;
  actorName: string | null;
  createdAt: string;
  reason: string | null;
  details: string;
}
export interface SuggestedRoom {
  bookingId: string;
  roomId: string;
  roomNumber: string;
  allocationStatus: string;
  ready: boolean;
}

const base = '/front-desk/room-assignments';
export async function getAssignmentCalendar(from: string, to: string) {
  return (await apiClient.get<ApiResponse<AssignmentCalendar>>(base, { params: { from, to } })).data
    .data;
}
export async function suggestAssignment(roomTypeId: string, from: string, to: string) {
  return (
    await apiClient.post<ApiResponse<AssignmentPlan>>(`${base}/plans`, { roomTypeId, from, to })
  ).data.data;
}
export async function getLatestAssignment(roomTypeId: string, from: string, to: string) {
  return (
    await apiClient.get<ApiResponse<AssignmentPlan | null>>(`${base}/plans/latest`, {
      params: { roomTypeId, from, to },
    })
  ).data.data;
}
export async function applyAssignment(plan: AssignmentPlan) {
  return (
    await apiClient.post<ApiResponse<AssignmentPlan>>(`${base}/plans/${plan.id}/apply`, {
      expectedVersion: plan.sourceVersion,
    })
  ).data.data;
}
export async function getAssignmentPlan(id: string) {
  return (await apiClient.get<ApiResponse<AssignmentPlan>>(`${base}/plans/${id}`)).data.data;
}
export async function assignBooking(
  bookingId: string,
  request: {
    roomId: string | null;
    locked: boolean;
    expectedVersion: string;
    reason: string;
    from: string;
    to: string;
  },
) {
  return (
    await apiClient.put<ApiResponse<AssignmentCalendar>>(`${base}/bookings/${bookingId}`, request)
  ).data.data;
}
export async function getAssignmentHistory(roomTypeId: string) {
  return (
    await apiClient.get<ApiResponse<AssignmentHistory[]>>(`${base}/history`, {
      params: { roomTypeId },
    })
  ).data.data;
}
export async function getSuggestedRoom(bookingCode: string) {
  return (
    await apiClient.get<ApiResponse<SuggestedRoom | null>>(`${base}/suggested-room`, {
      params: { bookingCode },
    })
  ).data.data;
}
