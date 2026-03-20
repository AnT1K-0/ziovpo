package com.example.shop.service;

import com.example.shop.signature.SigningService;
import com.example.shop.controller.dto.*;
import com.example.shop.model.*;
import com.example.shop.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LicenseService {

    private static final long TICKET_TTL_SECONDS = 300L;
    private final SigningService signingService;
    private final ProductRepository productRepository;
    private final LicenseTypeRepository licenseTypeRepository;
    private final LicenseRepository licenseRepository;
    private final LicenseHistoryRepository licenseHistoryRepository;
    private final UserAccountRepository userAccountRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceLicenseRepository deviceLicenseRepository;

    @Transactional
    public CreateLicenseResponse createLicense(CreateLicenseRequest request, Long adminId) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        LicenseType licenseType = licenseTypeRepository.findById(request.typeId())
                .orElseThrow(() -> new EntityNotFoundException("License type not found"));

        UserAccount owner = userAccountRepository.findById(request.ownerId())
                .orElseThrow(() -> new EntityNotFoundException("Owner not found"));

        UserAccount admin = userAccountRepository.findById(adminId)
                .orElseThrow(() -> new EntityNotFoundException("Admin not found"));

        License license = new License();
        license.setCode(generateCode());
        license.setOwner(owner);
        license.setUser(null);
        license.setProduct(product);
        license.setType(licenseType);
        license.setFirstActivationDate(null);
        license.setEndingDate(null);
        license.setBlocked(false);
        license.setDeviceCount(request.deviceCount());
        license.setDescription(request.description());

        License saved = licenseRepository.save(license);

        LicenseHistory history = new LicenseHistory();
        history.setLicense(saved);
        history.setUser(admin);
        history.setStatus(LicenseHistoryStatus.CREATED);
        history.setChangeDate(OffsetDateTime.now());
        history.setDescription("License created by admin");
        licenseHistoryRepository.save(history);

        return new CreateLicenseResponse(
                saved.getId(),
                saved.getCode(),
                saved.getOwner().getId(),
                saved.getProduct().getId(),
                saved.getType().getId(),
                saved.getDeviceCount(),
                saved.isBlocked(),
                saved.getFirstActivationDate(),
                saved.getEndingDate(),
                saved.getDescription()
        );
    }

    @Transactional
    public TicketResponse activateLicense(ActivateLicenseRequest request, Long userId) throws IllegalAccessException {
        License license = licenseRepository.findByCode(request.activationKey())
                .orElseThrow(() -> new EntityNotFoundException("License not found"));

        if (license.getUser() != null && !license.getUser().getId().equals(userId)) {
            throw new IllegalAccessException("License owned by another user");
        }

        if (license.isBlocked()) {
            throw new IllegalStateException("License is blocked");
        }

        if (license.getProduct().isBlocked()) {
            throw new IllegalStateException("Product is blocked");
        }

        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Device device = deviceRepository.findByMacAddress(request.deviceMac())
                .orElseGet(() -> {
                    Device d = new Device();
                    d.setName(request.deviceName());
                    d.setMacAddress(request.deviceMac());
                    d.setUser(user);
                    return deviceRepository.save(d);
                });

        if (license.getUser() == null) {
            OffsetDateTime now = OffsetDateTime.now();

            license.setUser(user);
            license.setFirstActivationDate(now);
            license.setEndingDate(now.plusDays(license.getType().getDefaultDurationInDays()));
            licenseRepository.save(license);

            createDeviceLicenseIfAbsent(license, device, now);
            saveHistory(license, user, LicenseHistoryStatus.ACTIVATED, "First activation");

            return buildTicketResponse(license, device);
        }

        long currentDeviceCount = deviceLicenseRepository.countByLicense(license);

        if (!deviceLicenseRepository.existsByLicenseAndDevice(license, device)
                && currentDeviceCount >= license.getDeviceCount()) {
            throw new IllegalStateException("Device limit reached");
        }

        createDeviceLicenseIfAbsent(license, device, OffsetDateTime.now());
        saveHistory(license, user, LicenseHistoryStatus.ACTIVATED, "Additional device activation");

        return buildTicketResponse(license, device);
    }

    @Transactional
    public TicketResponse renewLicense(RenewLicenseRequest request, Long userId) throws IllegalAccessException {
        License license = licenseRepository.findByCode(request.activationKey())
                .orElseThrow(() -> new EntityNotFoundException("License not found"));

        if (license.getUser() == null || !license.getUser().getId().equals(userId)) {
            throw new IllegalAccessException("License belongs to another user");
        }

        if (license.isBlocked()) {
            throw new IllegalStateException("License is blocked");
        }

        OffsetDateTime now = OffsetDateTime.now();

        boolean renewable = license.getEndingDate() == null
                || license.getEndingDate().isBefore(now)
                || !license.getEndingDate().isAfter(now.plusDays(7));

        if (!renewable) {
            throw new IllegalStateException("License is not yet renewable");
        }

        OffsetDateTime baseDate = license.getEndingDate() != null && license.getEndingDate().isAfter(now)
                ? license.getEndingDate()
                : now;

        license.setEndingDate(baseDate.plusDays(license.getType().getDefaultDurationInDays()));
        licenseRepository.save(license);

        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        saveHistory(license, user, LicenseHistoryStatus.RENEWED, "License renewed");

        Device device = deviceLicenseRepository.findFirstByLicense(license)
                .map(DeviceLicense::getDevice)
                .orElseThrow(() -> new EntityNotFoundException("Device not found"));

        return buildTicketResponse(license, device);
    }

    @Transactional(readOnly = true)
    public TicketResponse checkLicense(CheckLicenseRequest request, Long userId) {
        Device device = deviceRepository.findByMacAddress(request.deviceMac())
                .orElseThrow(() -> new EntityNotFoundException("Device not found"));

        if (!device.getUser().getId().equals(userId)) {
            throw new EntityNotFoundException("Device not found");
        }

        License license = licenseRepository.findActiveByDeviceUserAndProduct(request.deviceMac(), userId, request.productId())
                .orElseThrow(() -> new EntityNotFoundException("License not found"));

        return buildTicketResponse(license, device);
    }

    private void createDeviceLicenseIfAbsent(License license, Device device, OffsetDateTime activationTime) {
        boolean exists = deviceLicenseRepository.existsByLicenseAndDevice(license, device);
        if (!exists) {
            DeviceLicense deviceLicense = new DeviceLicense();
            deviceLicense.setLicense(license);
            deviceLicense.setDevice(device);
            deviceLicense.setActivationDate(activationTime);
            deviceLicenseRepository.save(deviceLicense);
        }
    }

    private void saveHistory(License license, UserAccount user, LicenseHistoryStatus status, String description) {
        LicenseHistory history = new LicenseHistory();
        history.setLicense(license);
        history.setUser(user);
        history.setStatus(status);
        history.setChangeDate(OffsetDateTime.now());
        history.setDescription(description);
        licenseHistoryRepository.save(history);
    }

    private TicketResponse buildTicketResponse(License license, Device device) {
        Ticket ticket = new Ticket(
                OffsetDateTime.now(),
                TICKET_TTL_SECONDS,
                license.getFirstActivationDate(),
                license.getEndingDate(),
                license.getUser() != null ? license.getUser().getId() : null,
                device.getId(),
                license.isBlocked()
        );

        return new TicketResponse(ticket, signingService.sign(ticket));
    }

    private String generateCode() {
        return "LIC-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}