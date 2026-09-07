import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const directory = path.dirname(fileURLToPath(import.meta.url));
const output = path.join(directory, "soft-shop-microservices.postman_collection.json");

const jsonHeaders = [{ key: "Content-Type", value: "application/json" }];
const auth = (token = "buyer_token") => ({ key: "Authorization", value: `Bearer {{${token}}}` });
const userId = () => ({ key: "X-User-Id", value: "{{buyer_id}}" });

function request(name, method, url, options = {}) {
  const expected = options.expected ?? [200];
  const scripts = [
    `pm.test("HTTP status is ${expected.join(" or ")}", () => pm.expect(${JSON.stringify(expected)}).to.include(pm.response.code));`,
  ];
  if (options.json !== false && !expected.includes(204)) {
    scripts.push("pm.test(\"Response is JSON\", () => pm.response.to.be.json);");
    if (options.envelope) scripts.push("pm.test(\"Response uses the API envelope\", () => { const body = pm.response.json(); pm.expect(body).to.be.an('object'); pm.expect(body.code).to.be.a('number'); });");
  }
  if (options.tests) scripts.push(...options.tests);

  const headers = [...(options.body === undefined ? [] : jsonHeaders), ...(options.headers ?? [])];
  return {
    name,
    request: {
      method,
      header: headers,
      url: { raw: url, host: [url] },
      ...(options.body === undefined ? {} : { body: { mode: "raw", raw: JSON.stringify(options.body, null, 2), options: { raw: { language: "json" } } } }),
      description: options.description ?? "",
    },
    event: [{ listen: "test", script: { type: "text/javascript", exec: scripts } }],
  };
}

const captureApiData = (variable, expression) => [
  `const body = pm.response.json(); pm.expect(body.code).to.eql(0); pm.collectionVariables.set("${variable}", ${expression});`,
];

const buyer = "{{buyer_username}}";
const seller = "{{seller_username}}";
const disposable = "{{disposable_username}}";

// API regression sells its primary product. Keep several approved products available
// for the UI suite that runs immediately afterwards in the same E2E environment.
const uiSeedItems = [1, 2, 3].flatMap((index) => [
  request(`MS-UI-FIXTURE-PUBLISH-00${index} publish available product`, "POST", "{{catalog_url}}/api/products", {
    headers: [auth("seller_token")],
    body: {
      scene: "used",
      title: `端到端可售商品${index}-{{run_id}}`,
      image: "https://example.test/e2e-fixture.png",
      category: "数码",
      price: 99 + index,
      condition: "九成新",
      description: "供后续浏览器端到端测试使用的可售商品",
      story: "UI E2E fixture",
      floorPrice: 80,
      location: "武汉",
    },
    tests: [...captureApiData(`ui_product_id_${index}`, "body.data.id")],
  }),
  request(`MS-UI-FIXTURE-APPROVE-00${index} approve available product`, "POST", `{{gateway_url}}/api/admin/audit/{{ui_product_id_${index}}}`, {
    headers: [auth("admin_token")],
    body: { action: "approve", reason: "UI E2E fixture" },
  }),
]);

const folders = [
  {
    name: "00 Gateway routing contract",
    item: [
      request("MS-GATEWAY-CATALOG-001 products route", "GET", "{{gateway_url}}/api/products", {
        tests: ["pm.expect(pm.response.json().data).to.be.an('array');"],
      }),
      request("MS-GATEWAY-TRADE-001 cart route", "GET", "{{gateway_url}}/api/cart", { expected: [401] }),
      request("MS-GATEWAY-INTERACTION-001 topics route", "GET", "{{gateway_url}}/api/topics", {
        tests: ["pm.expect(pm.response.json().data).to.be.an('array');"],
      }),
      request("MS-GATEWAY-USER-001 users route", "GET", "{{gateway_url}}/api/users/999999999", { expected: [404] }),
    ],
  },
  {
    name: "00 Health and authentication failures",
    item: [
      request("MS-HEALTH-001 interaction health", "GET", "{{interaction_url}}/api/interaction/health", {
        tests: ["pm.expect(pm.response.json().data).to.eql('interaction-service');"],
      }),
      request("MS-AUTH-NEG-001 cart requires login", "GET", "{{trade_url}}/api/cart", { expected: [401] }),
      request("MS-AUTH-NEG-002 forged token is rejected", "GET", "{{user_url}}/api/auth/me", {
        headers: [{ key: "Authorization", value: "Bearer forged-token" }], expected: [401],
      }),
      request("MS-PRODUCT-NEG-001 invalid publish body", "POST", "{{catalog_url}}/api/products", {
        headers: [auth("seller_token")], body: { title: "", price: -1 }, expected: [400],
      }),
      request("MS-TOPIC-NEG-001 topic creation requires login", "POST", "{{interaction_url}}/api/topics", {
        body: { title: "unauthorized", desc: "must fail" }, expected: [401],
      }),
    ],
  },
  {
    name: "01 User service - auth and users",
    item: [
      request("MS-AUTH-REG-001 register buyer", "POST", "{{user_url}}/api/auth/register", {
        body: { username: buyer, password: "{{test_password}}", phone: "", role: "buyer" },
        tests: [
          ...captureApiData("buyer_token", "body.data.token"),
          "pm.collectionVariables.set('buyer_id', body.data.user.userId);",
        ],
      }),
      request("MS-AUTH-REG-002 register seller", "POST", "{{user_url}}/api/auth/register", {
        body: { username: seller, password: "{{test_password}}", phone: "", role: "seller" },
        tests: [
          ...captureApiData("seller_token", "body.data.token"),
          "pm.collectionVariables.set('seller_id', body.data.user.userId);",
        ],
      }),
      request("MS-AUTH-REG-003 register disposable user", "POST", "{{user_url}}/api/auth/register", {
        body: { username: disposable, password: "{{test_password}}", phone: "", role: "buyer" },
        tests: [...captureApiData("disposable_user_id", "body.data.user.userId")],
      }),
      request("MS-AUTH-REG-NEG-001 duplicate username", "POST", "{{user_url}}/api/auth/register", {
        body: { username: buyer, password: "{{test_password}}", phone: "", role: "buyer" }, expected: [409],
      }),
      request("MS-AUTH-LOGIN-001 buyer login", "POST", "{{user_url}}/api/auth/login", {
        body: { username: "{{buyer_username}}", password: "{{test_password}}" },
        tests: [...captureApiData("buyer_token", "body.data.token")],
      }),
      request("MS-AUTH-LOGIN-002 seller login", "POST", "{{user_url}}/api/auth/login", {
        body: { username: "{{seller_username}}", password: "{{test_password}}" },
        tests: [...captureApiData("seller_token", "body.data.token")],
      }),
      request("MS-AUTH-LOGIN-003 admin login", "POST", "{{user_url}}/api/auth/login", {
        body: { username: "admin", password: "admin123" },
        tests: [...captureApiData("admin_token", "body.data.token")],
      }),
      request("MS-AUTH-LOGIN-NEG-001 wrong password", "POST", "{{user_url}}/api/auth/login", {
        body: { username: "{{buyer_username}}", password: "wrong-password" }, expected: [401],
      }),
      request("MS-AUTH-ME-001 current user", "GET", "{{user_url}}/api/auth/me", {
        headers: [auth()], tests: ["pm.expect(pm.response.json().data.userId).to.eql(Number(pm.collectionVariables.get('buyer_id')));"],
      }),
      request("MS-AUTH-PROFILE-001 update profile", "PUT", "{{user_url}}/api/auth/me/profile", {
        headers: [auth()], body: { avatarUrl: "https://example.test/avatar.png" },
        tests: ["pm.expect(pm.response.json().data.avatarUrl).to.eql('https://example.test/avatar.png');"],
      }),
      request("MS-AUTH-SEARCH-001 search users", "GET", "{{user_url}}/api/auth/search-users?keyword={{buyer_username}}", {
        tests: ["pm.expect(pm.response.json().data).to.be.an('array').that.is.not.empty;"],
      }),
      request("MS-USERS-GET-001 get user by id", "GET", "{{user_url}}/api/users/{{buyer_id}}"),
      request("MS-USERS-POST-NEG-001 duplicate basic user", "POST", "{{user_url}}/api/users", {
        body: { username: "{{buyer_username}}", password: "{{test_password}}", phone: "13800000000", role: "buyer" }, expected: [409],
      }),
      request("MS-USERS-INTERNAL-001 internal user", "GET", "{{user_url}}/internal/users/{{buyer_id}}"),
    ],
  },
  {
    name: "02 User service - address and center",
    item: [
      request("MS-ADDRESS-ADD-001 add address", "POST", "{{user_url}}/api/address", {
        headers: [auth()], body: { receiver: "API Buyer", phone: "13800138000", province: "湖北省", city: "武汉市", district: "洪山区", detail: "软件园 1 号" },
        tests: [
          "const body=pm.response.json(); pm.expect(body.code).to.eql(0); pm.expect(body.data).to.be.an('array').that.is.not.empty;",
          "pm.collectionVariables.set('address_id', body.data[body.data.length-1].addressId);",
        ],
      }),
      request("MS-ADDRESS-LIST-001 list addresses", "GET", "{{user_url}}/api/address", { headers: [auth()] }),
      request("MS-ADDRESS-UPDATE-001 update address", "PUT", "{{user_url}}/api/address/{{address_id}}", {
        headers: [auth()], body: { receiver: "API Buyer Updated", phone: "13800138000", province: "湖北省", city: "武汉市", district: "洪山区", detail: "软件园 2 号" },
      }),
      request("MS-ADDRESS-DEFAULT-001 set default address", "PUT", "{{user_url}}/api/address/{{address_id}}/default", { headers: [auth()] }),
      request("MS-ADDRESS-INTERNAL-001 internal address", "GET", "{{user_url}}/internal/users/{{buyer_id}}/addresses/{{address_id}}"),
      request("MS-CENTER-BUYER-001 buyer center", "GET", "{{user_url}}/api/center/buyer", { headers: [auth()] }),
      request("MS-CENTER-SELLER-001 seller center", "GET", "{{user_url}}/api/center/seller", { headers: [auth("seller_token")] }),
      request("MS-CENTER-ADMIN-001 admin center", "GET", "{{user_url}}/api/center/admin", { headers: [auth("admin_token")] }),
      request("MS-CENTER-BUYER-REALNAME-001 submit buyer realname", "POST", "{{user_url}}/api/center/buyer/realname", {
        headers: [auth()], body: { realName: "测试买家", idCard: "420100200001010011" },
        tests: [...captureApiData("buyer_realname_id", "body.data.realName.id")],
      }),
      request("MS-CENTER-REALNAME-APPROVE-001 approve buyer realname", "PUT", "{{user_url}}/api/center/admin/realname/{{buyer_realname_id}}/approve", { headers: [auth("admin_token")] }),
      request("MS-CENTER-BUYER-REALNAME-CANCEL-001 cancel buyer realname", "PUT", "{{user_url}}/api/center/buyer/realname/cancel", { headers: [auth()] }),
      request("MS-CENTER-SELLER-REALNAME-001 submit seller realname", "POST", "{{user_url}}/api/center/seller/realname", {
        headers: [auth("seller_token")], body: { realName: "测试卖家", idCard: "420100199901010022" },
        tests: [...captureApiData("seller_realname_id", "body.data.realName.id")],
      }),
      request("MS-CENTER-REALNAME-REJECT-001 reject seller realname", "PUT", "{{user_url}}/api/center/admin/realname/{{seller_realname_id}}/reject", {
        headers: [auth("admin_token")], body: { reason: "自动化测试驳回" },
      }),
      request("MS-CENTER-SELLER-REALNAME-CANCEL-001 cancel seller realname", "PUT", "{{user_url}}/api/center/seller/realname/cancel", { headers: [auth("seller_token")] }),
      request("MS-CENTER-ITEM-ADD-001 add buyer favorite", "POST", "{{user_url}}/api/center/buyer/items/favorite", {
        headers: [auth()], body: { itemId: "1", title: "API fixture item", storeName: "API Store" },
      }),
      request("MS-CENTER-ITEM-LIST-001 list buyer favorites", "GET", "{{user_url}}/api/center/buyer/items/favorite", { headers: [auth()] }),
      request("MS-CENTER-ITEM-CLEAR-001 clear buyer favorites", "PUT", "{{user_url}}/api/center/buyer/items/favorite/clear", { headers: [auth()] }),
      request("MS-CENTER-ADMIN-STATUS-001 disable disposable user", "PUT", "{{user_url}}/api/center/admin/users/{{disposable_user_id}}/status", {
        headers: [auth("admin_token")], body: { status: "disabled" },
      }),
      request("MS-CENTER-ADMIN-CREDIT-001 adjust buyer credit", "PUT", "{{user_url}}/api/center/admin/users/{{buyer_id}}/credit", {
        headers: [auth("admin_token")], body: { changeValue: 1, reason: "API automation" },
      }),
      request("MS-CENTER-ADMIN-DELETE-001 delete disposable user", "DELETE", "{{user_url}}/api/center/admin/users/{{disposable_user_id}}", { headers: [auth("admin_token")] }),
      request("MS-ADDRESS-DELETE-001 remove address", "DELETE", "{{user_url}}/api/address/{{address_id}}", { headers: [auth()] }),
    ],
  },
  {
    name: "03 Catalog service",
    item: [
      request("MS-PRODUCT-PUBLISH-001 publish product", "POST", "{{catalog_url}}/api/products", {
        headers: [auth("seller_token")], body: { scene: "used", title: "{{product_title}}", image: "https://example.test/product.png", category: "数码", price: 88.80, condition: "九成新", description: "微服务全量 API 自动测试商品", story: "automation", floorPrice: 70, location: "武汉" },
        tests: [
          ...captureApiData("product_id", "body.data.id"),
          "pm.collectionVariables.set('store_id', body.data.storeId);",
        ],
      }),
      request("MS-PRODUCT-AUDIT-LIST-001 admin lists pending products", "GET", "{{gateway_url}}/api/admin/audit", {
        headers: [auth("admin_token")],
      }),
      request("MS-PRODUCT-AUDIT-APPROVE-001 admin approves product", "POST", "{{gateway_url}}/api/admin/audit/{{product_id}}", {
        headers: [auth("admin_token")], body: { action: "approve", reason: "API automation approved" },
      }),
      request("MS-PRODUCT-LIST-001 list products", "GET", "{{catalog_url}}/api/products?keyword={{product_title}}"),
      request("MS-PRODUCT-MINE-001 seller products", "GET", "{{catalog_url}}/api/products/mine", { headers: [auth("seller_token")] }),
      request("MS-PRODUCT-DETAIL-001 product detail", "GET", "{{catalog_url}}/api/products/{{product_id}}"),
      request("MS-PRODUCT-UPDATE-001 update product", "PUT", "{{catalog_url}}/api/products/{{product_id}}", {
        headers: [auth("seller_token")], body: { title: "{{product_title}} updated", category: "数码", description: "updated", condition: "九成新", story: "automation", price: 89.90, floorPrice: 70, location: "武汉", image: "https://example.test/product-updated.png" },
      }),
      request("MS-PRODUCT-SNAPSHOT-001 internal product snapshot", "GET", "{{catalog_url}}/internal/products/{{product_id}}/snapshot"),
      request("MS-STORE-LIST-001 list stores", "GET", "{{catalog_url}}/api/stores"),
      request("MS-STORE-MINE-001 seller store", "GET", "{{catalog_url}}/api/stores/mine", { headers: [{ key: "X-User-Id", value: "{{seller_id}}" }] }),
      request("MS-STORE-DETAIL-001 store detail", "GET", "{{catalog_url}}/api/stores/{{store_id}}"),
      request("MS-STORE-PRODUCTS-001 store products", "GET", "{{catalog_url}}/api/stores/{{store_id}}/products"),
      request("MS-RELATION-ADD-001 catalog favorite", "POST", "{{catalog_url}}/api/center/buyer/items/favorite", {
        headers: [userId()], body: { goodsId: "{{product_id}}" }, expected: [201],
      }),
      request("MS-RELATION-LIST-001 catalog favorites", "GET", "{{catalog_url}}/api/center/buyer/items/favorite", { headers: [userId()] }),
      request("MS-RELATION-CLEAR-001 clear catalog favorites", "PUT", "{{catalog_url}}/api/center/buyer/items/favorite/clear", { headers: [userId()], expected: [204], json: false }),
    ],
  },
  {
    name: "04 Trade service",
    item: [
      request("MS-CART-ADD-001 add cart item", "POST", "{{trade_url}}/api/cart", {
        headers: [auth()], body: { goodsId: "{{product_id}}", quantity: 1 },
        tests: [
          "const body=pm.response.json(); pm.expect(body.code).to.eql(0); pm.expect(body.data).to.be.an('array').that.is.not.empty;",
          "pm.collectionVariables.set('cart_id', body.data[body.data.length-1].cartId);",
        ],
      }),
      request("MS-CART-LIST-001 list cart", "GET", "{{trade_url}}/api/cart", { headers: [auth()] }),
      request("MS-CART-UPDATE-001 update cart quantity", "PUT", "{{trade_url}}/api/cart/{{cart_id}}", { headers: [auth()], body: { quantity: 2 } }),
      request("MS-CART-SELECT-001 select cart item", "PUT", "{{trade_url}}/api/cart/{{cart_id}}/select", { headers: [auth()], body: { selected: true } }),
      request("MS-ORDER-CREATE-001 create order", "POST", "{{trade_url}}/api/orders", {
        headers: [auth()], body: { items: [{ goodsId: "{{product_id}}", quantity: 1 }] },
        tests: [
          "const body=pm.response.json(); pm.expect(body.code).to.eql(0); pm.expect(body.data).to.be.an('array').that.is.not.empty;",
          "const created=body.data.find(x => String(x.goodsId) === String(pm.collectionVariables.get('product_id'))) || body.data[0]; pm.collectionVariables.set('order_id', created.id);",
        ],
      }),
      request("MS-ORDER-LIST-001 list orders", "GET", "{{trade_url}}/api/orders", { headers: [auth()] }),
      request("MS-ORDER-DETAIL-001 order detail", "GET", "{{trade_url}}/api/orders/{{order_id}}"),
      request("MS-ORDER-INTERNAL-001 internal order", "GET", "{{trade_url}}/internal/orders/{{order_id}}"),
      request("MS-ORDER-PARTICIPANTS-001 order participants", "GET", "{{trade_url}}/internal/orders/{{order_id}}/participants"),
      request("MS-SELLER-SUMMARY-001 seller summary", "GET", "{{trade_url}}/internal/sellers/{{seller_id}}/summary"),
      request("MS-ORDER-REVIEW-001 review confirmed order", "POST", "{{trade_url}}/api/orders/{{order_id}}/review", {
        headers: [auth()], body: { productScore: 5, sellerScore: 5, content: "API automation review" },
      }),
      request("MS-ORDER-CANCEL-001 cancel order", "POST", "{{trade_url}}/api/orders/{{order_id}}/cancel", { headers: [auth()] }),
      request("MS-CART-DELETE-001 remove cart item", "DELETE", "{{trade_url}}/api/cart/{{cart_id}}", { headers: [auth()] }),
    ],
  },
  {
    name: "05 Interaction service",
    item: [
      request("MS-TOPIC-CREATE-001 create topic", "POST", "{{interaction_url}}/api/topics", {
        headers: [auth()], body: { title: "{{topic_title}}", desc: "微服务 API 自动测试话题", type: "经验", cover: "", tags: ["api", "microservice"] },
        tests: [...captureApiData("topic_id", "body.data.id")],
      }),
      request("MS-TOPIC-LIST-001 list topics", "GET", "{{interaction_url}}/api/topics?keyword={{topic_title}}"),
      request("MS-TOPIC-DETAIL-001 topic detail", "GET", "{{interaction_url}}/api/topics/{{topic_id}}", { headers: [auth()] }),
      request("MS-TOPIC-FOLLOW-001 follow topic", "POST", "{{interaction_url}}/api/topics/{{topic_id}}/follow", { headers: [auth()] }),
      request("MS-POST-CREATE-001 create post", "POST", "{{interaction_url}}/api/topics/{{topic_id}}/posts", {
        headers: [auth()], body: { content: "自动测试帖子", images: [], productId: "{{product_id}}", storeId: "{{store_id}}" },
        tests: [
          "const body=pm.response.json(); pm.expect(body.code).to.eql(0); pm.expect(body.data).to.be.an('array').that.is.not.empty;",
          "pm.collectionVariables.set('post_id', body.data[body.data.length-1].id);",
        ],
      }),
      request("MS-POST-LIST-001 list posts", "GET", "{{interaction_url}}/api/topics/{{topic_id}}/posts", { headers: [auth()] }),
      request("MS-COMMENT-CREATE-001 comment post", "POST", "{{interaction_url}}/api/topic-posts/{{post_id}}/comments", { headers: [auth()], body: { content: "自动测试评论" } }),
      request("MS-POST-LIKE-001 toggle like", "POST", "{{interaction_url}}/api/topic-posts/{{post_id}}/like", { headers: [auth()] }),
      request("MS-POST-ACTION-001 toggle collect action", "POST", "{{interaction_url}}/api/topic-posts/{{post_id}}/action", { headers: [auth()], body: { actionType: "collect" } }),
      request("MS-TOPIC-UNFOLLOW-001 unfollow topic", "DELETE", "{{interaction_url}}/api/topics/{{topic_id}}/follow", { headers: [auth()] }),
    ],
  },
  {
    name: "06 UI E2E fixtures",
    item: uiSeedItems,
  },
];

const variables = {
  user_url: "http://127.0.0.1:8081",
  catalog_url: "http://127.0.0.1:8082",
  trade_url: "http://127.0.0.1:8083",
  interaction_url: "http://127.0.0.1:8084",
  gateway_url: "http://127.0.0.1:18080",
  test_password: "test123",
  run_id: "", product_title: "", topic_title: "",
  buyer_username: "", seller_username: "", disposable_username: "", buyer_token: "", seller_token: "", admin_token: "",
  buyer_id: "", seller_id: "", disposable_user_id: "", address_id: "", product_id: "", store_id: "",
  cart_id: "", order_id: "", topic_id: "", post_id: "", buyer_realname_id: "", seller_realname_id: "",
};

const collection = {
  info: {
    _postman_id: "a258550f-c27c-4d45-9178-01201c55e60d",
    name: "Soft Shop - Microservices complete API regression",
    description: "Covers every HTTP method/path exposed by the four business microservices, plus representative authentication, validation and conflict paths. Generated by build-microservices-collection.mjs.",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
  },
  item: folders,
  event: [{
    listen: "prerequest",
    script: {
      type: "text/javascript",
      exec: [
        "if (!pm.collectionVariables.get('run_id')) {",
        "  const runId = `${Date.now()}${Math.floor(Math.random() * 100000)}`;",
        "  pm.collectionVariables.set('run_id', runId);",
        "  pm.collectionVariables.set('buyer_username', `ms_buyer_${runId}`);",
        "  pm.collectionVariables.set('seller_username', `ms_seller_${runId}`);",
        "  pm.collectionVariables.set('disposable_username', `ms_delete_${runId}`);",
        "  pm.collectionVariables.set('product_title', `ms-product-${runId}`);",
        "  pm.collectionVariables.set('topic_title', `ms-topic-${runId}`);",
        "}",
      ],
    },
  }],
  variable: Object.entries(variables).map(([key, value]) => ({ key, value, type: "string" })),
};

fs.writeFileSync(output, `${JSON.stringify(collection, null, 2)}\n`, "utf8");
console.log(`Generated ${output}`);
