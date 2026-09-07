import fs from "node:fs";
import path from "node:path";

const directory = path.dirname(new URL(import.meta.url).pathname.replace(/^\/(?:([A-Za-z]:))/, "$1"));
const source = path.join(directory, "Soft Shop API Tests.postman_collection.json");
const target = path.join(directory, "soft-shop-api.postman_collection.json");

if (!fs.existsSync(source) && !fs.existsSync(target)) {
  throw new Error("Postman collection was not found");
}

const input = fs.existsSync(source) ? source : target;
const collection = JSON.parse(fs.readFileSync(input, "utf8"));

collection.variable ??= [];
function setVariable(key, value) {
  const variable = collection.variable.find((entry) => entry.key === key);
  if (variable) {
    variable.value = value;
    variable.type = "string";
  } else {
    collection.variable.push({ key, value, type: "string" });
  }
}

setVariable("base_url", "http://localhost");
setVariable("buyer_token", "");
setVariable("seller_token", "");
setVariable("ci_invalid_token", "not-a-valid-jwt");
setVariable("unique_username", "");
setVariable("unique_phone", "");

const expectedById = new Map(Object.entries({
  "API-AUTH-REG-01": [200, 0],
  "API-AUTH-REG-02": [200, 0],
  "API-AUTH-REG-03": [409, 409],
  "API-AUTH-REG-04": [400, 400],
  "API-AUTH-REG-05": [400, 400],
  "API-AUTH-REG-06": [400, 400],
  "API-AUTH-REG-07": [400, 400],
  "API-AUTH-LOGIN-01": [200, 0],
  "API-AUTH-LOGIN-02": [200, 0],
  "API-AUTH-LOGIN-03": [401, 401],
  "API-AUTH-LOGIN-04": [401, 401],
  "API-AUTH-LOGIN-05": [400, 400],
  "API-AUTH-LOGIN-06": [400, 400],
  "API-AUTH-LOGIN-07": [400, 400],
  "API-AUTH-LOGIN-08": [400, 400],
  "API-AUTH-LOGIN-09": [403, 403],
  "API-AUTH-ME-01": [200, 0],
  "API-AUTH-ME-02": [200, 0],
  "API-AUTH-ME-03": [401, 401],
  "API-AUTH-ME-04": [401, 401],
  "API-AUTH-ME-05": [401, 401],
  "API-AUTH-ME-06": [401, 401],
  "API-AUTH-PROFILE-01": [200, 0],
  "API-AUTH-PROFILE-02": [200, 0],
  "API-AUTH-PROFILE-03": [400, 400],
  "API-AUTH-PROFILE-04": [400, 400],
  "API-AUTH-PROFILE-05": [401, 401],
  "API-AUTH-PROFILE-06": [401, 401],
  "API-AUTH-PROFILE-07": [200, 0],
  "API-AUTH-PROFILE-08": [400, 400],
  "API-AUTH-SEARCH-01": [200, 0],
  "API-AUTH-SEARCH-02": [200, 0],
  "API-AUTH-SEARCH-03": [200, 0],
  "API-AUTH-SEARCH-04": [200, 0],
  "API-AUTH-SEARCH-05": [200, 0],
  "API-AUTH-SEARCH-06": [200, 0],
  "API-AUTH-SEARCH-07": [400, 400],
  "API-AUTH-SEARCH-08": [200, 0],
  "API-AUTH-SEARCH-09": [200, 0],
  "API-AUTH-SEARCH-10": [200, 0],
  "API-PROD-LIST-01": [200, 0],
  "API-PROD-LIST-02": [200, 0],
  "API-PROD-LIST-03": [200, 0],
  "API-PROD-LIST-04": [200, 0],
  "API-PROD-LIST-05": [200, 0],
  "API-PROD-MINE-01": [200, 0],
  "API-PROD-MINE-02": [401, 401],
  "API-PROD-MINE-03": [401, 401],
  "API-PROD-DETAIL-01": [200, 0],
  "API-PROD-DETAIL-02": [404, 404],
  "API-PROD-DETAIL-03": [404, 404],
  "API-PROD-PUBLISH-01": [200, 0],
  "API-PROD-PUBLISH-02": [401, 401],
  "API-PROD-PUBLISH-03": [403, 403],
  "API-PROD-PUBLISH-04": [400, 400],
  "API-PROD-PUBLISH-05": [400, 400],
  "API-ORDER-LIST-01": [200, 0],
  "API-ORDER-LIST-02": [401, 401],
  "API-ORDER-LIST-03": [401, 401],
  "API-ORDER-CREATE-01": [200, 0],
  "API-ORDER-CREATE-02": [401, 401],
  "API-ORDER-CREATE-03": [404, 404],
  "API-ORDER-REVIEW-01": [200, 0],
  "API-ORDER-REVIEW-02": [401, 401],
}));

function requestId(name) {
  return [...expectedById.keys()].find((key) => name.startsWith(key));
}

function setEvent(item, listen, lines) {
  item.event ??= [];
  const event = item.event.find((entry) => entry.listen === listen);
  const script = { exec: lines, type: "text/javascript", packages: {}, requests: {} };
  if (event) {
    event.script = script;
  } else {
    item.event.push({ listen, script });
  }
}

function standardTests(id, expectedHttp, expectedBusiness) {
  const lines = [
    `pm.test("${id} HTTP ${expectedHttp}", function () {`,
    `  pm.response.to.have.status(${expectedHttp});`,
    "});",
    "",
    `pm.test("${id} 业务码 ${expectedBusiness}", function () {`,
    "  const body = pm.response.json();",
    `  pm.expect(Number(body.code)).to.eql(${expectedBusiness});`,
    "});",
  ];

  if (id === "API-AUTH-LOGIN-01" || id === "API-AUTH-LOGIN-02") {
    const tokenVariable = id.endsWith("01") ? "buyer_token" : "seller_token";
    lines.push(
      "",
      `pm.test("${id} 返回有效 Token", function () {`,
      "  const body = pm.response.json();",
      "  pm.expect(body.data.token).to.be.a(\"string\").and.not.empty;",
      "});",
      `pm.collectionVariables.set("${tokenVariable}", pm.response.json().data.token);`,
    );
  }

  return lines;
}

function bearer(variable) {
  return {
    type: "bearer",
    bearer: [{ key: "token", value: `{{${variable}}}`, type: "string" }],
  };
}

const buyerIds = new Set([
  "API-AUTH-ME-01",
  "API-AUTH-PROFILE-01", "API-AUTH-PROFILE-02", "API-AUTH-PROFILE-03",
  "API-AUTH-PROFILE-04", "API-AUTH-PROFILE-07", "API-AUTH-PROFILE-08",
  "API-AUTH-SEARCH-01", "API-AUTH-SEARCH-02", "API-AUTH-SEARCH-03",
  "API-AUTH-SEARCH-04", "API-AUTH-SEARCH-05", "API-AUTH-SEARCH-06",
  "API-AUTH-SEARCH-07", "API-AUTH-SEARCH-08", "API-AUTH-SEARCH-09",
  "API-PROD-PUBLISH-03",
  "API-ORDER-LIST-01", "API-ORDER-CREATE-01", "API-ORDER-CREATE-03",
  "API-ORDER-REVIEW-01",
]);

const sellerIds = new Set([
  "API-AUTH-ME-02", "API-PROD-MINE-01", "API-PROD-PUBLISH-01",
  "API-PROD-PUBLISH-04", "API-PROD-PUBLISH-05",
]);

const invalidTokenIds = new Set([
  "API-AUTH-ME-04", "API-AUTH-PROFILE-06", "API-PROD-MINE-03",
  "API-ORDER-LIST-03",
]);

function removeAuthorizationHeaders(request) {
  request.header = (request.header ?? []).filter(
    (header) => String(header.key).toLowerCase() !== "authorization",
  );
}

function setJsonBody(item, values) {
  if (!item.request.body?.raw) return;
  try {
    const body = JSON.parse(item.request.body.raw);
    Object.assign(body, values);
    item.request.body.raw = JSON.stringify(body, null, 2);
  } catch {
    // Invalid JSON bodies are intentional in some negative tests.
  }
}

let requestCount = 0;
let replacedUrlCount = 0;
let removedResponseCount = 0;

function visit(items = []) {
  for (const item of items) {
    if (item.request) {
      requestCount += 1;
      item.name = String(item.name ?? "").replace(/\s+Copy$/u, "");

      const rawUrl = typeof item.request.url === "string"
        ? item.request.url
        : item.request.url?.raw;

      if (rawUrl) {
        const normalizedUrl = rawUrl.replace(
          /^https?:\/\/(?:localhost|127\.0\.0\.1)(?::\d+)?/u,
          "{{base_url}}",
        );
        if (normalizedUrl !== rawUrl) {
          item.request.url = normalizedUrl;
          replacedUrlCount += 1;
        }
      }

      removedResponseCount += Array.isArray(item.response) ? item.response.length : 0;
      item.response = [];

      const id = requestId(item.name);
      const expected = id ? expectedById.get(id) : null;
      if (id && expected) {
        setEvent(item, "test", standardTests(id, ...expected));
      }

      if (id === "API-AUTH-REG-01" || id === "API-AUTH-REG-02") {
        const role = id.endsWith("01") ? "buyer" : "seller";
        setEvent(item, "prerequest", [
          "const timestamp = Date.now();",
          `pm.collectionVariables.set("unique_username", "ci_${role}_" + timestamp);`,
          "pm.collectionVariables.set(\"unique_phone\", \"19\" + String(timestamp).slice(-9));",
        ]);
        setJsonBody(item, {
          username: "{{unique_username}}",
          phone: "{{unique_phone}}",
          role,
        });
      }

      if (id === "API-AUTH-REG-03") {
        setEvent(item, "prerequest", [
          "pm.collectionVariables.set(\"unique_phone\", \"19\" + String(Date.now()).slice(-9));",
        ]);
        setJsonBody(item, { username: "demo", phone: "{{unique_phone}}", role: "buyer" });
      }

      if (id === "API-AUTH-LOGIN-01") {
        setJsonBody(item, { username: "demo", password: "demo123" });
      } else if (id === "API-AUTH-LOGIN-02") {
        setJsonBody(item, { username: "seller", password: "seller123" });
      } else if (id === "API-AUTH-LOGIN-03") {
        setJsonBody(item, { username: "demo", password: "definitely-wrong" });
      } else if (id === "API-AUTH-LOGIN-09") {
        setJsonBody(item, { username: "ci_disabled", password: "demo123" });
      }

      if (id === "API-AUTH-SEARCH-09") {
        item.request.url = "{{base_url}}/api/auth/search-users?keyword=%25";
      } else if (id === "API-ORDER-REVIEW-01") {
        item.request.url = "{{base_url}}/api/orders/9003/review";
      }

      removeAuthorizationHeaders(item.request);
      if (buyerIds.has(id)) {
        item.request.auth = bearer("buyer_token");
      } else if (sellerIds.has(id)) {
        item.request.auth = bearer("seller_token");
      } else if (invalidTokenIds.has(id)) {
        item.request.auth = bearer("ci_invalid_token");
      } else if (id === "API-AUTH-ME-06") {
        item.request.auth = { type: "noauth" };
        item.request.header.push({ key: "Authorization", value: "{{buyer_token}}", type: "text" });
      } else {
        item.request.auth = { type: "noauth" };
      }
    }

    visit(item.item);
  }
}

visit(collection.item);
fs.writeFileSync(target, `${JSON.stringify(collection, null, 2)}\n`, "utf8");

if (source !== target && fs.existsSync(source)) {
  fs.rmSync(source);
}

console.log(JSON.stringify({ requestCount, replacedUrlCount, removedResponseCount }));
