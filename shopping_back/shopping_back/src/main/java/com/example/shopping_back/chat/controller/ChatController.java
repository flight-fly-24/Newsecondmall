package com.example.shopping_back.chat.controller;

import com.example.shopping_back.auth.AuthService;
import com.example.shopping_back.auth.dto.AuthUserView;
import com.example.shopping_back.chat.service.ChatMessageService;
import com.example.shopping_back.chat.service.ConversationService;
import com.example.shopping_back.chat.service.AiBargainService;
import com.example.shopping_back.chat.model.ChatMessage;
import com.example.shopping_back.chat.model.Conversation;
import com.example.shopping_back.common.dto.ApiResult;
import com.example.shopping_back.chat.dto.CreateConversationRequest;
import com.example.shopping_back.chat.dto.ChatMessageDto;
import com.example.shopping_back.chat.dto.UpdateConversationStatusRequest;
import com.example.shopping_back.chat.dto.AiBargainResponse;
import com.example.shopping_back.chat.dto.AiBargainSuggestion;
import com.example.shopping_back.chat.dto.ConversationListDto;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AuthService authService;
    private final ConversationService conversationService;
    private final ChatMessageService chatMessageService;
    private final AiBargainService aiBargainService;

    public ChatController(AuthService authService,
                          ConversationService conversationService,
                          ChatMessageService chatMessageService,
                          AiBargainService aiBargainService) {
        this.authService = authService;
        this.conversationService = conversationService;
        this.chatMessageService = chatMessageService;
        this.aiBargainService = aiBargainService;
    }

    @GetMapping("/conversations")
    public ApiResult<List<ConversationListDto>> listConversations(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthUserView currentUser = authService.me(bearerToken(authorization));
        List<ConversationListDto> conversations = conversationService.getConversationDisplayList(currentUser.getUserId());
        return ApiResult.ok(conversations);
    }

    @GetMapping("/conversations/{covId}/messages")
    public ApiResult<List<ChatMessage>> listMessages(
            @PathVariable("covId") Integer covId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthUserView currentUser = authService.me(bearerToken(authorization));
        Conversation conversation = conversationService.getConversation(covId);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        authorizeConversationAccess(currentUser, conversation);
        List<ChatMessage> messages = chatMessageService.getMessages(covId);
        return ApiResult.ok(messages);
    }

    @PostMapping("/conversations")
    public ApiResult<Conversation> createConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthUserView currentUser = authService.me(bearerToken(authorization));
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            request.setStatus("ai");
        }
        Conversation conversation = conversationService.createConversation(request, currentUser.getUserId());
        if (conversation == null || conversation.getCovId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create conversation");
        }
        return ApiResult.ok(conversation);
    }

    //非实时
    @PostMapping("/conversations/{covId}/messages")
    public ApiResult<ChatMessage> sendMessage(
            @PathVariable("covId") Integer covId,
            @Valid @RequestBody ChatMessageDto request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthUserView currentUser = authService.me(bearerToken(authorization));
        Conversation conversation = conversationService.getConversation(covId);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        authorizeConversationAccess(currentUser, conversation);
        request.setCovId(covId);
        request.setSenderId(currentUser.getUserId());
        ChatMessage chatMessage = chatMessageService.sendMessageAndBroadcast(request);
        conversationService.updateLastActiveTime(covId);
        // AI 自动回复：买家发送消息后自动以卖家身份回复
        triggerAutoReplyIfNeeded(covId, conversation, currentUser);
        return ApiResult.ok(chatMessage);
    }

    @PostMapping("/conversations/{covId}/transfer")
    public ApiResult<Conversation> transferToHuman(
            @PathVariable("covId") Integer covId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthUserView currentUser = authService.me(bearerToken(authorization));
        Conversation conversation = conversationService.getConversation(covId);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        authorizeConversationAccess(currentUser, conversation);
        UpdateConversationStatusRequest req = new UpdateConversationStatusRequest();
        req.setStatus("active");
        conversationService.updateStatus(req, covId);
        conversation.setStatus("active");
        return ApiResult.ok(conversation);
    }

    //AI议价
    @PostMapping("/conversations/{covId}/ai-bargain")
    public ApiResult<AiBargainResponse> triggerAiBargain(
        @PathVariable("covId") Integer covId,
        @RequestHeader(value = "Authorization", required = false) String authorization) {
    
        AuthUserView currentUser = authService.me(bearerToken(authorization));
        Conversation conversation = conversationService.getConversation(covId);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        authorizeConversationAccess(currentUser, conversation);
        AiBargainSuggestion suggestion = aiBargainService.getBargainSuggestion(
                covId, currentUser.getUserId(), currentUser.getRole());
        ChatMessageDto aiMessageDto = new ChatMessageDto();
        aiMessageDto.setCovId(covId);
        aiMessageDto.setContent(suggestion.getContent());
        aiMessageDto.setSenderId(currentUser.getUserId());
        aiMessageDto.setType("AI_REPLY");
        ChatMessage chatMessage = chatMessageService.sendMessageAndBroadcast(aiMessageDto);
        conversationService.updateLastActiveTime(covId);
        return ApiResult.ok(new AiBargainResponse(chatMessage, suggestion.getSource()));
    }

    @PostMapping("/{covId}/read")
    public ApiResult<Void> markAsRead(
        @PathVariable Integer covId, 
        @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthUserView currentUser = authService.me(bearerToken(authorization));
        conversationService.markAllAsRead(covId, currentUser.getUserId());
        return ApiResult.ok(null);
    }

    @PutMapping("/conversations/{covId}/status")
    public ApiResult<Conversation> updateConversationStatus(
            @PathVariable("covId") Integer covId,
            @Valid @RequestBody UpdateConversationStatusRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthUserView currentUser = authService.me(bearerToken(authorization));
        Conversation conversation = conversationService.getConversation(covId);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        authorizeConversationAccess(currentUser, conversation);
        conversationService.updateStatus(request, covId);
        conversation.setStatus(request.getStatus());
        return ApiResult.ok(conversation);
    }

    @DeleteMapping("/conversations/{covId}")
    public ApiResult<Void> deleteConversation(
            @PathVariable("covId") Integer covId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthUserView currentUser = authService.me(bearerToken(authorization));
        Conversation conversation = conversationService.getConversation(covId);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        authorizeConversationAccess(currentUser, conversation);
        conversationService.deleteConversation(covId);
        return ApiResult.ok(null);
    }

    private static void authorizeConversationAccess(AuthUserView currentUser, Conversation conversation) {
        Integer userId = currentUser.getUserId();
        if (!userId.equals(conversation.getBuyerId()) && !userId.equals(conversation.getSellerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden to access this conversation");
        }
    }

    private void triggerAutoReplyIfNeeded(Integer covId, Conversation conversation, AuthUserView currentUser) {
        if (!"ai".equals(conversation.getStatus()) && !"pending".equals(conversation.getStatus())) {
            return;
        }
        if (!currentUser.getUserId().equals(conversation.getBuyerId())) {
            return;
        }
        AiBargainSuggestion suggestion = aiBargainService.getAutoReply(covId, currentUser.getUserId());
        ChatMessageDto aiMsg = new ChatMessageDto();
        aiMsg.setCovId(covId);
        aiMsg.setContent(suggestion.getContent());
        aiMsg.setSenderId(conversation.getSellerId());
        aiMsg.setType("AI_REPLY");
        chatMessageService.sendMessageAndBroadcast(aiMsg);
        if ("pending".equals(conversation.getStatus())) {
            UpdateConversationStatusRequest req = new UpdateConversationStatusRequest();
            req.setStatus("ai");
            conversationService.updateStatus(req, covId);
            conversation.setStatus("ai");
        }
    }

    private static String bearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String trimmed = authorization.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

}
