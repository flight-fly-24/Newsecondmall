package com.example.shopping_back.chat.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shopping_back.auth.AuthService;
import com.example.shopping_back.auth.dto.AuthUserView;
import com.example.shopping_back.chat.dto.ConversationListDto;
import com.example.shopping_back.chat.dto.CreateConversationRequest;
import com.example.shopping_back.chat.dto.UpdateConversationStatusRequest;
import com.example.shopping_back.chat.model.ChatMessage;
import com.example.shopping_back.chat.mapper.ChatMessageMapper;
import com.example.shopping_back.chat.mapper.ConversationMapper;
import com.example.shopping_back.chat.model.Conversation;
import com.example.shopping_back.shop.mapper.ShopProductMapper;
import com.example.shopping_back.shop.model.ProductRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;

class ConversationServiceTest {

    private ConversationMapper conversationMapper;
    private ChatMessageMapper chatMessageMapper;
    private ShopProductMapper shopProductMapper;
    private ConversationService conversationService;
    private AuthService authService;
    private ChatMessageService chatMessageService;

    @BeforeEach
    void setUp() {
        conversationMapper = mock(ConversationMapper.class);
        chatMessageMapper = mock(ChatMessageMapper.class);
        shopProductMapper = mock(ShopProductMapper.class);
        authService = mock(AuthService.class);
        chatMessageService = mock(ChatMessageService.class);
        conversationService = new ConversationService(
                conversationMapper,
                shopProductMapper,
                authService,
                chatMessageService,
                chatMessageMapper);
    }

    @Test
    void deleteConversationRemovesMessagesThenConversation() {
        Conversation conversation = new Conversation();
        conversation.setCovId(12);
        when(conversationMapper.selectById(12)).thenReturn(conversation);

        conversationService.deleteConversation(12);

        verify(chatMessageMapper).deleteByCovId(12);
        verify(conversationMapper).deleteById(12);
    }

    @Test
    void deleteConversationRejectsMissingConversation() {
        when(conversationMapper.selectById(99)).thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> conversationService.deleteConversation(99));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(chatMessageMapper, never()).deleteByCovId(99);
        verify(conversationMapper, never()).deleteById(99);
    }

    @Test
    void createConversationReturnsExistingConversationWhenAlreadyExists() {
        CreateConversationRequest request = new CreateConversationRequest();
        request.setGoodsId(9);

        Conversation existing = new Conversation();
        existing.setCovId(12);
        existing.setBuyerId(7);
        existing.setSellerId(3);
        existing.setGoodsId(9);

        when(conversationMapper.findByBuyerAndGoods(7, 9)).thenReturn(existing);

        Conversation result = conversationService.createConversation(request, 7);

        assertEquals(12, result.getCovId());
        verify(conversationMapper, never()).insert(any(Conversation.class));
    }

    @Test
    void createConversationCreatesNewConversationForValidProduct() {
        CreateConversationRequest request = new CreateConversationRequest();
        request.setGoodsId(9);
        request.setStatus("pending");

        ProductRecord product = new ProductRecord();
        product.setGoodsId(9);
        product.setSellerId(3);

        when(conversationMapper.findByBuyerAndGoods(7, 9)).thenReturn(null);
        when(shopProductMapper.selectById(9)).thenReturn(product);

        Conversation result = conversationService.createConversation(request, 7);

        assertNotNull(result);
        assertEquals(7, result.getBuyerId());
        assertEquals(3, result.getSellerId());
        assertEquals(9, result.getGoodsId());
        verify(conversationMapper).insert(result);
    }

    @Test
    void createConversationRejectsMissingProduct() {
        CreateConversationRequest request = new CreateConversationRequest();
        request.setGoodsId(99);

        when(conversationMapper.findByBuyerAndGoods(7, 99)).thenReturn(null);
        when(shopProductMapper.selectById(99)).thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> conversationService.createConversation(request, 7));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }
        // ==================== 聊天列表测试（TC-CHAT-11） ====================

    @Test
    // TC-CHAT-11 查看聊天列表
    void getConversationDisplayListReturnsListWithDetails() {
        // 1. 准备会话数据
        Conversation conv = new Conversation();
        conv.setCovId(12);
        conv.setBuyerId(7);
        conv.setSellerId(3);
        conv.setGoodsId(9);
        conv.setStatus("pending");
        conv.setUpdateTime(new Date());

        // 2. Mock Mapper 返回会话列表
        when(conversationMapper.selectByUserId(7)).thenReturn(List.of(conv));

        // 3. Mock 目标用户信息
        AuthUserView targetUser = new AuthUserView(3, "sellerA", "138****8000", 100, "seller", "卖家", false, "normal", "");
        when(authService.getUserById(3)).thenReturn(targetUser);

        // 4. Mock 商品信息
        ProductRecord product = new ProductRecord();
        product.setGoodsId(9);
        product.setGoodsName("测试商品");
        product.setImage("/images/test.jpg");
        when(shopProductMapper.selectById(9)).thenReturn(product);

        // 5. Mock 最后一条消息
        ChatMessage lastMsg = new ChatMessage();
        lastMsg.setContent("最后一条消息内容");
        when(chatMessageService.getLastMessageByCovId(12)).thenReturn(lastMsg);

        // 6. Mock 未读消息数量
        when(chatMessageService.countUnread(12, 7)).thenReturn(3);

        // 7. 执行方法
        List<ConversationListDto> result = conversationService.getConversationDisplayList(7);

        // 8. 断言
        assertNotNull(result);
        assertEquals(1, result.size());

        ConversationListDto dto = result.get(0);
        assertEquals(12, dto.getCovId());
        assertEquals(9, dto.getGoodsId());
        assertEquals("pending", dto.getStatus());
        assertEquals("sellerA", dto.getTargetName());
        assertEquals("测试商品", dto.getGoodsName());
        assertEquals("/images/test.jpg", dto.getGoodsImageUrl());
        assertEquals("最后一条消息内容", dto.getLastMessage());
        assertEquals(3, dto.getUnreadCount());
        assertNotNull(dto.getLastTime());
    }

        // ==================== 补充 ConversationService 覆盖率测试 ====================

    @Test
    // 获取用户所有会话
    void getUserConversations_returnsList() {
        Conversation conv = new Conversation();
        conv.setCovId(12);
        conv.setBuyerId(7);
        conv.setSellerId(3);
        when(conversationMapper.selectByUserId(7)).thenReturn(List.of(conv));

        List<Conversation> result = conversationService.getUserConversations(7);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(12, result.get(0).getCovId());
    }

    @Test
    // 获取用户会话 - 空列表
    void getUserConversations_returnsEmpty() {
        when(conversationMapper.selectByUserId(999)).thenReturn(List.of());

        List<Conversation> result = conversationService.getUserConversations(999);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    // 更新会话状态
    void updateStatus_success() {
        UpdateConversationStatusRequest request = new UpdateConversationStatusRequest();
        request.setStatus("completed");

        conversationService.updateStatus(request, 12);

        verify(conversationMapper, times(1)).updateStatus(12, "completed");
    }

    @Test
    // 验证会话访问权限 - 买家有权限
    void validateAccess_buyerAllowed() {
        Conversation conv = new Conversation();
        conv.setBuyerId(7);
        conv.setSellerId(3);

        AuthUserView buyer = new AuthUserView(7, "buyer", "138****8000", 100, "buyer", "买家", false, "normal", "");
        assertDoesNotThrow(() -> conversationService.validateAccess(buyer, conv));
    }

    @Test
    // 验证会话访问权限 - 卖家有权限
    void validateAccess_sellerAllowed() {
        Conversation conv = new Conversation();
        conv.setBuyerId(7);
        conv.setSellerId(3);

        AuthUserView seller = new AuthUserView(3, "seller", "138****8000", 100, "seller", "卖家", false, "normal", "");
        assertDoesNotThrow(() -> conversationService.validateAccess(seller, conv));
    }

    @Test
    // 验证会话访问权限 - 无权用户
    void validateAccess_unauthorized() {
        Conversation conv = new Conversation();
        conv.setBuyerId(7);
        conv.setSellerId(3);

        AuthUserView other = new AuthUserView(5, "other", "138****8000", 100, "buyer", "买家", false, "normal", "");
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> conversationService.validateAccess(other, conv)
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    // 标记所有消息为已读
    void markAllAsRead_success() {
        conversationService.markAllAsRead(12, 7);
        verify(chatMessageMapper, times(1)).markAllAsRead(12, 7);
    }

    @Test
    // 更新最后活跃时间
    void updateLastActiveTime_success() {
        conversationService.updateLastActiveTime(12);
        verify(conversationMapper, times(1)).updateLastActiveTime(12);
    }

    @Test
    // 获取另一方参与者ID - 买家视角
    void getOtherParticipantId_buyerView() {
        Conversation conv = new Conversation();
        conv.setBuyerId(7);
        conv.setSellerId(3);
        when(conversationMapper.selectById(12)).thenReturn(conv);

        Integer result = conversationService.getOtherParticipantId(12, 7);
        assertEquals(3, result);
    }

    @Test
    // 获取另一方参与者ID - 卖家视角
    void getOtherParticipantId_sellerView() {
        Conversation conv = new Conversation();
        conv.setBuyerId(7);
        conv.setSellerId(3);
        when(conversationMapper.selectById(12)).thenReturn(conv);

        Integer result = conversationService.getOtherParticipantId(12, 3);
        assertEquals(7, result);
    }

    @Test
    // 获取另一方参与者ID - 用户不匹配（应返回 null）
    void getOtherParticipantId_notMatch() {
        Conversation conv = new Conversation();
        conv.setBuyerId(7);
        conv.setSellerId(3);
        when(conversationMapper.selectById(12)).thenReturn(conv);

        Integer result = conversationService.getOtherParticipantId(12, 99);
        assertNull(result);
    }
}
