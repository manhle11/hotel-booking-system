package com.hanbay.hotel.authentication.repository;

import com.hanbay.hotel.authentication.domain.entity.Role;
import com.hanbay.hotel.authentication.domain.enums.RoleCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(RoleCode code);
}
