package com.example.mall.interaction;

import com.example.mall.common.AuthClient;
import com.example.mall.common.AuthClient.AuthUser;
import com.example.mall.common.dto.ApiResult;
import com.example.mall.interaction.TopicService.CurrentUser;
import com.example.mall.interaction.TopicService.TopicCommentRequest;
import com.example.mall.interaction.TopicService.TopicCreateRequest;
import com.example.mall.interaction.TopicService.TopicPostRequest;
import com.example.mall.interaction.TopicService.TopicPostView;
import com.example.mall.interaction.TopicService.TopicView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TopicController {
    private final TopicService topicService;
    private final AuthClient authClient;

    public TopicController(TopicService topicService, AuthClient authClient) {
        this.topicService = topicService;
        this.authClient = authClient;
    }

    @GetMapping("/api/topics")
    public ApiResult<List<TopicView>> topics(
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(topicService.list(tag, keyword));
    }

    @GetMapping("/api/topics/{id}")
    public ApiResult<TopicView> topic(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(topicService.topic(id, currentUser(authorization)));
    }

    @PostMapping("/api/topics")
    public ApiResult<TopicView> createTopic(
            @Valid @RequestBody CreateTopicBody request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(topicService.createTopic(
                new TopicCreateRequest(request.title(), request.desc(), request.type(), request.cover(), request.tags()),
                currentUser(authorization)));
    }

    @PostMapping("/api/topics/{id}/follow")
    public ApiResult<TopicView> follow(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(topicService.followTopic(id, currentUser(authorization)));
    }

    @DeleteMapping("/api/topics/{id}/follow")
    public ApiResult<TopicView> unfollow(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(topicService.unfollowTopic(id, currentUser(authorization)));
    }

    @GetMapping("/api/topics/{id}/posts")
    public ApiResult<List<TopicPostView>> posts(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(topicService.topicPosts(id, currentUser(authorization)));
    }

    @PostMapping("/api/topics/{id}/posts")
    public ApiResult<List<TopicPostView>> createPost(
            @PathVariable String id,
            @Valid @RequestBody CreatePostBody request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(topicService.createTopicPost(
                id,
                new TopicPostRequest(request.content(), request.images(), request.productId(), request.storeId()),
                currentUser(authorization)));
    }

    @PostMapping("/api/topic-posts/{id}/comments")
    public ApiResult<List<TopicPostView>> comment(
            @PathVariable String id,
            @Valid @RequestBody CommentBody request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(topicService.createTopicComment(
                id, new TopicCommentRequest(request.content()), currentUser(authorization)));
    }

    @PostMapping("/api/topic-posts/{id}/like")
    public ApiResult<List<TopicPostView>> like(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(topicService.toggleLike(id, currentUser(authorization)));
    }

    @PostMapping("/api/topic-posts/{id}/action")
    public ApiResult<List<TopicPostView>> action(
            @PathVariable String id,
            @Valid @RequestBody ActionBody request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResult.ok(topicService.toggleAction(id, request.actionType(), currentUser(authorization)));
    }

    @GetMapping("/api/interaction/health")
    public ApiResult<String> health() {
        return ApiResult.ok("interaction-service");
    }

    private CurrentUser currentUser(String authorization) {
        AuthUser user = authClient.optionalUser(authorization);
        if (user == null) {
            return null;
        }
        return new CurrentUser((int) user.userId(), user.username(), user.role(), "");
    }

    public record CreateTopicBody(
            @NotBlank(message = "话题标题不能为空") String title,
            @NotBlank(message = "话题简介不能为空") String desc,
            String type,
            String cover,
            List<String> tags) {}

    public record CreatePostBody(
            @NotBlank(message = "帖子内容不能为空") String content,
            List<String> images,
            String productId,
            String storeId) {}

    public record CommentBody(@NotBlank(message = "评论内容不能为空") String content) {}

    public record ActionBody(@NotBlank(message = "操作类型不能为空") String actionType) {}
}
