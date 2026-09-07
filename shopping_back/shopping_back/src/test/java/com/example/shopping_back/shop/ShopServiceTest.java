package com.example.shopping_back.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shopping_back.auth.dto.AuthUserView;
import com.example.shopping_back.shop.ShopDtos.AuditRequest;
import com.example.shopping_back.shop.ShopDtos.AuditResult;
import com.example.shopping_back.shop.ShopDtos.ProductView;
import com.example.shopping_back.shop.ShopDtos.StoreView;
import com.example.shopping_back.shop.ShopDtos.TopicPostView;
import com.example.shopping_back.shop.ShopDtos.TopicView;
import com.example.shopping_back.shop.mapper.ShopOrderMapper;
import com.example.shopping_back.shop.mapper.ShopProductMapper;
import com.example.shopping_back.shop.mapper.ShopStoreMapper;
import com.example.shopping_back.shop.mapper.ShopTopicMapper;
import com.example.shopping_back.shop.model.ProductRecord;
import com.example.shopping_back.shop.model.StoreRecord;
import com.example.shopping_back.shop.model.TopicPostRecord;
import com.example.shopping_back.shop.model.TopicRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock
    private ShopProductMapper productMapper;

    @Mock
    private ShopStoreMapper storeMapper;

    @Mock
    private ShopOrderMapper orderMapper;

    @Mock
    private ShopTopicMapper topicMapper;

    private ShopService shopService;
    private ProductRecord validProduct;

    @BeforeEach
    void setUp() {
        lenient().when(productMapper.countGoodsColumn(anyString())).thenReturn(1);
        lenient().when(productMapper.statusColumnLength()).thenReturn(20);
        lenient().when(storeMapper.countStoreColumn(anyString())).thenReturn(1);
        lenient().when(storeMapper.countFollowUniqueIndex()).thenReturn(1);
        lenient().when(topicMapper.countPostColumn(anyString())).thenReturn(1);

        shopService = new ShopService(
                productMapper,
                storeMapper,
                orderMapper,
                topicMapper,
                new ObjectMapper(),
                "",
                "gpt-4.1-mini",
                "https://api.openai.com/v1/responses",
                "",
                "qwen-plus",
                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        );

        validProduct = createValidProductRecord();

        StoreRecord mockStore = new StoreRecord();
        mockStore.setStoreId(1);
        mockStore.setSellerId(100);
        mockStore.setStoreName("测试店铺");
        lenient().when(storeMapper.selectBySeller(100)).thenReturn(mockStore);
        lenient().when(storeMapper.followerCount(1)).thenReturn(0);
        lenient().when(storeMapper.selectById(1)).thenReturn(mockStore);
    }

    @Test
    void createProductSuccess() {
        doAnswer(invocation -> {
            ProductRecord product = invocation.getArgument(0);
            product.setGoodsId(101);
            return 1;
        }).when(productMapper).insert(any(ProductRecord.class));
        when(productMapper.selectById(101)).thenReturn(validProduct);

        ShopDtos.PublishRequest request = createValidPublishRequest();
        ShopDtos.ProductView result = shopService.publish(request, sellerUser());

        assertNotNull(result);
        assertEquals("测试商品", result.title());
        verify(productMapper, times(1)).insert(any(ProductRecord.class));
    }

    @Test
    void createProductRejectsEmptyTitle() {
        ShopDtos.PublishRequest request = new ShopDtos.PublishRequest(
                "new",
                "",
                "/images/test.jpg",
                "数码",
                BigDecimal.valueOf(399),
                "9成新",
                "测试描述",
                "测试故事",
                BigDecimal.valueOf(320),
                "北京"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shopService.publish(request,sellerUser())
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void createProductRejectsNonPositivePrice() {
        ShopDtos.PublishRequest request = new ShopDtos.PublishRequest(
                "new",
                "测试商品",
                "/images/test.jpg",
                "数码",
                BigDecimal.ZERO,
                "9成新",
                "测试描述",
                "测试故事",
                BigDecimal.ZERO,
                "北京"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shopService.publish(request, sellerUser())
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void getProductListReturnsList() {
        when(productMapper.selectApproved("all", "")).thenReturn(List.of(validProduct));

        List<ShopDtos.ProductView> result = shopService.products("all", "");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("测试商品", result.get(0).title());
    }

    @Test
    void searchProductsWithKeyword() {
        when(productMapper.selectApproved("all", "测试")).thenReturn(List.of(validProduct));

        List<ShopDtos.ProductView> result = shopService.products("all", "测试");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("测试商品", result.get(0).title());
    }

    @Test
    void searchProductsWithNoResult() {
        when(productMapper.selectApproved("all", "暂无结果")).thenReturn(List.of());

        List<ShopDtos.ProductView> result = shopService.products("all", "暂无结果");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getProductDetailWhenExists() {
        when(productMapper.selectById(101)).thenReturn(validProduct);

        ShopDtos.ProductView result = shopService.product("101");

        assertNotNull(result);
        assertEquals("101", result.id());
        assertEquals("测试商品", result.title());
    }

    @Test
    void getProductDetailWhenNotExists() {
        when(productMapper.selectById(404)).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shopService.product("404")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    // ==================== 店铺模块测试（TC-STORE-01 ~ 04） ====================

    @Test
    // TC-STORE-01 查看店铺信息
    void getStoreInfoSuccess() {
        StoreRecord store = new StoreRecord();
        store.setStoreId(1);
        store.setSellerId(10);
        store.setSellerName("卖家A");
        store.setStoreName("测试店铺");
        store.setStoreDesc("这是一个测试店铺");
        store.setStatus("normal");
        when(storeMapper.selectById(1)).thenReturn(store);
        when(storeMapper.followerCount(1)).thenReturn(12);

        ShopDtos.StoreDetailView result = shopService.store("1", null);

        assertNotNull(result);
        assertEquals("测试店铺", result.name());
        assertEquals("这是一个测试店铺", result.desc());
    }

    @Test
    // TC-STORE-02 关注店铺
    void followStoreSuccess() {
        StoreRecord store = new StoreRecord();
        store.setStoreId(1);
        store.setSellerId(10);
        store.setSellerName("卖家A");
        store.setStoreName("测试店铺");
        store.setStoreDesc("这是一个测试店铺");
        store.setStatus("normal");
        when(storeMapper.selectById(1)).thenReturn(store);
        when(storeMapper.follow(100, 1, "测试店铺")).thenReturn(1);
        when(storeMapper.followerCount(1)).thenReturn(13);
        when(storeMapper.isFollowed(100, 1)).thenReturn(1);

        AuthUserView user = new AuthUserView(100, "alice", "138****8000", 100, "buyer", "买家", false, "normal", "");
        ShopDtos.StoreDetailView result = shopService.followStore("1", user);

        assertNotNull(result);
        assertEquals("测试店铺", result.name());
        verify(storeMapper, times(1)).follow(100, 1, "测试店铺");
    }

    @Test
    // TC-STORE-03 取消关注店铺
    void unfollowStoreSuccess() {
        StoreRecord store = new StoreRecord();
        store.setStoreId(1);
        store.setSellerId(10);
        store.setSellerName("卖家A");
        store.setStoreName("测试店铺");
        store.setStoreDesc("这是一个测试店铺");
        store.setStatus("normal");
        when(storeMapper.selectById(1)).thenReturn(store);
        when(storeMapper.unfollow(100, 1)).thenReturn(1);
        when(storeMapper.followerCount(1)).thenReturn(11);
        when(storeMapper.isFollowed(100, 1)).thenReturn(0);

        AuthUserView user = new AuthUserView(100, "alice", "138****8000", 100, "buyer", "买家", false, "normal", "");
        ShopDtos.StoreDetailView result = shopService.unfollowStore("1", user);

        assertNotNull(result);
        assertEquals("测试店铺", result.name());
        verify(storeMapper, times(1)).unfollow(100, 1);
    }

    @Test
    // TC-STORE-04 查看店铺商品列表
    void getStoreProductsSuccess() {
        StoreRecord store = new StoreRecord();
        store.setStoreId(1);
        store.setSellerId(10);
        store.setSellerName("卖家A");
        store.setStoreName("测试店铺");
        store.setStatus("normal");
        when(storeMapper.selectById(1)).thenReturn(store);
        when(productMapper.selectBySeller(10)).thenReturn(List.of(validProduct));

        List<ShopDtos.ProductView> result = shopService.storeProducts("1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("测试商品", result.get(0).title());
    }

    // ==================== 话题社区模块测试（TC-TOPIC-01 ~ 08） ====================

    @Test
    void listTopicsReturnsMatchingTopics() {
        TopicRecord topic = new TopicRecord();
        topic.setTopicId(1);
        topic.setTitle("校园二手交易");
        topic.setTopicDesc("新品与二手闲聊");
        topic.setType("买家话题");
        topic.setStatus("normal");
        when(topicMapper.selectTopics("all", "校园")).thenReturn(List.of(topic));

        List<ShopDtos.TopicView> result = shopService.topics("all", "校园");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("校园二手交易", result.get(0).title());
    }

    @Test
    void createTopicRequiresBuyerRole() {
        AuthUserView seller = new AuthUserView(10, "seller", "138****8000", 100, "seller", "卖家", false, "normal", "");
        ShopDtos.TopicCreateRequest request = new ShopDtos.TopicCreateRequest("校园二手交易", "一起交流", "买家话题", "", List.of("二手"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shopService.createTopic(request, seller)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void createTopicPostSuccess() {
        AuthUserView buyer = new AuthUserView(100, "alice", "138****8000", 100, "buyer", "买家", false, "normal", "");
        TopicRecord topic = new TopicRecord();
        topic.setTopicId(1);
        topic.setTitle("校园二手交易");
        topic.setStatus("normal");
        when(topicMapper.selectTopic(1, 100)).thenReturn(topic);

        TopicPostRecord post = new TopicPostRecord();
        post.setPostId(5);
        post.setTopicId(1);
        post.setUsername("alice");
        post.setContent("这件商品很适合在宿舍用");
        post.setCreatedAt(LocalDateTime.now());
        when(topicMapper.selectPosts(1, 100)).thenReturn(List.of(post));

        List<ShopDtos.TopicPostView> result = shopService.createTopicPost(
                "1",
                new ShopDtos.TopicPostRequest("这件商品很适合在宿舍用", List.of(), "", ""),
                buyer
        );

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("这件商品很适合在宿舍用", result.get(0).content());
    }

    @Test
    void createTopicCommentSuccess() {
        AuthUserView buyer = new AuthUserView(100, "alice", "138****8000", 100, "buyer", "买家", false, "normal", "");
        when(topicMapper.topicIdByPost(5)).thenReturn(1);
        when(topicMapper.selectPosts(1, 100)).thenReturn(List.of(topicPostRecord(5, 1, "评论测试")));

        List<ShopDtos.TopicPostView> result = shopService.createTopicComment(
                "5",
                new ShopDtos.TopicCommentRequest("这评论也很有意思"),
                buyer
        );

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void toggleTopicPostLikeSuccess() {
        AuthUserView buyer = new AuthUserView(100, "alice", "138****8000", 100, "buyer", "买家", false, "normal", "");
        when(topicMapper.topicIdByPost(5)).thenReturn(1);
        when(topicMapper.likeExists(5, 100)).thenReturn(0);
        when(topicMapper.insertLike(5, 100)).thenReturn(1);
        when(topicMapper.selectPosts(1, 100)).thenReturn(List.of(topicPostRecord(5, 1, "点赞测试")));

        List<ShopDtos.TopicPostView> result = shopService.toggleTopicPostLike("5", buyer);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(topicMapper).insertLike(5, 100);
    }

    @Test
    void followAndUnfollowTopicSuccess() {
        AuthUserView buyer = new AuthUserView(100, "alice", "138****8000", 100, "buyer", "买家", false, "normal", "");
        TopicRecord topic = new TopicRecord();
        topic.setTopicId(1);
        topic.setTitle("校园二手交易");
        topic.setTopicDesc("聊二手");
        topic.setType("买家话题");
        topic.setStatus("normal");
        when(topicMapper.selectTopic(1, 100)).thenReturn(topic);
        when(topicMapper.insertTopicFollow(100, 1, "校园二手交易")).thenReturn(1);
        when(topicMapper.deleteTopicFollow(1, 100)).thenReturn(1);

        ShopDtos.TopicView followed = shopService.followTopic("1", buyer);
        assertEquals("校园二手交易", followed.title());

        ShopDtos.TopicView unfollowed = shopService.unfollowTopic("1", buyer);
        assertEquals("校园二手交易", unfollowed.title());
    }

    // ==================== 话题社区模块补充测试 ====================

    @Test
    // TC-TOPIC-02 查看话题详情
    void topicDetailReturnsTopic() {
        TopicRecord topic = new TopicRecord();
        topic.setTopicId(1);
        topic.setTitle("校园二手交易");
        topic.setTopicDesc("聊二手");
        topic.setStatus("normal");
        when(topicMapper.selectTopic(1, null)).thenReturn(topic);
        TopicView result = shopService.topic("1", null);
        assertNotNull(result);
        assertEquals("校园二手交易", result.title());
    }

    @Test
    // 查看话题帖子列表
    void topicPostsReturnsPosts() {
        when(topicMapper.selectPosts(1, null)).thenReturn(List.of(topicPostRecord(5, 1, "帖子内容")));
        List<TopicPostView> result = shopService.topicPosts("1", null);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    private TopicPostRecord topicPostRecord(Integer postId, Integer topicId, String content) {
        TopicPostRecord record = new TopicPostRecord();
        record.setPostId(postId);
        record.setTopicId(topicId);
        record.setUsername("alice");
        record.setContent(content);
        record.setCreatedAt(LocalDateTime.now());
        record.setLikeCount(0);
        record.setCommentCount(0);
        return record;
    }

    private AuthUserView buyerUser() {
        return new AuthUserView(100, "alice", "138****8000", 100, "buyer", "买家", false, "normal", "");
    }

    private AuthUserView sellerUser() {
        return new AuthUserView(100, "seller", "138****8000", 100, "seller", "卖家", false, "normal", "");
    }

    private ShopDtos.PublishRequest createValidPublishRequest() {
        return new ShopDtos.PublishRequest(
                "new",
                "测试商品",
                "/images/test.jpg",
                "数码",
                BigDecimal.valueOf(399),
                "9成新",
                "测试描述",
                "测试故事",
                BigDecimal.valueOf(320),
                "北京"
        );
    }

    private ProductRecord createValidProductRecord() {
        ProductRecord product = new ProductRecord();
        product.setGoodsId(101);
        product.setSellerId(null);
        product.setSellerName("卖家A");
        product.setSellerCredit(96);
        product.setGoodsName("测试商品");
        product.setCategory("数码");
        product.setGoodsDesc("这是测试用商品描述");
        product.setGoodsCondition("9成新");
        product.setStory("使用很少，状态良好");
        product.setPrice(BigDecimal.valueOf(399));
        product.setFloorPrice(BigDecimal.valueOf(320));
        product.setScene("new");
        product.setAddress("北京");
        product.setImage("/images/test.jpg");
        product.setStatus("approved");
        product.setCreateTime(LocalDateTime.now());
        return product;
    }

    // ==================== 用户中心模块测试（TC-USER-07） ====================

    @Test
    // TC-USER-07 查看我发布的商品
    void myProductsReturnsListForLoggedInUser() {
        // 1. 准备一个商品数据（已审核通过）
        ProductRecord product = new ProductRecord();
        product.setGoodsId(101);
        product.setSellerId(100);
        product.setGoodsName("我的测试商品");
        product.setCategory("数码");
        product.setGoodsDesc("这是我发布的测试商品");
        product.setGoodsCondition("全新");
        product.setPrice(BigDecimal.valueOf(399));
        product.setScene("new");
        product.setAddress("北京");
        product.setImage("/images/test.jpg");
        product.setStatus("approved");
        product.setCreateTime(LocalDateTime.now());

        // 2. Mock Mapper 行为：返回包含该商品的列表
        when(productMapper.selectBySeller(100)).thenReturn(List.of(product));

        // 3. 执行查询（使用 buyerUser() 返回 userId=100 的用户）
        List<ProductView> result = shopService.myProducts(buyerUser());

        // 4. 断言
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("我的测试商品", result.get(0).title());
        assertEquals("approved", result.get(0).status());
        assertEquals("new", result.get(0).scene());
        assertEquals(BigDecimal.valueOf(399), result.get(0).price());
    }

    // ==================== 管理审核模块测试（TC-ADMIN-01 ~ 03） ====================
    @Test
    // TC-ADMIN-01 查看待审核商品列表
    void pendingProductsReturnsList() {
        when(productMapper.selectPending()).thenReturn(List.of(validProduct));
        List<ProductView> result = shopService.pendingProducts();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("测试商品", result.get(0).title());
    }

    @Test
    // TC-ADMIN-02 审核通过
    void auditApproveSuccess() {
        String productId = "101";
        when(productMapper.updateAuditStatus(101, "approved", "")).thenReturn(1);
        AuditResult result = shopService.audit(productId, new AuditRequest("approve", ""));
        assertNotNull(result);
        assertEquals("approved", result.status());
    }

    @Test
    // TC-ADMIN-03 审核拒绝
    void auditRejectSuccess() {
        String productId = "101";
        String reason = "图片模糊";
        when(productMapper.updateAuditStatus(101, "rejected", reason)).thenReturn(1);
        AuditResult result = shopService.audit(productId, new AuditRequest("reject", reason));
        assertNotNull(result);
        assertEquals("rejected", result.status());
        assertEquals(reason, result.rejectReason());
    }
        // ==================== AI 议价/建议测试（TC-CHAT-08 ~ 09） ====================

    @Test
    // TC-CHAT-08 AI议价-启用（对应 ShopService.assist）
    void assistReturnsAiResponseForProduct() {
        // 1. 准备一个有效商品
        when(productMapper.selectById(101)).thenReturn(validProduct);

        // 2. 构造请求
        ShopDtos.AiAssistRequest request = new ShopDtos.AiAssistRequest("101", "这个价格还能再便宜吗？", BigDecimal.valueOf(300));

        // 3. 执行方法
        ShopDtos.AiAssistResponse response = shopService.assist(request);

        // 4. 断言
        assertNotNull(response);
        assertNotNull(response.answer());
        assertTrue(response.answer().contains("测试商品")); // 应包含商品名
        assertNotNull(response.checklist());
        assertFalse(response.checklist().isEmpty());
        assertNotNull(response.consensus());
    }

    @Test
    // TC-CHAT-09 AI议价-建议生成（对应 ShopService.suggestPublish）
    void suggestPublishReturnsMockSuggestionWhenNoApiKey() {
        // 注意：当前配置的 API Key 为空，所以会走 mock 分支
        ShopDtos.AiPublishSuggestionRequest request = new ShopDtos.AiPublishSuggestionRequest(
                "used",      // scene
                "数码",      // category
                "9成新",     // condition
                "耳机"       // keyword
        );

        ShopDtos.AiPublishSuggestionResponse response = shopService.suggestPublish(request);

        assertNotNull(response);
        assertNotNull(response.title());
        assertTrue(response.title().contains("二手") || response.title().contains("耳机"));
        assertNotNull(response.price());
        assertTrue(response.price().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(response.description());
        assertNotNull(response.story());
        assertEquals("mock_missing_key", response.source()); // 确认走的是 mock 分支
    }
    // ==================== 补充覆盖率测试（提升 shop 模块覆盖率） ====================

    @Test
    // 场景：productMapper 抛异常时，products 方法应优雅降级返回空列表
    void products_handlesMapperExceptionGracefully() {
        when(productMapper.selectApproved("all", ""))
            .thenThrow(new RuntimeException("DB connection error"));
        
        List<ShopDtos.ProductView> result = shopService.products("all", "");
        
        assertNotNull(result);
        assertTrue(result.isEmpty(), "Mapper异常时应返回空列表，而非抛出异常");
    }

    @Test
    // 场景：发布二手商品但未填成色 -> 应返回 400
    void createProductRejectsUsedSceneWithoutCondition() {
        ShopDtos.PublishRequest request = new ShopDtos.PublishRequest(
                "used",                     // scene
                "二手耳机",                  // title
                "/img/headphone.jpg",       // image
                "数码",                     // category
                BigDecimal.valueOf(200),    // price
                "",                         // condition (空)
                "功能完好",                 // description
                "自用转让",                 // story
                BigDecimal.valueOf(180),    // floorPrice
                "北京"                      // location
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shopService.publish(request, sellerUser())
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    // 场景：发布二手商品但未填故事 -> 应返回 400
    void createProductRejectsUsedSceneWithoutStory() {
        ShopDtos.PublishRequest request = new ShopDtos.PublishRequest(
                "used",                     // scene
                "二手耳机",                  // title
                "/img/headphone.jpg",       // image
                "数码",                     // category
                BigDecimal.valueOf(200),    // price
                "9成新",                    // condition (有)
                "功能完好",                 // description
                "",                         // story (空)
                BigDecimal.valueOf(180),    // floorPrice
                "北京"                      // location
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shopService.publish(request, sellerUser())
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    // 场景：未登录用户关注店铺 -> 应返回 401
    void followStore_throwsWhenUserNull() {
        assertThrows(ResponseStatusException.class, () -> shopService.followStore("1", null));
    }

    @Test
    // 场景：关注不存在的店铺 -> 应返回 404
    void followStore_throwsWhenStoreNotFound() {
        when(storeMapper.selectById(999)).thenReturn(null);
        
        AuthUserView user = buyerUser();
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shopService.followStore("999", user)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    // 场景：未登录用户取消关注店铺 -> 应返回 401
    void unfollowStore_throwsWhenUserNull() {
        assertThrows(ResponseStatusException.class, () -> shopService.unfollowStore("1", null));
    }

    // ==================== 编辑商品测试（TC-PROD-11 / BUG-UNIT-003） ====================

    @Test
    // TC-PROD-11 编辑商品成功
    void updateProductSuccess() {
        AuthUserView seller = new AuthUserView(100, "seller", "138****8000", 100, "seller", "卖家", false, "normal", "");

        ProductRecord product = new ProductRecord();
        product.setGoodsId(101);
        product.setSellerId(100);
        product.setGoodsName("测试商品");
        product.setCategory("数码");
        product.setGoodsDesc("描述");
        product.setGoodsCondition("9成新");
        product.setStory("故事");
        product.setPrice(BigDecimal.valueOf(399));
        product.setFloorPrice(BigDecimal.valueOf(320));
        product.setScene("new");
        product.setAddress("北京");
        product.setImage("/images/test.jpg");
        product.setStatus("approved");
        product.setCreateTime(LocalDateTime.now());

        when(productMapper.selectById(101)).thenReturn(product);
        when(productMapper.update(any(ProductRecord.class))).thenReturn(1);

        ProductRecord updated = product;
        updated.setGoodsName("更新后的标题");
        when(productMapper.selectById(101)).thenReturn(updated);

        ShopDtos.UpdateProductRequest request = new ShopDtos.UpdateProductRequest(
                "更新后的标题",
                "数码",
                BigDecimal.valueOf(599),
                "/images/updated.jpg",
                "9成新",
                "更新后的描述",
                "更新后的故事",
                BigDecimal.valueOf(500),
                "北京"
        );

        ShopDtos.ProductView result = shopService.updateProduct("101", request, seller);
        assertNotNull(result);
        assertEquals("更新后的标题", result.title());
        verify(productMapper, times(1)).update(any(ProductRecord.class));
    }



        // ==================== 补充覆盖率测试（shop 模块） ====================

    @Test
    // stores() 异常降级：Mapper 抛异常时返回内存列表
    void stores_handlesMapperExceptionGracefully() {
        when(storeMapper.selectNormalStores()).thenThrow(new RuntimeException("DB error"));
        List<StoreView> result = shopService.stores();
        assertNotNull(result);
        assertFalse(result.isEmpty()); // 内存列表有预设店铺
    }

    @Test
    // myStore() 正常返回店铺详情
    void myStore_returnsStoreDetail() {
        AuthUserView seller = sellerUser(); // userId=10
        // 已在 setUp 中 mock storeMapper.selectBySeller(10) 返回店铺
        ShopDtos.StoreDetailView result = shopService.myStore(seller);
        assertNotNull(result);
        assertEquals("测试店铺", result.name());
        assertEquals(100, result.sellerId());
    }

    @Test
    // updateMyStore() 正常更新店铺
    void updateMyStore_success() {
        AuthUserView seller = sellerUser();
        ShopDtos.StoreUpdateRequest request = new ShopDtos.StoreUpdateRequest(
                "新店铺名", "新简介", "新徽章", List.of("服务1", "服务2")
        );
        when(storeMapper.updateSellerStore(anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1);
        // mock myStore 中调用的方法
        when(storeMapper.followerCount(anyInt())).thenReturn(0);
        ShopDtos.StoreDetailView result = shopService.updateMyStore(request, seller);
        assertNotNull(result);
        verify(storeMapper, times(1)).updateSellerStore(anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    // updateMyStore() 未登录抛出 401
    void updateMyStore_rejectsNullUser() {
        ShopDtos.StoreUpdateRequest request = new ShopDtos.StoreUpdateRequest("name", "desc", "badge", List.of());
        assertThrows(ResponseStatusException.class, () -> shopService.updateMyStore(request, null));
    }

    @Test
    // store() 当用户已登录时返回 followed 状态
    void store_withFollowedStatus() {
        StoreRecord store = new StoreRecord();
        store.setStoreId(1);
        store.setSellerId(10);
        store.setStoreName("测试店铺");
        store.setStoreDesc("描述");
        store.setStatus("normal");
        when(storeMapper.selectById(1)).thenReturn(store);
        when(storeMapper.isFollowed(100, 1)).thenReturn(1); // 用户已关注
        when(storeMapper.followerCount(1)).thenReturn(10);
        // storeProducts 的 mock
        when(productMapper.selectBySeller(10)).thenReturn(List.of(validProduct));

        AuthUserView buyer = buyerUser(); // userId=100
        ShopDtos.StoreDetailView result = shopService.store("1", buyer);
        assertNotNull(result);
        assertTrue(result.followed());
    }

    @Test
    // topics() 异常降级
    void topics_handlesMapperExceptionGracefully() {
        when(topicMapper.selectTopics(anyString(), anyString())).thenThrow(new RuntimeException("DB error"));
        List<TopicView> result = shopService.topics("all", "");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    // topic() 话题不存在抛 404
    void topic_notFound() {
        when(topicMapper.selectTopic(anyInt(), isNull())).thenReturn(null);
        assertThrows(ResponseStatusException.class, () -> shopService.topic("999", null));
    }

    @Test
    // createTopic() 标题为空抛 400
    void createTopic_rejectsEmptyTitle() {
        AuthUserView buyer = buyerUser();
        ShopDtos.TopicCreateRequest request = new ShopDtos.TopicCreateRequest("", "desc", "type", "cover", List.of());
        assertThrows(ResponseStatusException.class, () -> shopService.createTopic(request, buyer));
    }

    @Test
    // createTopic() 描述为空抛 400
    void createTopic_rejectsEmptyDesc() {
        AuthUserView buyer = buyerUser();
        ShopDtos.TopicCreateRequest request = new ShopDtos.TopicCreateRequest("title", "", "type", "cover", List.of());
        assertThrows(ResponseStatusException.class, () -> shopService.createTopic(request, buyer));
    }

    @Test
    // createTopicPost() 内容为空抛 400
    void createTopicPost_rejectsEmptyContent() {
        AuthUserView buyer = buyerUser();
        // mock 话题存在
        TopicRecord topic = new TopicRecord();
        topic.setTopicId(1);
        topic.setStatus("normal");
        when(topicMapper.selectTopic(1, 100)).thenReturn(topic);

        ShopDtos.TopicPostRequest request = new ShopDtos.TopicPostRequest("", List.of(), "", "");
        assertThrows(ResponseStatusException.class, () -> shopService.createTopicPost("1", request, buyer));
    }

    @Test
    // createTopicPost() 话题不存在抛 404
    void createTopicPost_rejectsTopicNotFound() {
        AuthUserView buyer = buyerUser();
        when(topicMapper.selectTopic(anyInt(), anyInt())).thenReturn(null);
        ShopDtos.TopicPostRequest request = new ShopDtos.TopicPostRequest("内容", List.of(), "", "");
        assertThrows(ResponseStatusException.class, () -> shopService.createTopicPost("999", request, buyer));
    }

    @Test
    // createTopicComment() 评论内容为空抛 400
    void createTopicComment_rejectsEmptyContent() {
        AuthUserView buyer = buyerUser();
        when(topicMapper.topicIdByPost(1)).thenReturn(1);
        ShopDtos.TopicCommentRequest request = new ShopDtos.TopicCommentRequest("");
        assertThrows(ResponseStatusException.class, () -> shopService.createTopicComment("1", request, buyer));
    }

    @Test
    // createTopicComment() 帖子不存在抛 404
    void createTopicComment_rejectsPostNotFound() {
        AuthUserView buyer = buyerUser();
        when(topicMapper.topicIdByPost(999)).thenReturn(null);
        ShopDtos.TopicCommentRequest request = new ShopDtos.TopicCommentRequest("评论");
        assertThrows(ResponseStatusException.class, () -> shopService.createTopicComment("999", request, buyer));
    }

    @Test
    // toggleTopicPostAction() 想要/收藏动作
    void toggleTopicPostAction_wantAndCollect() {
        AuthUserView buyer = buyerUser();
        when(topicMapper.topicIdByPost(1)).thenReturn(1);
        // 第一次：添加 want
        when(topicMapper.actionExists(1, 100, "want")).thenReturn(0);
        when(topicMapper.insertAction(1, 100, "want")).thenReturn(1);
        when(topicMapper.selectPosts(1, 100)).thenReturn(List.of(createTopicPostRecord(1, "内容")));
        List<TopicPostView> result1 = shopService.toggleTopicPostAction("1", "want", buyer);
        assertNotNull(result1);

        // 第二次：取消 want
        when(topicMapper.actionExists(1, 100, "want")).thenReturn(1);
        when(topicMapper.deleteAction(1, 100, "want")).thenReturn(1);
        when(topicMapper.selectPosts(1, 100)).thenReturn(List.of(createTopicPostRecord(1, "内容")));
        List<TopicPostView> result2 = shopService.toggleTopicPostAction("1", "want", buyer);
        assertNotNull(result2);

        // 收藏同理
        when(topicMapper.actionExists(1, 100, "collect")).thenReturn(0);
        when(topicMapper.insertAction(1, 100, "collect")).thenReturn(1);
        when(topicMapper.selectPosts(1, 100)).thenReturn(List.of(createTopicPostRecord(1, "内容")));
        List<TopicPostView> result3 = shopService.toggleTopicPostAction("1", "collect", buyer);
        assertNotNull(result3);
    }

    @Test
    // toggleTopicPostLike() 取消点赞（点赞已存在）
    void toggleTopicPostLike_cancelLike() {
        AuthUserView buyer = buyerUser();
        when(topicMapper.topicIdByPost(1)).thenReturn(1);
        // likeExists 返回 1，表示已点赞，执行删除
        when(topicMapper.likeExists(1, 100)).thenReturn(1);
        when(topicMapper.deleteLike(1, 100)).thenReturn(1);
        when(topicMapper.selectPosts(1, 100)).thenReturn(List.of(createTopicPostRecord(1, "内容")));
        List<TopicPostView> result = shopService.toggleTopicPostLike("1", buyer);
        assertNotNull(result);
        verify(topicMapper, times(1)).deleteLike(1, 100);
    }

    @Test
    // publish() 未登录抛 401
    void publish_rejectsUnauthenticated() {
        ShopDtos.PublishRequest request = createValidPublishRequest();
        // ensureSellerStore 中 user 为 null 会抛 401
        assertThrows(ResponseStatusException.class, () -> shopService.publish(request, null));
    }

    @Test
    // updateProduct() 非卖家抛 403
    void updateProduct_rejectsNonSeller() {
        ProductRecord product = new ProductRecord();
        product.setGoodsId(101);
        product.setSellerId(100);
        ShopDtos.UpdateProductRequest request = new ShopDtos.UpdateProductRequest("title", "category", BigDecimal.TEN, "", "", "", "", null, "");
        AuthUserView buyer = buyerUser(); // role = buyer
        assertThrows(ResponseStatusException.class, () -> shopService.updateProduct("101", request, buyer));
    }

    @Test
    // updateProduct() 非商品所有者抛 403
    void updateProduct_rejectsNotOwner() {
        ProductRecord product = new ProductRecord();
        product.setGoodsId(101);
        product.setSellerId(200); // 不是当前用户
        when(productMapper.selectById(101)).thenReturn(product);
        ShopDtos.UpdateProductRequest request = new ShopDtos.UpdateProductRequest("title", "category", BigDecimal.TEN, "", "", "", "", null, "");
        AuthUserView seller = sellerUser(); // userId=10
        assertThrows(ResponseStatusException.class, () -> shopService.updateProduct("101", request, seller));
    }

    @Test
    // updateProduct() 商品不存在抛 404
    void updateProduct_rejectsProductNotFound() {
        when(productMapper.selectById(999)).thenReturn(null);
        ShopDtos.UpdateProductRequest request = new ShopDtos.UpdateProductRequest("title", "category", BigDecimal.TEN, "", "", "", "", null, "");
        AuthUserView seller = sellerUser();
        assertThrows(ResponseStatusException.class, () -> shopService.updateProduct("999", request, seller));
    }

    @Test
    // updateProduct() 价格<=0 抛 400
    void updateProduct_rejectsNonPositivePrice() {
        ProductRecord product = new ProductRecord();
        product.setGoodsId(101);
        product.setSellerId(10);
        product.setPrice(BigDecimal.TEN);
        when(productMapper.selectById(101)).thenReturn(product);
        ShopDtos.UpdateProductRequest request = new ShopDtos.UpdateProductRequest("title", "category", BigDecimal.ZERO, "", "", "", "", null, "");
        AuthUserView seller = sellerUser();
        assertThrows(ResponseStatusException.class, () -> shopService.updateProduct("101", request, seller));
    }

    @Test
    // audit() 无效 action 抛 400
    void audit_rejectsInvalidAction() {
        ShopDtos.AuditRequest request = new ShopDtos.AuditRequest("invalid", "");
        assertThrows(ResponseStatusException.class, () -> shopService.audit("1", request));
    }

    @Test
    // audit() 商品不存在抛 404
    void audit_rejectsNotFound() {
        // 让 productMapper.updateAuditStatus 返回 0，且内存列表中没有该商品
        when(productMapper.updateAuditStatus(anyInt(), anyString(), anyString())).thenReturn(0);
        ShopDtos.AuditRequest request = new ShopDtos.AuditRequest("approve", "");
        assertThrows(ResponseStatusException.class, () -> shopService.audit("999", request));
    }

    @Test
    // assist() 商品不存在抛 404
    void assist_rejectsProductNotFound() {
        when(productMapper.selectById(anyInt())).thenReturn(null);
        ShopDtos.AiAssistRequest request = new ShopDtos.AiAssistRequest("999", "question", BigDecimal.TEN);
        assertThrows(ResponseStatusException.class, () -> shopService.assist(request));
    }

    // 辅助方法：创建话题帖子记录
    private TopicPostRecord createTopicPostRecord(Integer postId, String content) {
        TopicPostRecord record = new TopicPostRecord();
        record.setPostId(postId);
        record.setTopicId(1);
        record.setUsername("alice");
        record.setContent(content);
        record.setCreatedAt(LocalDateTime.now());
        record.setLikeCount(0);
        record.setCommentCount(0);
        return record;
    }
}
