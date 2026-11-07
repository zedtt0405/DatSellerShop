package com.DatShop.service;

import com.DatShop.dto.account.AddressCreateRequest;
import com.DatShop.dto.account.AddressResponse;
import com.DatShop.dto.account.AddressUpdateRequest;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.AddressMapper;
import com.DatShop.model.Address;
import com.DatShop.model.User;
import com.DatShop.repository.AddressRepository;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AddressService {

    private final AddressRepository addressRepository;
    private final CurrentUserService currentUserService;

    public AddressService(AddressRepository addressRepository, CurrentUserService currentUserService) {
        this.addressRepository = addressRepository;
        this.currentUserService = currentUserService;
    }

    public List<AddressResponse> listMine(Authentication authentication) {
        User user = currentUserService.requireUser(authentication);
        return addressRepository.findByUser_IdOrderByCreatedAtAscIdAsc(user.getId()).stream()
                .map(AddressMapper::toResponse)
                .toList();
    }

    @Transactional
    public AddressResponse create(Authentication authentication, AddressCreateRequest request) {
        AddressCreateRequest normalized = normalize(request);
        User user = currentUserService.lockCurrentUser(authentication);
        boolean makeDefault = addressRepository.countByUser_Id(user.getId()) == 0 || normalized.isDefault();
        if (makeDefault) {
            clearDefaults(user.getId());
        }
        Address address = new Address();
        address.setUser(user);
        apply(address, normalized.recipientName(), normalized.phoneNumber(), normalized.province(),
                normalized.district(), normalized.ward(), normalized.addressLine(), makeDefault);
        return AddressMapper.toResponse(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse update(Authentication authentication, Long addressId, AddressUpdateRequest request) {
        AddressUpdateRequest normalized = normalize(request);
        User user = currentUserService.lockCurrentUser(authentication);
        Address address = requireOwned(user.getId(), addressId);
        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        boolean makeDefault = normalized.isDefault();
        if (makeDefault) {
            clearDefaults(user.getId());
        }
        apply(address, normalized.recipientName(), normalized.phoneNumber(), normalized.province(),
                normalized.district(), normalized.ward(), normalized.addressLine(), makeDefault);
        if (wasDefault && !makeDefault) {
            ensureSingleDefault(user.getId(), address);
        }
        addressRepository.flush();
        return AddressMapper.toResponse(address);
    }

    @Transactional
    public void delete(Authentication authentication, Long addressId) {
        User user = currentUserService.lockCurrentUser(authentication);
        Address address = requireOwned(user.getId(), addressId);
        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        addressRepository.delete(address);
        addressRepository.flush();
        if (wasDefault) {
            promoteOldest(user.getId());
        }
    }

    @Transactional
    public AddressResponse setDefault(Authentication authentication, Long addressId) {
        User user = currentUserService.lockCurrentUser(authentication);
        Address address = requireOwned(user.getId(), addressId);
        clearDefaults(user.getId());
        address.setIsDefault(true);
        addressRepository.flush();
        return AddressMapper.toResponse(address);
    }

    private Address requireOwned(Long userId, Long addressId) {
        return addressRepository.findByIdAndUser_Id(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
    }

    private void clearDefaults(Long userId) {
        for (Address address : addressRepository.findByUser_IdAndIsDefaultTrue(userId)) {
            address.setIsDefault(false);
        }
    }

    private void ensureSingleDefault(Long userId, Address current) {
        List<Address> remaining = addressRepository.findByUser_IdOrderByCreatedAtAscIdAsc(userId);
        if (remaining.size() <= 1) {
            current.setIsDefault(true);
            return;
        }
        current.setIsDefault(false);
        remaining.stream()
                .filter(address -> !address.getId().equals(current.getId()))
                .findFirst()
                .ifPresent(address -> address.setIsDefault(true));
    }

    private void promoteOldest(Long userId) {
        addressRepository.findByUser_IdOrderByCreatedAtAscIdAsc(userId).stream()
                .findFirst()
                .ifPresent(address -> address.setIsDefault(true));
    }

    private static void apply(
            Address address,
            String recipientName,
            String phoneNumber,
            String province,
            String district,
            String ward,
            String addressLine,
            boolean isDefault) {
        address.setRecipientName(recipientName);
        address.setPhoneNumber(phoneNumber);
        address.setProvince(province);
        address.setDistrict(district);
        address.setWard(ward);
        address.setAddressLine(addressLine);
        address.setIsDefault(isDefault);
    }

    private static AddressCreateRequest normalize(AddressCreateRequest request) {
        return new AddressCreateRequest(
                Text.trim(request.recipientName()),
                Text.trim(request.phoneNumber()),
                Text.trim(request.province()),
                Text.trim(request.district()),
                Text.trim(request.ward()),
                Text.trim(request.addressLine()),
                request.isDefault());
    }

    private static AddressUpdateRequest normalize(AddressUpdateRequest request) {
        return new AddressUpdateRequest(
                Text.trim(request.recipientName()),
                Text.trim(request.phoneNumber()),
                Text.trim(request.province()),
                Text.trim(request.district()),
                Text.trim(request.ward()),
                Text.trim(request.addressLine()),
                request.isDefault());
    }
}
