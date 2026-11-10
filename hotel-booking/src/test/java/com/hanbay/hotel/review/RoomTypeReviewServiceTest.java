package com.hanbay.hotel.review;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.hanbay.hotel.booking.config.HotelStayProperties;
import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.booking.exception.BookingNotFoundException;
import com.hanbay.hotel.booking.repository.*;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.review.domain.RoomTypeReview;
import com.hanbay.hotel.review.dto.CreateReviewRequest;
import com.hanbay.hotel.review.exception.ReviewException;
import com.hanbay.hotel.review.repository.RoomTypeReviewRepository;
import com.hanbay.hotel.review.service.RoomTypeReviewService;
import com.hanbay.hotel.room.domain.RoomType;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.repository.UserRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomTypeReviewServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");

    @Mock
    RoomTypeReviewRepository reviews;

    @Mock
    BookingRepository bookings;

    @Mock
    BookingOrderRepository orders;

    @Mock
    RoomTypeRepository roomTypes;

    @Mock
    UserRepository users;

    @Mock
    CurrentUserProvider currentUser;

    private RoomTypeReviewService service;
    private User owner;
    private RoomType type;
    private Booking booking;

    @BeforeEach
    void setup() {
        service = new RoomTypeReviewService(
                reviews,
                bookings,
                orders,
                roomTypes,
                users,
                currentUser,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new HotelStayProperties());
        owner = User.builder().id(UUID.randomUUID()).fullName("Nguyễn Văn An").build();
        type = RoomType.builder().id(UUID.randomUUID()).name("Deluxe Twin").build();
        booking = Booking.builder()
                .id(UUID.randomUUID())
                .bookedBy(owner)
                .roomType(type)
                .status(BookingStatus.CHECKED_OUT)
                .checkedOutAt(NOW.minus(Duration.ofDays(1)))
                .build();
    }

    private void authorized() {
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(currentUser.currentUserId()).thenReturn(Optional.of(owner.getId()));
    }

    @Test
    void ownerCanReviewAfterActualCheckoutAndNameIsMasked() {
        authorized();
        when(bookings.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(reviews.saveAndFlush(any())).thenAnswer(call -> {
            RoomTypeReview review = call.getArgument(0);
            review.setCreatedAt(NOW);
            return review;
        });
        var response = service.create(booking.getId(), new CreateReviewRequest(4, "  Phòng sạch.  "));
        assertThat(response.rating()).isEqualTo(4);
        assertThat(response.comment()).isEqualTo("Phòng sạch.");
        assertThat(response.authorName()).isEqualTo("Nguyễn ***");
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    void anotherAccountCannotReadEligibilityOrCreateReview() {
        authorized();
        when(currentUser.currentUserId()).thenReturn(Optional.of(UUID.randomUUID()));
        assertThatThrownBy(() -> service.eligibility(booking.getId())).isInstanceOf(BookingNotFoundException.class);
        assertThatThrownBy(() -> service.create(booking.getId(), new CreateReviewRequest(5, null)))
                .isInstanceOf(BookingNotFoundException.class);
        verify(reviews, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = "CHECKED_OUT", mode = EnumSource.Mode.EXCLUDE)
    void incompleteCancelledOrExpiredStaysCannotReview(BookingStatus status) {
        booking = Booking.builder()
                .id(booking.getId())
                .bookedBy(owner)
                .roomType(type)
                .status(status)
                .build();
        authorized();
        assertThat(service.eligibility(booking.getId()).reasonCode()).isEqualTo("REVIEW_STAY_NOT_COMPLETED");
    }

    @Test
    void reviewWindowUsesActualCheckoutAndAllowsExactDeadline() {
        booking = Booking.builder()
                .id(booking.getId())
                .bookedBy(owner)
                .roomType(type)
                .status(BookingStatus.CHECKED_OUT)
                .checkedOutAt(NOW.minus(Duration.ofDays(30)))
                .build();
        authorized();
        assertThat(service.eligibility(booking.getId()).canReview()).isTrue();
        var later = new RoomTypeReviewService(
                reviews,
                bookings,
                orders,
                roomTypes,
                users,
                currentUser,
                Clock.fixed(NOW.plusNanos(1), ZoneOffset.UTC),
                new HotelStayProperties());
        assertThat(later.eligibility(booking.getId()).reasonCode()).isEqualTo("REVIEW_WINDOW_EXPIRED");
    }

    @Test
    void checkedOutBookingWithoutActualTimestampCannotReview() {
        booking = Booking.builder()
                .id(booking.getId())
                .bookedBy(owner)
                .roomType(type)
                .status(BookingStatus.CHECKED_OUT)
                .build();
        authorized();
        assertThat(service.eligibility(booking.getId()).canReview()).isFalse();
    }

    @Test
    void duplicateAndDeletedReviewsCannotBeSubmittedAgain() {
        authorized();
        var review = RoomTypeReview.builder()
                .booking(booking)
                .roomType(type)
                .reviewerName("Nguyễn Văn An")
                .rating(1)
                .build();
        when(reviews.findByBookingId(booking.getId())).thenReturn(Optional.of(review));
        assertThat(service.eligibility(booking.getId()).reasonCode()).isEqualTo("REVIEW_ALREADY_EXISTS");
        review.delete(owner, NOW);
        var eligibility = service.eligibility(booking.getId());
        assertThat(eligibility.reasonCode()).isEqualTo("REVIEW_REMOVED");
        assertThat(eligibility.review()).isNull();
    }

    @Test
    void sameRoomTypeInOrderSharesReviewAndUsesLatestActualCheckout() {
        authorized();
        BookingOrder order =
                BookingOrder.builder().id(UUID.randomUUID()).bookedBy(owner).build();
        booking.assignOrder(order, 1);
        when(bookings.findLatestCheckOutForReview(order.getId(), type.getId())).thenReturn(NOW);
        assertThat(service.eligibility(booking.getId()).deadline()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        var review = RoomTypeReview.builder()
                .order(order)
                .roomType(type)
                .reviewerName("An")
                .rating(5)
                .build();
        when(reviews.findByOrderIdAndRoomTypeId(order.getId(), type.getId())).thenReturn(Optional.of(review));
        assertThat(service.eligibility(booking.getId()).canReview()).isFalse();
    }

    @Test
    void deletionRecordsManagerAndKeepsFirstDeletionAudit() {
        UUID id = UUID.randomUUID();
        var review = RoomTypeReview.builder().id(id).build();
        when(reviews.findByIdForUpdate(id)).thenReturn(Optional.of(review));
        when(currentUser.currentUserId()).thenReturn(Optional.of(owner.getId()));
        when(users.getReferenceById(owner.getId())).thenReturn(owner);
        service.delete(id, "Spam");
        service.delete(id, "Spam");
        assertThat(review.getDeletedAt()).isEqualTo(NOW);
        assertThat(review.getDeletedBy()).isSameAs(owner);
    }

    @Test
    void invalidRatingFiltersAndPaginationAreRejected() {
        assertThatThrownBy(() -> service.manage(null, 6, false, 0, 20)).isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.manage(null, 5, false, -1, 20)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(reviews);
    }

    @Test
    void anonymousCannotReviewEvenAfterCheckout() {
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(currentUser.currentUserId()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.eligibility(booking.getId()))
                .isInstanceOf(ReviewException.class)
                .extracting(error -> ((ReviewException) error).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
        assertThatThrownBy(() -> service.create(booking.getId(), new CreateReviewRequest(5, null)))
                .isInstanceOf(ReviewException.class);
        verifyNoInteractions(reviews);
    }

    @Test
    void loggedInAccountCannotReviewAnOrderBookedAsGuest() {
        BookingOrder order = BookingOrder.builder()
                .id(UUID.randomUUID())
                .contactName(owner.getFullName())
                .contactEmail("same@example.test")
                .build();
        booking.assignOrder(order, 1);
        authorized();
        assertThatThrownBy(() -> service.eligibility(booking.getId())).isInstanceOf(BookingNotFoundException.class);
        assertThatThrownBy(() -> service.create(booking.getId(), new CreateReviewRequest(5, null)))
                .isInstanceOf(BookingNotFoundException.class);
        verifyNoInteractions(reviews);
    }
}
