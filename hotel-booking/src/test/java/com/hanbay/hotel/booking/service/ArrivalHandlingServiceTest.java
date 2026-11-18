package com.hanbay.hotel.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hanbay.hotel.booking.config.HotelStayProperties;
import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingNight;
import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.booking.policy.UnifiedBookingPolicy;
import com.hanbay.hotel.booking.repository.BookingChangeRequestRepository;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.payment.repository.PaymentRepository;
import com.hanbay.hotel.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArrivalHandlingServiceTest {
    private static final String CODE = "BK-LEGACY-POLICY";
    private static final String TOKEN = "booking-access-token";
    private static final String TOKEN_HASH = "booking-access-token-hash";
    private static final LocalDate ARRIVAL = LocalDate.of(2026, 11, 18);

    @Mock
    private BookingRepository bookings;

    @Mock
    private PaymentRepository payments;

    @Mock
    private BookingOrderPaymentService orderPayments;

    @Mock
    private BookingAccessTokenService tokens;

    @Mock
    private CurrentUserProvider currentUser;

    @Mock
    private UserRepository users;

    @Mock
    private HotelStayPolicy policy;

    @Mock
    private Clock clock;

    @Mock
    private BookingChangeRequestRepository changeRequests;

    @Mock
    private BookingSettlementLedger settlementLedger;

    @InjectMocks
    private ArrivalHandlingService service;

    @BeforeEach
    void setup() {
        when(tokens.matches(TOKEN, TOKEN_HASH)).thenReturn(true);
        when(policy.getProperties()).thenReturn(new HotelStayProperties());
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void returnsLegacyPolicyWithNoOriginalFirstNightAmount(BookingStatus status) {
        Booking booking = booking(status).build();
        when(bookings.findByBookingCodeForUpdate(CODE)).thenReturn(Optional.of(booking));

        var summary = service.customerPolicy(CODE, TOKEN);

        assertThat(summary.bookingCode()).isEqualTo(CODE);
        assertThat(summary.firstNightAmount()).isNull();
        assertThat(summary.legacy()).isTrue();
        assertThat(summary.version()).isNull();
        assertThat(summary.terms()).isNull();
        assertThat(booking.getOriginalFirstNightAmount()).isNull();
    }

    @Test
    void pendingPolicyUsesFirstNightPriceWithoutAcceptingTerms() {
        Booking booking = booking(BookingStatus.PENDING).build();
        booking.addNight(
                BookingNight.builder().stayDate(ARRIVAL).pricePerNight(750_000).build());
        when(bookings.findByBookingCodeForUpdate(CODE)).thenReturn(Optional.of(booking));

        var summary = service.customerPolicy(CODE, TOKEN);

        assertThat(summary.firstNightAmount()).isEqualTo(750_000L);
        assertThat(summary.legacy()).isFalse();
        assertThat(summary.version()).isEqualTo(UnifiedBookingPolicy.VERSION);
        assertThat(summary.terms()).isEqualTo(UnifiedBookingPolicy.TERMS);
        assertThat(summary.acceptedAt()).isNull();
        assertThat(booking.getOriginalFirstNightAmount()).isNull();
        assertThat(booking.getTermsVersion()).isNull();
    }

    @Test
    void confirmedPolicyKeepsAcceptedOriginalAmountAfterNightPriceChanges() {
        Instant acceptedAt = Instant.parse("2026-11-10T07:00:00Z");
        Booking booking = booking(BookingStatus.CONFIRMED)
                .termsVersion(UnifiedBookingPolicy.VERSION)
                .acceptedTerms(UnifiedBookingPolicy.TERMS)
                .termsAcceptedAt(acceptedAt)
                .originalFirstNightAmount(800_000L)
                .policyFeeTotal(400_000)
                .build();
        booking.addNight(
                BookingNight.builder().stayDate(ARRIVAL).pricePerNight(600_000).build());
        when(bookings.findByBookingCodeForUpdate(CODE)).thenReturn(Optional.of(booking));

        var summary = service.customerPolicy(CODE, TOKEN);

        assertThat(summary.firstNightAmount()).isEqualTo(800_000L);
        assertThat(summary.accruedFee()).isEqualTo(400_000);
        assertThat(summary.legacy()).isFalse();
        assertThat(summary.acceptedAt()).isEqualTo(acceptedAt);
    }

    private Booking.BookingBuilder booking(BookingStatus status) {
        return Booking.builder()
                .bookingCode(CODE)
                .accessTokenHash(TOKEN_HASH)
                .status(status)
                .checkInDate(ARRIVAL)
                .checkOutDate(ARRIVAL.plusDays(2));
    }
}
