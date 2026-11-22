package com.DatShop.mapper;

import com.DatShop.dto.account.AddressResponse;
import com.DatShop.model.Address;

public final class AddressMapper {

    private AddressMapper() {
    }

    public static AddressResponse toResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getRecipientName(),
                address.getPhoneNumber(),
                address.getProvince(),
                address.getDistrict(),
                address.getWard(),
                address.getAddressLine(),
                address.getIsDefault(),
                address.getCreatedAt(),
                address.getUpdatedAt());
    }
}
