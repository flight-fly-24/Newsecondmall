package com.example.mall.identity.address;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.mall.identity.address.AddressDtos.AddressRequest;
import com.example.mall.identity.address.AddressDtos.AddressView;
import com.example.mall.identity.auth.AuthService;
import com.example.mall.identity.auth.dto.AuthUserView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressMapper addressMapper;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AddressService addressService;

    private AuthUserView testUser;
    private AddressRecord testAddress;

    @BeforeEach
    void setUp() {
        testUser = new AuthUserView(100, "testuser", "138****8000", 100, "buyer", "买家", false, "normal", "");
        testAddress = new AddressRecord();
        testAddress.setAddressId(1);
        testAddress.setUserId(100);
        testAddress.setReceiver("张三");
        testAddress.setPhone("13800138000");
        testAddress.setProvince("广东省");
        testAddress.setCity("深圳市");
        testAddress.setDistrict("南山区");
        testAddress.setDetail("科技园路1号");
        testAddress.setDefault(true);
    }

    @Test
    // TC-ADDR-01 新增地址
    void addAddressSuccess() {
        when(authService.me("valid-token")).thenReturn(testUser);
        when(addressMapper.countByUser(100)).thenReturn(0);
        when(addressMapper.insert(any(AddressRecord.class))).thenReturn(1);
        when(addressMapper.selectByUser(100)).thenReturn(List.of(testAddress));

        AddressRequest request = new AddressRequest(
                "张三",
                "13800138000",
                "广东省",
                "深圳市",
                "南山区",
                "科技园路1号"
        );
        List<AddressView> result = addressService.add("valid-token", request);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("张三", result.get(0).receiver());
        verify(addressMapper, times(1)).insert(any(AddressRecord.class));
    }

    @Test
    // TC-ADDR-01 新增地址-未登录
    void addAddressRejectsUnauthenticated() {
        when(authService.me("invalid-token")).thenThrow(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录")
        );

        AddressRequest request = new AddressRequest("张三", "13800138000", "广东省", "深圳市", "南山区", "科技园路1号");
        assertThrows(ResponseStatusException.class, () -> addressService.add("invalid-token", request));
    }

    @Test
    // TC-ADDR-01 新增地址-收件人为空
    void addAddressRejectsEmptyReceiver() {
        when(authService.me("valid-token")).thenReturn(testUser);

        AddressRequest request = new AddressRequest("", "13800138000", "广东省", "深圳市", "南山区", "科技园路1号");
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> addressService.add("valid-token", request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    // TC-ADDR-02 编辑地址
    void updateAddressSuccess() {
        when(authService.me("valid-token")).thenReturn(testUser);
        doReturn(testAddress).when(addressMapper).selectById(1, 100);
        when(addressMapper.update(any(AddressRecord.class))).thenReturn(1);
        when(addressMapper.selectByUser(100)).thenReturn(List.of(testAddress));

        AddressRequest request = new AddressRequest(
                "李四",
                "13900139000",
                "广东省",
                "广州市",
                "天河区",
                "天河路1号"
        );
        List<AddressView> result = addressService.update("valid-token", 1, request);

        assertNotNull(result);
        verify(addressMapper, times(1)).update(any(AddressRecord.class));
    }

    @Test
    // TC-ADDR-02 编辑地址-地址不存在
    void updateAddressRejectsNotFound() {
        when(authService.me("valid-token")).thenReturn(testUser);
        when(addressMapper.selectById(999, 100)).thenReturn(null);

        AddressRequest request = new AddressRequest("李四", "13900139000", "广东省", "广州市", "天河区", "天河路1号");
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> addressService.update("valid-token", 999, request)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    // TC-ADDR-02 编辑地址-手机号为空
    void updateAddressRejectsEmptyPhone() {
        when(authService.me("valid-token")).thenReturn(testUser);

        AddressRequest request = new AddressRequest("李四", "", "广东省", "广州市", "天河区", "天河路1号");
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> addressService.update("valid-token", 1, request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    // TC-ADDR-03 删除地址
    void removeAddressSuccess() {
        when(authService.me("valid-token")).thenReturn(testUser);
        when(addressMapper.selectById(1, 100)).thenReturn(testAddress);
        when(addressMapper.delete(1, 100)).thenReturn(1);
        when(addressMapper.selectByUser(100)).thenReturn(List.of());

        List<AddressView> result = addressService.remove("valid-token", 1);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(addressMapper, times(1)).delete(1, 100);
    }

    @Test
    // TC-ADDR-03 删除地址-删除不存在的地址
    void removeAddressRejectsNotFound() {
        when(authService.me("valid-token")).thenReturn(testUser);
        when(addressMapper.selectById(999, 100)).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> addressService.remove("valid-token", 999)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    // TC-ADDR-04 设置默认地址
    void setDefaultAddressSuccess() {
        when(authService.me("valid-token")).thenReturn(testUser);
        when(addressMapper.selectById(1, 100)).thenReturn(testAddress);
        when(addressMapper.clearDefault(100)).thenReturn(1);
        when(addressMapper.setDefault(1, 100)).thenReturn(1);
        when(addressMapper.selectByUser(100)).thenReturn(List.of(testAddress));

        List<AddressView> result = addressService.setDefault("valid-token", 1);

        assertNotNull(result);
        verify(addressMapper, times(1)).clearDefault(100);
        verify(addressMapper, times(1)).setDefault(1, 100);
    }

    @Test
    // TC-ADDR-04 设置默认地址-地址不存在
    void setDefaultAddressRejectsNotFound() {
        when(authService.me("valid-token")).thenReturn(testUser);
        when(addressMapper.selectById(999, 100)).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> addressService.setDefault("valid-token", 999)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    // TC-ADDR-05 地址表单校验-详细地址为空
    void addAddressRejectsEmptyDetail() {
        when(authService.me("valid-token")).thenReturn(testUser);

        AddressRequest request = new AddressRequest("张三", "13800138000", "广东省", "深圳市", "南山区", "");
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> addressService.add("valid-token", request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    // TC-ADDR-05 地址表单校验-收件人为空（已在上面覆盖）
    void addAddressRejectsEmptyReceiverAlreadyTested() {
        // 已有测试覆盖，这里不再重复
    }

    @Test
    // 获取地址列表
    void listAddressesSuccess() {
        when(authService.me("valid-token")).thenReturn(testUser);
        when(addressMapper.selectByUser(100)).thenReturn(List.of(testAddress));

        List<AddressView> result = addressService.list("valid-token");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("张三", result.get(0).receiver());
        assertTrue(result.get(0).isDefault());
    }
}

