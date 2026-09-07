package com.example.shopping_back.address;

import com.example.shopping_back.address.AddressDtos.AddressRequest;
import com.example.shopping_back.address.AddressDtos.AddressView;
import com.example.shopping_back.auth.AuthService;
import com.example.shopping_back.auth.dto.AuthUserView;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AddressService {
    private final AddressMapper addressMapper;
    private final AuthService authService;

    public AddressService(AddressMapper addressMapper, AuthService authService) {
        this.addressMapper = addressMapper;
        this.authService = authService;
        ensureSchema();
    }

    public List<AddressView> list(String token) {
        AuthUserView user = current(token);
        ensureSchema();
        return addressMapper.selectByUser(user.getUserId()).stream().map(this::toView).toList();
    }

    public List<AddressView> add(String token, AddressRequest request) {
        AuthUserView user = current(token);
        ensureSchema();
        validate(request);
        AddressRecord record = new AddressRecord();
        record.setUserId(user.getUserId());
        record.setReceiver(request.receiver().trim());
        record.setPhone(request.phone().trim());
        record.setProvince(trim(request.province()));
        record.setCity(trim(request.city()));
        record.setDistrict(trim(request.district()));
        record.setDetail(request.detail().trim());
        record.setDefault(addressMapper.countByUser(user.getUserId()) == 0);
        addressMapper.insert(record);
        return list(token);
    }

    public List<AddressView> update(String token, Integer addressId, AddressRequest request) {
        AuthUserView user = current(token);
        ensureSchema();
        validate(request);
        requireOwn(user.getUserId(), addressId);
        AddressRecord record = new AddressRecord();
        record.setAddressId(addressId);
        record.setUserId(user.getUserId());
        record.setReceiver(request.receiver().trim());
        record.setPhone(request.phone().trim());
        record.setProvince(trim(request.province()));
        record.setCity(trim(request.city()));
        record.setDistrict(trim(request.district()));
        record.setDetail(request.detail().trim());
        addressMapper.update(record);
        return list(token);
    }

    public List<AddressView> remove(String token, Integer addressId) {
        AuthUserView user = current(token);
        ensureSchema();
        requireOwn(user.getUserId(), addressId);
        addressMapper.delete(addressId, user.getUserId());
        return list(token);
    }

    public List<AddressView> setDefault(String token, Integer addressId) {
        AuthUserView user = current(token);
        ensureSchema();
        requireOwn(user.getUserId(), addressId);
        addressMapper.clearDefault(user.getUserId());
        addressMapper.setDefault(addressId, user.getUserId());
        return list(token);
    }

    private void validate(AddressRequest request) {
        if (request == null || isBlank(request.receiver())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "收件人不能为空");
        }
        if (isBlank(request.phone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号不能为空");
        }
        if (isBlank(request.detail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "详细地址不能为空");
        }
    }

    private void requireOwn(Integer userId, Integer addressId) {
        if (addressId == null || addressMapper.selectById(addressId, userId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "地址不存在");
        }
    }

    private AuthUserView current(String token) {
        return authService.me(token);
    }

    private void ensureSchema() {
        try {
            addressMapper.createTable();
        } catch (RuntimeException ignored) {
        }
    }

    private AddressView toView(AddressRecord record) {
        return new AddressView(
                record.getAddressId(),
                record.getReceiver(),
                record.getPhone(),
                record.getProvince() == null ? "" : record.getProvince(),
                record.getCity() == null ? "" : record.getCity(),
                record.getDistrict() == null ? "" : record.getDistrict(),
                record.getDetail(),
                record.isDefault()
        );
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
