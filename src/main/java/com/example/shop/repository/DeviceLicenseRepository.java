package com.example.shop.repository;

import com.example.shop.model.Device;
import com.example.shop.model.DeviceLicense;
import com.example.shop.model.License;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceLicenseRepository extends JpaRepository<DeviceLicense, Long> {

    long countByLicense(License license);

    boolean existsByLicenseAndDevice(License license, Device device);

    Optional<DeviceLicense> findByLicenseAndDevice(License license, Device device);

    Optional<DeviceLicense> findFirstByLicense(License license);
}