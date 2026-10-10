package com.hanbay.hotel.maintenance.repository;

import com.hanbay.hotel.maintenance.domain.MaintenanceRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, UUID> {

    boolean existsByRoomIdAndStatusNot(
            UUID roomId, com.hanbay.hotel.maintenance.domain.MaintenanceRequestStatus status);

    @Override
    @EntityGraph(attributePaths = {"room", "createdBy"})
    Page<MaintenanceRequest> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"room", "createdBy"})
    @Query("select request from MaintenanceRequest request where request.id = :id")
    Optional<MaintenanceRequest> findDetailsById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from MaintenanceRequest request where request.id = :id")
    Optional<MaintenanceRequest> findByIdForUpdate(@Param("id") UUID id);
}
