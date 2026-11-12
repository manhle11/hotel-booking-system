package com.hanbay.hotel.booking.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingGuest;
import com.hanbay.hotel.booking.domain.BookingStatus;
import com.hanbay.hotel.booking.dto.request.CheckInGuestRequest;
import com.hanbay.hotel.booking.exception.CheckInConflictException;
import com.hanbay.hotel.booking.repository.BookingRepository;
import com.hanbay.hotel.common.exception.DomainException;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.room.domain.Room;
import com.hanbay.hotel.room.domain.RoomStatus;
import com.hanbay.hotel.room.domain.RoomType;
import com.hanbay.hotel.room.repository.RoomRepository;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckInServiceImplTest {

    private static final UUID BOOKING_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String BOOKING_CODE = "BK20260819000001";
    private static final UUID ROOM_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_TYPE_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID RECEPTIONIST_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-19T07:00:00Z");

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private final Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"));

    @InjectMocks
    private CheckInServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CheckInServiceImpl(
                bookingRepository,
                roomRepository,
                userRepository,
                currentUserProvider,
                clock,
                new com.hanbay.hotel.booking.policy.HotelStayPolicy(
                        clock, new com.hanbay.hotel.booking.config.HotelStayProperties()));
    }

    @Test
    void previewsConfirmedBookingWithDebtAndPrimaryGuestWithoutChangingIt() {
        var booking = Booking.builder()
                .id(BOOKING_ID)
                .bookingCode(BOOKING_CODE)
                .roomType(roomType(ROOM_TYPE_ID))
                .checkInDate(LocalDate.of(2026, 8, 19))
                .checkOutDate(LocalDate.of(2026, 8, 20))
                .status(BookingStatus.CONFIRMED)
                .totalAmount(2500000L)
                .paidAmount(2000000L)
                .build();
        booking.addGuest(BookingGuest.builder()
                .id(UUID.randomUUID())
                .fullName("Khách chính")
                .phone("0900000000")
                .isPrimary(true)
                .build());
        when(bookingRepository.findByBookingCode(BOOKING_CODE)).thenReturn(Optional.of(booking));

        var preview = serviceAt(NOW.minusSeconds(3600)).getBookingContext(BOOKING_CODE);

        assertThat(preview.primaryGuest().fullName()).isEqualTo("Khách chính");
        assertThat(preview.canCheckIn()).isTrue();
        assertThat(preview.earlyArrivalRequired()).isTrue();
        assertThat(preview.outstandingAmount()).isEqualTo(500000L);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getAssignedRoom()).isNull();
        org.mockito.Mockito.verifyNoInteractions(roomRepository);
    }

    @Test
    void previewsRecordedRoomAndActualTimeForRecovery() {
        var type = roomType(ROOM_TYPE_ID);
        var booking = booking(BookingStatus.CONFIRMED, LocalDate.of(2026, 8, 19), type);
        var room = room(type, RoomStatus.OCCUPIED);
        booking.checkIn(room, User.builder().id(RECEPTIONIST_ID).build(), NOW, null);
        when(bookingRepository.findByBookingCode(BOOKING_CODE)).thenReturn(Optional.of(booking));

        var preview = service.getBookingContext(BOOKING_CODE);

        assertThat(preview.canCheckIn()).isFalse();
        assertThat(preview.assignedRoom().roomId()).isEqualTo(ROOM_ID);
        assertThat(preview.checkedInAt()).isEqualTo(NOW);
        assertThat(booking.getStatusHistories()).hasSize(1);
    }

    @Test
    void lateArrivalPreviewDoesNotRequireAnEarlyArrivalReason() {
        var booking = booking(BookingStatus.CONFIRMED, LocalDate.of(2026, 8, 19), roomType(ROOM_TYPE_ID));
        when(bookingRepository.findByBookingCode(BOOKING_CODE)).thenReturn(Optional.of(booking));
        var preview = serviceAt(Instant.parse("2026-08-19T19:00:00Z")).getBookingContext(BOOKING_CODE);
        assertThat(preview.canCheckIn()).isTrue();
        assertThat(preview.earlyArrivalRequired()).isFalse();
        var overdue = serviceAt(Instant.parse("2026-08-19T23:00:00Z")).getBookingContext(BOOKING_CODE);
        assertThat(overdue.canCheckIn()).isFalse();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void assignsRoomAndChecksInWithinLockedFlow() {
        RoomType roomType = roomType(ROOM_TYPE_ID);
        Booking booking = booking(BookingStatus.CONFIRMED, LocalDate.of(2026, 8, 19), roomType);
        Room room = room(roomType, RoomStatus.AVAILABLE);
        User receptionist = User.builder().id(RECEPTIONIST_ID).build();

        when(bookingRepository.findByBookingCodeForUpdate(BOOKING_CODE)).thenReturn(Optional.of(booking));
        when(roomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(room));
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(RECEPTIONIST_ID));
        when(userRepository.findById(RECEPTIONIST_ID)).thenReturn(Optional.of(receptionist));

        var response = service.checkIn(BOOKING_CODE, new CheckInGuestRequest(ROOM_ID));

        assertThat(response.bookingStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(response.checkedInAt()).isEqualTo(NOW);
        assertThat(response.room().status()).isEqualTo(RoomStatus.OCCUPIED);
        assertThat(booking.getAssignedRoom()).isSameAs(room);
        assertThat(booking.getStatusHistories()).singleElement().satisfies(history -> {
            assertThat(history.getOldStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(history.getNewStatus()).isEqualTo(BookingStatus.CHECKED_IN);
            assertThat(history.getChangedBy()).isSameAs(receptionist);
        });
        var lockOrder = inOrder(bookingRepository, roomRepository);
        lockOrder.verify(bookingRepository).findByBookingCodeForUpdate(BOOKING_CODE);
        lockOrder.verify(roomRepository).findByIdForUpdate(ROOM_ID);
    }

    @Test
    void rejectsBookingThatIsNotConfirmedBeforeLockingRoom() {
        Booking booking = booking(BookingStatus.PENDING, LocalDate.of(2026, 8, 19), roomType(ROOM_TYPE_ID));
        when(bookingRepository.findByBookingCodeForUpdate(BOOKING_CODE)).thenReturn(Optional.of(booking));

        assertConflict("BOOKING_NOT_CONFIRMED", () -> service.checkIn(BOOKING_CODE, new CheckInGuestRequest(ROOM_ID)));

        verify(roomRepository, never()).findByIdForUpdate(ROOM_ID);
    }

    @Test
    void rejectsBookingThatWasAlreadyCheckedInWithSpecificCode() {
        Booking booking = booking(BookingStatus.CHECKED_IN, LocalDate.of(2026, 8, 19), roomType(ROOM_TYPE_ID));
        when(bookingRepository.findByBookingCodeForUpdate(BOOKING_CODE)).thenReturn(Optional.of(booking));

        assertConflict(
                "BOOKING_ALREADY_CHECKED_IN", () -> service.checkIn(BOOKING_CODE, new CheckInGuestRequest(ROOM_ID)));

        verify(roomRepository, never()).findByIdForUpdate(ROOM_ID);
    }

    @Test
    void replacesPreviousAssignmentWhenNewRoomIsValid() {
        RoomType roomType = roomType(ROOM_TYPE_ID);
        Room previousRoom = Room.builder()
                .id(UUID.randomUUID())
                .roomType(roomType)
                .roomNumber("0500")
                .floor(5)
                .status(RoomStatus.AVAILABLE)
                .build();
        Booking booking = Booking.builder()
                .id(BOOKING_ID)
                .bookingCode(BOOKING_CODE)
                .roomType(roomType)
                .assignedRoom(previousRoom)
                .checkInDate(LocalDate.of(2026, 8, 19))
                .checkOutDate(LocalDate.of(2026, 8, 20))
                .status(BookingStatus.CONFIRMED)
                .build();
        Room replacementRoom = room(roomType, RoomStatus.AVAILABLE);
        User receptionist = User.builder().id(RECEPTIONIST_ID).build();
        when(bookingRepository.findByBookingCodeForUpdate(BOOKING_CODE)).thenReturn(Optional.of(booking));
        when(roomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(replacementRoom));
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(RECEPTIONIST_ID));
        when(userRepository.findById(RECEPTIONIST_ID)).thenReturn(Optional.of(receptionist));

        service.checkIn(BOOKING_CODE, new CheckInGuestRequest(ROOM_ID));

        assertThat(booking.getAssignedRoom()).isSameAs(replacementRoom);
        assertThat(replacementRoom.getStatus()).isEqualTo(RoomStatus.OCCUPIED);
        assertThat(previousRoom.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
    }

    @Test
    void rejectsCheckInOutsideArrivalDate() {
        Booking booking = booking(BookingStatus.CONFIRMED, LocalDate.of(2026, 8, 20), roomType(ROOM_TYPE_ID));
        when(bookingRepository.findByBookingCodeForUpdate(BOOKING_CODE)).thenReturn(Optional.of(booking));

        assertConflict(
                "CHECK_IN_DATE_NOT_ALLOWED", () -> service.checkIn(BOOKING_CODE, new CheckInGuestRequest(ROOM_ID)));
    }

    @Test
    void rejectsRoomOfAnotherType() {
        Booking booking = booking(BookingStatus.CONFIRMED, LocalDate.of(2026, 8, 19), roomType(ROOM_TYPE_ID));
        Room room = room(roomType(UUID.randomUUID()), RoomStatus.AVAILABLE);
        when(bookingRepository.findByBookingCodeForUpdate(BOOKING_CODE)).thenReturn(Optional.of(booking));
        when(roomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(room));

        assertConflict("ROOM_TYPE_MISMATCH", () -> service.checkIn(BOOKING_CODE, new CheckInGuestRequest(ROOM_ID)));
    }

    @Test
    void rejectsRoomThatIsNoLongerAvailable() {
        RoomType roomType = roomType(ROOM_TYPE_ID);
        Booking booking = booking(BookingStatus.CONFIRMED, LocalDate.of(2026, 8, 19), roomType);
        Room room = room(roomType, RoomStatus.OCCUPIED);
        when(bookingRepository.findByBookingCodeForUpdate(BOOKING_CODE)).thenReturn(Optional.of(booking));
        when(roomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(room));

        assertConflict("ROOM_NOT_AVAILABLE", () -> service.checkIn(BOOKING_CODE, new CheckInGuestRequest(ROOM_ID)));
    }

    @Test
    void listsAvailableRoomsForConfirmedArrivalInDisplayOrder() {
        RoomType roomType = roomType(ROOM_TYPE_ID);
        Booking booking = booking(BookingStatus.CONFIRMED, LocalDate.of(2026, 8, 19), roomType);
        Room firstRoom = room(roomType, RoomStatus.AVAILABLE);
        Room secondRoom = Room.builder()
                .id(UUID.randomUUID())
                .roomType(roomType)
                .roomNumber("0502")
                .floor(5)
                .status(RoomStatus.AVAILABLE)
                .build();
        when(bookingRepository.findByBookingCode(BOOKING_CODE)).thenReturn(Optional.of(booking));
        when(roomRepository.findAllByRoomTypeIdAndStatusOrderByFloorAscRoomNumberAsc(
                        ROOM_TYPE_ID, RoomStatus.AVAILABLE))
                .thenReturn(List.of(firstRoom, secondRoom));

        var rooms = service.getAvailableRooms(BOOKING_CODE);

        assertThat(rooms).extracting("roomNumber").containsExactly("0501", "0502");
        assertThat(rooms).extracting("floor").containsExactly(5, 5);
    }

    private void assertConflict(String code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CheckInConflictException.class)
                .extracting(exception -> ((DomainException) exception).getErrorCode())
                .isEqualTo(code);
    }

    private CheckInServiceImpl serviceAt(Instant instant) {
        var clock = Clock.fixed(instant, ZoneId.of("Asia/Ho_Chi_Minh"));
        return new CheckInServiceImpl(
                bookingRepository,
                roomRepository,
                userRepository,
                currentUserProvider,
                clock,
                new com.hanbay.hotel.booking.policy.HotelStayPolicy(
                        clock, new com.hanbay.hotel.booking.config.HotelStayProperties()));
    }

    @Test
    void requiresReceptionistReasonBeforeStandardArrivalTime() {
        var booking = booking(BookingStatus.CONFIRMED, LocalDate.of(2026, 8, 19), roomType(ROOM_TYPE_ID));
        when(bookingRepository.findByBookingCodeForUpdate(BOOKING_CODE)).thenReturn(Optional.of(booking));
        assertThatThrownBy(
                        () -> serviceAt(NOW.minusSeconds(3600)).checkIn(BOOKING_CODE, new CheckInGuestRequest(ROOM_ID)))
                .isInstanceOf(com.hanbay.hotel.common.exception.RequestValidationException.class)
                .hasMessageContaining("trước giờ chuẩn");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        org.mockito.Mockito.verifyNoInteractions(roomRepository);
    }

    @Test
    void recordsApprovedEarlyArrivalAndActualUtcTime() {
        var type = roomType(ROOM_TYPE_ID);
        var booking = booking(BookingStatus.CONFIRMED, LocalDate.of(2026, 8, 19), type);
        var room = room(type, RoomStatus.AVAILABLE);
        when(bookingRepository.findByBookingCodeForUpdate(BOOKING_CODE)).thenReturn(Optional.of(booking));
        when(roomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(room));
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(RECEPTIONIST_ID));
        when(userRepository.findById(RECEPTIONIST_ID))
                .thenReturn(Optional.of(User.builder().id(RECEPTIONIST_ID).build()));
        serviceAt(NOW.minusSeconds(3600)).checkIn(BOOKING_CODE, new CheckInGuestRequest(ROOM_ID, "Phòng đã dọn xong"));
        assertThat(booking.getCheckedInAt()).isEqualTo(NOW.minusSeconds(3600));
        assertThat(booking.getStatusHistories().get(0).getReason()).contains("duyệt nhận phòng sớm");
    }

    @Test
    void acceptsLateArrivalAfterMidnightWithoutChangingOriginalStayDates() {
        var type = roomType(ROOM_TYPE_ID);
        var booking = booking(BookingStatus.CONFIRMED, LocalDate.of(2026, 8, 19), type);
        var room = room(type, RoomStatus.AVAILABLE);
        when(bookingRepository.findByBookingCodeForUpdate(BOOKING_CODE)).thenReturn(Optional.of(booking));
        when(roomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(room));
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(RECEPTIONIST_ID));
        when(userRepository.findById(RECEPTIONIST_ID))
                .thenReturn(Optional.of(User.builder().id(RECEPTIONIST_ID).build()));
        var actual = Instant.parse("2026-08-19T19:00:00Z");
        serviceAt(actual).checkIn(BOOKING_CODE, new CheckInGuestRequest(ROOM_ID));
        assertThat(booking.getCheckInDate()).isEqualTo(LocalDate.of(2026, 8, 19));
        assertThat(booking.getCheckOutDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(booking.getCheckedInAt()).isEqualTo(actual);
    }

    private Booking booking(BookingStatus status, LocalDate checkInDate, RoomType roomType) {
        return Booking.builder()
                .id(BOOKING_ID)
                .bookingCode("BK20260819000001")
                .roomType(roomType)
                .checkInDate(checkInDate)
                .checkOutDate(checkInDate.plusDays(1))
                .status(status)
                .build();
    }

    private Room room(RoomType roomType, RoomStatus status) {
        return Room.builder()
                .id(ROOM_ID)
                .roomType(roomType)
                .roomNumber("0501")
                .floor(5)
                .status(status)
                .build();
    }

    private RoomType roomType(UUID id) {
        return RoomType.builder().id(id).build();
    }
}
