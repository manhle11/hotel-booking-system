package com.hanbay.hotel.booking.dto.response;

import com.hanbay.hotel.booking.domain.BookingOrderStatus;
import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.payment.domain.PaymentRefundStatus;
import com.hanbay.hotel.payment.domain.PaymentStatus;
import com.hanbay.hotel.payment.dto.response.CreatePaymentRequestResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BookingOrderResponse(
        UUID orderId,
        String orderCode,
        BookingOrderStatus status,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        String contactName,
        String contactEmail,
        long totalAmount,
        long paidAmount,
        String currency,
        Instant expiresAt,
        Instant createdAt,
        List<Room> rooms,
        CreatePaymentRequestResponse payment,
        PaymentStatus paymentStatus,
        long refundAmount,
        PaymentRefundStatus refundStatus,
        boolean refundInformationRequired,
        long bookedAmount,
        long additionalChargeAmount) {
    public record Room(
            UUID bookingId,
            String bookingCode,
            UUID roomTypeId,
            String roomTypeName,
            int adults,
            int children,
            long totalAmount,
            long paidAmount,
            BookingStatus status,
            String roomNumber,
            List<Integer> childAges) {
        public Room(
                UUID bookingId,
                String bookingCode,
                UUID roomTypeId,
                String roomTypeName,
                int adults,
                int children,
                long totalAmount,
                long paidAmount,
                BookingStatus status,
                String roomNumber) {
            this(
                    bookingId,
                    bookingCode,
                    roomTypeId,
                    roomTypeName,
                    adults,
                    children,
                    totalAmount,
                    paidAmount,
                    status,
                    roomNumber,
                    null);
        }
    }
}
