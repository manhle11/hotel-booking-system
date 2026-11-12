package com.hanbay.hotel.roommap;

import static com.hanbay.hotel.roommap.RoomMapDto.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hanbay.hotel.common.exception.RequestValidationException;
import com.hanbay.hotel.room.domain.*;
import com.hanbay.hotel.room.dto.request.RoomWriteRequest;
import com.hanbay.hotel.room.repository.*;
import com.hanbay.hotel.room.service.RoomManagementService;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class RoomMapIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    RoomMapService service;

    @Autowired
    RoomRepository rooms;

    @Autowired
    RoomTypeRepository types;

    @Autowired
    RoomManagementService management;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Test
    void unconfiguredFloorIncludesEveryRoomWithoutGuessingPositions() {
        Room room = room(188, RoomStatus.AVAILABLE);
        var floor = service.managementFloor(188);
        assertThat(floor.version()).isZero();
        assertThat(floor.publishedAt()).isNull();
        assertThat(floor.elements()).isEmpty();
        assertThat(floor.rooms()).extracting(StaffRoom::roomId).containsExactly(room.getId());
    }

    @Test
    void publishingRoundTripsLayoutAndLeavesUnplacedRoomsVisible() {
        Room placed = room(188, RoomStatus.CLEANING), unplaced = room(188, RoomStatus.AVAILABLE);
        var floor = service.publish(
                188,
                plan(0, List.of(node(placed), new WriteElement(ElementKind.CORRIDOR, null, "Hành lang", 0, 4, 24, 2))));
        assertThat(floor.version()).isEqualTo(1);
        assertThat(floor.publishedAt()).isNotNull();
        assertThat(floor.elements()).hasSize(2);
        assertThat(floor.rooms())
                .extracting(StaffRoom::roomId)
                .containsExactlyInAnyOrder(placed.getId(), unplaced.getId());
        assertThat(floor.elements().get(0).roomKey()).isEqualTo(placed.getId().toString());
        assertThat(rooms.findById(placed.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.CLEANING);
    }

    @Test
    void publicResponseCannotExposeOccupancyRoomIdsOrPrivateNotes() throws Exception {
        Room room = room(188, RoomStatus.OCCUPIED);
        service.publish(188, plan(0, List.of(node(room))));
        var publicFloor = service.publicMap().floors().stream()
                .filter(f -> f.floorNumber() == 188)
                .findFirst()
                .orElseThrow();
        assertThat(publicFloor.elements().get(0).roomKey()).isEqualTo(room.getRoomNumber());
        mvc.perform(get("/api/v1/hotel-map"))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                                room.getId().toString()))))
                .andExpect(content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("operationalStatus"))))
                .andExpect(content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE-ROOM-NOTE"))))
                .andExpect(
                        content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("OCCUPIED"))));
    }

    @Test
    void staleVersionDoesNotReplacePublishedElements() {
        Room room = room(188, RoomStatus.AVAILABLE);
        service.publish(188, plan(0, List.of(node(room))));
        assertThatThrownBy(() -> service.publish(188, plan(0, List.of())))
                .isInstanceOfSatisfying(FloorPlanConflictException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo("FLOOR_PLAN_VERSION_CONFLICT"));
        assertThat(service.managementFloor(188).elements()).hasSize(1);
    }

    @Test
    void rejectsRoomFromAnotherFloorAndUnknownRoom() {
        Room other = room(189, RoomStatus.AVAILABLE);
        assertThatThrownBy(() -> service.publish(188, plan(0, List.of(node(other)))))
                .isInstanceOf(RequestValidationException.class);
        assertThatThrownBy(() -> service.publish(
                        188, plan(0, List.of(new WriteElement(ElementKind.ROOM, UUID.randomUUID(), "", 0, 0, 4, 3)))))
                .isInstanceOf(RequestValidationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hotel_floor_plans WHERE floor_number = 188", Long.class))
                .isZero();
    }

    @Test
    void duplicateRoomIsRejectedEvenAtDifferentPositions() {
        Room room = room(188, RoomStatus.AVAILABLE);
        assertThatThrownBy(() -> service.publish(
                        188,
                        plan(0, List.of(node(room), new WriteElement(ElementKind.ROOM, room.getId(), "", 5, 0, 4, 3)))))
                .isInstanceOf(RequestValidationException.class);
    }

    static Stream<List<WriteElement>> invalidGeometry() {
        return Stream.of(
                List.of(new WriteElement(ElementKind.COMMON_AREA, null, "", 23, 0, 4, 3)),
                List.of(new WriteElement(ElementKind.COMMON_AREA, null, "", -1, 0, 4, 3)),
                List.of(new WriteElement(ElementKind.COMMON_AREA, null, "", 0, 0, 0, 3)),
                List.of(new WriteElement(ElementKind.ROOM, null, "", 0, 0, 4, 3)),
                List.of(new WriteElement(ElementKind.STAIRS, UUID.randomUUID(), "", 0, 0, 4, 3)),
                List.of(
                        new WriteElement(ElementKind.CORRIDOR, null, "", 0, 0, 4, 3),
                        new WriteElement(ElementKind.STAIRS, null, "", 3, 1, 4, 3)));
    }

    @ParameterizedTest
    @MethodSource("invalidGeometry")
    void invalidGeometryCannotBePublished(List<WriteElement> elements) {
        assertThatThrownBy(() -> service.publish(188, plan(0, elements)))
                .isInstanceOf(RequestValidationException.class);
    }

    @Test
    void placedRoomCannotBeDeletedOrMovedButRenamingIsReflected() {
        Room room = room(188, RoomStatus.AVAILABLE);
        service.publish(188, plan(0, List.of(node(room))));
        assertThatThrownBy(() -> management.delete(room.getId())).isInstanceOf(FloorPlanConflictException.class);
        assertThatThrownBy(() -> management.update(
                        room.getId(), new RoomWriteRequest(room.getRoomType().getId(), room.getRoomNumber(), 189, "")))
                .isInstanceOf(FloorPlanConflictException.class);
        management.update(room.getId(), new RoomWriteRequest(room.getRoomType().getId(), "MAP-RENAMED", 188, ""));
        assertThat(service.publicMap().floors().stream()
                        .filter(f -> f.floorNumber() == 188)
                        .findFirst()
                        .orElseThrow()
                        .elements()
                        .get(0)
                        .roomKey())
                .isEqualTo("MAP-RENAMED");
    }

    @Test
    void removingElementAllowsDeletingUnusedRoom() {
        Room room = room(188, RoomStatus.AVAILABLE);
        service.publish(188, plan(0, List.of(node(room))));
        service.publish(188, plan(1, List.of()));
        management.delete(room.getId());
        assertThat(rooms.existsById(room.getId())).isFalse();
    }

    @Test
    void staffMapReturnsActualStatusesAndOnlyRequestedFloor() {
        Room room = room(188, RoomStatus.MAINTENANCE);
        var map = service.staffMap(188);
        assertThat(map.serverNow()).isNotNull();
        assertThat(map.floors()).hasSize(1);
        assertThat(map.floors().get(0).rooms())
                .contains(new StaffRoom(
                        room.getId().toString(),
                        room.getId(),
                        room.getRoomNumber(),
                        room.getRoomType().getId(),
                        room.getRoomType().getName(),
                        RoomStatus.MAINTENANCE));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentFirstPublicationHasExactlyOneWinner() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> publish = () -> {
            start.await(10, TimeUnit.SECONDS);
            try {
                service.publish(187, plan(0, List.of()));
                return true;
            } catch (FloorPlanConflictException conflict) {
                return false;
            }
        };
        try {
            Future<Boolean> first = executor.submit(publish), second = executor.submit(publish);
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(service.managementFloor(187).version()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            jdbc.update("DELETE FROM hotel_floor_plans WHERE floor_number = 187");
        }
    }

    private WritePlan plan(long version, List<WriteElement> elements) {
        return new WritePlan("Tầng thử nghiệm", 24, 16, version, elements);
    }

    private WriteElement node(Room room) {
        return new WriteElement(ElementKind.ROOM, room.getId(), "", 0, 0, 4, 3);
    }

    private Room room(int floor, RoomStatus status) {
        var type = types.saveAndFlush(RoomType.builder()
                .code("MAP-" + UUID.randomUUID())
                .name("Map test type")
                .maxGuests(2)
                .capacityAdults(2)
                .capacityChildren(0)
                .basePrice(1000000)
                .build());
        return rooms.saveAndFlush(Room.builder()
                .roomType(type)
                .roomNumber("MAP-" + UUID.randomUUID().toString().substring(0, 12))
                .floor(floor)
                .status(status)
                .note("PRIVATE-ROOM-NOTE")
                .build());
    }
}
