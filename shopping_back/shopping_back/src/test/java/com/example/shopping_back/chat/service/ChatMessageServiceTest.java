package com.example.shopping_back.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shopping_back.chat.dto.ChatMessageDto;
import com.example.shopping_back.chat.mapper.ChatMessageMapper;
import com.example.shopping_back.chat.model.ChatMessage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class ChatMessageServiceTest {

    private ChatMessageMapper chatMessageMapper;
    private SimpMessagingTemplate messagingTemplate;
    private ChatMessageService chatMessageService;

    @BeforeEach
    void setUp() {
        chatMessageMapper = mock(ChatMessageMapper.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        chatMessageService = new ChatMessageService(chatMessageMapper);
        ReflectionTestUtils.setField(chatMessageService, "messagingTemplate", messagingTemplate);
    }

    @Test
    void createMessageUsesDefaultTypeAndMarksUnread() {
        ChatMessageDto dto = new ChatMessageDto();
        dto.setCovId(12);
        dto.setSenderId(7);
        dto.setContent("你好");
        dto.setPriceValue(new BigDecimal("99.9"));
        dto.setType(" ");

        ChatMessage result = chatMessageService.createMessage(dto);

        assertNotNull(result);
        assertEquals(12, result.getCovId());
        assertEquals(7, result.getSenderId());
        assertEquals("你好", result.getContent());
        assertEquals("CHAT_MESSAGE", result.getType());
        assertFalse(result.getIsRead());
        verify(chatMessageMapper).insert(any(ChatMessage.class));
    }

    @Test
    void sendMessageAndBroadcastPublishesToChatTopic() {
        ChatMessageDto dto = new ChatMessageDto();
        dto.setCovId(12);
        dto.setSenderId(7);
        dto.setContent("我想买这个");
        dto.setType("CHAT_MESSAGE");
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenReturn(1);

        ChatMessage result = chatMessageService.sendMessageAndBroadcast(dto);

        assertNotNull(result);
        assertEquals("我想买这个", result.getContent());
        verify(messagingTemplate).convertAndSend("/topic/chat/12", result);
    }

        // ==================== 补充 ChatMessageService 覆盖率测试 ====================

    @Test
    // 根据 covId 获取消息列表
    void getMessages_returnsList() {
        ChatMessage msg = new ChatMessage();
        msg.setCovId(12);
        msg.setSenderId(7);
        msg.setContent("测试消息");
        when(chatMessageMapper.selectByCovId(12)).thenReturn(List.of(msg));

        List<ChatMessage> result = chatMessageService.getMessages(12);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("测试消息", result.get(0).getContent());
    }

    @Test
    // getMessages 返回空列表
    void getMessages_returnsEmpty() {
        when(chatMessageMapper.selectByCovId(999)).thenReturn(List.of());

        List<ChatMessage> result = chatMessageService.getMessages(999);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    // 获取最近消息（limit）
    void getRecentMessages_returnsLimitedList() {
        ChatMessage msg1 = new ChatMessage();
        msg1.setCovId(12);
        msg1.setContent("第一条");
        ChatMessage msg2 = new ChatMessage();
        msg2.setCovId(12);
        msg2.setContent("第二条");
        // 使用 ArrayList 包装，使其可变
        List<ChatMessage> mockList = new ArrayList<>(List.of(msg2, msg1));
        when(chatMessageMapper.findRecentByCovId(12, 2)).thenReturn(mockList);

        List<ChatMessage> result = chatMessageService.getRecentMessages(12, 2);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("第一条", result.get(0).getContent()); // 反转后顺序
    }

    @Test
    // 获取最后一条消息
    void getLastMessageByCovId_returnsLast() {
        ChatMessage msg = new ChatMessage();
        msg.setCovId(12);
        msg.setContent("最后一条消息");
        when(chatMessageMapper.findLastByCovId(12)).thenReturn(msg);

        ChatMessage result = chatMessageService.getLastMessageByCovId(12);

        assertNotNull(result);
        assertEquals("最后一条消息", result.getContent());
    }

    @Test
    // 获取最后一条消息 - 不存在返回 null
    void getLastMessageByCovId_returnsNull() {
        when(chatMessageMapper.findLastByCovId(999)).thenReturn(null);

        ChatMessage result = chatMessageService.getLastMessageByCovId(999);

        assertNull(result);
    }

    @Test
    // 统计未读消息数量
    void countUnread_returnsCount() {
        when(chatMessageMapper.countUnreadByCovId(12, 7)).thenReturn(3);

        int result = chatMessageService.countUnread(12, 7);

        assertEquals(3, result);
    }

    @Test
    // 统计未读消息数量 - 无未读
    void countUnread_returnsZero() {
        when(chatMessageMapper.countUnreadByCovId(12, 7)).thenReturn(0);

        int result = chatMessageService.countUnread(12, 7);

        assertEquals(0, result);
    }
}
