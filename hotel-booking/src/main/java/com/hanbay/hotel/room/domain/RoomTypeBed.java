package com.hanbay.hotel.room.domain;

import com.hanbay.hotel.common.domain.BaseEntity;
import com.hanbay.hotel.room.exception.RoomValidationException;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "room_type_beds", uniqueConstraints = @UniqueConstraint(columnNames = {"room_type_id", "bed_type"}))
public class RoomTypeBed extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Enumerated(EnumType.STRING)
    @Column(name = "bed_type", nullable = false, length = 50)
    private BedType bedType;

    @Column(nullable = false)
    private int quantity;

    RoomTypeBed(RoomType roomType, BedType bedType, int quantity) {
        if (bedType == null || quantity <= 0) {
            throw new RoomValidationException("Loại giường và số lượng giường không hợp lệ.");
        }
        this.roomType = roomType;
        this.bedType = bedType;
        this.quantity = quantity;
    }

    void updateQuantity(int quantity) {
        if (quantity <= 0) throw new RoomValidationException("Số lượng giường phải lớn hơn 0.");
        this.quantity = quantity;
    }
}
