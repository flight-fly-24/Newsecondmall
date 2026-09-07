package com.example.shopping_back.address;

public final class AddressDtos {
    private AddressDtos() {
    }

    public record AddressView(
            Integer addressId,
            String receiver,
            String phone,
            String province,
            String city,
            String district,
            String detail,
            boolean isDefault
    ) {
    }

    public record AddressRequest(
            String receiver,
            String phone,
            String province,
            String city,
            String district,
            String detail
    ) {
    }
}
