package com.hanbay.hotel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HotelBookingApplicationTests {

    @Test
    void applicationEntryPointIsAvailable() {
        assertEquals("com.hanbay.hotel.HotelBookingApplication", HotelBookingApplication.class.getName());
    }
}
