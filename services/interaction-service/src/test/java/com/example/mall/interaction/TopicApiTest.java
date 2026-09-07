package com.example.mall.interaction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:interaction_api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.auth.base-url=http://127.0.0.1:9",
        "app.catalog.base-url=http://127.0.0.1:9"
})
class TopicApiTest {
    @Autowired TestRestTemplate http;
    @Autowired TopicService service;

    @Test
    void topicAndPostApisAreReachable() {
        TopicService.CurrentUser buyer = new TopicService.CurrentUser(8, "bob", "buyer", "");
        TopicService.TopicView topic = service.createTopic(
                new TopicService.TopicCreateRequest("二手显示器", "校内转卖", "买家话题", "", java.util.List.of("数码")),
                buyer);
        service.createTopicPost(topic.id(), new TopicService.TopicPostRequest("还在保修期内", java.util.List.of(), "", ""), buyer);

        ResponseEntity<String> topics = http.getForEntity("/api/topics", String.class);
        assertThat(topics.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(topics.getBody()).contains("\"code\":0");
        assertThat(topics.getBody()).contains("二手显示器");

        ResponseEntity<String> posts = http.getForEntity("/api/topics/" + topic.id() + "/posts", String.class);
        assertThat(posts.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(posts.getBody()).contains("\"code\":0");
        assertThat(posts.getBody()).contains("还在保修期内");

        ResponseEntity<String> health = http.getForEntity("/api/interaction/health", String.class);
        assertThat(health.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(health.getBody()).contains("interaction-service");
    }
}
