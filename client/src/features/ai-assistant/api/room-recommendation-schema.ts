import { z } from 'zod';
import { ApiClientError } from '../../../types/api';
import type { RoomRecommendationResult } from '../types/room-recommendation';

const date = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);
const money = z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER);
const bed = z.object({
  bedType: z.enum(['SINGLE', 'DOUBLE', 'QUEEN', 'KING']),
  quantity: z.number().int().positive(),
});
const adjustment = z.object({
  name: z.string(),
  ruleType: z.enum(['BASE', 'WEEKEND', 'HOLIDAY', 'SEASONAL', 'PROMOTION']),
  adjustmentType: z.enum(['FIXED', 'PERCENTAGE']),
  adjustmentDirection: z.enum(['INCREASE', 'DISCOUNT']),
  adjustmentValue: z.number(),
  amount: money,
});
const room = z
  .object({
    id: z.string().min(1),
    code: z.string().min(1),
    name: z.string().min(1),
    description: z.string().nullable(),
    basePrice: money,
    maxGuests: z.number().int().positive(),
    adultCapacity: z.number().int().positive(),
    childCapacity: z.number().int().nonnegative(),
    bedType: z.string().nullable(),
    areaM2: z.number().positive().nullable(),
    imageUrl: z.string().nullable(),
    availableRooms: z.number().int().positive(),
    readyRooms: z.number().int().nonnegative(),
    totalNights: z.number().int().positive(),
    roomSubtotal: money,
    roomDiscount: money,
    roomTotal: money,
    nightlyPrices: z
      .array(z.object({ stayDate: date, price: money, adjustments: z.array(adjustment) }))
      .min(1),
    beds: z.array(bed),
    viewType: z.enum(['SEA', 'CITY', 'COURTYARD']).nullable(),
    hasBalcony: z.boolean().nullable(),
    highFloor: z.boolean().nullable(),
  })
  .passthrough();

const resultSchema = z
  .object({
    status: z.enum(['NEEDS_CLARIFICATION', 'RECOMMENDED', 'NO_MATCH', 'TEMPORARILY_UNAVAILABLE']),
    criteria: z
      .object({
        checkIn: date.nullish().transform((v) => v ?? undefined),
        checkOut: date.nullish().transform((v) => v ?? undefined),
        adults: z
          .number()
          .int()
          .positive()
          .nullish()
          .transform((v) => v ?? undefined),
        children: z
          .number()
          .int()
          .nonnegative()
          .nullish()
          .transform((v) => v ?? undefined),
        childAges: z
          .array(z.number().int().nonnegative())
          .nullish()
          .transform((v) => v ?? undefined),
      })
      .passthrough(),
    evaluatedAt: z.string().datetime({ offset: true }),
    currency: z.literal('VND'),
    priceScope: z.literal('ROOM_ONLY'),
    scoringVersion: z.string().min(1),
    recommendations: z
      .array(
        z.object({
          room,
          amenities: z.array(
            z.object({ id: z.string().min(1), name: z.string(), icon: z.string() }),
          ),
          score: z.number().min(0).max(100).nullable(),
          scoreBreakdown: z.array(
            z.object({
              code: z.string(),
              weight: z.number().int().positive(),
              value: z.number().min(0).max(1),
              explanation: z.string(),
            }),
          ),
          reasons: z.array(z.string()),
          unmetPreferences: z.array(z.string()),
        }),
      )
      .max(3),
    questions: z.array(z.string()),
    rejections: z.array(
      z.object({ code: z.string(), count: z.number().int().nonnegative(), message: z.string() }),
    ),
    suggestions: z.array(z.string()),
    warnings: z.array(z.string()),
  })
  .superRefine((result, ctx) => {
    const fail = () =>
      ctx.addIssue({ code: 'custom', message: 'Inconsistent recommendation result' });
    if (result.status !== 'RECOMMENDED') {
      if (result.recommendations.length) fail();
      return;
    }
    const c = result.criteria;
    if (
      !result.recommendations.length ||
      !c.checkIn ||
      !c.checkOut ||
      !c.adults ||
      c.children == null ||
      c.checkOut <= c.checkIn
    ) {
      fail();
      return;
    }
    if (c.childAges && c.childAges.length !== c.children) fail();
    for (const { room: r } of result.recommendations) {
      const nights = r.nightlyPrices;
      if (
        r.totalNights !== nights.length ||
        nights.reduce((sum, n) => sum + n.price, 0) !== r.roomTotal ||
        r.roomSubtotal - r.roomDiscount !== r.roomTotal ||
        c.adults > r.adultCapacity ||
        c.children > r.childCapacity ||
        c.adults + c.children > r.maxGuests ||
        nights[0].stayDate !== c.checkIn ||
        nights.some((n) => n.stayDate < c.checkIn! || n.stayDate >= c.checkOut!)
      )
        fail();
    }
  });

export function parseRoomRecommendationResult(value: unknown): RoomRecommendationResult {
  const result = resultSchema.safeParse(value);
  if (!result.success)
    throw new ApiClientError({ message: 'Dữ liệu tư vấn phòng không hợp lệ.', kind: 'api' });
  return result.data as RoomRecommendationResult;
}
