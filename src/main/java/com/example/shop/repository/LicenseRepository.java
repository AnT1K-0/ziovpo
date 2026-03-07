package com.example.shop.repository;

import com.example.shop.model.License;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface LicenseRepository extends JpaRepository<License, Long> {

    Optional<License> findByCode(String code);

    @Query("""
            select l
            from License l
            join DeviceLicense dl on dl.license = l
            join dl.device d
            where d.macAddress = :deviceMac
              and l.user.id = :userId
              and l.product.id = :productId
              and l.blocked = false
              and l.endingDate is not null
              and l.endingDate >= CURRENT_TIMESTAMP
            """)
    Optional<License> findActiveByDeviceUserAndProduct(String deviceMac, Long userId, Long productId);
}