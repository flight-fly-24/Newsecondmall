<template>
  <view class="me-page">
    <view class="topbar">
      <view class="content-wrap topbar-inner">
        <view class="brand" @click="navTo('/pages/home/home')">
          <view class="brand-mark">
            <image class="brand-logo" src="/static/logo.png" mode="aspectFit"></image>
          </view>
          <view>
            <text class="brand-name">松果集市</text>
            <text class="brand-sub">可信的新旧商品流转平台</text>
          </view>
        </view>
        <view class="web-nav">
          <text class="nav-link" @click="navTo('/pages/home/home')">首页</text>
          <text class="nav-link" @click="navTo('/pages/browse/browse')">发现</text>
          <text class="nav-link" @click="navTo('/pages/cart/cart')">购物车</text>
          <text class="nav-link" @click="navTo('/pages/message/message')">消息</text>
					<text class="nav-link" @click="navTo('/pages/ai-assistant/ai-assistant')">AI 助手</text>
          <text class="nav-link on">我的</text>
        </view>
        <view class="top-actions"></view>
      </view>
    </view>

    <view class="me-wrap">
      <view v-if="!loggedIn" class="login-card">
        <text class="login-title">登录松果集市</text>
        <text class="login-desc">登录后查看订单、收藏、足迹、店铺关注和信用记录。</text>
        <view class="login-actions">
          <button class="primary" @click="goLogin">登录</button>
          <button class="ghost" @click="goRegister">注册</button>
        </view>
      </view>

      <view v-else class="account-layout">
        <view class="side">
          <view class="side-profile">
            <view class="avatar avatar-clickable" @click="chooseAvatar">
              <image v-if="avatarUrl" class="avatar-img" :src="avatarUrl" mode="aspectFill"></image>
              <text v-else>{{ avatar }}</text>
            </view>
            <view class="side-user">
              <text class="side-name">{{ user.username || '松果用户' }}</text>
              <text class="side-role">{{ roleLabel }} · 信用 {{ user.credit || 100 }}</text>
            </view>
          </view>

          <view v-for="group in navGroups" :key="group.title" class="nav-group">
            <view class="group-title">{{ group.title }}</view>
            <view
              v-for="item in group.items"
              :key="item.key"
              class="side-item"
              :class="{ active: activeNav === item.key }"
              @click="handleNav(item)"
            >
              <text class="side-icon">{{ item.icon }}</text>
              <text class="side-text">{{ item.label }}</text>
              <text v-if="item.badge" class="side-badge">{{ item.badge }}</text>
            </view>
          </view>
        </view>

        <view class="main">
          <view class="profile-card">
            <view class="profile-identity">
              <view class="profile-avatar avatar-clickable" @click="chooseAvatar">
                <image v-if="avatarUrl" class="avatar-img" :src="avatarUrl" mode="aspectFill"></image>
                <text v-else>{{ avatar }}</text>
                <view class="avatar-edit">更换头像</view>
              </view>
              <view class="profile-main">
                <text class="name">{{ user.username || '松果用户' }}</text>
                <text class="meta">{{ center.phoneMasked || user.phoneMasked || '未绑定手机' }} · {{ verifiedText }}</text>
                <view class="chips">
                  <text class="chip">{{ roleLabel }}</text>
                  <text class="chip">信用 {{ user.credit || 100 }}</text>
                  <text class="chip">{{ center.accountStatus || '正常' }}</text>
                </view>
              </view>
            </view>
            <view class="profile-actions">
              <button v-if="isSeller" class="primary small" @click="enterRoleHome">卖家工作台</button>
              <button v-if="isAdmin" class="primary small" @click="enterRoleHome">管理后台</button>
              <button class="ghost small" @click="logout">退出登录</button>
            </view>
          </view>

          <view v-if="activeNav === 'overview'" class="dashboard">
            <view v-if="isBuyer" class="panel order-panel">
              <view class="section-head">
                <text class="section-title">我的订单</text>
                <text class="section-more" @click="goOrders">查看全部</text>
              </view>
              <view class="order-grid">
                <view v-for="item in buyerOrderCards" :key="item.label" class="order-item" @click="openOrderTab(item)">
                  <text class="order-icon">{{ item.icon }}</text>
                  <text class="order-label">{{ item.label }}</text>
                  <text v-if="item.badge" class="order-count">{{ item.badge }}</text>
                </view>
              </view>
            </view>

            <view v-if="isSeller" class="panel order-panel">
              <view class="section-head">
                <text class="section-title">卖家经营</text>
                <text class="section-more" @click="enterRoleHome">进入工作台</text>
              </view>
              <view class="seller-actions-grid">
                <view class="seller-action" @click="enterRoleHome">
                  <text class="action-title">订单概览</text>
                  <text class="action-desc">查看买家订单和售后</text>
                </view>
                <view class="seller-action" @click="navTo('/pages/publish/publish')">
                  <text class="action-title">发布商品</text>
                  <text class="action-desc">新品或二手上架审核</text>
                </view>
                <view class="seller-action" @click="navTo('/pages/user/published')">
                  <text class="action-title">我的发布</text>
                  <text class="action-desc">管理审核状态和在售商品</text>
                </view>
              </view>
            </view>

            <view class="stat-grid">
              <view v-for="item in summaryCards" :key="item.title" class="stat-card" @click="handleSummary(item)">
                <text class="stat-value">{{ item.value }}</text>
                <text class="stat-label">{{ item.title }}</text>
                <text class="stat-hint">{{ item.desc }}</text>
              </view>
            </view>

            <view v-if="isBuyer" class="panel">
              <view class="section-head">
                <text class="section-title">足迹收藏</text>
                <text class="section-more">数据库同步</text>
              </view>
              <view class="shortcut-grid">
                <view v-for="item in buyerShortcuts" :key="item.key" :data-testid="'buyer-nav-' + item.key" class="shortcut" @click="handleNav(item)">
                  <text class="shortcut-title">{{ item.label }}</text>
                  <text class="shortcut-desc">{{ item.desc }}</text>
                </view>
              </view>
            </view>
          </view>

          <view v-if="['credit', 'favorite', 'history', 'follow', 'topicFollow'].includes(activeNav)" class="panel interaction-panel">
            <view class="section-head">
              <text class="section-title">{{ activePanelTitle }}</text>
              <text class="section-more">数据库同步</text>
            </view>
            <view v-if="activeNav !== 'credit'" class="panel-actions">
              <button class="ghost small" :loading="panelLoading" @click="clearInteraction">清空记录</button>
            </view>
            <view class="interaction-list">
              <view v-if="!interactionItems.length" class="empty-line">暂无记录。收藏、浏览商品或关注店铺后会自动同步到这里。</view>
              <view
                v-for="item in interactionItems"
                :key="item.type + '-' + item.id"
                class="interaction-row"
                :class="{ clickable: canOpenInteraction(item) }"
                @click="openInteraction(item)"
              >
                <view>
                  <text data-testid="interaction-title" class="interaction-title">{{ item.title }}</text>
                  <text class="interaction-desc">{{ item.desc }} · {{ item.createdAt }}</text>
                </view>
                <text v-if="canOpenInteraction(item)" class="interaction-arrow">›</text>
                <text v-else class="interaction-type">{{ item.type }}</text>
              </view>
            </view>
          </view>

          <view v-if="activeNav === 'realname'" class="panel realname-panel">
            <view class="section-head">
              <text class="section-title">实名认证</text>
              <text class="section-more">{{ verifiedText }}</text>
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
              <input v-model="realNameForm.idCard" class="realname-input" placeholder="身份证号，提交后仅保存脱敏值" />
              <button class="primary small" :loading="submittingRealName" @click="submitRealNameForm">提交实名</button>
            </view>
            <view class="realname-actions">
              <button class="ghost small" :disabled="!hasRealName" :loading="cancelingRealName" @click="cancelRealNameForm">取消认证</button>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { getToken, getCachedUser, clearSession, goRoleHome, setSession } from '@/utils/auth.js'
import { fetchMe, updateProfile, uploadAvatarImage } from '@/services/auth.js'
import { cancelRealName, cancelSellerRealName, clearBuyerItems, fetchBuyerCenter, fetchBuyerItems, fetchSellerCenter, submitRealName, submitSellerRealName } from '@/services/center.js'
import { fetchOrders } from '@/services/shop.js'
import { resolveImageUrl } from '@/utils/media.js'

export default {
  data() {
    return {
      loggedIn: false,
      user: {},
      center: {},
      orders: [],
      activeNav: 'overview',
      interactionItems: [],
      panelLoading: false,
      submittingRealName: false,
      cancelingRealName: false,
      realNameForm: {
        realName: '',
        idCard: ''
      },
      seenBadgeIds: {
        orders: [],
        reviews: []
      }
    }
  },
  computed: {
    isBuyer() {
      return this.user.role === 'buyer'
    },
    isSeller() {
      return this.user.role === 'seller'
    },
    isAdmin() {
      return this.user.role === 'admin'
    },
    roleLabel() {
      if (this.isSeller) return '卖家'
      if (this.isAdmin) return '管理员'
      return '买家'
    },
    avatar() {
      return (this.user.username || 'M').slice(0, 1).toUpperCase()
    },
    avatarUrl() {
      return this.user.avatarUrl ? resolveImageUrl(this.user.avatarUrl) : ''
    },
    verifiedText() {
      const status = this.center.realName && this.center.realName.status
      if (status === 'approved') return '已实名'
      if (status === 'pending') return '待审核'
      if (status === 'rejected') return '已驳回'
      return '未实名'
    },
    realNameInfo() {
      return this.center.realName || {}
    },
    hasRealName() {
      return !!(this.realNameInfo && this.realNameInfo.id)
    },
    activePanelTitle() {
      const map = {
        credit: '信用分记录',
        favorite: '商品收藏',
        history: '浏览足迹',
        follow: '关注店铺',
        topicFollow: '关注话题'
      }
      return map[this.activeNav] || '记录'
    },
    navGroups() {
      if (this.isSeller) {
        return [
          { title: '卖家中心', items: [
            { key: 'overview', label: '工作台概览', icon: '▣' },
            { key: 'sellerDashboard', label: '卖家工作台', icon: '▤', action: 'sellerDashboard' },
            { key: 'publish', label: '发布商品', icon: '+', action: 'publish' },
            { key: 'published', label: '我的发布', icon: '□', action: 'published' }
          ] },
          { title: '账户设置', items: [
            { key: 'realname', label: '实名认证', icon: '◇' },
            { key: 'credit', label: '信用记录', icon: '☆' },
            { key: 'logout', label: '退出登录', icon: '×', action: 'logout' }
          ] }
        ]
      }
      if (this.isAdmin) {
        return [
          { title: '管理中心', items: [
            { key: 'overview', label: '账号概览', icon: '▣' },
            { key: 'adminDashboard', label: '管理后台', icon: '▤', action: 'adminDashboard' }
          ] },
          { title: '账户设置', items: [
            { key: 'logout', label: '退出登录', icon: '×', action: 'logout' }
          ] }
        ]
      }
      return [
        { title: '订单中心', items: [
          { key: 'overview', label: '我的概览', icon: '▣' },
          { key: 'orders', label: '我的订单', icon: '□', action: 'orders', badge: this.unseenOrderCount || '' },
          { key: 'reviewOrders', label: '待评价', icon: '☆', action: 'reviewOrders', badge: this.unseenReviewCount || '' }
        ] },
        { title: '足迹收藏', items: [
          { key: 'favorite', label: '商品收藏', icon: '♡', desc: '追踪心仪商品' },
          { key: 'history', label: '浏览足迹', icon: '○', desc: '找回看过的商品' },
          { key: 'follow', label: '关注店铺', icon: '◇', desc: '查看店铺上新' },
          { key: 'topicFollow', label: '关注话题', icon: '#', desc: '回到感兴趣的讨论' }
        ] },
        { title: '账户设置', items: [
          { key: 'realname', label: '实名认证', icon: '▧' },
          { key: 'credit', label: '信用记录', icon: '☆' },
          { key: 'address', label: '地址管理', icon: '⌂', action: 'address' },
          { key: 'logout', label: '退出登录', icon: '×', action: 'logout' }
        ] }
      ]
    },
    summaryCards() {
      if (this.isSeller) {
        const modules = this.center.modules || []
        return modules.length ? modules : [
          { title: '订单概览', value: '--', desc: '数据库 orders' },
          { title: '店铺信用', value: this.user.credit || 100, desc: '店铺经营信用' },
          { title: '商品管理', value: '--', desc: '我的发布与审核' },
          { title: '消息沟通', value: '--', desc: '买家咨询会话' }
        ]
      }
      return [
        { title: '我的订单', value: this.orders.length, desc: '已同步数据库订单', action: 'orders' },
        { title: '待评价', value: this.pendingReviewCount, desc: '完成订单后可评价', action: 'reviewOrders' },
        { title: '信用分', value: this.user.credit || 100, desc: '来自 users.credit', type: 'credit' },
        { title: '实名认证', value: this.verifiedText, desc: '提交后由管理员审核', type: 'realname' }
      ]
    },
    pendingReviewCount() {
      return this.pendingReviewIds.length
    },
    orderIds() {
      return this.collectOrderIds(this.orders)
    },
    pendingReviewIds() {
      return this.collectOrderIds(this.orders.filter((item) => item.reviewable))
    },
    unseenOrderCount() {
      return this.countUnseen(this.orderIds, this.seenBadgeIds.orders)
    },
    unseenReviewCount() {
      return this.countUnseen(this.pendingReviewIds, this.seenBadgeIds.reviews)
    },
    buyerOrderCards() {
      return [
        { label: '全部订单', icon: '□', badge: this.unseenOrderCount || '', action: 'orders' },
        { label: '已完成', icon: '✓', action: 'orders' },
        { label: '待评价', icon: '☆', badge: this.unseenReviewCount || '', action: 'reviewOrders' },
        { label: '已评价', icon: '●', action: 'orders' }
      ]
    },
    buyerShortcuts() {
      return [
        { key: 'favorite', label: '商品收藏', desc: '追踪心仪商品' },
        { key: 'history', label: '浏览足迹', desc: '找回看过的商品' },
        { key: 'follow', label: '关注店铺', desc: '查看店铺上新' },
        { key: 'topicFollow', label: '关注话题', desc: '回到感兴趣的讨论' }
      ]
    }
  },
  onShow() {
    this.refresh()
  },
  methods: {
    async refresh() {
      const token = getToken()
      if (!token) {
        this.loggedIn = false
        this.user = {}
        return
      }
      const cached = getCachedUser()
      if (cached) {
        this.user = cached
        this.loggedIn = true
      }
      try {
        const body = await fetchMe()
        if (body.code === 0 && body.data) {
          this.user = body.data
          this.loggedIn = true
          setSession(token, body.data)
          if (this.user.role === 'buyer') {
            this.loadSeenBadges()
            await Promise.all([this.loadBuyerCenter(), this.loadOrders()])
          } else if (this.user.role === 'seller') {
            await this.loadSellerCenter()
          }
        }
      } catch (e) {
        clearSession()
        this.loggedIn = false
        this.user = {}
      }
    },
    async loadBuyerCenter() {
      try {
        const body = await fetchBuyerCenter()
        this.center = body.code === 0 && body.data ? body.data : {}
      } catch (e) {
        this.center = {}
      }
    },
    chooseAvatar() {
      if (!this.loggedIn) return
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: async (res) => {
          const filePath = res.tempFilePaths && res.tempFilePaths[0]
          if (!filePath) return
          await this.saveAvatar(filePath)
        }
      })
    },
    async saveAvatar(filePath) {
      uni.showLoading({ title: '上传头像中' })
      try {
        const uploaded = await uploadAvatarImage(filePath)
        if (!uploaded || uploaded.code !== 0 || !uploaded.data) {
          throw new Error(uploaded && uploaded.message ? uploaded.message : 'upload failed')
        }
        const body = await updateProfile({ avatarUrl: uploaded.data })
        if (body.code === 0 && body.data) {
          this.user = body.data
          setSession(getToken(), body.data)
          uni.showToast({ title: '头像已更新', icon: 'success' })
        } else {
          throw new Error(body && body.message ? body.message : 'save failed')
        }
      } catch (e) {
        uni.showToast({ title: '头像更新失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    },
    async loadSellerCenter() {
      try {
        const body = await fetchSellerCenter()
        this.center = body.code === 0 && body.data ? body.data : {}
      } catch (e) {
        this.center = {}
      }
    },
    async loadOrders() {
      try {
        const body = await fetchOrders()
        this.orders = body.code === 0 && Array.isArray(body.data) ? body.data : []
      } catch (e) {
        this.orders = []
      }
    },
    handleNav(item) {
      if (item.action === 'orders') return this.goOrders()
      if (item.action === 'reviewOrders') return this.goOrders('待评价')
      if (item.action === 'sellerDashboard' || item.action === 'adminDashboard') return this.enterRoleHome()
      if (item.action === 'publish') return this.navTo('/pages/publish/publish')
      if (item.action === 'published') return this.navTo('/pages/user/published')
      if (item.action === 'address') return this.navTo('/pages/address/list')
      if (item.action === 'logout') return this.logout()
      this.activeNav = item.key
      if (['credit', 'favorite', 'history', 'follow', 'topicFollow'].includes(this.activeNav)) this.loadInteractionItems()
    },
    handleSummary(item) {
      if (item.action === 'orders') return this.goOrders()
      if (item.action === 'reviewOrders') return this.goOrders('待评价')
      if (item.type) this.handleNav({ key: item.type })
    },
    openOrderTab(item) {
      if (item.action === 'reviewOrders') return this.goOrders('待评价')
      this.goOrders()
    },
    async loadInteractionItems() {
      if (!['credit', 'favorite', 'history', 'follow', 'topicFollow'].includes(this.activeNav)) return
      if (!this.isBuyer) {
        this.interactionItems = this.activeNav === 'credit'
          ? [{ id: 'seller-credit', title: this.isSeller ? '店铺信用' : '账号信用', desc: `当前信用 ${this.user.credit || 100}`, type: 'credit', createdAt: '实时' }]
          : []
        return
      }
      this.panelLoading = true
      try {
        const body = await fetchBuyerItems(this.activeNav)
        if (body.code === 0) this.interactionItems = body.data || []
      } finally {
        this.panelLoading = false
      }
    },
    interactionTargetId(item) {
      const raw = item && (item.targetId || item.itemId || item.goodsId || item.storeId || item.topicId)
      const value = Number(raw)
      return Number.isInteger(value) && value > 0 ? value : 0
    },
    canOpenInteraction(item) {
      if (!item || this.activeNav === 'credit') return false
      if (this.activeNav === 'follow') return !!(this.interactionTargetId(item) || item.title)
      return this.interactionTargetId(item) > 0
    },
    openInteraction(item) {
      if (!this.canOpenInteraction(item)) return
      const targetId = this.interactionTargetId(item)
      if (this.activeNav === 'favorite' || this.activeNav === 'history') {
        this.navTo('/pages/goods/detail?id=' + encodeURIComponent(targetId))
        return
      }
      if (this.activeNav === 'follow') {
        const query = targetId
          ? '?id=' + encodeURIComponent(targetId)
          : '?name=' + encodeURIComponent(item.title || '')
        this.navTo('/pages/store/store' + query)
        return
      }
      if (this.activeNav === 'topicFollow') {
        this.navTo('/pages/topic/detail?id=' + encodeURIComponent(targetId))
      }
    },
    async clearInteraction() {
      if (!['favorite', 'history', 'follow', 'topicFollow'].includes(this.activeNav)) return
      this.panelLoading = true
      try {
        const body = await clearBuyerItems(this.activeNav)
        if (body.code === 0 && body.data) {
          this.center = body.data
          this.interactionItems = []
          uni.showToast({ title: '已清空', icon: 'none' })
        }
      } finally {
        this.panelLoading = false
      }
    },
    async submitRealNameForm() {
      if (!this.realNameForm.realName || !this.realNameForm.idCard) {
        uni.showToast({ title: '请填写姓名和证件号', icon: 'none' })
        return
      }
      this.submittingRealName = true
      try {
        const body = this.isSeller ? await submitSellerRealName(this.realNameForm) : await submitRealName(this.realNameForm)
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
        const body = this.isSeller ? await cancelSellerRealName() : await cancelRealName()
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
    goLogin() {
      uni.navigateTo({ url: '/pages/auth/login' })
    },
    goRegister() {
      uni.navigateTo({ url: '/pages/auth/register' })
    },
    collectOrderIds(list) {
      return (list || []).map((item) => String(item && item.id ? item.id : '')).filter(Boolean)
    },
    countUnseen(currentIds, seenIds) {
      const seen = new Set(seenIds || [])
      return currentIds.filter((id) => !seen.has(id)).length
    },
    badgeUserKey() {
      return String((this.user && (this.user.userId || this.user.id || this.user.username)) || '')
    },
    loadSeenBadges() {
      const userKey = this.badgeUserKey()
      if (!userKey) {
        this.seenBadgeIds = { orders: [], reviews: [] }
        return
      }
      try {
        const all = uni.getStorageSync('me_nav_badge_seen') || {}
        const mine = all[userKey] || {}
        this.seenBadgeIds = {
          orders: Array.isArray(mine.orders) ? mine.orders.map(String) : [],
          reviews: Array.isArray(mine.reviews) ? mine.reviews.map(String) : []
        }
      } catch (e) {
        this.seenBadgeIds = { orders: [], reviews: [] }
      }
    },
    markBadgesSeen(kind) {
      if (kind === 'reviews') {
        this.seenBadgeIds = { ...this.seenBadgeIds, reviews: this.pendingReviewIds.slice() }
      } else {
        this.seenBadgeIds = { ...this.seenBadgeIds, orders: this.orderIds.slice() }
      }
      const userKey = this.badgeUserKey()
      if (!userKey) return
      try {
        const all = uni.getStorageSync('me_nav_badge_seen') || {}
        all[userKey] = this.seenBadgeIds
        uni.setStorageSync('me_nav_badge_seen', all)
      } catch (e) {}
    },
    goOrders(tab = '') {
      this.markBadgesSeen(tab === '待评价' ? 'reviews' : 'orders')
      const query = tab ? '?tab=' + encodeURIComponent(tab) : ''
      uni.navigateTo({ url: '/pages/order/list' + query })
    },
    navTo(url) {
      if (['/pages/home/home', '/pages/browse/browse', '/pages/cart/cart', '/pages/message/message', '/pages/user/index'].includes(url)) {
        uni.switchTab({ url })
        return
      }
      uni.navigateTo({ url })
    },
    enterRoleHome() {
      goRoleHome(this.user, 'reLaunch')
    },
    logout() {
      clearSession()
      this.loggedIn = false
      this.user = {}
      uni.showToast({ title: '已退出登录', icon: 'none' })
    }
  }
}
</script>

<style scoped>
.me-page {
  min-height: 100vh;
  background: #f5f6f8;
  padding: 0;
  padding-bottom: 84px;
  box-sizing: border-box;
  color: #17231d;
}
.me-wrap {
  max-width: 1260px;
  margin: 0 auto;
}
.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  margin: 0 0 22px;
  background: rgba(255,255,255,.94);
  backdrop-filter: blur(18px);
  border-bottom: 1px solid rgba(203, 213, 225, .55);
  box-shadow: 0 10px 40px rgba(60,64,67,.06);
}
.topbar + .me-wrap {
  padding: 0 22px;
  box-sizing: border-box;
}
.topbar-inner {
  display: grid;
  grid-template-columns: 300px 350px minmax(0, 1fr);
  align-items: center;
  gap: 18px;
  height: 82px;
  padding: 0 22px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}
.brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
}
.brand-logo {
  width: 100%;
  height: 100%;
}
.brand-name,
.brand-sub,
.side-name,
.side-role,
.name,
.meta,
.chip,
.section-title,
.section-more,
.stat-value,
.stat-label,
.stat-hint,
.order-icon,
.order-label,
.action-title,
.action-desc,
.shortcut-title,
.shortcut-desc {
  display: block;
}
.brand-name {
  font-size: 20px;
  font-weight: 900;
  color: #202124;
}
.brand-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #667085;
}
.web-nav {
		overflow-x: auto;
			white-space: nowrap;
  justify-self: center;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px;
  height: 50px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid rgba(203, 213, 225, .72);
  box-sizing: border-box;
  box-shadow: 0 14px 38px rgba(60,64,67,.08);
}
.top-actions {
  min-height: 1px;
}
		.nav-link {
  width: auto;
		padding: 0 8px;
		flex-shrink: 0;
  height: 38px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5f6b85;
  font-size: 12px;
  font-weight: 800;
}
.nav-link.on,
.nav-link:hover {
  background: linear-gradient(135deg, #ffffff, #f5f7fa);
  color: #12372a;
  box-shadow: 0 10px 26px rgba(18, 55, 42, .14);
}
.login-card,
.profile-card,
.side,
.panel,
.stat-card {
  background: #fff;
  border: 1px solid rgba(222, 226, 230, .95);
  border-radius: 8px;
  box-shadow: 0 12px 36px rgba(31, 41, 55, .06);
}
.login-card {
  max-width: 760px;
  margin: 120px auto 0;
  padding: 36px;
  text-align: center;
}
.login-title {
  display: block;
  font-size: 30px;
  font-weight: 900;
}
.login-desc {
  display: block;
  margin-top: 12px;
  color: #667085;
  font-size: 15px;
}
.login-actions {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 30px;
}
button {
  margin: 0;
  width: 200rpx;  /* 或使用 rpx 单位，更灵活 */
  height: 80rpx;
  padding: 0;
  border: 0;
  line-height: 1;
}
button::after {
  border: 0;
}
.primary,
.ghost {
  height: 46px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
  box-sizing: border-box;
}
.primary {
  background: #12372a;
  color: #fff;
}
.ghost {
  background: #fff;
  color: #12372a;
  border: 1px solid rgba(95, 99, 104, .22);
}
.primary.small,
.ghost.small {
  width: 126px;
  height: 40px;
  font-size: 13px;
}
.account-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}
.side {
  position: sticky;
  top: 104px;
  padding: 18px;
}
.side-profile {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 18px;
  border-bottom: 1px solid #edf1ee;
}
.avatar {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  background: #12372a;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 900;
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
}
.avatar-clickable {
  cursor: pointer;
}
.avatar-img {
  width: 100%;
  height: 100%;
  display: block;
}
.profile-avatar {
  width: 84px;
  height: 84px;
  border-radius: 8px;
  background: #12372a;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  font-weight: 900;
  position: relative;
  overflow: hidden;
  flex-shrink: 0;
}
.avatar-edit {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(18, 55, 42, .82);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
}
.side-user {
  min-width: 0;
}
.side-name {
  font-size: 16px;
  font-weight: 900;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.side-role {
  margin-top: 5px;
  color: #667085;
  font-size: 12px;
}
.nav-group {
  padding-top: 16px;
}
.group-title {
  margin-bottom: 8px;
  color: #98a2b3;
  font-size: 12px;
  font-weight: 900;
}
.side-item {
  height: 42px;
  border-radius: 8px;
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
  color: #344054;
  box-sizing: border-box;
}
.side-item.active,
.side-item:hover {
  background: #eef5f0;
  color: #12372a;
  font-weight: 900;
}
.side-icon,
.side-text,
.side-badge {
  display: flex;
  align-items: center;
}
.side-icon {
  justify-content: center;
  font-weight: 900;
}
.side-text {
  min-width: 0;
  font-size: 14px;
}
.side-badge {
  justify-content: center;
  min-width: 22px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: #d66a2c;
  color: #fff;
  font-size: 11px;
  box-sizing: border-box;
}
.main {
  min-width: 0;
}
.profile-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 28px;
  background: linear-gradient(135deg, #fff 0%, #f8fbf9 100%);
}
.profile-identity {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
  flex: 1;
  text-align: left;
}
.profile-main {
  min-width: 0;
  flex: 1;
  text-align: left;
}
.name,
.meta {
  display: block;
  text-align: left;
}
.name {
  font-size: 26px;
  font-weight: 900;
}
.meta {
  margin-top: 6px;
  color: #667085;
  font-size: 14px;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.chip {
  padding: 6px 11px;
  border-radius: 999px;
  background: #eef5f0;
  color: #12372a;
  font-size: 13px;
  font-weight: 800;
}
.profile-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.dashboard,
.interaction-list {
  display: grid;
  gap: 16px;
  margin-top: 16px;
}
.panel {
  padding: 22px;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.section-title {
  font-size: 20px;
  font-weight: 900;
}
.section-more {
  color: #12372a;
  font-size: 14px;
  font-weight: 900;
}
.order-grid,
.seller-actions-grid,
.shortcut-grid,
.stat-grid {
  display: grid;
  gap: 14px;
}
.order-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 18px;
}
.order-item,
.seller-action,
.shortcut,
.stat-card {
  border-radius: 8px;
  background: #f8faf9;
  border: 1px solid #edf1ee;
  box-sizing: border-box;
}
.order-item {
  position: relative;
  min-height: 106px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.order-icon {
  color: #12372a;
  font-size: 28px;
  font-weight: 900;
}
.order-label {
  margin-top: 8px;
  font-size: 14px;
  color: #344054;
  font-weight: 800;
}
.order-count {
  position: absolute;
  top: 12px;
  right: 14px;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: #d66a2c;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 900;
  box-sizing: border-box;
}
.stat-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.stat-card {
  padding: 20px;
}
.stat-value {
  font-size: 24px;
  font-weight: 900;
  color: #12372a;
}
.stat-label {
  margin-top: 6px;
  color: #344054;
  font-size: 14px;
  font-weight: 900;
}
.stat-hint {
  margin-top: 8px;
  color: #8a94a6;
  font-size: 12px;
  line-height: 1.5;
}
.seller-actions-grid,
.shortcut-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 18px;
}
.seller-action,
.shortcut {
  padding: 18px;
}
.action-title,
.shortcut-title {
  color: #12372a;
  font-size: 16px;
  font-weight: 900;
}
.action-desc,
.shortcut-desc {
  margin-top: 8px;
  color: #667085;
  font-size: 13px;
  line-height: 1.5;
}
.panel-actions {
  display: flex;
  gap: 12px;
  margin-top: 18px;
  align-items: center;
}
.interaction-row,
.empty-line,
.detail-row {
  border-radius: 8px;
  background: #f8faf9;
  border: 1px solid #edf1ee;
}
.interaction-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
}
.interaction-row.clickable {
  cursor: pointer;
  transition: background .16s ease, border-color .16s ease, transform .16s ease;
}
.interaction-row.clickable:hover {
  background: #f0f7f3;
  border-color: #cfe6d8;
  transform: translateX(2px);
}
.interaction-title,
.interaction-desc {
  display: block;
}
.interaction-title {
  color: #12372a;
  font-size: 15px;
  font-weight: 900;
}
.interaction-desc {
  margin-top: 6px;
  color: #667085;
  font-size: 12px;
}
.interaction-type {
  padding: 6px 10px;
  border-radius: 999px;
  background: #e8f3ed;
  color: #1f5c43;
  font-size: 12px;
  font-weight: 900;
}
.interaction-arrow {
  flex-shrink: 0;
  color: #94a3b8;
  font-size: 22px;
  line-height: 1;
}
.empty-line {
  padding: 16px;
  color: #667085;
  font-size: 14px;
}
.realname-panel {
  margin-top: 16px;
}
.realname-detail {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}
.detail-row {
  padding: 14px 16px;
}
.detail-label,
.detail-value {
  display: block;
}
.detail-label {
  color: #667085;
  font-size: 12px;
}
.detail-value {
  margin-top: 8px;
  color: #12372a;
  font-size: 16px;
  font-weight: 900;
}
.realname-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.3fr) 126px;
  gap: 12px;
  margin-top: 18px;
  align-items: center;
}
.realname-input {
  height: 42px;
  padding: 0 8px;
  border-radius: 8px;
  border: 1px solid rgba(203, 213, 225, .9);
  background: #fff;
  box-sizing: border-box;
  font-size: 14px;
}
.realname-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.ghost.small[disabled] {
  opacity: .45;
}
@media screen and (max-width: 900px) {
  .me-page {
    padding: 0;
    padding-bottom: 84px;
  }
  .topbar {
    margin: 0 0 14px;
  }
  .topbar + .me-wrap {
    padding: 0 8px;
  }
  .topbar-inner {
    display: flex;
    height: 64px;
  }
  .brand-sub,
  .web-nav {
		overflow-x: auto;
			white-space: nowrap;
    display: none;
  }
  .account-layout {
    grid-template-columns: 1fr;
  }
  .side {
    position: static;
  }
  .profile-card {
    align-items: center;
  }
  .profile-identity {
    flex: 1 1 auto;
  }
  .profile-actions {
    margin-left: auto;
  }
  .order-grid,
  .stat-grid,
  .seller-actions-grid,
  .shortcut-grid,
  .realname-detail,
  .realname-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .realname-form .primary.small {
    width: 100%;
  }
}

	/* #ifdef MP-WEIXIN */
	.topbar {
		position: relative;
		padding-top: 72px;
	}
	.topbar-inner {
		display: flex;
		flex-direction: column;
		height: auto;
		padding: 10px 14px 12px;
		gap: 8px;
		align-items: center;
	}
	.brand {
		width: 100%;
		justify-content: center;
	}
	.brand-mark {
		width: 48px;
		height: 48px;
	}
	.brand-logo {
		width: 100%;
		height: 100%;
	}
	.brand-name {
		font-size: 22px;
		text-align: center;
	}
	.brand-sub {
		display: block;
		text-align: center;
	}
	.web-nav {
		display: flex;
		flex-wrap: nowrap;
		overflow-x: auto;
		white-space: nowrap;
		width: 100%;
		justify-content: center;
	}
	.page-shell {
		padding: 0 14px 24px;
	}
	.profile-card {
		padding: 20px 14px;
	}
	.profile-avatar {
		width: 64px;
		height: 64px;
	}
	.profile-name {
		font-size: 20px;
	}
	.section {
		padding: 0;
	}
		.brand-sub {
		display: none;
	}
/* #endif */

</style>
