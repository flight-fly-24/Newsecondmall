package com.example.mall.interaction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:interaction;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.auth.base-url=http://127.0.0.1:9",
        "app.catalog.base-url=http://127.0.0.1:9"
})
class TopicServiceTest {
    @Autowired TopicService service;

    @Test
    void createsAndListsTopicsInInteractionDatabase() {
        TopicService.TopicView created = service.create(7, "校园二手数码交流");
        assertThat(service.find(created.topicId()).title()).isEqualTo("校园二手数码交流");
        assertThat(service.list(null, null)).isNotEmpty();
    }

    @Test
    void buyerCanPostCommentAndLike() {
        TopicService.CurrentUser buyer = new TopicService.CurrentUser(100, "alice", "buyer", "");
        TopicService.TopicView topic = service.createTopic(
                new TopicService.TopicCreateRequest("宿舍桌搭", "分享布置", "买家话题", "", java.util.List.of("宿舍")),
                buyer);
        service.createTopicPost(topic.id(), new TopicService.TopicPostRequest("这件很好用", java.util.List.of(), "", ""), buyer);
        var posts = service.topicPosts(topic.id(), buyer);
        assertThat(posts).hasSize(1);
        String postId = posts.get(0).id();
        service.createTopicComment(postId, new TopicService.TopicCommentRequest("同意"), buyer);
        service.toggleLike(postId, buyer);
        var after = service.topicPosts(topic.id(), buyer);
        assertThat(after.get(0).liked()).isTrue();
        assertThat(after.get(0).comments()).hasSize(1);
        assertThat(after.get(0).author()).isEqualTo("alice");
    }

    @Test
    void followAndUnfollowAreIdempotent() {
        TopicService.CurrentUser buyer = new TopicService.CurrentUser(101, "bob", "buyer", "");
        var topic = service.createTopic(new TopicService.TopicCreateRequest(
                "关注话题", "验证关注", "经验", "", java.util.List.of("测试")), buyer);
        assertThat(service.followTopic(topic.id(), buyer).followed()).isTrue();
        assertThat(service.followTopic(topic.id(), buyer).followed()).isTrue();
        assertThat(service.unfollowTopic(topic.id(), buyer).followed()).isFalse();
    }

    @Test
    void creationRequiresBuyerAndCompleteContent() {
        TopicService.CurrentUser seller = new TopicService.CurrentUser(102, "seller", "seller", "");
        assertThatThrownBy(() -> service.createTopic(
                new TopicService.TopicCreateRequest("标题", "简介", "类型", "", java.util.List.of()), null))
                .hasMessageContaining("401 UNAUTHORIZED");
        assertThatThrownBy(() -> service.createTopic(
                new TopicService.TopicCreateRequest("标题", "简介", "类型", "", java.util.List.of()), seller))
                .hasMessageContaining("400 BAD_REQUEST");
        var buyer = new TopicService.CurrentUser(103, "buyer", "buyer", "");
        assertThatThrownBy(() -> service.createTopic(
                new TopicService.TopicCreateRequest(" ", "简介", "类型", "", java.util.List.of()), buyer))
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void postOperationsRejectMissingEntitiesAndBadActions() {
        var buyer = new TopicService.CurrentUser(104, "buyer", "buyer", "");
        assertThatThrownBy(() -> service.topic("bad-id", buyer)).hasMessageContaining("404 NOT_FOUND");
        assertThatThrownBy(() -> service.createTopicPost("999999",
                new TopicService.TopicPostRequest("内容", java.util.List.of(), "", ""), buyer))
                .hasMessageContaining("404 NOT_FOUND");
        assertThatThrownBy(() -> service.createTopicComment("999999",
                new TopicService.TopicCommentRequest("评论"), buyer)).hasMessageContaining("404 NOT_FOUND");
        assertThatThrownBy(() -> service.toggleAction("999999", "collect", buyer))
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void commentAndPostContentCannotBeBlankAndActionsToggle() {
        var buyer = new TopicService.CurrentUser(105, "carol", "buyer", "avatar.png");
        var topic = service.createTopic(new TopicService.TopicCreateRequest(
                "操作话题", "验证操作", "经验", "", java.util.List.of("a", "a", "b")), buyer);
        assertThatThrownBy(() -> service.createTopicPost(topic.id(),
                new TopicService.TopicPostRequest(" ", java.util.List.of(), "", ""), buyer))
                .hasMessageContaining("400 BAD_REQUEST");
        service.createTopicPost(topic.id(), new TopicService.TopicPostRequest(
                "帖子", java.util.List.of("a.png", "b.png"), "", ""), buyer);
        String postId = service.topicPosts(topic.id(), buyer).get(0).id();
        assertThatThrownBy(() -> service.createTopicComment(postId,
                new TopicService.TopicCommentRequest(" "), buyer)).hasMessageContaining("400 BAD_REQUEST");
        assertThat(service.toggleAction(postId, "collect", buyer).get(0).collected()).isTrue();
        assertThat(service.toggleAction(postId, "collect", buyer).get(0).collected()).isFalse();
        assertThatThrownBy(() -> service.toggleAction(postId, "invalid", buyer))
                .hasMessageContaining("400 BAD_REQUEST");
        assertThat(service.toggleLike(postId, buyer).get(0).liked()).isTrue();
        assertThat(service.toggleLike(postId, buyer).get(0).liked()).isFalse();
    }
}
