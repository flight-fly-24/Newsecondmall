package com.example.shopping_back.shop;

import com.example.shopping_back.auth.dto.AuthUserView;
import com.example.shopping_back.shop.ShopDtos.AiAssistRequest;
import com.example.shopping_back.shop.ShopDtos.AiAssistResponse;
import com.example.shopping_back.shop.ShopDtos.AiPublishSuggestionRequest;
import com.example.shopping_back.shop.ShopDtos.AiPublishSuggestionResponse;
import com.example.shopping_back.shop.ShopDtos.AuditRequest;
import com.example.shopping_back.shop.ShopDtos.AuditResult;
import com.example.shopping_back.shop.ShopDtos.CreateOrderRequest;
import com.example.shopping_back.shop.ShopDtos.KeyValue;
import com.example.shopping_back.shop.ShopDtos.OrderView;
import com.example.shopping_back.shop.ShopDtos.ProductView;
import com.example.shopping_back.shop.ShopDtos.PublishRequest;
import com.example.shopping_back.shop.ShopDtos.UpdateProductRequest;
import com.example.shopping_back.shop.ShopDtos.ReviewView;
import com.example.shopping_back.shop.ShopDtos.ReviewRequest;
import com.example.shopping_back.shop.ShopDtos.StoreView;
import com.example.shopping_back.shop.ShopDtos.StoreDetailView;
import com.example.shopping_back.shop.ShopDtos.StoreUpdateRequest;
import com.example.shopping_back.shop.ShopDtos.TimelineNode;
import com.example.shopping_back.shop.ShopDtos.TopicView;
import com.example.shopping_back.shop.ShopDtos.TopicCommentRequest;
import com.example.shopping_back.shop.ShopDtos.TopicCommentView;
import com.example.shopping_back.shop.ShopDtos.TopicCreateRequest;
import com.example.shopping_back.shop.ShopDtos.TopicPostRequest;
import com.example.shopping_back.shop.ShopDtos.TopicPostView;
import com.example.shopping_back.shop.mapper.ShopOrderMapper;
import com.example.shopping_back.shop.mapper.ShopProductMapper;
import com.example.shopping_back.shop.mapper.ShopStoreMapper;
import com.example.shopping_back.shop.mapper.ShopTopicMapper;
import com.example.shopping_back.shop.model.OrderRecord;
import com.example.shopping_back.shop.model.ProductRecord;
import com.example.shopping_back.shop.model.ProductReviewRecord;
import com.example.shopping_back.shop.model.StoreRecord;
import com.example.shopping_back.shop.model.TopicCommentRecord;
import com.example.shopping_back.shop.model.TopicPostRecord;
import com.example.shopping_back.shop.model.TopicRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ShopService {
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final List<ProductView> products = new ArrayList<>();
    private final List<StoreView> stores = new ArrayList<>();
    private final ShopProductMapper productMapper;
    private final ShopStoreMapper storeMapper;
    private final ShopOrderMapper orderMapper;
    private final ShopTopicMapper topicMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String openAiApiKey;
    private final String openAiModel;
    private final String openAiResponsesUrl;
    private final String dashScopeApiKey;
    private final String dashScopeModel;
    private final String dashScopeChatUrl;

    public ShopService(
            ShopProductMapper productMapper,
            ShopStoreMapper storeMapper,
            ShopOrderMapper orderMapper,
            ShopTopicMapper topicMapper,
            ObjectMapper objectMapper,
            @Value("${openai.api.key:}") String openAiApiKey,
            @Value("${openai.model:gpt-4.1-mini}") String openAiModel,
            @Value("${openai.responses.url:https://api.openai.com/v1/responses}") String openAiResponsesUrl,
            @Value("${dashscope.api.key:}") String dashScopeApiKey,
            @Value("${dashscope.model:qwen-plus}") String dashScopeModel,
            @Value("${dashscope.chat.url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}") String dashScopeChatUrl
    ) {
        this.productMapper = productMapper;
        this.storeMapper = storeMapper;
        this.orderMapper = orderMapper;
        this.topicMapper = topicMapper;
        this.objectMapper = objectMapper;
        this.openAiApiKey = openAiApiKey == null ? "" : openAiApiKey.trim();
        this.openAiModel = openAiModel == null || openAiModel.isBlank() ? "gpt-4.1-mini" : openAiModel.trim();
        this.openAiResponsesUrl = openAiResponsesUrl == null || openAiResponsesUrl.isBlank()
                ? "https://api.openai.com/v1/responses"
                : openAiResponsesUrl.trim();
        this.dashScopeApiKey = dashScopeApiKey == null ? "" : dashScopeApiKey.trim();
        this.dashScopeModel = dashScopeModel == null || dashScopeModel.isBlank() ? "qwen-plus" : dashScopeModel.trim();
        this.dashScopeChatUrl = dashScopeChatUrl == null || dashScopeChatUrl.isBlank()
                ? "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
                : dashScopeChatUrl.trim();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        seed();
        ensureProductSchema();
        ensureStoreSchema();
        ensureOrderSchema();
        ensureTopicSchema();
    }

    public List<ProductView> products(String scene, String keyword) {
        try {
            List<ProductRecord> records = productMapper.selectApproved(scene, keyword);
            return records.stream().map(this::toView).toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    public ProductView product(String id) {
        ProductRecord record = productMapper.selectById(parseDbId(id));
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return toView(record);
    }

    public List<ProductView> myProducts(AuthUserView user) {
        Integer userId = user == null ? null : user.getUserId();
        try {
            if (userId != null) {
                List<ProductRecord> records = productMapper.selectBySeller(userId);
                if (!records.isEmpty()) {
                    return records.stream().map(this::toView).toList();
                }
            }
        } catch (RuntimeException ignored) {
        }
        String username = user == null ? "" : user.getUsername();
        return products.stream()
                .filter(item -> (userId != null && userId.equals(item.publisherId())) || (!username.isBlank() && username.equals(item.publisherName())))
                .toList();
    }

    public List<StoreView> stores() {
        try {
            return storeMapper.selectNormalStores().stream().map(this::toStoreView).toList();
        } catch (RuntimeException ignored) {
            return stores;
        }
    }

    public StoreDetailView myStore(AuthUserView user) {
        StoreRecord store = ensureSellerStore(user);
        List<ProductView> goods = storeProducts(String.valueOf(store.getStoreId()));
        int followers = safeFollowerCount(store.getStoreId());
        long newCount = goods.stream().filter(item -> "new".equals(item.scene())).count();
        long usedCount = goods.stream().filter(item -> "used".equals(item.scene())).count();
        return toStoreDetail(store, false, followers, goods.size(), newCount, usedCount);
    }

    public StoreDetailView updateMyStore(StoreUpdateRequest request, AuthUserView user) {
        StoreRecord store = ensureSellerStore(user);
        String storeName = defaultText(request == null ? "" : request.name(), defaultText(store.getStoreName(), user.getUsername() + " 的店铺"));
        String desc = defaultText(request == null ? "" : request.desc(), defaultStoreDesc(store));
        String badge = defaultText(request == null ? "" : request.badge(), defaultText(store.getBadge(), "信用店铺"));
        String serviceTags = joinServiceTags(request == null ? null : request.service(), store.getServiceTags());
        try {
            storeMapper.updateSellerStore(store.getStoreId(), user.getUserId(), storeName, desc, badge, serviceTags);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "店铺更新失败");
        }
        return myStore(user);
    }

    public StoreDetailView store(String id, AuthUserView user) {
        StoreRecord store = findStore(id);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found");
        }
        List<ProductView> goods = storeProducts(id);
        int followers = safeFollowerCount(store.getStoreId());
        boolean followed = user != null && storeMapper.isFollowed(user.getUserId(), store.getStoreId()) > 0;
        long newCount = goods.stream().filter(item -> "new".equals(item.scene())).count();
        long usedCount = goods.stream().filter(item -> "used".equals(item.scene())).count();
        return toStoreDetail(store, followed, followers, goods.size(), newCount, usedCount);
    }

    public List<ProductView> storeProducts(String id) {
        StoreRecord store = findStore(id);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found");
        }
        try {
            return productMapper.selectBySeller(store.getSellerId()).stream()
                    .filter(item -> "approved".equals(normalizeStatus(item.getStatus())))
                    .map(this::toView)
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    public StoreDetailView followStore(String id, AuthUserView user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please login first");
        }
        StoreRecord store = findStore(id);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found");
        }
        storeMapper.follow(user.getUserId(), store.getStoreId(), store.getStoreName());
        return store(id, user);
    }

    public StoreDetailView unfollowStore(String id, AuthUserView user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please login first");
        }
        StoreRecord store = findStore(id);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found");
        }
        storeMapper.unfollow(user.getUserId(), store.getStoreId());
        return store(id, user);
    }

    public List<TopicView> topics(String tag, String keyword) {
        try {
            ensureTopicSchema();
            return topicMapper.selectTopics(tag, keyword).stream().map(this::toTopicView).toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    public TopicView topic(String id, AuthUserView user) {
        ensureTopicSchema();
        TopicRecord record = topicMapper.selectTopic(parseDbId(id), user == null ? null : user.getUserId());
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "话题不存在");
        }
        return toTopicView(record);
    }

    public TopicView followTopic(String id, AuthUserView user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后关注话题");
        }
        ensureTopicSchema();
        Integer topicId = parseDbId(id);
        TopicRecord record = topicMapper.selectTopic(topicId, user.getUserId());
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "话题不存在");
        }
        topicMapper.insertTopicFollow(user.getUserId(), topicId, record.getTitle());
        return topic(String.valueOf(topicId), user);
    }

    public TopicView unfollowTopic(String id, AuthUserView user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后取消关注");
        }
        ensureTopicSchema();
        Integer topicId = parseDbId(id);
        TopicRecord record = topicMapper.selectTopic(topicId, user.getUserId());
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "话题不存在");
        }
        topicMapper.deleteTopicFollow(topicId, user.getUserId());
        return topic(String.valueOf(topicId), user);
    }

    public TopicView createTopic(TopicCreateRequest request, AuthUserView user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后创建话题");
        }
        if (!"buyer".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前仅买家可以创建话题");
        }
        String title = request == null ? "" : defaultText(request.title(), "").trim();
        String desc = request == null ? "" : defaultText(request.desc(), "").trim();
        if (title.isBlank() || desc.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "话题标题和简介不能为空");
        }
        TopicRecord record = new TopicRecord();
        record.setType(defaultText(request.type(), "买家话题"));
        record.setTitle(title);
        record.setTopicDesc(desc);
        record.setAuthor(defaultText(user.getUsername(), "买家"));
        record.setCover(defaultText(request.cover(), "/static/goods/viewtop-monitor.jpg"));
        record.setTags(joinTopicTags(request.tags()));
        record.setStatus("normal");
        ensureTopicSchema();
        topicMapper.insertTopic(record);
        TopicRecord created = topicMapper.selectTopic(record.getTopicId(), user.getUserId());
        return toTopicView(created == null ? record : created);
    }

    public List<TopicPostView> topicPosts(String topicId, AuthUserView user) {
        ensureTopicSchema();
        Integer id = parseDbId(topicId);
        Integer userId = user == null ? null : user.getUserId();
        return topicMapper.selectPosts(id, userId).stream().map(this::toTopicPostView).toList();
    }

    public List<TopicPostView> createTopicPost(String topicId, TopicPostRequest request, AuthUserView user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后发帖");
        }
        ensureTopicSchema();
        Integer id = parseDbId(topicId);
        if (topicMapper.selectTopic(id, user.getUserId()) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "话题不存在");
        }
        String content = request == null ? "" : defaultText(request.content(), "").trim();
        if (content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "帖子内容不能为空");
        }
        String images = request == null || request.images() == null ? "" : String.join(",", request.images());
        Integer productId = request == null || isBlank(request.productId()) ? null : parseDbId(request.productId());
        Integer storeId = request == null || isBlank(request.storeId()) ? null : parseDbId(request.storeId());
        if (productId != null && productMapper.selectById(productId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "推荐商品不存在");
        }
        if (storeId != null && storeMapper.selectById(storeId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "推荐店铺不存在");
        }
        topicMapper.insertPost(id, user.getUserId(), productId, storeId, content, images);
        return topicPosts(topicId, user);
    }

    public List<TopicPostView> createTopicComment(String postId, TopicCommentRequest request, AuthUserView user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后评论");
        }
        ensureTopicSchema();
        Integer id = parseDbId(postId);
        Integer topicId = topicMapper.topicIdByPost(id);
        if (topicId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在");
        }
        String content = request == null ? "" : defaultText(request.content(), "").trim();
        if (content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评论内容不能为空");
        }
        topicMapper.insertComment(id, user.getUserId(), content);
        return topicPosts(String.valueOf(topicId), user);
    }

    public List<TopicPostView> toggleTopicPostAction(String postId, String actionType, AuthUserView user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后操作");
        }
        ensureTopicSchema();
        Integer id = parseDbId(postId);
        Integer topicId = topicMapper.topicIdByPost(id);
        if (topicId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在");
        }
        String action = normalizeTopicAction(actionType);
        if (topicMapper.actionExists(id, user.getUserId(), action) > 0) {
            topicMapper.deleteAction(id, user.getUserId(), action);
        } else {
            topicMapper.insertAction(id, user.getUserId(), action);
        }
        return topicPosts(String.valueOf(topicId), user);
    }

    public List<TopicPostView> toggleTopicPostLike(String postId, AuthUserView user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后点赞");
        }
        ensureTopicSchema();
        Integer id = parseDbId(postId);
        Integer topicId = topicMapper.topicIdByPost(id);
        if (topicId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在");
        }
        if (topicMapper.likeExists(id, user.getUserId()) > 0) {
            topicMapper.deleteLike(id, user.getUserId());
        } else {
            topicMapper.insertLike(id, user.getUserId());
        }
        return topicPosts(String.valueOf(topicId), user);
    }

    public List<OrderView> orders(AuthUserView user) {
        return orders(user, null);
    }

    public List<OrderView> orders(AuthUserView user, String status) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后查看订单");
        }
        ensureOrderSchema();
        String normalized = normalizeOrderStatus(status);
        return orderMapper.selectBuyerOrdersFiltered(user.getUserId(), normalized).stream()
                .map(this::toOrderView)
                .toList();
    }

    public OrderView cancelOrder(String orderId, AuthUserView user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后取消订单");
        }
        ensureOrderSchema();
        Integer id = parseDbId(orderId);
        OrderRecord order = orderMapper.selectOrder(id);
        if (order == null || !user.getUserId().equals(order.getBuyerId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        if ("cancelled".equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单已取消");
        }
        orderMapper.updateOrderStatus(id, "cancelled");
        return toOrderView(orderMapper.selectOrder(id));
    }

    public List<OrderView> createOrders(CreateOrderRequest request, AuthUserView user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后下单");
        }
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单商品不能为空");
        }
        ensureOrderSchema();
        for (ShopDtos.CreateOrderItem item : request.items()) {
            Integer goodsId = parseDbId(item.goodsId());
            ProductRecord product = productMapper.selectById(goodsId);
            if (product == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
            }
            if (user.getUserId().equals(product.getSellerId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能购买自己发布的商品");
            }
            int quantity = item.quantity() == null || item.quantity() <= 0 ? 1 : item.quantity();
            BigDecimal price = product.getPrice() == null ? BigDecimal.ZERO : product.getPrice();
            orderMapper.insertOrder(user.getUserId(), product.getSellerId(), goodsId, "completed", price.multiply(new BigDecimal(quantity)));
        }
        return orders(user);
    }

    public OrderView reviewOrder(String orderId, ReviewRequest request, AuthUserView user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后评价");
        }
        ensureOrderSchema();
        Integer id = parseDbId(orderId);
        OrderRecord order = orderMapper.selectOrder(id);
        if (order == null || !user.getUserId().equals(order.getBuyerId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        if (!"completed".equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单完成后才能评价");
        }
        if (orderMapper.reviewCountByOrder(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该订单已评价");
        }
        int productScore = normalizeScore(request == null ? null : request.productScore());
        int sellerScore = normalizeScore(request == null ? null : request.sellerScore());
        String content = request == null ? "" : defaultText(request.content(), "").trim();
        if (content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评价内容不能为空");
        }
        orderMapper.insertReview(id, order.getGoodsId(), user.getUserId(), order.getSellerId(), productScore, sellerScore, content);
        orderMapper.refreshSellerCredit(order.getSellerId());
        orderMapper.refreshStoreCredit(order.getSellerId());
        return toOrderView(orderMapper.selectOrder(id));
    }

    public ProductView publish(PublishRequest request, AuthUserView user) {
        validatePublish(request);
        ensureSellerStore(user);
        ProductView dbCreated = publishToDatabase(request, user);
        if (dbCreated != null) {
            return dbCreated;
        }
        String scene = normalizeScene(request.scene());
        String sellerName = user == null ? "我的个人店铺" : user.getUsername();
        Integer sellerId = user == null ? null : user.getUserId();
        String now = DISPLAY_TIME.format(LocalDateTime.now());
        ProductView created = new ProductView(
                "published-" + (products.size() + 1),
                scene,
                request.category() == null ? "未分类" : request.category(),
                request.title(),
                "用户新发布 · 等待审核通过后上架",
                request.price() == null ? BigDecimal.ZERO : request.price(),
                request.price() == null ? BigDecimal.ZERO : request.price().add(new BigDecimal("80")),
                request.image(),
                "待审核",
                request.condition() == null ? "待补充" : request.condition(),
                user == null || user.getCredit() == null ? 96 : user.getCredit(),
                request.location() == null ? "未知地区" : request.location(),
                sellerName,
                "发布后由卖家设置配送方式",
                List.of("平台担保", "审核上架"),
                List.of("AI 已生成标题建议", "等待管理员审核"),
                request.story() == null ? request.description() : request.story(),
                buildParams(request, "待审核", ""),
                List.of(),
                buildTimeline(scene, request.story(), now, null),
                List.of("建议补充瑕疵照片", "建议设置最低可接受价"),
                "pending",
                sellerId,
                sellerName,
                now,
                "",
                request.floorPrice(),
                request.description(),
                ""
        );
        products.add(0, created);
        return created;
    }

    public ProductView updateProduct(String id, UpdateProductRequest request, AuthUserView user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录卖家账号");
        }
        if (!"seller".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前仅卖家可以编辑商品");
        }
        Integer goodsId = parseDbId(id);
        ProductRecord existing = productMapper.selectById(goodsId);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        if (existing.getSellerId() == null || !existing.getSellerId().equals(user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能编辑自己发布的商品");
        }
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "价格必须大于 0");
        }
        try {
            ensureProductSchema();
            ProductRecord record = new ProductRecord();
            record.setGoodsId(goodsId);
            record.setGoodsName(request.title());
            record.setCategory(request.category());
            record.setGoodsDesc(request.description());
            record.setGoodsCondition(defaultText(request.condition(), existing.getGoodsCondition()));
            record.setStory(request.story());
            record.setPrice(request.price());
            record.setFloorPrice(request.floorPrice());
            record.setAddress(defaultText(request.location(), existing.getAddress()));
            record.setImage(request.image());
            if (productMapper.update(record) > 0) {
                return product(id);
            }
        } catch (RuntimeException ignored) {
            // 数据库不可用时回退到内存列表更新
        }
        for (int i = 0; i < products.size(); i++) {
            ProductView item = products.get(i);
            if (!item.id().equals(id)) {
                continue;
            }
            ProductView updated = new ProductView(
                    item.id(), item.scene(), request.category(), request.title(), item.subtitle(),
                    request.price(), item.originPrice(), defaultText(request.image(), item.cover()),
                    item.tag(), defaultText(request.condition(), item.condition()), item.credit(),
                    defaultText(request.location(), item.location()), item.shopName(), item.delivery(),
                    item.service(), item.highlights(), defaultText(request.story(), item.story()),
                    item.params(), item.reviews(), item.timeline(), item.aiTips(), item.status(),
                    item.publisherId(), item.publisherName(), item.publishedAt(), item.rejectReason(),
                    request.floorPrice(), defaultText(request.description(), item.description()), item.storeId()
            );
            products.set(i, updated);
            return updated;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
    }

    public List<ProductView> pendingProducts() {
        List<ProductView> pending = new ArrayList<>();
        try {
            ensureProductSchema();
            List<ProductRecord> records = productMapper.selectPending();
            pending.addAll(records.stream().map(this::toView).toList());
        } catch (RuntimeException ignored) {
        }
        for (ProductView item : products) {
            if (!"pending".equals(item.status())) {
                continue;
            }
            if (pending.stream().noneMatch(current -> current.id().equals(item.id()))) {
                pending.add(item);
            }
        }
        return pending;
    }

    public AuditResult audit(String id, AuditRequest request) {
        String action = request.action() == null ? "" : request.action().trim().toLowerCase(Locale.ROOT);
        String status;
        if ("approve".equals(action)) {
            status = "approved";
        } else if ("reject".equals(action)) {
            status = "rejected";
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action 只能是 approve 或 reject");
        }
        String reason = "rejected".equals(status) ? defaultText(request.reason(), "信息不完整，请补充后重新提交") : "";
        try {
            ensureProductSchema();
            int affected = productMapper.updateAuditStatus(parseDbId(id), auditStatusForDatabase(status), reason);
            if (affected > 0) {
                return new AuditResult(id, status, reason);
            }
        } catch (RuntimeException ignored) {
        }
        for (int i = 0; i < products.size(); i++) {
            ProductView item = products.get(i);
            if (item.id().equals(id)) {
                ProductView next = withAuditStatus(item, status, reason);
                products.set(i, next);
                return new AuditResult(id, status, reason);
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
    }

    public AiAssistResponse assist(AiAssistRequest request) {
        ProductView product = product(request.productId());
        String answer = "我会先确认 " + product.title() + " 的成色、瑕疵、配件和发货方式，再根据卖家信用给出报价建议。";
        List<String> checklist = List.of("确认商品实拍图", "确认是否支持平台担保", "确认瑕疵和售后约定", "保留聊天中的交易共识");
        String consensus = "交易共识：按平台担保下单，卖家如实说明瑕疵，买家收货验货后确认。";
        return new AiAssistResponse(answer, checklist, consensus);
    }

    public AiPublishSuggestionResponse suggestPublish(AiPublishSuggestionRequest request) {
        if (!dashScopeApiKey.isBlank()) {
            try {
                return suggestPublishWithDashScope(request);
            } catch (RuntimeException e) {
                return mockPublishSuggestion(request, "mock_dashscope_failed");
            }
        }
        if (!openAiApiKey.isBlank()) {
            try {
                return suggestPublishWithOpenAi(request);
            } catch (RuntimeException e) {
                return mockPublishSuggestion(request, "mock_api_failed");
            }
        }
        return mockPublishSuggestion(request, "mock_missing_key");
    }

    private AiPublishSuggestionResponse suggestPublishWithDashScope(AiPublishSuggestionRequest request) {
        String scene = normalizeScene(request.scene());
        String category = defaultText(request.category(), "校园好物");
        String condition = defaultText(request.condition(), "9 成新");
        String keyword = defaultText(request.keyword(), category);
        Map<String, Object> payload = Map.of(
                "model", dashScopeModel,
                "messages", List.of(
                        Map.of("role", "system", "content", buildAiInstructions()),
                        Map.of("role", "user", "content", buildAiInput(scene, category, condition, keyword))
                ),
                "temperature", 0.7
        );

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(dashScopeChatUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + dashScopeApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("DashScope API HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode suggestion = objectMapper.readTree(extractJsonObject(content));
            return toAiSuggestionResponse(request, suggestion, "dashscope");
        } catch (IOException e) {
            throw new IllegalStateException("DashScope API 返回解析失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DashScope API 调用被中断", e);
        }
    }

    private AiPublishSuggestionResponse suggestPublishWithOpenAi(AiPublishSuggestionRequest request) {
        String scene = normalizeScene(request.scene());
        String category = defaultText(request.category(), "校园好物");
        String condition = defaultText(request.condition(), "9 成新");
        String keyword = defaultText(request.keyword(), category);
        Map<String, Object> payload = Map.of(
                "model", openAiModel,
                "instructions", buildAiInstructions(),
                "input", buildAiInput(scene, category, condition, keyword),
                "max_output_tokens", 500
        );

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(openAiResponsesUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI API HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode suggestion = objectMapper.readTree(extractJsonObject(extractOutputText(root)));
            return toAiSuggestionResponse(request, suggestion, "openai");
        } catch (IOException e) {
            throw new IllegalStateException("OpenAI API 返回解析失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI API 调用被中断", e);
        }
    }

    private AiPublishSuggestionResponse toAiSuggestionResponse(AiPublishSuggestionRequest request, JsonNode suggestion, String source) {
        String scene = normalizeScene(request.scene());
        String category = defaultText(request.category(), "校园好物");
        String condition = defaultText(request.condition(), "9 成新");
        String keyword = defaultText(request.keyword(), category);
        AiPublishSuggestionResponse fallback = mockPublishSuggestion(request, "mock");
        return new AiPublishSuggestionResponse(
                defaultText(suggestion.path("title").asText(), keyword),
                parsePositivePrice(suggestion.path("price"), "used".equals(scene) ? estimateUsedPrice(category, condition) : estimateNewPrice(category)),
                defaultText(suggestion.path("description").asText(), fallback.description()),
                defaultText(suggestion.path("story").asText(), fallback.story()),
                source
        );
    }

    private String buildAiInstructions() {
        return """
                你是校园二手交易平台的商品发布助手。
                请根据用户提供的商品类型、分类、成色和关键词，生成适合发布页直接填充的中文内容。
                必须只输出 JSON，不要 Markdown，不要解释。
                JSON 字段必须为：title, price, description, story。
                price 必须是合理数字，description 60 到 120 字，story 40 到 100 字。
                二手商品要突出真实成色、瑕疵说明、验货方式和流转故事；新品要突出卖点、服务和适用场景。
                """;
    }

    private String buildAiInput(String scene, String category, String condition, String keyword) {
        return """
                商品类型：%s
                分类：%s
                成色：%s
                关键词：%s
                """.formatted("used".equals(scene) ? "二手" : "新品", category, condition, keyword);
    }

    private AiPublishSuggestionResponse mockPublishSuggestion(AiPublishSuggestionRequest request, String source) {
        String scene = normalizeScene(request.scene());
        String category = defaultText(request.category(), "校园好物");
        String condition = defaultText(request.condition(), "9 成新");
        String keyword = defaultText(request.keyword(), category);
        boolean used = "used".equals(scene);
        BigDecimal price = used ? estimateUsedPrice(category, condition) : estimateNewPrice(category);
        String title = (used ? "[二手] " : "[新品] ") + keyword + " · " + (used ? condition : "现货严选");
        String description = used
                ? "这件" + category + "整体为" + condition + "，功能正常，适合校园内当面验货。已尽量清洁整理，关键配件和瑕疵会在交易前补充实拍。"
                : "这款" + category + "适合学习、办公和日常使用，主打稳定、易用和售后省心。建议突出规格、质保和发货时效。";
        String story = used
                ? "它陪上一任主人完成了一段认真使用的日常，现在整理出来转给需要的人，希望继续被好好使用。"
                : "新品卖点可以围绕品质、服务和适用场景展开，让买家快速判断是否适合自己。";
        return new AiPublishSuggestionResponse(title, price, description, story, source);
    }

    private String extractOutputText(JsonNode root) {
        if (root.hasNonNull("output_text")) {
            return root.path("output_text").asText();
        }
        for (JsonNode output : root.path("output")) {
            for (JsonNode content : output.path("content")) {
                String type = content.path("type").asText();
                if ("output_text".equals(type) || "text".equals(type)) {
                    String text = content.path("text").asText();
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        throw new IllegalStateException("OpenAI API 未返回文本内容");
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("OpenAI API 未返回 JSON 对象");
        }
        return text.substring(start, end + 1);
    }

    private BigDecimal parsePositivePrice(JsonNode node, BigDecimal fallback) {
        try {
            BigDecimal value = node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText());
            return value.compareTo(BigDecimal.ZERO) > 0 ? value : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private void ensureProductSchema() {
        try {
            productMapper.widenStatusColumn();
            if (productMapper.countGoodsColumn("reject_reason") == 0) {
                productMapper.addRejectReasonColumn();
            }
            if (productMapper.countGoodsColumn("reviewed_at") == 0) {
                productMapper.addReviewedAtColumn();
            }
            productMapper.migrateApprovedStatus();
            productMapper.migratePendingStatus();
            productMapper.migrateTruncatedPendingStatus();
        } catch (RuntimeException ignored) {
        }
    }

    private void ensureStoreSchema() {
        try {
            if (storeMapper.countStoreColumn("store_desc") == 0) {
                storeMapper.addStoreDescColumn();
            }
            if (storeMapper.countStoreColumn("badge") == 0) {
                storeMapper.addBadgeColumn();
            }
            if (storeMapper.countStoreColumn("service_tags") == 0) {
                storeMapper.addServiceTagsColumn();
            }
            if (storeMapper.countFollowUniqueIndex() == 0) {
                storeMapper.dedupeFollows();
                storeMapper.addFollowUniqueIndex();
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void ensureOrderSchema() {
        try {
            orderMapper.createOrdersTable();
            orderMapper.createReviewTable();
        } catch (RuntimeException ignored) {
        }
    }

    private void ensureTopicSchema() {
        try {
            topicMapper.createTopicTable();
            topicMapper.createPostTable();
            topicMapper.createCommentTable();
            topicMapper.createLikeTable();
            topicMapper.createActionTable();
            topicMapper.createFollowTopicTable();
            if (topicMapper.countPostColumn("product_id") == 0) {
                topicMapper.addPostProductIdColumn();
            }
            if (topicMapper.countPostColumn("store_id") == 0) {
                topicMapper.addPostStoreIdColumn();
            }
        } catch (RuntimeException ignored) {
        }
    }

    private boolean usesWideGoodsStatus() {
        try {
            Integer length = productMapper.statusColumnLength();
            return length != null && length >= 8;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String pendingStatusForDatabase() {
        return usesWideGoodsStatus() ? "pending" : "2";
    }

    private String auditStatusForDatabase(String status) {
        if (!usesWideGoodsStatus()) {
            return "approved".equals(status) ? "0" : "3";
        }
        return status;
    }

    private ProductView publishToDatabase(PublishRequest request, AuthUserView user) {
        try {
            ensureProductSchema();
            ProductRecord record = new ProductRecord();
            record.setSellerId(user == null ? null : user.getUserId());
            record.setGoodsName(request.title());
            record.setCategory(request.category());
            record.setGoodsDesc(request.description());
            record.setGoodsCondition(defaultText(request.condition(), "待补充"));
            record.setStory(request.story());
            record.setPrice(request.price());
            record.setFloorPrice(request.floorPrice());
            record.setScene(normalizeScene(request.scene()));
            record.setAddress(defaultText(request.location(), "未知地区"));
            record.setImage(request.image());
            record.setStatus(pendingStatusForDatabase());
            productMapper.insert(record);
            ProductRecord selected = productMapper.selectById(record.getGoodsId());
            return toView(selected == null ? record : selected);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ProductView productFromDatabase(String id) {
        try {
            ProductRecord record = productMapper.selectById(parseDbId(id));
            return record == null ? null : toView(record);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private StoreRecord findStore(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            try {
                return storeMapper.selectById(Integer.parseInt(id));
            } catch (NumberFormatException ignored) {
                return storeMapper.selectByName(id);
            }
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private StoreRecord ensureSellerStore(AuthUserView user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录卖家账号");
        }
        if (!"seller".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前仅卖家可以管理店铺");
        }
        try {
            StoreRecord existing = storeMapper.selectBySeller(user.getUserId());
            if (existing != null) {
                return existing;
            }
            String storeName = defaultText(user.getUsername(), "卖家") + " 的店铺";
            storeMapper.insertSellerStore(
                    user.getUserId(),
                    storeName,
                    storeName + "由卖家自主经营，商品经过平台记录，支持在站内沟通、关注和浏览。",
                    "信用店铺",
                    "平台担保,真实商品,信用卖家"
            );
            StoreRecord created = storeMapper.selectBySeller(user.getUserId());
            if (created != null) {
                return created;
            }
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "店铺初始化失败");
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "店铺初始化失败");
    }

    private StoreView toStoreView(StoreRecord record) {
        int followers = safeFollowerCount(record.getStoreId());
        return new StoreView(
                String.valueOf(record.getStoreId()),
                defaultText(record.getStoreName(), "店铺"),
                scoreText(record.getScore()),
                formatFans(followers),
                defaultText(record.getStoreDesc(), defaultStoreDesc(record)),
                defaultText(record.getBadge(), "信用店铺"),
                defaultText(record.getSellerAvatar(), "")
        );
    }

    private StoreDetailView toStoreDetail(StoreRecord record, boolean followed, int followers, long productCount, long newCount, long usedCount) {
        return new StoreDetailView(
                String.valueOf(record.getStoreId()),
                record.getSellerId(),
                defaultText(record.getSellerName(), "卖家"),
                defaultText(record.getStoreName(), "店铺"),
                scoreText(record.getScore()),
                record.getCreditScore() == null ? 100 : record.getCreditScore(),
                formatFans(followers),
                defaultText(record.getStoreDesc(), defaultStoreDesc(record)),
                defaultText(record.getBadge(), "信用店铺"),
                parseServiceTags(record.getServiceTags()),
                followed,
                productCount,
                newCount,
                usedCount
        );
    }

    private int safeFollowerCount(Integer storeId) {
        try {
            return storeMapper.followerCount(storeId);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private List<String> parseServiceTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of("平台担保", "真实商品", "信用卖家");
        }
        return List.of(raw.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private String joinServiceTags(List<String> tags, String fallback) {
        if (tags == null || tags.isEmpty()) {
            return defaultText(fallback, "平台担保,真实商品,信用卖家");
        }
        String joined = tags.stream()
                .map(item -> item == null ? "" : item.trim())
                .filter(item -> !item.isEmpty())
                .limit(6)
                .collect(Collectors.joining(","));
        return defaultText(joined, defaultText(fallback, "平台担保,真实商品,信用卖家"));
    }

    private String scoreText(BigDecimal score) {
        return score == null ? "4.8" : score.stripTrailingZeros().toPlainString();
    }

    private String formatFans(int count) {
        if (count >= 10000) {
            return String.format(Locale.ROOT, "%.1fw", count / 10000.0);
        }
        return String.valueOf(count);
    }

    private String defaultStoreDesc(StoreRecord record) {
        String name = defaultText(record.getStoreName(), "这家店铺");
        return name + "由卖家自主经营，商品经过平台记录，支持在站内沟通、关注和浏览。";
    }

    private ProductView toView(ProductRecord record) {
        StoreRecord sellerStore = ensureStoreForProduct(record);
        String scene = normalizeScene(record.getScene());
        String status = normalizeStatus(record.getStatus());
        String title = defaultText(record.getGoodsName(), "未命名商品");
        String description = defaultText(record.getGoodsDesc(), "卖家暂未填写详细描述");
        String condition = defaultText(record.getGoodsCondition(), "待补充");
        BigDecimal price = record.getPrice() == null ? BigDecimal.ZERO : record.getPrice();
        BigDecimal originPrice = price.add("used".equals(scene) ? new BigDecimal("80") : new BigDecimal("120"));
        String publishedAt = formatTime(record.getCreateTime());
        String story = defaultText(record.getStory(), description);
        String shopName = sellerStore == null
                ? defaultText(record.getSellerName(), "个人卖家")
                : defaultText(sellerStore.getStoreName(), defaultText(record.getSellerName(), "个人卖家"));
        String storeId = sellerStore == null || sellerStore.getStoreId() == null ? "" : String.valueOf(sellerStore.getStoreId());
        return new ProductView(
                String.valueOf(record.getGoodsId()),
                scene,
                defaultText(record.getCategory(), "未分类"),
                title,
                statusSubtitle(status, scene, condition),
                price,
                originPrice,
                defaultText(record.getImage(), "/static/goods/viewtop-monitor.jpg"),
                statusTag(status),
                condition,
                record.getSellerCredit() == null ? 96 : record.getSellerCredit(),
                defaultText(record.getAddress(), "未知地区"),
                shopName,
                "发布后由卖家设置配送方式",
                List.of("平台担保", statusTag(status)),
                List.of("真实描述", "pending".equals(status) ? "待审核" : "平台审核记录"),
                story,
                buildParamsFromRecord(record, status),
                reviewsForGoods(record.getGoodsId()),
                buildTimeline(scene, story, publishedAt, record.getReviewedAt()),
                List.of("可询问成色和配件", "可生成验货清单", "可根据最低价辅助议价"),
                status,
                record.getSellerId(),
                shopName,
                publishedAt,
                defaultText(record.getRejectReason(), ""),
                record.getFloorPrice(),
                description,
                storeId
        );
    }

    private StoreRecord ensureStoreForProduct(ProductRecord record) {
        if (record == null || record.getSellerId() == null) {
            return null;
        }
        try {
            StoreRecord existing = storeMapper.selectBySeller(record.getSellerId());
            if (existing != null) {
                return existing;
            }
            String storeName = defaultText(record.getSellerName(), "卖家") + " 的店铺";
            storeMapper.insertSellerStore(
                    record.getSellerId(),
                    storeName,
                    storeName + "由卖家自主经营，商品经过平台记录，支持在站内沟通、关注和浏览。",
                    "信用店铺",
                    "平台担保,真实商品,信用卖家"
            );
            return storeMapper.selectBySeller(record.getSellerId());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void validatePublish(PublishRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品参数不能为空");
        }
        if (isBlank(request.title())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题不能为空");
        }
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "价格必须大于 0");
        }
        if ("used".equals(normalizeScene(request.scene()))) {
            if (isBlank(request.condition())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "二手商品必须填写成色");
            }
            if (isBlank(request.story())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "二手商品必须填写故事");
            }
        }
    }

    private ProductView withAuditStatus(ProductView item, String status, String reason) {
        LocalDateTime reviewedAt = "approved".equals(status) ? LocalDateTime.now() : null;
        return new ProductView(
                item.id(), item.scene(), item.category(), item.title(), statusSubtitle(status, item.scene(), item.condition()),
                item.price(), item.originPrice(), item.cover(), statusTag(status), item.condition(), item.credit(),
                item.location(), item.shopName(), item.delivery(), List.of("平台担保", statusTag(status)), item.highlights(),
                item.story(), item.params(), item.reviews(), buildTimeline(item.scene(), item.story(), item.publishedAt(), reviewedAt),
                item.aiTips(), status, item.publisherId(), item.publisherName(), item.publishedAt(), reason,
                item.floorPrice(), item.description(), item.storeId()
        );
    }

    private List<KeyValue> buildParams(PublishRequest request, String statusText, String rejectReason) {
        List<KeyValue> rows = new ArrayList<>();
        rows.add(new KeyValue("状态", statusText));
        rows.add(new KeyValue("分类", defaultText(request.category(), "未分类")));
        rows.add(new KeyValue("成色", defaultText(request.condition(), "待补充")));
        if (request.floorPrice() != null) {
            rows.add(new KeyValue("最低可接受价", "¥" + request.floorPrice()));
        }
        if (!isBlank(rejectReason)) {
            rows.add(new KeyValue("拒绝原因", rejectReason));
        }
        return rows;
    }

    private List<KeyValue> buildParamsFromRecord(ProductRecord record, String status) {
        List<KeyValue> rows = new ArrayList<>();
        rows.add(new KeyValue("状态", statusTag(status)));
        rows.add(new KeyValue("分类", defaultText(record.getCategory(), "未分类")));
        rows.add(new KeyValue("成色", defaultText(record.getGoodsCondition(), "待补充")));
        if (record.getFloorPrice() != null) {
            rows.add(new KeyValue("最低可接受价", "¥" + record.getFloorPrice()));
        }
        if (!isBlank(record.getRejectReason())) {
            rows.add(new KeyValue("拒绝原因", record.getRejectReason()));
        }
        return rows;
    }

    private List<TimelineNode> buildTimeline(String scene, String story, String publishedAt, LocalDateTime reviewedAt) {
        if (!"used".equals(scene)) {
            return List.of();
        }
        String publishTime = defaultText(publishedAt, "今天");
        String reviewTime = reviewedAt == null ? "待审核" : formatTime(reviewedAt);
        return List.of(
                new TimelineNode("首次购入", "首次购入", "上一任主人把它带进日常生活，开始认真使用。", "🛒"),
                new TimelineNode("使用经历", "使用经历", defaultText(story, "它经历过稳定使用，功能和状态会在交易前充分说明。"), "✨"),
                new TimelineNode(publishTime, "发布转让", "卖家完成清洁整理并提交平台审核。", "📮"),
                new TimelineNode(reviewTime, "审核通过", reviewedAt == null ? "管理员正在核验描述、图片和价格合理性。" : "平台审核通过，商品流转信息已记录。", "✅"),
                new TimelineNode("进行中", "等待新主人", "等待合适的买家接手，继续延长物品的使用价值。", "🏠")
        );
    }

    private List<ReviewView> reviewsForGoods(Integer goodsId) {
        if (goodsId == null) {
            return List.of();
        }
        try {
            ensureOrderSchema();
            return orderMapper.selectProductReviews(goodsId).stream()
                    .map(this::toReviewView)
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private ReviewView toReviewView(ProductReviewRecord record) {
        return new ReviewView(
                defaultText(record.getBuyerName(), "买家"),
                defaultText(record.getContent(), "买家未填写文字评价"),
                String.valueOf(record.getProductScore() == null ? 5 : record.getProductScore()),
                List.of("商品 " + safeScore(record.getProductScore()) + " 星", "卖家 " + safeScore(record.getSellerScore()) + " 星")
        );
    }

    private OrderView toOrderView(OrderRecord record) {
        boolean reviewed = record.getReviewedAt() != null || record.getProductScore() != null;
        boolean completed = "completed".equals(record.getStatus());
        boolean cancelled = "cancelled".equals(record.getStatus());
        String status = cancelled ? "已取消" : reviewed ? "已评价" : completed ? "已完成" : "待收货";
        return new OrderView(
                String.valueOf(record.getOrderId()),
                defaultText(record.getSellerName(), "卖家"),
                status,
                defaultText(record.getGoodsName(), "商品"),
                defaultText(record.getGoodsImage(), ""),
                "new".equals(record.getScene()) ? "新品" : "二手",
                "平台担保",
                record.getAmount() == null ? BigDecimal.ZERO : record.getAmount(),
                record.getGoodsId() == null ? "" : String.valueOf(record.getGoodsId()),
                completed && !reviewed,
                reviewed,
                record.getProductScore(),
                record.getSellerScore(),
                defaultText(record.getReviewContent(), "")
        );
    }

    private TopicView toTopicView(TopicRecord record) {
        int postCount = record.getPostCount() == null ? 0 : record.getPostCount();
        int likeCount = record.getLikeCount() == null ? 0 : record.getLikeCount();
        int followCount = record.getFollowCount() == null ? 0 : record.getFollowCount();
        boolean followed = record.getFollowed() != null && record.getFollowed() > 0;
        String heat = postCount + " 帖 · " + followCount + " 关注";
        return new TopicView(
                String.valueOf(record.getTopicId()),
                defaultText(record.getType(), "话题"),
                defaultText(record.getTitle(), "未命名话题"),
                defaultText(record.getTopicDesc(), "一起讨论这个话题。"),
                heat,
                defaultText(record.getAuthor(), "松果社区"),
                defaultText(record.getCover(), "/static/goods/viewtop-monitor.jpg"),
                parseTags(record.getTags()),
                postCount,
                likeCount,
                followCount,
                followed
        );
    }

    private TopicPostView toTopicPostView(TopicPostRecord record) {
        return new TopicPostView(
                String.valueOf(record.getPostId()),
                String.valueOf(record.getTopicId()),
                defaultText(record.getUsername(), "松果用户"),
                defaultText(record.getAuthorAvatar(), ""),
                defaultText(record.getContent(), ""),
                parseImages(record.getImages()),
                postProduct(record.getProductId()),
                postStore(record.getStoreId()),
                formatTime(record.getCreatedAt()),
                record.getLikeCount() == null ? 0 : record.getLikeCount(),
                record.getWantCount() == null ? 0 : record.getWantCount(),
                record.getCollectCount() == null ? 0 : record.getCollectCount(),
                record.getCommentCount() == null ? 0 : record.getCommentCount(),
                record.getLiked() != null && record.getLiked() > 0,
                record.getWanted() != null && record.getWanted() > 0,
                record.getCollected() != null && record.getCollected() > 0,
                topicMapper.selectComments(record.getPostId()).stream().map(this::toTopicCommentView).toList()
        );
    }

    private ProductView postProduct(Integer productId) {
        if (productId == null) {
            return null;
        }
        ProductRecord record = productMapper.selectById(productId);
        return record == null ? null : toView(record);
    }

    private StoreView postStore(Integer storeId) {
        if (storeId == null) {
            return null;
        }
        StoreRecord record = storeMapper.selectById(storeId);
        return record == null ? null : toStoreView(record);
    }

    private TopicCommentView toTopicCommentView(TopicCommentRecord record) {
        return new TopicCommentView(
                String.valueOf(record.getCommentId()),
                defaultText(record.getUsername(), "松果用户"),
                defaultText(record.getContent(), ""),
                formatTime(record.getCreatedAt())
        );
    }

    private List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.replace("，", ",").split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private String joinTopicTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "买家话题";
        }
        return tags.stream()
                .map(item -> defaultText(item, "").trim())
                .filter(item -> !item.isEmpty())
                .distinct()
                .limit(6)
                .reduce((left, right) -> left + "," + right)
                .orElse("买家话题");
    }

    private List<String> parseImages(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private String normalizeTopicAction(String actionType) {
        String value = defaultText(actionType, "").toLowerCase(Locale.ROOT);
        if ("want".equals(value) || "collect".equals(value)) {
            return value;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的操作");
    }

    private int safeScore(Integer score) {
        return score == null ? 5 : Math.max(1, Math.min(5, score));
    }

    private int normalizeScore(Integer score) {
        if (score == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评分不能为空");
        }
        return Math.max(1, Math.min(5, score));
    }

    private BigDecimal estimateUsedPrice(String category, String condition) {
        BigDecimal base = category.contains("数码") ? new BigDecimal("680") : category.contains("图书") ? new BigDecimal("28") : new BigDecimal("180");
        if (condition.contains("全新") || condition.contains("99")) {
            return base.add(new BigDecimal("120"));
        }
        if (condition.contains("8") || condition.contains("瑕疵")) {
            return base.subtract(new BigDecimal("60")).max(new BigDecimal("10"));
        }
        return base;
    }

    private BigDecimal estimateNewPrice(String category) {
        if (category.contains("数码")) {
            return new BigDecimal("699");
        }
        if (category.contains("家居")) {
            return new BigDecimal("129");
        }
        return new BigDecimal("99");
    }

    private Integer parseDbId(String id) {
        try {
            return Integer.parseInt(id);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }
    }

    private String normalizeScene(String scene) {
        return "new".equals(scene) ? "new" : "used";
    }

    private String normalizeOrderStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String value = status.trim();
        return switch (value) {
            case "待付款", "pending_pay" -> "pending_pay";
            case "待发货", "pending_ship" -> "pending_ship";
            case "待收货", "pending_receive" -> "pending_receive";
            case "已完成", "completed" -> "completed";
            case "已取消", "cancelled" -> "cancelled";
            default -> null;
        };
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "approved";
        }
        String normalized = status.trim();
        if ("0".equals(normalized)) return "approved";
        if ("2".equals(normalized)) return "pending";
        if ("3".equals(normalized)) return "rejected";
        if ("rejected".equals(normalized) || "pending".equals(normalized) || "approved".equals(normalized)) {
            return normalized;
        }
        if (normalized.startsWith("pe")) {
            return "pending";
        }
        return "approved";
    }

    private String statusTag(String status) {
        if ("pending".equals(status)) return "待审核";
        if ("rejected".equals(status)) return "已拒绝";
        return "审核通过";
    }

    private String statusSubtitle(String status, String scene, String condition) {
        String type = "new".equals(scene) ? "新品" : "二手";
        return type + " " + defaultText(condition, "待补充") + " · " + statusTag(status);
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : DISPLAY_TIME.format(time);
    }

    private String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void seed() {
        stores.add(new StoreView("store-1", "松果严选数码", "4.9", "1.2w", "新品数码与官方配件，售后响应快。", "官方严选", "/static/goods/airwave-pro.jpg"));
        stores.add(new StoreView("store-2", "南湖旧书摊", "4.8", "6.4k", "课程教材、考研资料和学长笔记流转地。", "校园认证", "/static/goods/open-book.jpg"));
        stores.add(new StoreView("store-3", "榕树下的小店", "4.7", "4.1k", "家居生活闲置为主，重视真实描述。", "信用卖家", "/static/goods/green-chair.jpg"));
        stores.add(new StoreView("store-4", "阿洛的桌面仓库", "4.9", "812", "数码桌搭和自用设备流转，支持细节沟通。", "个人卖家", "/static/goods/viewtop-monitor.jpg"));
        stores.add(new StoreView("store-5", "松果生活馆", "4.8", "2.2k", "宿舍、桌面和生活用品，兼顾新品与实用体验。", "生活严选", "/static/goods/sunrise-kettle.jpg"));

        products.add(new ProductView(
            "airwave-pro", "new", "数码影音", "AirWave Pro 降噪耳机", "全新正品 · 48 小时发货 · 支持七天无理由",
            new BigDecimal("699"), new BigDecimal("899"), "/static/goods/airwave-pro.jpg", "官方新品", "全新", 100, "上海", "松果严选数码",
                "顺丰包邮，48 小时内发货", List.of("平台担保", "七天无理由", "官方质保"),
                List.of("45dB 主动降噪", "38 小时续航", "低延迟游戏模式"),
            "适合通勤、学习和线上会议的轻量耳机，主打稳定、舒适和清晰通话。",
            List.of(new KeyValue("品牌", "AirWave"), new KeyValue("连接方式", "蓝牙 5.4"), new KeyValue("续航", "38 小时"), new KeyValue("质保", "一年官方质保")),
            List.of(new ReviewView("晨光买家", "降噪很稳，佩戴一下午也不夹耳。", "4.9", List.of("降噪好", "发货快"))),
            List.of(), List.of("可询问保修政策", "可比较同价位耳机", "可查看发票与质保"), "store-1"
        ));
        products.add(new ProductView(
            "songuo-pad", "new", "数码影音", "松果 Pad 11 学习平板", "新品首发 · 学习办公两用 · 赠保护套",
            new BigDecimal("2299"), new BigDecimal("2599"), "/static/goods/songuo-pad.jpg", "新品首发", "全新", 100, "杭州", "松果严选数码",
            "次日达覆盖核心城市", List.of("平台担保", "官方质保", "学生优惠"),
            List.of("2.5K 护眼屏", "8300mAh 电池", "手写笔低延迟"),
            "面向课程笔记、网课和轻办公场景，兼顾屏幕素质与续航。",
            List.of(new KeyValue("内存", "8GB + 256GB"), new KeyValue("屏幕", "11 英寸 2.5K"), new KeyValue("重量", "485g"), new KeyValue("网络", "Wi-Fi")),
            List.of(new ReviewView("期末冲刺中", "做笔记很顺手，续航够一天课。", "4.7", List.of("学习友好"))),
            List.of(), List.of("可生成学习设备清单", "可估算分期预算"), "store-1"
        ));
        products.add(new ProductView(
            "viewtop-monitor", "used", "数码影音", "ViewTop 27 英寸 2K 显示器", "二手 9 成新 · 无坏点 · 支持当面验货",
            new BigDecimal("680"), new BigDecimal("1099"), "/static/goods/viewtop-monitor.jpg", "同城自提", "9 成新", 97, "广州大学城", "阿洛的桌面仓库",
                "同城自提 / 到付快递", List.of("平台担保", "当面验货", "48 小时售后协商"),
                List.of("2K 分辨率", "接口齐全", "办公游戏都够用"),
            "陪前任主人完成了毕业设计和第一份实习作品集，现在桌面升级，等待下一位使用者。",
            List.of(new KeyValue("品牌", "ViewTop"), new KeyValue("分辨率", "2560 x 1440"), new KeyValue("接口", "HDMI / DP"), new KeyValue("成色", "9 成新")),
                List.of(new ReviewView("桌搭玩家", "卖家说明很细，现场验货顺利。", "4.9", List.of("描述真实"))),
            List.of(new TimelineNode("2024.09", "入手第一天", "用于设计作业和剪辑练习。"), new TimelineNode("2025.06", "完成毕业项目", "屏幕一直稳定，无亮点坏点。"), new TimelineNode("2026.05", "准备流转", "已清洁打包，支持同城验货。")),
                List.of("可帮你砍价到 620-650", "可生成验货清单"), "store-4"
        ));
        products.add(new ProductView(
            "software-book", "used", "图书文创", "软件工程导论与项目管理笔记", "二手教材 · 含重点标注 · 适合课程复习",
            new BigDecimal("18"), new BigDecimal("69"), "/static/goods/software-book.jpg", "学长笔记", "8.5 成新", 99, "武汉", "南湖旧书摊",
            "校园面交 / 普通快递", List.of("真实笔记", "可拍内页", "平台担保"),
            List.of("重点页有标记", "附课程项目清单", "适合期末复习"),
            "上一任主人用它完成了一次软工大作业，夹着需求评审清单和测试用例模板。",
            List.of(new KeyValue("版本", "第 3 版"), new KeyValue("语言", "中文"), new KeyValue("成色", "8.5 成新"), new KeyValue("附赠", "复习提纲")),
            List.of(new ReviewView("赶 ddl 的同学", "笔记很实用，重点划得很清楚。", "4.8", List.of("内容实用"))),
            List.of(new TimelineNode("2025.03", "开始软工课程", "第一章写下需求分析重点。"), new TimelineNode("2025.06", "项目答辩通过", "附带的用例模板帮了大忙。"), new TimelineNode("2026.05", "转给下一届", "希望继续发挥作用。")),
            List.of("可提取重点页", "可生成复习计划"), "store-2"
        ));
        products.add(new ProductView(
            "ergo-chair", "used", "家居生活", "人体工学椅 Pro", "二手 9 成新 · 腰托完整 · 适合宿舍/工位",
            new BigDecimal("420"), new BigDecimal("899"), "/static/goods/ergo-chair.jpg", "大件同城", "9 成新", 95, "成都", "榕树下的小店",
            "同城搬运可协商", List.of("平台担保", "线下验货", "议价空间"),
            List.of("腰托可调", "坐垫回弹正常", "无明显破损"),
            "陪伴过很多个赶项目的夜晚，椅背和扶手状态良好，适合继续服役。",
            List.of(new KeyValue("材质", "网布 + 金属脚"), new KeyValue("功能", "升降 / 后仰 / 腰托"), new KeyValue("成色", "9 成新"), new KeyValue("配送", "同城优先")),
            List.of(new ReviewView("新工位用户", "坐感不错，卖家帮忙叫了车。", "4.6", List.of("服务好"))),
            List.of(new TimelineNode("2024.11", "入驻工作室", "成为第一把正式办公椅。"), new TimelineNode("2025.12", "陪伴项目冲刺", "坐垫和腰托依旧稳定。"), new TimelineNode("2026.05", "搬家出闲置", "同城优先，欢迎试坐。")),
            List.of("可协商同城运费", "可询问坐垫塌陷情况"), "store-3"
        ));
        products.add(new ProductView(
            "desk-lamp", "new", "家居生活", "折叠护眼台灯", "新品 · 宿舍桌面友好 · 三档色温",
            new BigDecimal("89"), new BigDecimal("129"), "/static/goods/desk-lamp.jpg", "宿舍好物", "全新", 100, "深圳", "松果生活馆",
            "满 59 包邮", List.of("七天无理由", "一年质保", "平台担保"),
            List.of("无频闪", "USB-C 供电", "可折叠收纳"),
            "为宿舍、书桌和夜间阅读设计的小型台灯，亮度柔和，收纳方便。",
            List.of(new KeyValue("供电", "USB-C"), new KeyValue("光源", "LED"), new KeyValue("色温", "三档调节"), new KeyValue("功率", "8W")),
            List.of(new ReviewView("夜读党", "光线柔和，不占桌面。", "4.7", List.of("护眼"))),
            List.of(), List.of("可推荐宿舍桌搭组合", "可计算顺手买优惠"), "store-5"
        ));
    }
}
