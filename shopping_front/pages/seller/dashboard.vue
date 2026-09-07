<template>
  <view class="role-app" :class="{ 'is-messages': active === 'messages' }">
    <view class="top">
      <view class="brand">
        <view class="mark">&#21334;</view>
        <view>
          <text class="brand-title">&#21334;&#23478;&#24037;&#20316;&#21488;</text>
          <text class="brand-sub">{{ user.username || 'seller' }} · {{ user.phoneMasked || '137****0000' }}</text>
        </view>
      </view>
      <button class="top-action" @click="goPublish">&#21457;&#24067;&#21830;&#21697;</button>
    </view>

    <view class="role-nav">
      <view v-for="item in tabs" :key="item.key" class="role-nav-item" :class="{ on: active === item.key }" @click="active = item.key">
        <text>{{ item.label }}</text>
      </view>
    </view>

    <view v-if="active === 'home'" class="content">
      <view class="hero">
        <view>
          <text class="hero-kicker">SELLER MODE</text>
          <text class="hero-title">&#20170;&#26085;&#24215;&#38138;&#27010;&#35272;</text>
          <text class="hero-copy">&#20320;&#24050;&#36827;&#20837;&#21334;&#23478;&#31471;&#65292;&#21487;&#31649;&#29702;&#21830;&#21697;&#12289;&#35746;&#21333;&#12289;&#21806;&#21518;&#21644;&#24215;&#38138;&#20449;&#29992;&#12290;</text>
        </view>
      </view>
      <view class="stat-grid">
        <view v-for="item in stats" :key="item.title || item.label" class="stat-card">
          <text class="stat-value">{{ item.value }}</text>
          <text class="stat-label">{{ item.title || item.label }}</text>
        </view>
      </view>
      <view class="section">
        <text class="section-title">&#24453;&#22788;&#29702;</text>
        <view v-for="item in todos" :key="item" class="line">{{ item }}</view>
      </view>
      <view class="section subtle-section">
        <text class="section-title">&#36817;&#26399;&#21160;&#24577;</text>
        <view class="line">&#20170;&#26085;&#26032;&#22686; 14 &#26465;&#21672;&#35810;&#65292;&#25968;&#30721;&#24433;&#38899;&#31867;&#30446;&#26368;&#27963;&#36291;</view>
        <view class="line">&#24314;&#35758;&#32473; ViewTop 27 &#34917;&#20805;&#25509;&#21475;&#21644;&#23631;&#24149;&#28857;&#20142;&#29031;&#29255;</view>
      </view>
    </view>

    <view v-if="active === 'products'" class="content">
      <view class="section-head">
        <text class="page-title">&#21830;&#21697;&#31649;&#29702;</text>
        <text class="link" @click="goPublish">&#26032;&#22686;&#21457;&#24067;</text>
      </view>
      <view v-for="item in products" :key="item.title" class="list-card">
        <view>
          <text class="item-title">{{ item.title }}</text>
          <text class="item-desc">{{ item.desc }}</text>
        </view>
        <text class="pill">{{ item.status }}</text>
      </view>
    </view>

    <view v-if="active === 'orders'" class="content">
      <text class="page-title">&#35746;&#21333;&#21806;&#21518;</text>
      <view v-for="item in orders" :key="item.title" class="list-card">
        <view>
          <text class="item-title">{{ item.title }}</text>
          <text class="item-desc">{{ item.buyer }} · {{ item.price }}</text>
        </view>
        <text class="pill warn">{{ item.status }}</text>
      </view>
    </view>

    <view v-if="active === 'messages'" class="message-page">
      <view class="message-workspace">
        <view class="conversation-pane">
          <view class="pane-head">
            <view>
              <text class="pane-kicker">Messages</text>
              <text class="pane-title">卖家消息</text>
            </view>
            <view class="total-pill">{{ filteredList.length }}</view>
          </view>

          <view class="search-box">
            <text class="search-icon">⌕</text>
            <input v-model="keyword" class="search-input" placeholder="搜索联系人或商品" />
          </view>

          <scroll-view class="conversation-list" scroll-y :show-scrollbar="false">
            <view
              v-for="item in filteredList"
              :key="item.covId"
              class="conversation-item"
              :class="{ active: item.covId === covId }"
              @click="open(item)"
            >
              <view class="avatar-wrap">
                <image v-if="item.goodsImageUrl" class="avatar-img" :src="item.goodsImageUrl" mode="aspectFill"></image>
                <view v-else class="avatar">{{ item.icon }}</view>
                <text v-if="item.unreadCount > 0" class="msg-badge">{{ item.unreadCount }}</text>
              </view>
              <view class="conversation-main">
                <view class="conversation-head">
                  <text class="conversation-name">{{ item.title }}</text>
                  <text class="conversation-time">{{ item.time }}</text>
                </view>
                <text class="conversation-sub">{{ item.sub }}</text>
                <text v-if="item.goodsName" class="goods-line">{{ item.goodsName }}</text>
              </view>
            </view>

            <view v-if="filteredList.length === 0" class="empty-list">
              <text class="empty-title">暂无会话</text>
              <text class="empty-sub">买家咨询你的商品后，会话会出现在这里。</text>
            </view>
          </scroll-view>
        </view>

        <view class="chat-pane">
          <template v-if="covId && activeConversation">
            <view class="chat-header">
              <view class="chat-contact">
                <view class="chat-avatar">{{ activeConversation.icon }}</view>
                <view>
                  <text class="chat-title">{{ activeConversation.title }}</text>
                  <text class="chat-subtitle">{{ activeConversation.goodsName || '正在咨询商品' }}</text>
                </view>
              </view>
            </view>

            <scroll-view class="messages" scroll-y :scroll-top="scrollTop" :show-scrollbar="false">
              <view class="session-tip">
                <view class="tip-line"></view>
                <text>上次聊到这里</text>
                <view class="tip-line"></view>
              </view>

              <view v-if="messages.length === 0" class="empty-chat">
                <text class="empty-title">暂无消息</text>
                <text class="empty-sub">开始聊天吧</text>
              </view>

              <view v-for="msg in messages" :key="msg.cmId || `${msg.senderId}-${msg.createTime}`">
                <view v-if="msg.showTime" class="time-divider">
                  {{ formatChatTime(msg.createTime) }}
                </view>
                <view class="message-row" :class="{ mine: msg.senderId === myUserId }">
                  <view v-if="msg.senderId !== myUserId" class="mini-avatar">{{ activeConversation.icon }}</view>
                  <view class="bubble-wrap">
                    <view v-if="msg.card" class="message-product-card" @click.stop="openProductById(msg.card.id)">
                      <image v-if="msg.card.cover" class="card-thumb" :src="msg.card.cover" mode="aspectFill"></image>
                      <view class="card-body">
                        <text class="card-label">商品卡片</text>
                        <text class="card-title">{{ msg.card.title }}</text>
                        <view class="card-meta">
                          <text class="card-price">{{ priceLabel(msg.card.price) }}</text>
                          <text v-if="msg.card.scene" class="card-tag">{{ sceneLabel(msg.card.scene) }}</text>
                        </view>
                      </view>
                    </view>
                    <view v-else class="bubble">
                      <text>{{ msg.content }}</text>
                    </view>
                    <text v-if="msg.senderId === myUserId" class="read-receipt">
                      {{ msg.isRead ? '已读' : '未读' }}
                    </text>
                  </view>
                </view>
              </view>
            </scroll-view>

            <scroll-view class="quick-row" scroll-x :show-scrollbar="false">
              <view class="quick-track">
                <button v-for="text in quickOptions" :key="text" class="quick-btn" @click="sendQuick(text)">{{ text }}</button>
              </view>
            </scroll-view>

            <view class="composer" @click.stop>
              <view v-if="showEmojiPicker" class="emoji-panel">
                <view class="emoji-title">{{ currentEmojiGroupName }}</view>
                <scroll-view class="emoji-grid" scroll-y :show-scrollbar="false">
                  <view class="emoji-grid-inner">
                    <button
                      v-for="emoji in currentEmojiOptions"
                      :key="emoji"
                      class="emoji-item"
                      @click="chooseEmoji(emoji)"
                    >{{ emoji }}</button>
                  </view>
                </scroll-view>
                <view class="emoji-tabs">
                  <button
                    v-for="group in emojiGroups"
                    :key="group.key"
                    class="emoji-tab"
                    :class="{ on: activeEmojiGroup === group.key }"
                    @click="activeEmojiGroup = group.key"
                  >{{ group.name }}</button>
                </view>
              </view>

              <view class="tool-row">
                <button class="tool-btn" :class="{ active: showEmojiPicker }" @click="toggleEmojiPicker">☺</button>
                <button class="tool-btn" @click="viewProduct">▧</button>
                <text class="counter">{{ inputContent.length }} / 500</text>
              </view>
              <textarea
                v-model="inputContent"
                class="message-input"
                maxlength="500"
                placeholder="请输入回复买家的内容..."
                @confirm="sendMessage"
              />
              <view class="composer-actions">
                <button class="send-btn secondary" @click="viewProduct">查看商品</button>
                <button class="send-btn" @click="sendMessage">发送</button>
              </view>
            </view>
          </template>

          <view v-else class="chat-empty-state">
            <text class="empty-title">请选择一个会话</text>
            <text class="empty-sub">左侧会展示买家对你店铺商品的咨询。</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="active === 'me'" class="content">
      <view class="profile-card">
        <view class="avatar">S</view>
        <view>
          <text class="item-title">{{ user.username || 'seller' }}</text>
          <text class="item-desc">{{ user.phoneMasked || '137****0000' }} · {{ verifiedText }}</text>
        </view>
      </view>
      <view class="menu-grid">
        <view v-for="item in centerModules" :key="item.title" class="menu-card">
          <text class="menu-title">{{ item.title }}</text>
          <text class="item-desc">{{ item.value }} · {{ item.desc }}</text>
        </view>
        <view class="menu-card clickable" @click="toggleRealNamePanel">
          <text class="menu-title">实名认证</text>
          <text class="item-desc">{{ verifiedText }} · 点开查看脱敏资料</text>
        </view>
        <view class="menu-card" @click="logout"><text class="menu-title">&#36864;&#20986;&#30331;&#24405;</text><text class="item-desc">&#22238;&#21040;&#20080;&#23478;&#40664;&#35748;&#31471;</text></view>
      </view>
      <view v-if="showRealNamePanel" class="section realname-panel">
        <view class="section-head">
          <text class="section-title">实名认证模拟</text>
          <text class="link">{{ verifiedText }}</text>
        </view>
        <view class="realname-detail">
          <view class="detail-row">
            <text class="detail-label">认证状态</text>
            <text class="detail-value">{{ verifiedText }}</text>
          </view>
          <view class="detail-row">
            <text class="detail-label">脱敏姓名</text>
            <text class="detail-value">{{ realNameInfo.realName || '暂未提交' }}</text>
          </view>
          <view class="detail-row">
            <text class="detail-label">脱敏证件号</text>
            <text class="detail-value">{{ realNameInfo.idCardMasked || '暂未提交' }}</text>
          </view>
        </view>
        <view class="realname-form">
          <input v-model="realNameForm.realName" class="realname-input" placeholder="真实姓名" />
          <input v-model="realNameForm.idCard" class="realname-input" placeholder="身份证号，提交后只保存脱敏值" />
          <button class="top-action form-action" :loading="submittingRealName" @click="submitRealNameForm">提交实名</button>
        </view>
        <view class="realname-actions">
          <button class="cancel-action" :disabled="!hasRealName" :loading="cancelingRealName" @click="cancelRealNameForm">取消认证</button>
        </view>
      </view>
    </view>

    <view class="role-tabbar">
      <view v-for="item in tabs" :key="item.key" class="tab" :class="{ on: active === item.key }" @click="active = item.key">
        <text>{{ item.label }}</text>
      </view>
    </view>
  </view>
</template>

<script>
import SockJS from 'sockjs-client'
import Stomp from 'stompjs/lib/stomp.js'
import { clearSession, getCachedUser, getToken } from '@/utils/auth.js'
import { fetchMe } from '@/services/auth.js'
import { cancelSellerRealName, fetchSellerCenter, submitSellerRealName } from '@/services/center.js'
import { fetchProduct } from '@/services/shop.js'
import { get, post } from '@/utils/request.js'

const WS_URL = 'http://127.0.0.1:8080/ws'
const PRODUCT_CARD_PREFIX = '__PRODUCT_CARD__'

export default {
  data() {
    return {
      active: 'home',
      user: {},
      center: {},
      showRealNamePanel: false,
      submittingRealName: false,
      cancelingRealName: false,
      keyword: '',
      list: [],
      covId: null,
      messages: [],
      inputContent: '',
      quickOptions: ['您好，商品还在', '支持平台担保', '可以补充细节图', '今天可以发货', '配件信息如下', '价格可以小幅协商'],
      myUserId: null,
      stompClient: null,
      chatSubscription: null,
      focusProduct: null,
      connected: false,
      scrollTop: 0,
      showEmojiPicker: false,
      activeEmojiGroup: 'face',
      emojiGroups: [
        { key: 'face', name: '经典', items: ['😀', '😁', '😂', '🤣', '😊', '😇', '🙂', '😉', '😍', '😘', '😋', '😜', '😎', '🤓', '🤔', '🤗', '😳', '🥺', '😭', '😤', '😡', '😱', '😴', '🤒', '😵', '🤯', '🥳', '😅', '😆', '😬', '🙄', '😏', '😌', '😔', '😮', '🤭', '🤫', '🤐', '😷', '🤧', '🥰', '😚', '😛', '😝', '🤤', '😪', '😫', '😈'] },
        { key: 'hand', name: '手势', items: ['👍', '👎', '👌', '✌️', '🤞', '🤟', '🤙', '👋', '👏', '🙌', '🙏', '💪', '🤝', '🫶', '☝️', '👉', '👈', '👇', '👆', '✋', '🤚', '🖐️', '🫡', '🤲'] },
        { key: 'trade', name: '交易', items: ['💬', '💰', '💸', '🧾', '📦', '🚚', '🎁', '🏷️', '💳', '✅', '❌', '⚠️', '📌', '🔍', '🛒', '⭐', '🔥', '💡', '📮', '⏰', '🧡', '💯', '📱', '💻', '🎧', '📚', '🏠', '☕'] },
        { key: 'heart', name: '心情', items: ['❤️', '🧡', '💛', '💚', '💙', '💜', '🤍', '🤎', '🖤', '💔', '💕', '💞', '💓', '💗', '💖', '💘', '💝', '✨', '🌟', '🎉', '🌈', '☀️', '🌙', '🍀'] }
      ],
      realNameForm: {
        realName: '',
        idCard: ''
      },
      tabs: [
        { key: 'home', label: '\u5de5\u4f5c\u53f0' },
        { key: 'products', label: '\u5546\u54c1' },
        { key: 'orders', label: '\u8ba2\u5355' },
        { key: 'messages', label: '\u6d88\u606f' },
        { key: 'me', label: '\u6211\u7684' }
      ],
      stats: [],
      todos: ['3 \u4e2a\u8ba2\u5355\u5f85\u53d1\u8d27', '1 \u4e2a\u552e\u540e\u5f85\u56de\u590d', '2 \u4ef6\u5546\u54c1\u5efa\u8bae\u8865\u5145\u56fe\u7247'],
      products: [
        { title: 'AirWave Pro', desc: '\u6570\u7801\u5f71\u97f3 · 699', status: '\u5728\u552e' },
        { title: 'ViewTop 27', desc: '\u6570\u7801\u5f71\u97f3 · 680', status: '\u5f85\u5ba1\u6838' },
        { title: 'Songuo Pad 11', desc: '\u56fe\u4e66\u6587\u521b · 2299', status: '\u5728\u552e' }
      ],
      orders: [
        { title: 'AirWave Pro', buyer: '\u4e70\u5bb6 A', price: '699', status: '\u5f85\u53d1\u8d27' },
        { title: 'ViewTop 27', buyer: '\u4e70\u5bb6 B', price: '680', status: '\u552e\u540e' }
      ]
    }
  },
  computed: {
    centerModules() {
      return this.center.modules || []
    },
    realNameInfo() {
      return this.center.realName || {}
    },
    hasRealName() {
      return !!(this.realNameInfo && this.realNameInfo.id)
    },
    verifiedText() {
      const status = this.realNameInfo.status
      if (status === 'approved') return '已实名'
      if (status === 'pending') return '待审核'
      if (status === 'rejected') return '已驳回'
      return '未实名'
    },
    filteredList() {
      const word = this.keyword.trim().toLowerCase()
      if (!word) return this.list
      return this.list.filter(item => [item.title, item.goodsName].some(value => String(value || '').toLowerCase().includes(word)))
    },
    activeConversation() {
      return this.list.find(item => item.covId === this.covId) || null
    },
    currentEmojiOptions() {
      const group = this.emojiGroups.find(item => item.key === this.activeEmojiGroup)
      return group ? group.items : []
    },
    currentEmojiGroupName() {
      const group = this.emojiGroups.find(item => item.key === this.activeEmojiGroup)
      return group ? group.name : '经典'
    }
  },
  watch: {
    active(val) {
      if (val !== 'messages') {
        this.closeFloaters()
        this.disconnect()
        this.connected = false
        this.stompClient = null
      }
    }
  },
  onShow() {
    this.user = getCachedUser() || {}
    this.loadCenter()
    this.fetchMessages()
  },
  onHide() {
    this.disconnect()
  },
  onUnload() {
    this.disconnect()
  },
  methods: {
    closeFloaters() {
      this.showEmojiPicker = false
    },
    appendText(text) {
      this.inputContent = `${this.inputContent}${text}`.slice(0, 500)
    },
    toggleEmojiPicker() {
      this.showEmojiPicker = !this.showEmojiPicker
    },
    chooseEmoji(emoji) {
      this.appendText(emoji)
    },
    async loadCenter() {
      try {
        const body = await fetchSellerCenter()
        if (body.code === 0 && body.data) {
          this.center = body.data
          this.stats = body.data.modules || []
        }
      } catch (e) {
        this.stats = [
          { title: '\u5e97\u94fa\u4fe1\u606f', value: '--', desc: '\u7b49\u5f85\u540e\u7aef\u8fde\u63a5' },
          { title: '\u5546\u54c1\u6982\u89c8', value: '--', desc: '\u6570\u636e\u5e93 goods' },
          { title: '\u8ba2\u5355\u6982\u89c8', value: '--', desc: '\u6570\u636e\u5e93 orders' },
          { title: '\u5e97\u94fa\u4fe1\u7528', value: this.user.credit || 100, desc: '\u5e97\u94fa\u7ecf\u8425\u4fe1\u7528' }
        ]
      }
    },
    toggleRealNamePanel() {
      this.showRealNamePanel = !this.showRealNamePanel
    },
    async submitRealNameForm() {
      if (!this.realNameForm.realName || !this.realNameForm.idCard) {
        uni.showToast({ title: '请填写姓名和证件号', icon: 'none' })
        return
      }
      this.submittingRealName = true
      try {
        const body = await submitSellerRealName(this.realNameForm)
        if (body.code === 0 && body.data) {
          this.center = body.data
          this.realNameForm.realName = ''
          this.realNameForm.idCard = ''
          uni.showToast({ title: '已提交审核', icon: 'success' })
        }
      } catch (e) {
        uni.showToast({ title: '提交失败，请稍后重试', icon: 'none' })
      } finally {
        this.submittingRealName = false
      }
    },
    async cancelRealNameForm() {
      if (!this.hasRealName) {
        uni.showToast({ title: '暂无可取消的认证', icon: 'none' })
        return
      }
      this.cancelingRealName = true
      try {
        const body = await cancelSellerRealName()
        if (body.code === 0 && body.data) {
          this.center = body.data
          this.realNameForm.realName = ''
          this.realNameForm.idCard = ''
          uni.showToast({ title: '已取消认证', icon: 'none' })
        }
      } catch (e) {
        uni.showToast({ title: '取消失败，请稍后重试', icon: 'none' })
      } finally {
        this.cancelingRealName = false
      }
    },
    async fetchMessages() {
      try {
        const res = await get('/api/chat/conversations')
        const rawList = (res.data && res.data.data) ? res.data.data : []
        this.list = rawList.map(item => ({
          covId: item.covId,
          icon: this.avatarText(item.targetName),
          title: item.targetName || '用户',
          time: this.formatTime(item.lastTime),
          sub: this.formatConversationLastMessage(item.lastMessage),
          unreadCount: item.unreadCount || 0,
          goodsImageUrl: item.goodsImageUrl,
          goodsName: item.goodsName,
          goodsId: item.goodsId
        }))
      } catch (e) {
        console.error('加载消息失败', e)
      }
    },
    async open(item) {
      if (item.unreadCount > 0) {
        item.unreadCount = 0
      }
      try {
        await post(`/api/chat/${item.covId}/read`)
      } catch (e) {
        console.error('标记已读接口调用失败', e)
      }
      if (this.covId === item.covId) return
      this.disconnect()
      this.connected = false
      this.stompClient = null
      this.covId = item.covId
      this.messages = []
      this.inputContent = ''
      this.focusProduct = null
      this.closeFloaters()
      await this.initChat()
    },
    async initChat() {
      try {
        await this.fetchUserInfo()
        await Promise.all([this.loadHistory(), this.loadConversationProduct()])
        this.connectWebSocket()
        setTimeout(() => this.sendReadReceipt(), 1000)
      } catch (error) {
        console.error('聊天初始化失败', error)
        uni.showToast({ title: '聊天启动失败，请重试', icon: 'none' })
      }
    },
    async loadConversationProduct() {
      const active = this.activeConversation
      const goodsId = active && active.goodsId
      if (!goodsId) {
        this.focusProduct = null
        return null
      }
      try {
        const body = await fetchProduct(goodsId)
        if (body && body.code === 0 && body.data) {
          this.focusProduct = body.data
          return body.data
        }
      } catch (e) {
        console.error('加载商品失败', e)
      }
      this.focusProduct = null
      return null
    },
    async fetchUserInfo() {
      const body = await fetchMe()
      if (body && body.code === 0 && body.data) {
        this.myUserId = body.data.userId
      }
    },
    async loadHistory() {
      const res = await get(`/api/chat/conversations/${this.covId}/messages`)
      const rawList = (res.data && res.data.data) ? res.data.data : []
      this.messages = this.processMessages(rawList)
      this.$nextTick(() => this.scrollToBottom())
    },
    connectWebSocket() {
      if (this.connected || !this.covId) return
      const token = getToken()
      const socket = new SockJS(WS_URL)
      this.stompClient = Stomp.over(socket)
      this.stompClient.debug = null
      this.stompClient.connect(
        { Authorization: token ? `Bearer ${token}` : '' },
        () => {
          this.connected = true
          this.subscribeTopic()
        },
        (error) => {
          console.error('WebSocket 连接失败', error)
          uni.showToast({ title: '实时聊天连接失败', icon: 'none' })
        }
      )
    },
    sendReadReceipt() {
      if (this.stompClient && this.connected) {
        this.stompClient.send(
          '/app/chat/read',
          {},
          JSON.stringify({
            covId: this.covId,
            readerId: this.myUserId
          })
        )
      }
    },
    shouldShowTime(newMsg, prevMsg) {
      if (!prevMsg) return true
      const current = new Date(newMsg.createTime).getTime()
      const prev = new Date(prevMsg.createTime).getTime()
      return (current - prev) > 5 * 60 * 1000
    },
    processMessages(list) {
      return list.map((msg, index) => {
        const prevMsg = index > 0 ? list[index - 1] : null
        return this.normalizeMessage({
          ...msg,
          showTime: index === 0 || this.shouldShowTime(msg, prevMsg)
        }, prevMsg)
      })
    },
    normalizeMessage(msg, prevMsg) {
      return {
        ...msg,
        card: this.parseProductCard(msg.content),
        showTime: msg.showTime !== undefined ? msg.showTime : this.shouldShowTime(msg, prevMsg)
      }
    },
    parseProductCard(content) {
      if (!content || typeof content !== 'string' || !content.startsWith(PRODUCT_CARD_PREFIX)) return null
      try {
        return JSON.parse(content.slice(PRODUCT_CARD_PREFIX.length))
      } catch (e) {
        return null
      }
    },
    formatConversationLastMessage(content) {
      const card = this.parseProductCard(content)
      if (card) return `[商品卡片] ${card.title || '商品'}`
      return content || '暂无消息'
    },
    isDuplicateMessage(chatMessage) {
      return this.messages.some(m => m.cmId && chatMessage.cmId && m.cmId === chatMessage.cmId)
    },
    subscribeTopic() {
      if (!this.stompClient || !this.covId) return
      if (this.chatSubscription) {
        this.chatSubscription.unsubscribe()
        this.chatSubscription = null
      }
      const covId = this.covId
      this.chatSubscription = this.stompClient.subscribe(`/topic/chat/${covId}`, (message) => {
        if (!message.body) return
        try {
          const chatMessage = JSON.parse(message.body)
          if (chatMessage.type !== 'CHAT_MESSAGE') return
          if (Number(chatMessage.covId) !== Number(this.covId)) return
          if (this.isDuplicateMessage(chatMessage)) return
          const lastMsg = this.messages[this.messages.length - 1]
          const showTime = this.shouldShowTime(chatMessage, lastMsg)
          this.messages.push(this.normalizeMessage({ ...chatMessage, showTime }, lastMsg))
          this.$nextTick(() => {
            this.scrollToBottom()
            if (chatMessage.senderId !== this.myUserId) {
              this.sendReadReceipt()
            }
          })
        } catch (e) {
          console.error('解析消息失败', e)
        }
      })
      this.stompClient.subscribe('/user/queue/chat/read-status', (message) => {
        const status = JSON.parse(message.body)
        if (status.type === 'STATUS_UPDATE') {
          this.messages.forEach(m => {
            if (m.senderId === this.myUserId && !m.isRead) {
              m.isRead = true
            }
          })
        }
      })
    },
    disconnect() {
      if (this.chatSubscription) {
        this.chatSubscription.unsubscribe()
        this.chatSubscription = null
      }
      if (this.stompClient && this.connected) {
        try {
          this.stompClient.disconnect(() => {
            this.connected = false
            this.stompClient = null
          })
        } catch (e) {
          console.warn('WebSocket 断开连接失败', e)
        }
      }
    },
    sendQuick(text) {
      this.inputContent = text
      this.sendMessage()
    },
    async sendChatContent(content) {
      if (!content || !this.covId) return false
      const payload = { covId: this.covId, content, type: 'CHAT_MESSAGE' }
      if (this.stompClient && this.connected) {
        const token = getToken()
        this.stompClient.send(
          `/app/chat/${this.covId}`,
          { Authorization: `Bearer ${token}` },
          JSON.stringify(payload)
        )
        return true
      }
      try {
        await post(`/api/chat/conversations/${this.covId}/messages`, payload)
        return true
      } catch (error) {
        console.error('消息发送失败', error)
        uni.showToast({ title: '消息发送失败，请检查网络', icon: 'none' })
        return false
      }
    },
    async sendMessage() {
      const content = this.inputContent && this.inputContent.trim()
      if (!content || !this.covId) return
      const sent = await this.sendChatContent(content)
      if (sent) this.inputContent = ''
    },
    async viewProduct() {
      if (!this.covId) return
      let product = this.focusProduct
      if (!product || !product.id) {
        product = await this.loadConversationProduct()
      }
      if (!product || !product.id) {
        uni.showToast({ title: '暂无商品信息', icon: 'none' })
        return
      }
      this.openProductById(product.id)
    },
    openProductById(id) {
      if (!id) return
      uni.navigateTo({ url: `/pages/goods/detail?id=${id}` })
    },
    priceLabel(price) {
      if (price === null || price === undefined || price === '') return '价格待确认'
      return `¥${Number(price).toFixed(2)}`
    },
    sceneLabel(scene) {
      if (scene === 'new') return '新品'
      if (scene === 'used') return '二手'
      return scene || ''
    },
    async triggerAiBargain() {
      try {
        uni.showLoading({ title: 'AI 思考中...' })
        const res = await post(`/api/chat/conversations/${this.covId}/ai-bargain`)
        const data = (res.data && res.data.data) ? res.data.data : {}
        uni.hideLoading()
        uni.showToast({
          title: data.source === 'ai' ? 'AI 已生成回复建议' : 'AI 不可用，已用兜底建议',
          icon: 'none'
        })
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: 'AI 议价请求失败', icon: 'none' })
      }
    },
    scrollToBottom() {
      this.$nextTick(() => {
        this.scrollTop = this.scrollTop === 999999 ? 999998 : 999999
      })
    },
    formatChatTime(value) {
      if (!value) return ''
      const date = new Date(value)
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    },

    goPublish() {
      uni.navigateTo({ url: '/pages/publish/publish' })
    },
    logout() {
      clearSession()
      uni.reLaunch({ url: '/pages/home/home' })
    },
    avatarText(name) {
      return String(name || '聊').slice(0, 1).toUpperCase()
    },
    formatTime(dateStr) {
      if (!dateStr) return ''
      const timePart = dateStr.split('T')[1].split('.')[0]
      const [h, m] = timePart.split(':')
      return `${h}:${m}`
    }
  }
}
</script>

<style scoped>
@import './role-mobile.css';

</style>
