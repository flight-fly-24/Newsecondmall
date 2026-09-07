# 前端结构说明

前端项目位于 `shopping_front/`，技术栈为 uni-app + Vue 3，当前以 H5 展示为主，同时兼容微信小程序。

## 核心文件

- `pages.json`：页面路由、导航栏、tabBar 配置。该文件必须保持合法 JSON，否则微信小程序产物会生成失败。
- `manifest.json`：uni-app 应用配置，包含 H5 devServer、微信小程序 AppID 等。
- `uni.scss`：全局样式变量和基础视觉 token。
- `data/catalog.js`：前端演示数据，包括商品、话题、店铺、订单 tab 等。
- `utils/request.js`：统一请求封装，会自动携带登录 token。
- `utils/auth.js`：token、用户缓存和错误信息处理。
- `utils/cart.js`：购物车接口封装（`/api/cart`，trade-service）与按店铺分组。
- `utils/address.js`：收货地址本地存储、默认地址管理。
- `services/auth.js`：登录、注册、获取当前用户信息。

## 页面结构

### Tab 页面

- `pages/home/home.vue`：首页，包含搜索、新品/二手切换、推荐商品、交易保障、二手时间线入口。
- `pages/browse/browse.vue`：逛逛/社区发现，包含话题清单、二手故事、热门店铺。
- `pages/cart/cart.vue`：购物车，按店铺分组，支持勾选、数量修改、删除和结算。
- `pages/user/index.vue`：个人中心，展示用户信息、信用分、订单入口、发布管理、审核入口。

### 商品与交易页面

- `pages/goods/detail.vue`：商品详情，展示图片、价格、卖家、保障、参数、评价、二手时间线和 AI 议价入口。
- `pages/order/confirm.vue`：确认订单，展示地址、已选商品、金额和模拟支付入口。
- `pages/order/list.vue`：订单列表，展示订单状态和操作入口。
- `pages/order/pay-result.vue`：支付结果。
- `pages/order/logistics.vue`：物流跟踪。
- `pages/order/review.vue`：发表评价。

### 发布、店铺、管理页面

- `pages/publish/publish.vue`：发布商品，支持新品/二手、图片、标题、分类、价格、成色、描述、故事和 AI 建议。
- `pages/user/published.vue`：我的发布。
- `pages/store/store.vue`：店铺主页。
- `pages/admin/audit.vue`：管理员审核模拟。
- `pages/message/message.vue`：消息与 AI 议价助手。
- `pages/address/list.vue`：收货地址管理。
- `pages/auth/login.vue`：登录。
- `pages/auth/register.vue`：注册。

## 视觉设计约定

- 主背景色：`#f4f6f4`
- 主色：`#1f5c43`
- 强调价格/促销色：`#d66a2c`
- 卡片背景：`#ffffff`
- 页面风格：清爽可信 + 社区淘货感。

## 小程序兼容注意事项

- `pages.json` 中的标题当前使用英文，主要是为了避免 Windows 编码损坏导致 JSON 断裂；页面内部仍可使用中文。
- 不要直接编辑 `unpackage/dist/dev/mp-weixin/app.json`，它是编译产物。
- 修改 `pages.json` 后必须重新通过 HBuilderX 编译到微信小程序。

## 修改文档要求

如果新增、删除或重命名页面，必须同步更新：

- `shopping_front/pages.json`
- 本文档的“页面结构”部分
- 如影响小程序运行，更新 `docs/wechat-devtools.md`
