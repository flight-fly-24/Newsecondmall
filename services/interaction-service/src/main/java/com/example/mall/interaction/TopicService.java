package com.example.mall.interaction;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TopicService {
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final JdbcClient jdbc;
    private final CatalogClient catalogClient;

    public TopicService(JdbcClient jdbc, CatalogClient catalogClient) {
        this.jdbc = jdbc;
        this.catalogClient = catalogClient;
    }

    public List<TopicView> list(String tag, String keyword) {
        try {
            return jdbc.sql("""
                    SELECT t.topic_id AS topicId, t.type, t.title, t.topic_desc AS topicDesc, t.author, t.cover, t.tags,
                           COUNT(DISTINCT p.post_id) AS postCount,
                           COUNT(DISTINCT l.id) AS likeCount,
                           COUNT(DISTINCT ft.id) AS followCount,
                           0 AS followed
                    FROM community_topic t
                    LEFT JOIN topic_post p ON p.topic_id = t.topic_id
                    LEFT JOIN topic_post_like l ON l.post_id = p.post_id
                    LEFT JOIN follow_topic ft ON ft.topic_id = t.topic_id
                    WHERE COALESCE(t.status, 'normal') = 'normal'
                      AND (:tag IS NULL OR :tag = '' OR REPLACE(COALESCE(t.tags, ''), '，', ',') LIKE CONCAT('%', :tag, '%'))
                      AND (:keyword IS NULL OR :keyword = ''
                           OR t.title LIKE CONCAT('%', :keyword, '%')
                           OR COALESCE(t.topic_desc, '') LIKE CONCAT('%', :keyword, '%')
                           OR COALESCE(t.type, '') LIKE CONCAT('%', :keyword, '%')
                           OR COALESCE(t.tags, '') LIKE CONCAT('%', :keyword, '%')
                           OR COALESCE(t.author, '') LIKE CONCAT('%', :keyword, '%'))
                    GROUP BY t.topic_id, t.type, t.title, t.topic_desc, t.author, t.cover, t.tags
                    ORDER BY t.topic_id ASC
                    """).param("tag", tag).param("keyword", keyword).query(TopicRow.class).list()
                    .stream().map(row -> toTopicView(row, 0)).toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    /** 骨架测试兼容：只写标题。 */
    public TopicView create(long creatorId, String title) {
        CurrentUser user = new CurrentUser((int) creatorId, "松果用户", "buyer", "");
        return createTopic(new TopicCreateRequest(title, "一起讨论这个话题。", "买家话题", "", List.of()), user);
    }

    public TopicView find(long id) {
        return topic(String.valueOf(id), null);
    }

    public TopicView topic(String id, CurrentUser user) {
        TopicRow row = selectTopic(parseDbId(id), user == null ? null : user.userId());
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "话题不存在");
        }
        return toTopicView(row, row.followed() == null ? 0 : row.followed());
    }

    public TopicView followTopic(String id, CurrentUser user) {
        requireLogin(user, "请先登录后关注话题");
        Integer topicId = parseDbId(id);
        TopicRow row = selectTopic(topicId, user.userId());
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "话题不存在");
        }
        jdbc.sql("INSERT IGNORE INTO follow_topic(user_id, topic_id, topic_title) VALUES(:userId, :topicId, :title)")
                .param("userId", user.userId()).param("topicId", topicId).param("title", row.title()).update();
        return topic(String.valueOf(topicId), user);
    }

    public TopicView unfollowTopic(String id, CurrentUser user) {
        requireLogin(user, "请先登录后取消关注");
        Integer topicId = parseDbId(id);
        if (selectTopic(topicId, user.userId()) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "话题不存在");
        }
        jdbc.sql("DELETE FROM follow_topic WHERE topic_id=:topicId AND user_id=:userId")
                .param("topicId", topicId).param("userId", user.userId()).update();
        return topic(String.valueOf(topicId), user);
    }

    public TopicView createTopic(TopicCreateRequest request, CurrentUser user) {
        requireLogin(user, "请先登录后创建话题");
        if (!"buyer".equals(user.role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前仅买家可以创建话题");
        }
        String title = defaultText(request == null ? null : request.title(), "").trim();
        String desc = defaultText(request == null ? null : request.desc(), "").trim();
        if (title.isBlank() || desc.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "话题标题和简介不能为空");
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO community_topic(type, title, topic_desc, author, cover, tags, status, creator_id)
                VALUES(:type, :title, :desc, :author, :cover, :tags, 'normal', :creatorId)
                """).param("type", defaultText(request.type(), "买家话题"))
                .param("title", title).param("desc", desc)
                .param("author", defaultText(user.username(), "买家"))
                .param("cover", defaultText(request.cover(), "/static/goods/viewtop-monitor.jpg"))
                .param("tags", joinTopicTags(request.tags()))
                .param("creatorId", user.userId())
                .update(keys, "topic_id");
        Number topicId = keys.getKey();
        return topic(String.valueOf(topicId == null ? 0 : topicId.intValue()), user);
    }

    public List<TopicPostView> topicPosts(String topicId, CurrentUser user) {
        Integer id = parseDbId(topicId);
        Integer userId = user == null ? null : user.userId();
        return jdbc.sql("""
                SELECT p.post_id AS postId, p.topic_id AS topicId, p.user_id AS userId, p.product_id AS productId,
                       p.store_id AS storeId, COALESCE(NULLIF(p.author_name, ''), '松果用户') AS username,
                       COALESCE(p.author_avatar, '') AS authorAvatar, p.content, p.images, p.created_at AS createdAt,
                       COUNT(DISTINCT l.id) AS likeCount, COUNT(DISTINCT c.comment_id) AS commentCount,
                       COUNT(DISTINCT CASE WHEN l.user_id = :userId THEN l.id END) AS liked,
                       COUNT(DISTINCT CASE WHEN a.action_type = 'want' THEN a.id END) AS wantCount,
                       COUNT(DISTINCT CASE WHEN a.action_type = 'collect' THEN a.id END) AS collectCount,
                       COUNT(DISTINCT CASE WHEN a.user_id = :userId AND a.action_type = 'want' THEN a.id END) AS wanted,
                       COUNT(DISTINCT CASE WHEN a.user_id = :userId AND a.action_type = 'collect' THEN a.id END) AS collected
                FROM topic_post p
                LEFT JOIN topic_post_like l ON l.post_id = p.post_id
                LEFT JOIN topic_comment c ON c.post_id = p.post_id
                LEFT JOIN topic_post_action a ON a.post_id = p.post_id
                WHERE p.topic_id = :topicId
                GROUP BY p.post_id, p.topic_id, p.user_id, p.product_id, p.store_id, p.author_name, p.author_avatar, p.content, p.images, p.created_at
                ORDER BY p.created_at DESC, p.post_id DESC
                """).param("topicId", id).param("userId", userId).query(PostRow.class).list()
                .stream().map(this::toTopicPostView).toList();
    }

    public List<TopicPostView> createTopicPost(String topicId, TopicPostRequest request, CurrentUser user) {
        requireLogin(user, "请先登录后发帖");
        Integer id = parseDbId(topicId);
        if (selectTopic(id, user.userId()) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "话题不存在");
        }
        String content = defaultText(request == null ? null : request.content(), "").trim();
        if (content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "帖子内容不能为空");
        }
        String images = request == null || request.images() == null ? "" : String.join(",", request.images());
        Integer productId = request == null || isBlank(request.productId()) ? null : parseDbId(request.productId());
        Integer storeId = request == null || isBlank(request.storeId()) ? null : parseDbId(request.storeId());
        jdbc.sql("""
                INSERT INTO topic_post(topic_id, user_id, product_id, store_id, content, images, author_name, author_avatar)
                VALUES(:topicId, :userId, :productId, :storeId, :content, :images, :authorName, :authorAvatar)
                """).param("topicId", id).param("userId", user.userId())
                .param("productId", productId).param("storeId", storeId)
                .param("content", content).param("images", images)
                .param("authorName", defaultText(user.username(), "松果用户"))
                .param("authorAvatar", defaultText(user.avatarUrl(), "")).update();
        return topicPosts(topicId, user);
    }

    public List<TopicPostView> createTopicComment(String postId, TopicCommentRequest request, CurrentUser user) {
        requireLogin(user, "请先登录后评论");
        Integer id = parseDbId(postId);
        Integer topicId = topicIdByPost(id);
        if (topicId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在");
        }
        String content = defaultText(request == null ? null : request.content(), "").trim();
        if (content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评论内容不能为空");
        }
        jdbc.sql("INSERT INTO topic_comment(post_id, user_id, content, author_name) VALUES(:postId, :userId, :content, :authorName)")
                .param("postId", id).param("userId", user.userId())
                .param("content", content).param("authorName", defaultText(user.username(), "松果用户")).update();
        return topicPosts(String.valueOf(topicId), user);
    }

    public List<TopicPostView> toggleLike(String postId, CurrentUser user) {
        requireLogin(user, "请先登录后点赞");
        Integer id = parseDbId(postId);
        Integer topicId = topicIdByPost(id);
        if (topicId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在");
        }
        Integer exists = jdbc.sql("SELECT COUNT(*) FROM topic_post_like WHERE post_id=:postId AND user_id=:userId")
                .param("postId", id).param("userId", user.userId()).query(Integer.class).single();
        if (exists != null && exists > 0) {
            jdbc.sql("DELETE FROM topic_post_like WHERE post_id=:postId AND user_id=:userId")
                    .param("postId", id).param("userId", user.userId()).update();
        } else {
            jdbc.sql("INSERT IGNORE INTO topic_post_like(post_id, user_id) VALUES(:postId, :userId)")
                    .param("postId", id).param("userId", user.userId()).update();
        }
        return topicPosts(String.valueOf(topicId), user);
    }

    public List<TopicPostView> toggleAction(String postId, String actionType, CurrentUser user) {
        requireLogin(user, "请先登录后操作");
        Integer id = parseDbId(postId);
        Integer topicId = topicIdByPost(id);
        if (topicId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在");
        }
        String action = normalizeTopicAction(actionType);
        Integer exists = jdbc.sql("SELECT COUNT(*) FROM topic_post_action WHERE post_id=:postId AND user_id=:userId AND action_type=:action")
                .param("postId", id).param("userId", user.userId()).param("action", action).query(Integer.class).single();
        if (exists != null && exists > 0) {
            jdbc.sql("DELETE FROM topic_post_action WHERE post_id=:postId AND user_id=:userId AND action_type=:action")
                    .param("postId", id).param("userId", user.userId()).param("action", action).update();
        } else {
            jdbc.sql("INSERT IGNORE INTO topic_post_action(post_id, user_id, action_type) VALUES(:postId, :userId, :action)")
                    .param("postId", id).param("userId", user.userId()).param("action", action).update();
        }
        return topicPosts(String.valueOf(topicId), user);
    }

    private TopicRow selectTopic(Integer topicId, Integer currentUserId) {
        List<TopicRow> rows = jdbc.sql("""
                SELECT t.topic_id AS topicId, t.type, t.title, t.topic_desc AS topicDesc, t.author, t.cover, t.tags,
                       COUNT(DISTINCT p.post_id) AS postCount,
                       COUNT(DISTINCT l.id) AS likeCount,
                       COUNT(DISTINCT ft.id) AS followCount,
                       COUNT(DISTINCT CASE WHEN ft.user_id = :userId THEN ft.id END) AS followed
                FROM community_topic t
                LEFT JOIN topic_post p ON p.topic_id = t.topic_id
                LEFT JOIN topic_post_like l ON l.post_id = p.post_id
                LEFT JOIN follow_topic ft ON ft.topic_id = t.topic_id
                WHERE t.topic_id = :topicId
                GROUP BY t.topic_id, t.type, t.title, t.topic_desc, t.author, t.cover, t.tags
                """).param("topicId", topicId).param("userId", currentUserId).query(TopicRow.class).list();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Integer topicIdByPost(Integer postId) {
        return jdbc.sql("SELECT topic_id FROM topic_post WHERE post_id=:postId")
                .param("postId", postId).query(Integer.class).optional().orElse(null);
    }

    private TopicView toTopicView(TopicRow row, int followedOverride) {
        int postCount = row.postCount() == null ? 0 : row.postCount();
        int likeCount = row.likeCount() == null ? 0 : row.likeCount();
        int followCount = row.followCount() == null ? 0 : row.followCount();
        boolean followed = followedOverride > 0 || (row.followed() != null && row.followed() > 0);
        String heat = postCount + " 帖 · " + followCount + " 关注";
        return new TopicView(
                String.valueOf(row.topicId()),
                defaultText(row.type(), "话题"),
                defaultText(row.title(), "未命名话题"),
                defaultText(row.topicDesc(), "一起讨论这个话题。"),
                heat,
                defaultText(row.author(), "松果社区"),
                defaultText(row.cover(), "/static/goods/viewtop-monitor.jpg"),
                parseTags(row.tags()),
                postCount,
                likeCount,
                followCount,
                followed
        );
    }

    private TopicPostView toTopicPostView(PostRow row) {
        return new TopicPostView(
                String.valueOf(row.postId()),
                String.valueOf(row.topicId()),
                defaultText(row.username(), "松果用户"),
                defaultText(row.authorAvatar(), ""),
                defaultText(row.content(), ""),
                parseImages(row.images()),
                catalogClient.product(row.productId()),
                catalogClient.store(row.storeId()),
                formatTime(row.createdAt()),
                nz(row.likeCount()),
                nz(row.wantCount()),
                nz(row.collectCount()),
                nz(row.commentCount()),
                row.liked() != null && row.liked() > 0,
                row.wanted() != null && row.wanted() > 0,
                row.collected() != null && row.collected() > 0,
                comments(row.postId())
        );
    }

    private List<TopicCommentView> comments(Integer postId) {
        return jdbc.sql("""
                SELECT comment_id AS commentId, COALESCE(NULLIF(author_name, ''), '松果用户') AS username,
                       content, created_at AS createdAt
                FROM topic_comment WHERE post_id=:postId ORDER BY created_at ASC, comment_id ASC
                """).param("postId", postId).query(CommentRow.class).list().stream()
                .map(row -> new TopicCommentView(String.valueOf(row.commentId()),
                        defaultText(row.username(), "松果用户"),
                        defaultText(row.content(), ""),
                        formatTime(row.createdAt())))
                .toList();
    }

    private List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.replace("，", ",").split(",")).stream().map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    private String joinTopicTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "买家话题";
        }
        return tags.stream().map(item -> defaultText(item, "").trim()).filter(item -> !item.isEmpty())
                .distinct().limit(6).reduce((left, right) -> left + "," + right).orElse("买家话题");
    }

    private List<String> parseImages(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.split(",")).stream().map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    private String normalizeTopicAction(String actionType) {
        String value = defaultText(actionType, "").toLowerCase(Locale.ROOT);
        if ("want".equals(value) || "collect".equals(value)) {
            return value;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的操作");
    }

    private Integer parseDbId(String id) {
        try {
            return Integer.parseInt(id);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "话题不存在");
        }
    }

    private String formatTime(Object time) {
        if (time == null) {
            return "";
        }
        if (time instanceof Timestamp timestamp) {
            return DISPLAY_TIME.format(timestamp.toLocalDateTime());
        }
        if (time instanceof LocalDateTime localDateTime) {
            return DISPLAY_TIME.format(localDateTime);
        }
        return String.valueOf(time);
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void requireLogin(CurrentUser user, String message) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
        }
    }

    public record CurrentUser(int userId, String username, String role, String avatarUrl) {}
    public record TopicCreateRequest(String title, String desc, String type, String cover, List<String> tags) {}
    public record TopicPostRequest(String content, List<String> images, String productId, String storeId) {}
    public record TopicCommentRequest(String content) {}
    public record TopicView(String id, String type, String title, String desc, String heat, String author,
                            String cover, List<String> tags, Integer postCount, Integer likeCount,
                            Integer followCount, boolean followed) {
        public long topicId() {
            try {
                return Long.parseLong(id);
            } catch (RuntimeException e) {
                return 0;
            }
        }
    }
    public record TopicPostView(String id, String topicId, String author, String authorAvatar, String content,
                                List<String> images, ProductCard product, StoreCard store, String createdAt,
                                Integer likeCount, Integer wantCount, Integer collectCount, Integer commentCount,
                                boolean liked, boolean wanted, boolean collected, List<TopicCommentView> comments) {}
    public record TopicCommentView(String id, String author, String content, String createdAt) {}
    public record ProductCard(String id, String title, String cover, BigDecimal price, String category) {}
    public record StoreCard(String id, String name, String score, String fans, String desc, String badge, String avatar) {}
    public record TopicRow(Integer topicId, String type, String title, String topicDesc, String author, String cover,
                           String tags, Integer postCount, Integer likeCount, Integer followCount, Integer followed) {}
    public record PostRow(Integer postId, Integer topicId, Integer userId, Integer productId, Integer storeId,
                          String username, String authorAvatar, String content, String images, Object createdAt,
                          Integer likeCount, Integer commentCount, Integer liked, Integer wantCount,
                          Integer collectCount, Integer wanted, Integer collected) {}
    public record CommentRow(Integer commentId, String username, String content, Object createdAt) {}
}
