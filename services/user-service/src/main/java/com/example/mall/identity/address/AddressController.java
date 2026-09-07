package com.example.mall.identity.address;

import com.example.mall.identity.address.AddressDtos.AddressRequest;
import com.example.mall.identity.address.AddressDtos.AddressView;
import com.example.mall.common.dto.ApiResult;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/address")
public class AddressController {
    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ApiResult<List<AddressView>> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(addressService.list(bearerToken(authorization)));
    }

    @PostMapping
    public ApiResult<List<AddressView>> add(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody AddressRequest body) {
        return ApiResult.ok(addressService.add(bearerToken(authorization), body));
    }

    @PutMapping("/{addressId}")
    public ApiResult<List<AddressView>> update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("addressId") Integer addressId,
            @RequestBody AddressRequest body) {
        return ApiResult.ok(addressService.update(bearerToken(authorization), addressId, body));
    }

    @DeleteMapping("/{addressId}")
    public ApiResult<List<AddressView>> remove(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("addressId") Integer addressId) {
        return ApiResult.ok(addressService.remove(bearerToken(authorization), addressId));
    }

    @PutMapping("/{addressId}/default")
    public ApiResult<List<AddressView>> setDefault(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("addressId") Integer addressId) {
        return ApiResult.ok(addressService.setDefault(bearerToken(authorization), addressId));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String v = authorization.trim();
        if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return v.substring(7).trim();
        }
        return null;
    }
}
