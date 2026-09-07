package com.example.shopping_back.shop;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public final class ShopDtos {
    private ShopDtos() {
    }

    public record ProductView(
            String id,
            String scene,
            String category,
            String title,
            String subtitle,
            BigDecimal price,
            BigDecimal originPrice,
            String cover,
            String tag,
            String condition,
            int credit,
            String location,
            String shopName,
            String delivery,
            List<String> service,
            List<String> highlights,
            String story,
            List<KeyValue> params,
            List<ReviewView> reviews,
            List<TimelineNode> timeline,
            List<String> aiTips,
            String status,
            Integer publisherId,
            String publisherName,
            String publishedAt,
            String rejectReason,
            BigDecimal floorPrice,
            String description,
            String storeId
    ) {
        public ProductView(
                String id,
                String scene,
                String category,
                String title,
                String subtitle,
                BigDecimal price,
                BigDecimal originPrice,
                String cover,
                String tag,
                String condition,
                int credit,
                String location,
                String shopName,
                String delivery,
                List<String> service,
                List<String> highlights,
                String story,
                List<KeyValue> params,
                List<ReviewView> reviews,
                List<TimelineNode> timeline,
                List<String> aiTips
        ) {
            this(id, scene, category, title, subtitle, price, originPrice, cover, tag, condition, credit,
                    location, shopName, delivery, service, highlights, story, params, reviews, timeline, aiTips, "");
        }

        public ProductView(
                String id,
                String scene,
                String category,
                String title,
                String subtitle,
                BigDecimal price,
                BigDecimal originPrice,
                String cover,
                String tag,
                String condition,
                int credit,
                String location,
                String shopName,
                String delivery,
                List<String> service,
                List<String> highlights,
                String story,
                List<KeyValue> params,
                List<ReviewView> reviews,
                List<TimelineNode> timeline,
                List<String> aiTips,
                String storeId
        ) {
            this(id, scene, category, title, subtitle, price, originPrice, cover, tag, condition, credit,
                    location, shopName, delivery, service, highlights, story, params, reviews, timeline, aiTips,
                    "approved", null, shopName, "", "", null, story, storeId);
        }
    }

    public record KeyValue(String key, String value) {
    }

    public record ReviewView(String user, String text, String score, List<String> tags) {
    }

    public record TimelineNode(String date, String title, String text, String icon) {
        public TimelineNode(String date, String title, String text) {
            this(date, title, text, "");
        }
    }

    public record StoreView(String id, String name, String score, String fans, String desc, String badge, String avatar) {
    }

    public record StoreDetailView(
            String id,
            Integer sellerId,
            String sellerName,
            String name,
            String score,
            Integer creditScore,
            String fans,
            String desc,
            String badge,
            List<String> service,
            boolean followed,
            long productCount,
            long newCount,
            long usedCount
    ) {
    }

    public record TopicView(
            String id,
            String type,
            String title,
            String desc,
            String heat,
            String author,
            String cover,
            List<String> tags,
            Integer postCount,
            Integer likeCount,
            Integer followCount,
            boolean followed
    ) {
        public TopicView(String id, String type, String title, String desc, String heat, String author, String cover, List<String> tags) {
            this(id, type, title, desc, heat, author, cover, tags, 0, 0, 0, false);
        }
    }

    public record TopicPostView(
            String id,
            String topicId,
            String author,
            String authorAvatar,
            String content,
            List<String> images,
            ProductView product,
            StoreView store,
            String createdAt,
            Integer likeCount,
            Integer wantCount,
            Integer collectCount,
            Integer commentCount,
            boolean liked,
            boolean wanted,
            boolean collected,
            List<TopicCommentView> comments
    ) {
    }

    public record TopicCommentView(String id, String author, String content, String createdAt) {
    }

    public record TopicPostRequest(
            @NotBlank(message = "帖子内容不能为空") String content,
            List<String> images,
            String productId,
            String storeId
    ) {
    }

    public record TopicCommentRequest(@NotBlank(message = "评论内容不能为空") String content) {
    }

    public record TopicActionRequest(@NotBlank(message = "操作类型不能为空") String actionType) {
    }

    public record TopicCreateRequest(
            @NotBlank(message = "话题标题不能为空") String title,
            @NotBlank(message = "话题简介不能为空") String desc,
            String type,
            String cover,
            List<String> tags
    ) {
    }

    public record OrderView(
            String id,
            String shop,
            String status,
            String title,
            String cover,
            String type,
            String service,
            BigDecimal amount,
            String goodsId,
            boolean reviewable,
            boolean reviewed,
            Integer productScore,
            Integer sellerScore,
            String reviewContent
    ) {
        public OrderView(String id, String shop, String status, String title, String cover, String type, String service, BigDecimal amount) {
            this(id, shop, status, title, cover, type, service, amount, "", false, false, null, null, "");
        }
    }

    public record CreateOrderItem(String goodsId, Integer quantity) {
    }

    public record CreateOrderRequest(List<CreateOrderItem> items) {
    }

    public record ReviewRequest(
            @NotNull(message = "商品评分不能为空") Integer productScore,
            @NotNull(message = "卖家评分不能为空") Integer sellerScore,
            @NotBlank(message = "评价内容不能为空") String content
    ) {
    }

    public record PublishRequest(
            String scene,
            @NotBlank(message = "标题不能为空") String title,
            @NotBlank(message = "图片不能为空") String image,
            @NotBlank(message = "分类不能为空") String category,
            @NotNull(message = "价格不能为空") BigDecimal price,
            String condition,
            @NotBlank(message = "描述不能为空") String description,
            String story,
            BigDecimal floorPrice,
            String location
    ) {
    }

    public record UpdateProductRequest(
            @NotBlank(message = "标题不能为空") String title,
            @NotBlank(message = "分类不能为空") String category,
            @NotNull(message = "价格不能为空") BigDecimal price,
            String image,
            String condition,
            String description,
            String story,
            BigDecimal floorPrice,
            String location
    ) {
    }

    public record StoreUpdateRequest(
            @NotBlank(message = "店铺名称不能为空") String name,
            String desc,
            String badge,
            List<String> service
    ) {
    }

    public record AiAssistRequest(String productId, String question, BigDecimal offer) {
    }

    public record AiAssistResponse(String answer, List<String> checklist, String consensus) {
    }

    public record AiPublishSuggestionRequest(String scene, String category, String condition, String keyword) {
    }

    public record AiPublishSuggestionResponse(String title, BigDecimal price, String description, String story, String source) {
        public AiPublishSuggestionResponse(String title, BigDecimal price, String description, String story) {
            this(title, price, description, story, "mock");
        }
    }

    public record AuditRequest(@NotBlank(message = "审核动作不能为空") String action, String reason) {
    }

    public record AuditResult(String id, String status, String rejectReason) {
    }
}
