package com.example.shopping_back.chat.service;

import com.example.shopping_back.auth.dto.AuthUserView;
import com.example.shopping_back.chat.mapper.ConversationMapper;
import com.example.shopping_back.shop.mapper.ShopProductMapper;
import com.example.shopping_back.chat.mapper.ChatMessageMapper;
import com.example.shopping_back.chat.model.Conversation;
import com.example.shopping_back.chat.dto.ConversationListDto;
import com.example.shopping_back.auth.AuthService;
import com.example.shopping_back.shop.model.ProductRecord;
import com.example.shopping_back.chat.model.ChatMessage;


import com.example.shopping_back.chat.dto.CreateConversationRequest;
import com.example.shopping_back.chat.dto.UpdateConversationStatusRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ConversationService {
    private final ConversationMapper conversationMapper;
    private final ShopProductMapper shopProductMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AuthService authService;
    private final ChatMessageService chatMessageService;

    public ConversationService(ConversationMapper conversationMapper, ShopProductMapper shopProductMapper, 
        AuthService authService, ChatMessageService chatMessageService, ChatMessageMapper chatMessageMapper) {
        this.conversationMapper = conversationMapper;
        this.shopProductMapper = shopProductMapper;
        this.authService = authService;
        this.chatMessageService = chatMessageService;
        this.chatMessageMapper = chatMessageMapper;
    }

    public List<Conversation> getUserConversations(Integer userId) {
        return conversationMapper.selectByUserId(userId);
    }

    public List<ConversationListDto> getConversationDisplayList(Integer userId) {
        List<Conversation> conversations = conversationMapper.selectByUserId(userId);
        return conversations.stream().map(c -> {
            ConversationListDto dto = new ConversationListDto();
            dto.setCovId(c.getCovId());
            dto.setGoodsId(c.getGoodsId());
            dto.setStatus(c.getStatus());
            Integer targetId = Objects.equals(c.getBuyerId(), userId) ? c.getSellerId() : c.getBuyerId();
            AuthUserView targetUser = authService.getUserById(targetId);
            dto.setTargetName(targetUser.getUsername());
            ProductRecord goods = shopProductMapper.selectById(c.getGoodsId());
            if (goods != null) {
                dto.setGoodsImageUrl(goods.getImage());
                dto.setGoodsName(goods.getGoodsName());
            }
            ChatMessage lastMsg = chatMessageService.getLastMessageByCovId(c.getCovId());
            if(lastMsg != null) {
                dto.setLastMessage(lastMsg.getContent());
            }
            dto.setLastTime(c.getUpdateTime());
            int unread = chatMessageService.countUnread(c.getCovId(), userId);
            dto.setUnreadCount(unread);
            return dto;
        }).collect(Collectors.toList());
    }

    public Conversation createConversation(CreateConversationRequest request, Integer buyerId) {
        Conversation existing = conversationMapper.findByBuyerAndGoods(buyerId, request.getGoodsId());
        if (existing != null) {
            return existing;
        }
        ProductRecord product = shopProductMapper.selectById(request.getGoodsId());
        if (product == null && (request.getSellerId() == null || request.getSellerId() <= 0)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        // 已迁移商品位于 catalog-service，旧聊天库中没有商品行；前端携带目录服务返回的发布者 ID。
        Integer sellerId = product == null ? request.getSellerId() : product.getSellerId();
        if (sellerId.equals(buyerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能与自己创建商品会话");
        }
        Conversation conv = new Conversation();
        conv.setBuyerId(buyerId);
        conv.setSellerId(sellerId);
        conv.setGoodsId(request.getGoodsId());
        conv.setStatus(request.getStatus() == null ? "pending" : request.getStatus());
        conv.setCreateTime(new Date());
        conversationMapper.insert(conv);
        return conv;
    }

    public Conversation getConversation(Integer covId) {
        return conversationMapper.selectById(covId);
    }

    public void updateStatus(UpdateConversationStatusRequest request, Integer covId) {
        conversationMapper.updateStatus(covId, request.getStatus());
    }

    public void validateAccess(AuthUserView user, Conversation conv) {
        if (!user.getUserId().equals(conv.getBuyerId()) && !user.getUserId().equals(conv.getSellerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问此会话");
        }
    }

    public void markAllAsRead(Integer covId, Integer userId) {
        chatMessageMapper.markAllAsRead(covId, userId);
    }

    public void updateLastActiveTime(Integer covId) {
        conversationMapper.updateLastActiveTime(covId);
    }

    @Transactional
    public void deleteConversation(Integer covId) {
        Conversation conversation = conversationMapper.selectById(covId);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        chatMessageMapper.deleteByCovId(covId);
        conversationMapper.deleteById(covId);
    }

    public Integer getOtherParticipantId(Integer covId, Integer userId) {
        Conversation conv = conversationMapper.selectById(covId);
        if (userId.equals(conv.getBuyerId())) {
            return conv.getSellerId();
        } else if (userId.equals(conv.getSellerId())) {
            return conv.getBuyerId();
        }
        return null;
    }

}
