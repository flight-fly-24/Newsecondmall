package com.example.shopping_back.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.shopping_back.chat.dto.AiBargainSuggestion;
import com.example.shopping_back.chat.model.ChatMessage;
import com.example.shopping_back.chat.model.Conversation;
import com.example.shopping_back.shop.mapper.ShopProductMapper;
import com.example.shopping_back.shop.model.ProductRecord;
import dev.langchain4j.model.chat.ChatLanguageModel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiBargainServiceTest {

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private ShopProductMapper shopProductMapper;

    @Mock
    private ChatLanguageModel chatModel;

    private Conversation mockConversation;
    private ProductRecord mockProduct;
    private List<ChatMessage> mockHistory;

    @BeforeEach
    void setUp() {
        mockConversation = new Conversation();
        mockConversation.setCovId(1);
        mockConversation.setGoodsId(101);

        mockProduct = new ProductRecord();
        mockProduct.setGoodsId(101);
        mockProduct.setGoodsName("测试耳机");
        mockProduct.setPrice(BigDecimal.valueOf(199));
        mockProduct.setFloorPrice(BigDecimal.valueOf(150));

        ChatMessage msg = new ChatMessage();
        msg.setContent("你好，请问能便宜点吗？");
        mockHistory = List.of(msg);
    }

    @Test
    // 买家砍价建议 - 无AI模型（fallback）
    void getBargainSuggestion_buyerFallbackWhenModelEmpty() {
        // 手动构造服务，传入 Optional.empty()
        AiBargainService service = new AiBargainService(
                chatMessageService,
                conversationService,
                Optional.empty(),
                shopProductMapper
        );

        when(conversationService.getConversation(1)).thenReturn(mockConversation);
        when(shopProductMapper.selectById(101)).thenReturn(mockProduct);
        when(chatMessageService.getRecentMessages(1, 5)).thenReturn(mockHistory);

        AiBargainSuggestion result = service.getBargainSuggestion(1, 7, "buyer");

        assertNotNull(result);
        assertEquals("fallback", result.getSource());
        assertTrue(result.getContent().contains("测试耳机") || result.getContent().contains("感兴趣"));
    }

    @Test
    // 卖家议价建议 - 无AI模型（fallback）
    void getBargainSuggestion_sellerFallbackWhenModelEmpty() {
        AiBargainService service = new AiBargainService(
                chatMessageService,
                conversationService,
                Optional.empty(),
                shopProductMapper
        );

        when(conversationService.getConversation(1)).thenReturn(mockConversation);
        when(shopProductMapper.selectById(101)).thenReturn(mockProduct);
        when(chatMessageService.getRecentMessages(1, 5)).thenReturn(mockHistory);

        AiBargainSuggestion result = service.getBargainSuggestion(1, 7, "seller");

        assertNotNull(result);
        assertEquals("fallback", result.getSource());
        assertTrue(result.getContent().contains("成色") || result.getContent().contains("标价"));
    }

    @Test
    // 买家砍价建议 - AI返回空，回退到 fallback
    void getBargainSuggestion_aiReturnsEmpty_fallback() {
        // 传入包含 mock 模型的 Optional
        AiBargainService service = new AiBargainService(
                chatMessageService,
                conversationService,
                Optional.of(chatModel),
                shopProductMapper
        );

        when(conversationService.getConversation(1)).thenReturn(mockConversation);
        when(shopProductMapper.selectById(101)).thenReturn(mockProduct);
        when(chatMessageService.getRecentMessages(1, 5)).thenReturn(mockHistory);
        when(chatModel.generate(anyString())).thenReturn("");

        AiBargainSuggestion result = service.getBargainSuggestion(1, 7, "buyer");

        assertNotNull(result);
        assertEquals("fallback", result.getSource());
    }

    @Test
    // 买家砍价建议 - AI抛出异常，回退到 fallback
    void getBargainSuggestion_aiThrowsException_fallback() {
        AiBargainService service = new AiBargainService(
                chatMessageService,
                conversationService,
                Optional.of(chatModel),
                shopProductMapper
        );

        when(conversationService.getConversation(1)).thenReturn(mockConversation);
        when(shopProductMapper.selectById(101)).thenReturn(mockProduct);
        when(chatMessageService.getRecentMessages(1, 5)).thenReturn(mockHistory);
        when(chatModel.generate(anyString())).thenThrow(new RuntimeException("AI error"));

        AiBargainSuggestion result = service.getBargainSuggestion(1, 7, "buyer");

        assertNotNull(result);
        assertEquals("fallback", result.getSource());
    }

    @Test
    // AI自动回复 - 无AI模型（fallback）
    void getAutoReply_fallbackWhenModelEmpty() {
        AiBargainService service = new AiBargainService(
                chatMessageService,
                conversationService,
                Optional.empty(),
                shopProductMapper
        );

        when(conversationService.getConversation(1)).thenReturn(mockConversation);
        when(shopProductMapper.selectById(101)).thenReturn(mockProduct);
        when(chatMessageService.getRecentMessages(1, 5)).thenReturn(mockHistory);

        AiBargainSuggestion result = service.getAutoReply(1, 7);

        assertNotNull(result);
        assertEquals("fallback", result.getSource());
        assertTrue(result.getContent().contains("测试耳机") || result.getContent().contains("您好"));
    }

    @Test
    // AI自动回复 - AI返回空，回退到 fallback
    void getAutoReply_aiReturnsEmpty_fallback() {
        AiBargainService service = new AiBargainService(
                chatMessageService,
                conversationService,
                Optional.of(chatModel),
                shopProductMapper
        );

        when(conversationService.getConversation(1)).thenReturn(mockConversation);
        when(shopProductMapper.selectById(101)).thenReturn(mockProduct);
        when(chatMessageService.getRecentMessages(1, 5)).thenReturn(mockHistory);
        when(chatModel.generate(anyString())).thenReturn("");

        AiBargainSuggestion result = service.getAutoReply(1, 7);

        assertNotNull(result);
        assertEquals("fallback", result.getSource());
    }

    @Test
    // AI自动回复 - AI抛出异常，回退到 fallback
    void getAutoReply_aiThrowsException_fallback() {
        AiBargainService service = new AiBargainService(
                chatMessageService,
                conversationService,
                Optional.of(chatModel),
                shopProductMapper
        );

        when(conversationService.getConversation(1)).thenReturn(mockConversation);
        when(shopProductMapper.selectById(101)).thenReturn(mockProduct);
        when(chatMessageService.getRecentMessages(1, 5)).thenReturn(mockHistory);
        when(chatModel.generate(anyString())).thenThrow(new RuntimeException("AI error"));

        AiBargainSuggestion result = service.getAutoReply(1, 7);

        assertNotNull(result);
        assertEquals("fallback", result.getSource());
    }

    @Test
    // AI自动回复 - 对话中包含价格关键词，fallback 应包含价格
    void getAutoReply_fallbackWithPriceTalk() {
        AiBargainService service = new AiBargainService(
                chatMessageService,
                conversationService,
                Optional.empty(),
                shopProductMapper
        );

        when(conversationService.getConversation(1)).thenReturn(mockConversation);
        when(shopProductMapper.selectById(101)).thenReturn(mockProduct);
        // 对话中包含价格关键词
        ChatMessage priceMsg = new ChatMessage();
        priceMsg.setContent("能不能便宜点？");
        when(chatMessageService.getRecentMessages(1, 5)).thenReturn(List.of(priceMsg));

        AiBargainSuggestion result = service.getAutoReply(1, 7);

        assertNotNull(result);
        assertTrue(result.getContent().contains("标价") || result.getContent().contains("199"));
    }
}