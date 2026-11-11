package com.hanbay.hotel.review.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** A star rating must be an integer; never truncate fractional scores or coerce strings. */
public class ReviewRatingDeserializer extends ValueDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return context.reportInputMismatch(Integer.class, "Số sao phải là số nguyên từ 1 đến 5");
        }
        return parser.getIntValue();
    }
}
