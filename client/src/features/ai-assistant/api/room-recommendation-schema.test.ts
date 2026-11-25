import { describe, expect, it } from 'vitest';
import { recommendation } from '../test/fixtures';
import { parseRoomRecommendationResult } from './room-recommendation-schema';

describe('recommendation response validation', () => {
  it('accepts server facts and converts unknown null criteria for incomplete searches', () => {
    expect(parseRoomRecommendationResult(recommendation)).toEqual(recommendation);
    const partial = parseRoomRecommendationResult({
      ...recommendation,
      status: 'NEEDS_CLARIFICATION',
      recommendations: [],
      criteria: { checkIn: null, checkOut: null, adults: null, children: null, childAges: null },
      questions: ['Ngày nhận phòng?'],
    });
    expect(partial.criteria.checkIn).toBeUndefined();
    expect(partial.questions).toEqual(['Ngày nhận phòng?']);
  });

  it.each([
    null,
    {},
    { ...recommendation, recommendations: [null] },
    {
      ...recommendation,
      recommendations: [{ ...recommendation.recommendations[0], amenities: null }],
    },
    { ...recommendation, recommendations: [{ ...recommendation.recommendations[0], score: 101 }] },
    { ...recommendation, status: 'NO_MATCH' },
    { ...recommendation, criteria: { ...recommendation.criteria, checkOut: '2026-11-30' } },
  ])('rejects incomplete or inconsistent cards with an application error', (value) => {
    expect(() => parseRoomRecommendationResult(value)).toThrow(
      'Dữ liệu tư vấn phòng không hợp lệ.',
    );
  });

  it('rejects sold-out rooms, wrong occupancy and price totals before enabling booking', () => {
    for (const change of [
      { availableRooms: 0 },
      { adultCapacity: 1 },
      { roomTotal: 1 },
      { totalNights: 3 },
    ]) {
      const payload = {
        ...recommendation,
        recommendations: recommendation.recommendations.map((r) => ({
          ...r,
          room: { ...r.room, ...change },
        })),
      };
      expect(() => parseRoomRecommendationResult(payload)).toThrow(
        'Dữ liệu tư vấn phòng không hợp lệ.',
      );
    }
  });
});
