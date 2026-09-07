package com.example.shopping_back.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.chat.ChatLanguageModel;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiAssistantServiceTest {

    @Test
    void askReturnsFallbackWhenQuestionIsBlank() {
        AiAssistantService service = new AiAssistantService(Optional.empty());

        String answer = service.ask("   ");

        assertEquals("请告诉我你想了解什么～", answer);
    }

    @Test
    void askUsesModelResponseWhenAvailable() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate("你是「松果集市」二手交易平台的官方AI助手，回答用户关于平台使用的问题。\n你的知识范围：\n- 买家如何浏览商品、发起咨询、下单购买、支付、收货确认\n- 卖家如何发布商品、管理订单、发货、收款\n- 平台担保交易流程：买家付款→平台托管→卖家发货→买家确认→平台打款\n- 议价功能：买家可以跟卖家协商价格，AI议价助手可以辅助砍价\n- 平台保障：实名认证、交易担保、售后纠纷处理\n- 商品分类：新品/二手，发布时需填写成色、价格、实拍图\n- 聊天功能：买家和卖家可以通过平台聊天沟通，AI自动回复辅助\n- 转人工客服：如果AI无法解决问题，可以转接人工客服\n\n回答要求：\n- 简洁明了，用自然的中文回答，200字以内\n- 根据用户身份（买家/卖家）给出针对性建议\n- 如果问题超出平台范围，礼貌说明你不知道并建议联系人工客服\n- 语气友好热情，像平台工作人员在耐心解答\n- 不要使用markdown格式，不要用**加粗**，不要用列表符号，用纯文本和换行分段即可\n\n用户问题：怎么买最省心\n请给出回答：\n")).thenReturn("这里是 AI 回答");

        AiAssistantService service = new AiAssistantService(Optional.of(model));

        String answer = service.ask("怎么买最省心");

        assertEquals("这里是 AI 回答", answer);
    }

    @Test
    void askReturnsBuyerGuideFallbackWhenQuestionMentionsBuying() {
        AiAssistantService service = new AiAssistantService(Optional.empty());

        String answer = service.ask("怎么下单");

        assertTrue(answer.contains("下单"));
    }

        // ==================== 补充 AiAssistantService 覆盖率测试 ====================

    @Test
    // AI 模型返回空字符串时，回退到 fallback
    void ask_fallbackWhenAiReturnsEmpty() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyString())).thenReturn("");
        AiAssistantService service = new AiAssistantService(Optional.of(model));

        String answer = service.ask("怎么买最省心");
        assertNotNull(answer);
        assertTrue(answer.contains("松果集市") || answer.contains("购物"));
    }

    @Test
    // AI 模型抛出异常时，回退到 fallback
    void ask_fallbackWhenAiThrowsException() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyString())).thenThrow(new RuntimeException("AI error"));
        AiAssistantService service = new AiAssistantService(Optional.of(model));

        String answer = service.ask("怎么卖东西");
        assertNotNull(answer);
        assertTrue(answer.contains("卖家") || answer.contains("发布"));
    }

    @Test
    // 问题包含"运费/发货"关键词 -> 回退到物流指南
    void ask_fallbackForShippingQuestion() {
        AiAssistantService service = new AiAssistantService(Optional.empty());
        String answer = service.ask("运费怎么算");
        assertTrue(answer.contains("运费") || answer.contains("发货") || answer.contains("物流"));
    }

    @Test
    // 问题包含"AI/议价/客服"关键词 -> 回退到 AI/客服指南
    void ask_fallbackForAiQuestion() {
        AiAssistantService service = new AiAssistantService(Optional.empty());
        String answer = service.ask("AI议价怎么用");
        assertTrue(answer.contains("AI") || answer.contains("议价") || answer.contains("客服"));
    }

    @Test
    // 问题包含"担保/安全/纠纷"关键词 -> 回退到担保指南
    void ask_fallbackForSecurityQuestion() {
        AiAssistantService service = new AiAssistantService(Optional.empty());
        String answer = service.ask("平台安全吗");
        assertTrue(answer.contains("担保") || answer.contains("保障") || answer.contains("安全"));
    }
}
