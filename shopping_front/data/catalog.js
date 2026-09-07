export const goodsCatalog = [
  {
    id: 'airwave-pro',
    scene: 'new',
    category: '数码影音',
    title: 'AirWave Pro 降噪耳机',
    subtitle: '全新正品 · 48 小时发货 · 支持七天无理由',
    price: 699,
    originPrice: 899,
    cover: '/static/goods/airwave-pro.jpg',
    tag: '官方新品',
    condition: '全新',
    credit: 100,
    location: '上海',
    shopName: '松果严选数码',
    delivery: '顺丰包邮，48 小时内发货',
    service: ['平台担保', '七天无理由', '官方质保'],
    highlights: ['45dB 主动降噪', '38 小时续航', '低延迟游戏模式'],
    story: '适合通勤、学习和线上会议的轻量耳机，主打稳定、舒适和清晰通话。',
    params: [
      ['品牌', 'AirWave'],
      ['连接方式', '蓝牙 5.4'],
      ['续航', '38 小时'],
      ['质保', '一年官方质保']
    ],
    reviews: [
      { user: '晨光买家', text: '降噪很稳，佩戴一下午也不夹耳。', score: '4.9', tags: ['降噪好', '发货快'] }
    ],
    timeline: [],
    aiTips: ['可询问保修政策', '可比较同价位耳机', '可查看发票与质保']
  },
  {
    id: 'songuo-pad',
    scene: 'new',
    category: '数码影音',
    title: '松果 Pad 11 学习平板',
    subtitle: '新品首发 · 学习办公两用 · 赠保护套',
    price: 2299,
    originPrice: 2599,
    cover: '/static/goods/songuo-pad.jpg',
    tag: '新品首发',
    condition: '全新',
    credit: 100,
    location: '杭州',
    shopName: '松果严选数码',
    delivery: '次日达覆盖核心城市',
    service: ['平台担保', '官方质保', '学生优惠'],
    highlights: ['2.5K 护眼屏', '8300mAh 电池', '手写笔低延迟'],
    story: '面向课程笔记、网课和轻办公场景，兼顾屏幕素质与续航。',
    params: [
      ['内存', '8GB + 256GB'],
      ['屏幕', '11 英寸 2.5K'],
      ['重量', '485g'],
      ['网络', 'Wi-Fi']
    ],
    reviews: [
      { user: '期末冲刺中', text: '做笔记很顺手，续航够一天课。', score: '4.7', tags: ['学习友好'] }
    ],
    timeline: [],
    aiTips: ['可生成学习设备清单', '可估算分期预算']
  },
  {
    id: 'viewtop-monitor',
    scene: 'used',
    category: '数码影音',
    title: 'ViewTop 27 英寸 2K 显示器',
    subtitle: '二手 9 成新 · 无坏点 · 支持当面验货',
    price: 680,
    originPrice: 1099,
    cover: '/static/goods/viewtop-monitor.jpg',
    tag: '同城自提',
    condition: '9 成新',
    credit: 97,
    location: '广州大学城',
    shopName: '阿洛的桌面仓库',
    delivery: '同城自提 / 到付快递',
    service: ['平台担保', '当面验货', '48 小时售后协商'],
    highlights: ['2K 分辨率', '接口齐全', '办公游戏都够用'],
    story: '陪前任主人完成了毕业设计和第一份实习作品集，现在桌面升级，等待下一位使用者。',
    params: [
      ['品牌', 'ViewTop'],
      ['分辨率', '2560 x 1440'],
      ['接口', 'HDMI / DP'],
      ['成色', '9 成新']
    ],
    reviews: [
      { user: '桌搭玩家', text: '卖家说明很细，现场验货顺利。', score: '4.9', tags: ['描述真实'] }
    ],
    timeline: [
      { date: '2024.09', title: '入手第一天', text: '用于设计作业和剪辑练习。' },
      { date: '2025.06', title: '完成毕业项目', text: '屏幕一直稳定，无亮点坏点。' },
      { date: '2026.05', title: '准备流转', text: '已清洁打包，支持同城验货。' }
    ],
    aiTips: ['可帮你砍价到 620-650', '可询问坏点和接口照片', '可生成验货清单']
  },
  {
    id: 'software-book',
    scene: 'used',
    category: '图书文创',
    title: '软件工程导论与项目管理笔记',
    subtitle: '二手教材 · 含重点标注 · 适合课程复习',
    price: 18,
    originPrice: 69,
    cover: '/static/goods/software-book.jpg',
    tag: '学长笔记',
    condition: '8.5 成新',
    credit: 99,
    location: '武汉',
    shopName: '南湖旧书摊',
    delivery: '校园面交 / 普通快递',
    service: ['真实笔记', '可拍内页', '平台担保'],
    highlights: ['重点页有标记', '附课程项目清单', '适合期末复习'],
    story: '上一任主人用它完成了一次软工大作业，夹着需求评审清单和测试用例模板。',
    params: [
      ['版本', '第 3 版'],
      ['语言', '中文'],
      ['成色', '8.5 成新'],
      ['附赠', '复习提纲']
    ],
    reviews: [
      { user: '赶 ddl 的同学', text: '笔记很实用，重点划得很清楚。', score: '4.8', tags: ['内容实用'] }
    ],
    timeline: [
      { date: '2025.03', title: '开始软工课程', text: '第一章写下需求分析重点。' },
      { date: '2025.06', title: '项目答辩通过', text: '附带的用例模板帮了大忙。' },
      { date: '2026.05', title: '转给下一届', text: '希望继续发挥作用。' }
    ],
    aiTips: ['可提取重点页', '可生成复习计划']
  },
  {
    id: 'ergo-chair',
    scene: 'used',
    category: '家居生活',
    title: '人体工学椅 Pro',
    subtitle: '二手 9 成新 · 腰托完整 · 适合宿舍/工位',
    price: 420,
    originPrice: 899,
    cover: '/static/goods/ergo-chair.jpg',
    tag: '大件同城',
    condition: '9 成新',
    credit: 95,
    location: '成都',
    shopName: '榕树下的小店',
    delivery: '同城搬运可协商',
    service: ['平台担保', '线下验货', '议价空间'],
    highlights: ['腰托可调', '坐垫回弹正常', '无明显破损'],
    story: '陪伴过很多个赶项目的夜晚，椅背和扶手状态良好，适合继续服役。',
    params: [
      ['材质', '网布 + 金属脚'],
      ['功能', '升降 / 后仰 / 腰托'],
      ['成色', '9 成新'],
      ['配送', '同城优先']
    ],
    reviews: [
      { user: '新工位用户', text: '坐感不错，卖家帮忙叫了车。', score: '4.6', tags: ['服务好'] }
    ],
    timeline: [
      { date: '2024.11', title: '入驻工作室', text: '成为第一把正式办公椅。' },
      { date: '2025.12', title: '陪伴项目冲刺', text: '坐垫和腰托依旧稳定。' },
      { date: '2026.05', title: '搬家出闲置', text: '同城优先，欢迎试坐。' }
    ],
    aiTips: ['可协商同城运费', '可询问坐垫塌陷情况']
  },
  {
    id: 'desk-lamp',
    scene: 'new',
    category: '家居生活',
    title: '折叠护眼台灯',
    subtitle: '新品 · 宿舍桌面友好 · 三档色温',
    price: 89,
    originPrice: 129,
    cover: '/static/goods/desk-lamp.jpg',
    tag: '宿舍好物',
    condition: '全新',
    credit: 100,
    location: '深圳',
    shopName: '松果生活馆',
    delivery: '满 59 包邮',
    service: ['七天无理由', '一年质保', '平台担保'],
    highlights: ['无频闪', 'USB-C 供电', '可折叠收纳'],
    story: '为宿舍、书桌和夜间阅读设计的小型台灯，亮度柔和，收纳方便。',
    params: [
      ['供电', 'USB-C'],
      ['光源', 'LED'],
      ['色温', '三档调节'],
      ['功率', '8W']
    ],
    reviews: [
      { user: '夜读党', text: '光线柔和，不占桌面。', score: '4.7', tags: ['护眼'] }
    ],
    timeline: [],
    aiTips: ['可推荐宿舍桌搭组合', '可计算顺手买优惠']
  }
]

export const hotStores = [
  {
    id: 'store-1',
    name: '松果严选数码',
    score: '4.9',
    fans: '1.2w',
    desc: '主营新品数码与官方配件，售后响应快，适合追求稳定购物体验。',
    badge: '官方严选',
    cover: '/static/stores/songuo-digital.jpg',
    fallbackCover: '/static/goods/songuo-pad.jpg',
    owner: '松果官方运营组',
    service: ['官方质保', '48 小时发货', '学生优惠'],
    announcement: '本店主推学习、办公和通勤数码，所有新品均支持平台担保与售后协商。'
  },
  {
    id: 'store-2',
    name: '南湖旧书摊',
    score: '4.8',
    fans: '6.4k',
    desc: '课程教材、考研资料和学长笔记流转地，支持拍内页确认。',
    badge: '校园认证',
    cover: '/static/stores/nanhu-books.jpg',
    fallbackCover: '/static/goods/software-book.jpg',
    owner: '南湖校友书摊',
    service: ['可拍内页', '校园面交', '复习资料'],
    announcement: '优先展示课程教材、复习资料和带笔记的二手书，适合开学季集中选购。'
  },
  {
    id: 'store-3',
    name: '榕树下的小店',
    score: '4.7',
    fans: '4.1k',
    desc: '家居生活闲置为主，重视真实描述与同城沟通。',
    badge: '信用卖家',
    cover: '/static/stores/rongshu-life.jpg',
    fallbackCover: '/static/goods/ergo-chair.jpg',
    owner: '榕树下的小店主',
    service: ['同城自提', '线下验货', '可议价'],
    announcement: '家居、桌搭和生活闲置会标注成色与搬运方式，建议下单前确认尺寸。'
  }
]

export const orderTabs = ['全部', '待付款', '待发货', '待收货', '待评价']

export function findGoodsById(id) {
  return goodsCatalog.find((item) => item.id === id) || null
}

export function buildGoodsDetailUrl(item = {}) {
  return '/pages/goods/detail?id=' + encodeURIComponent(item.id || 'goods')
}

export function buildTopicDetailUrl(item = {}) {
  return '/pages/topic/detail?id=' + encodeURIComponent(item.id || '')
}

export function formatTopicHeat(topic = {}) {
  const posts = Number(topic.postCount) || 0
  const follows = Number(topic.followCount) || 0
  return posts + ' 帖 · ' + follows + ' 关注'
}

export function withTopicHeat(topic) {
  if (!topic || typeof topic !== 'object') return {}
  return Object.assign({}, topic, { heat: formatTopicHeat(topic) })
}

export function getStoreCover(store = {}) {
  return store.cover || store.fallbackCover || '/static/goods/viewtop-monitor.jpg'
}

export function findStoreByName(name) {
  return hotStores.find((item) => item.name === name) || null
}

export function productsByStore(name) {
  return goodsCatalog.filter((item) => item.shopName === name)
}
