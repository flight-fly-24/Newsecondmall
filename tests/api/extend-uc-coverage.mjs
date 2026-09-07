import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const directory = path.dirname(fileURLToPath(import.meta.url));
const collectionPath = path.join(directory, "soft-shop-api.postman_collection.json");
const collection = JSON.parse(fs.readFileSync(collectionPath, "utf8"));

collection.variable ??= [];
for (const key of ["admin_token", "realname_id", "conversation_id"]) {
  if (!collection.variable.some((entry) => entry.key === key)) {
    collection.variable.push({ key, value: "", type: "string" });
  }
}

// Idempotency: generated coverage cases can be regenerated after the base collection changes.
collection.item = (collection.item ?? []).filter(
  (item) => !String(item.name ?? "").startsWith("API-UC-"),
);

function auth(variable) {
  return variable
    ? { type: "bearer", bearer: [{ key: "token", value: `{{${variable}}}`, type: "string" }] }
    : { type: "noauth" };
}

function tests(id, http, code, extra = []) {
  return [{
    listen: "test",
    script: {
      type: "text/javascript",
      exec: [
        `pm.test("${id} HTTP ${http}", () => pm.response.to.have.status(${http}));`,
        `pm.test("${id} 业务码 ${code}", () => pm.expect(Number(pm.response.json().code)).to.eql(${code}));`,
        ...extra,
      ],
    },
  }];
}

function request(id, uc, method, url, { token, body, http = 200, code = 0, extra = [] } = {}) {
  const value = {
    name: `API-UC-${uc}-${id}`,
    request: {
      method,
      header: [],
      url: `{{base_url}}${url}`,
      auth: auth(token),
    },
    response: [],
    event: tests(id, http, code, extra),
  };
  if (body !== undefined) {
    value.request.body = {
      mode: "raw",
      raw: JSON.stringify(body, null, 2),
      options: { raw: { language: "json" } },
    };
  }
  return value;
}

const cases = [
  request("API-AUTH-LOGIN-ADMIN-01", "UC01", "POST", "/api/auth/login", {
    body: { username: "admin", password: "admin123" },
    extra: [
      "pm.test(\"管理员 Token 非空\", () => pm.expect(pm.response.json().data.token).to.be.a(\"string\").and.not.empty);",
      "pm.collectionVariables.set(\"admin_token\", pm.response.json().data.token);",
    ],
  }),

  request("API-CENTER-FAVORITE-01", "UC03", "POST", "/api/center/buyer/items/favorite", {
    token: "buyer_token", body: { itemId: "1001", title: "AirWave Pro 降噪耳机", storeName: "松果严选数码" },
  }),
  request("API-CENTER-FAVORITE-02", "UC03", "GET", "/api/center/buyer/items/favorite", {
    token: "buyer_token",
    extra: ["pm.test(\"收藏列表包含测试商品\", () => pm.expect(pm.response.json().data.some(x => String(x.targetId) === \"1001\")).to.be.true);"],
  }),
  request("API-CENTER-FAVORITE-03", "UC03", "GET", "/api/center/buyer/items/favorite", { http: 401, code: 401 }),

  request("API-ADMIN-AUDIT-01", "UC05", "GET", "/api/admin/audit", { token: "admin_token" }),
  request("API-ADMIN-AUDIT-02", "UC05", "GET", "/api/admin/audit", { token: "buyer_token", http: 403, code: 403 }),
  request("API-ADMIN-AUDIT-03", "UC05", "GET", "/api/admin/audit", { http: 401, code: 401 }),

  request("API-STORE-DETAIL-01", "UC08", "GET", "/api/stores/1", {
    token: "buyer_token", extra: ["pm.test(\"返回目标店铺\", () => pm.expect(String(pm.response.json().data.id)).to.eql(\"1\"));"],
  }),
  request("API-STORE-FOLLOW-01", "UC08", "POST", "/api/stores/1/follow", { token: "buyer_token" }),
  request("API-STORE-FOLLOW-02", "UC08", "DELETE", "/api/stores/1/follow", { token: "buyer_token" }),

  request("API-CENTER-SELLER-01", "UC09", "GET", "/api/center/seller", { token: "seller_token" }),
  request("API-CENTER-SELLER-02", "UC09", "GET", "/api/center/seller", { token: "buyer_token", http: 403, code: 403 }),

  request("API-REALNAME-SUBMIT-01", "UC10", "POST", "/api/center/buyer/realname", {
    token: "buyer_token", body: { realName: "接口测试用户", idCard: "110101200001011234" },
    extra: [
      "pm.test(\"实名认证进入待审核\", () => pm.expect(pm.response.json().data.realName.status).to.eql(\"pending\"));",
      "pm.collectionVariables.set(\"realname_id\", pm.response.json().data.realName.id);",
    ],
  }),
  request("API-REALNAME-SUBMIT-02", "UC10", "POST", "/api/center/buyer/realname", {
    token: "buyer_token", body: { realName: "", idCard: "" }, http: 400, code: 400,
  }),

  request("API-CENTER-ADMIN-01", "UC11", "GET", "/api/center/admin", { token: "admin_token" }),
  request("API-CENTER-ADMIN-02", "UC11", "GET", "/api/center/admin", { token: "buyer_token", http: 403, code: 403 }),
  request("API-REALNAME-APPROVE-01", "UC11", "PUT", "/api/center/admin/realname/{{realname_id}}/approve", {
    token: "admin_token",
  }),

  request("API-TOPIC-LIST-01", "UC12", "GET", "/api/topics", {
    extra: ["pm.test(\"话题列表非空\", () => pm.expect(pm.response.json().data).to.be.an(\"array\").that.is.not.empty);"],
  }),
  request("API-TOPIC-POST-01", "UC12", "POST", "/api/topics/7001/posts", {
    token: "buyer_token", body: { content: "API 自动测试发布的社区帖子", images: [], productId: "1001", storeId: "1" },
  }),
  request("API-TOPIC-POST-02", "UC12", "POST", "/api/topics/7001/posts", {
    token: "buyer_token", body: { content: "", images: [] }, http: 400, code: 400,
  }),

  request("API-CHAT-CONVERSATION-01", "UC13", "POST", "/api/chat/conversations", {
    token: "buyer_token", body: { sellerId: 2, goodsId: 1001, status: "ai" },
    extra: [
      "pm.test(\"会话 ID 非空\", () => pm.expect(pm.response.json().data.covId).to.exist);",
      "pm.collectionVariables.set(\"conversation_id\", pm.response.json().data.covId);",
    ],
  }),
  request("API-CHAT-MESSAGE-01", "UC13", "POST", "/api/chat/conversations/{{conversation_id}}/messages", {
    token: "buyer_token", body: { content: "这个商品还可以便宜一些吗？", type: "CHAT_MESSAGE" },
  }),
  request("API-CHAT-MESSAGE-02", "UC13", "GET", "/api/chat/conversations/{{conversation_id}}/messages", {
    token: "admin_token", http: 403, code: 403,
  }),

  request("API-CHAT-BARGAIN-01", "UC14", "POST", "/api/chat/conversations/{{conversation_id}}/ai-bargain", {
    token: "buyer_token", extra: ["pm.test(\"议价建议非空\", () => pm.expect(pm.response.json().data.message.content).to.be.a(\"string\").and.not.empty);"],
  }),
  request("API-CHAT-TRANSFER-01", "UC14", "POST", "/api/chat/conversations/{{conversation_id}}/transfer", {
    token: "buyer_token", extra: ["pm.test(\"会话转为人工状态\", () => pm.expect(pm.response.json().data.status).to.eql(\"active\"));"],
  }),

  request("API-AI-ASSIST-01", "UC15", "POST", "/api/ai/assist", {
    body: { productId: "1001", question: "购买二手耳机需要检查什么？", offer: 600 },
    extra: ["pm.test(\"AI 建议非空\", () => pm.expect(pm.response.json().data.answer).to.be.a(\"string\").and.not.empty);"],
  }),
  request("API-AI-PUBLISH-01", "UC15", "POST", "/api/ai/publish-suggestion", {
    body: { scene: "used", category: "数码影音", condition: "九成新", keyword: "蓝牙耳机" },
    extra: ["pm.test(\"发布建议包含标题\", () => pm.expect(pm.response.json().data.title).to.be.a(\"string\").and.not.empty);"],
  }),
];

collection.item.push(...cases);
fs.writeFileSync(collectionPath, `${JSON.stringify(collection, null, 2)}\n`, "utf8");
console.log(`Added ${cases.length} UC-linked API requests.`);
