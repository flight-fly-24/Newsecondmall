<template>
	<view class="safe-page">
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
					<text class="nav-link on">消息</text>
						<text class="nav-link" @click="openAiAssistant">AI 助手</text>
					<text class="nav-link" @click="navTo('/pages/user/index')">我的</text>
				</view>
			</view>
		</view>

		<view class="content-wrap page-shell">
			<view class="message-workspace">
				<!-- 左侧：会话列表 -->
				<view class="conversation-pane">
					<view class="pane-head">
						<view>
							<text class="pane-kicker">Messages</text>
							<text class="pane-title">消息中心</text>
						</view>
						<view class="total-pill">{{ filteredList.length }}</view>
					</view>

					<view class="search-box">
						<text class="search-icon">⌕</text>
						<input v-model="keyword" class="search-input" placeholder="搜索联系人或商品" />
					</view>

					<scroll-view class="conversation-list" scroll-y :show-scrollbar="false" @scroll="closeContextMenu">
						<view
							v-for="item in filteredList"
							:key="item.covId"
							class="conversation-item"
							:class="{ active: item.covId === covId }"
							:data-cov-id="item.covId"
							@click="open(item)"
							@contextmenu.prevent="openContextMenu($event, item)"
							@longpress="openContextMenu($event, item)"
						>
							<view class="avatar-wrap">
								<image
									v-if="item.coverUrl"
									class="avatar-img"
									:src="item.coverUrl"
									mode="aspectFill"
									@error="onConversationCoverError(item)"
								></image>
								<view v-else class="avatar">
									<text class="avatar-letter">{{ item.icon }}</text>
								</view>
								<text v-if="item.unreadCount > 0" class="badge">{{ item.unreadCount }}</text>
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
							<text class="empty-sub">从商品详情页点击「去问问」后，会话会出现在这里。</text>
						</view>
					</scroll-view>
				</view>

				<!-- 右侧：聊天区域 -->
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
							<view class="header-actions">
								<template v-if="isAiMode">
									<button class="ghost-btn" :disabled="aiBargaining" @click="triggerAiBargain">{{ aiBargaining ? '生成中' : 'AI 议价' }}</button>
									<button class="ghost-btn human-btn" @click="transferToHuman">转人工</button>
								</template>
								<template v-else>
									<button class="ghost-btn" @click="resumeAiMode">切换回AI客服</button>
								</template>
							</view>
						</view>
						<scroll-view data-testid="chat-messages" class="messages" scroll-y :scroll-top="scrollTop" :show-scrollbar="false">
							<view class="session-tip">
								<view class="line"></view>
								<text>上次聊到这里</text>
								<view class="line"></view>
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
										<view v-else class="bubble" :class="{ 'ai-bubble': msg.type === 'AI_REPLY' }">
											<text v-if="msg.type === 'AI_REPLY'" class="ai-tag">AI</text>
											<text data-testid="chat-message-content">{{ msg.content }}</text>
										</view>
										<text v-if="msg.senderId === myUserId" class="read-receipt">
											{{ msg.isRead ? '已读' : '未读' }}
										</text>
									</view>
								</view>
							<!-- AI 回复后弹出转人工客服 -->
							<view v-if="msg.type === 'AI_REPLY' && !conversationTransferred" class="transfer-bar">
								<text class="transfer-hint">AI 已为您初步解答，是否需要转接人工客服？</text>
								<view class="transfer-actions">
									<button class="transfer-btn primary" @click="transferToHuman">转人工客服</button>
									<button class="transfer-btn ghost" @click="dismissTransfer">继续咨询</button>
								</view>
							</view>
							</view>
						</scroll-view>

						<scroll-view class="quick-row" scroll-x :show-scrollbar="false">
							<view class="quick-track">
								<button v-for="text in quickOptions" :key="text" class="quick-btn" @click="sendQuick(text)">{{ text }}</button>
							</view>
						</scroll-view>

						<view data-testid="chat-composer" :data-conversation-ready="Boolean(covId)" class="composer" @click.stop>
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
								data-testid="chat-input"
								v-model="inputContent"
								class="message-input"
								maxlength="500"
								placeholder="说点什么..."
								@confirm="sendMessage"
							></textarea>
							<view class="composer-actions">
								<button class="send-btn secondary" @click="viewProduct">查看宝贝</button>
								<button data-testid="chat-send" class="send-btn" @click="sendMessage">发送</button>
							</view>
						</view>
					</template>

					<view v-else class="chat-empty-state">
						<text class="empty-title">请选择一个会话</text>
						<text class="empty-sub">左侧会展示你和卖家的所有商品咨询。</text>
					</view>
				</view>
			</view>
		</view>

		<view
			v-if="contextMenu.visible"
			class="context-menu-mask"
			@click="closeContextMenu"
			@tap="closeContextMenu"
			@contextmenu.prevent="closeContextMenu"
		>
			<view
				class="context-menu"
				:style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
				@click.stop
				@tap.stop
			>
				<view class="context-menu-item danger" hover-class="context-menu-item-active" @click.stop="onContextDelete">
					删除对话
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { fetchProduct, requestAiAssist } from '@/services/shop.js'
		// #ifdef H5
	import SockJS from 'sockjs-client'
	import Stomp from 'stompjs/lib/stomp.js'
		// #endif
	import { fetchMe } from '@/services/auth.js'
	import { get, post, put, del } from '@/utils/request.js'
	import { getToken } from '@/utils/auth.js'
	import { isImageUrl, resolveImageUrl } from '@/utils/media.js'

	const WS_URL = 'http://127.0.0.1:8080/ws'
	const PRODUCT_CARD_PREFIX = '__PRODUCT_CARD__'

	export default {
		data() {
			return {
				assist: {
					consensus: '价格 650 元，平台担保下单；卖家承诺无坏点，买家收货 48 小时内完成验货。',
					checklist: ['确认商品实拍图', '确认是否支持平台担保', '确认瑕疵和售后约定']
				},
				keyword: '',
				list: [],
				covId: null,
				messages: [],
				inputContent: '',
				quickOptions: ['支持平台担保吗？', '是全新正品吗？', '是否包邮？', '最低多少钱？'],
				myUserId: null,
				stompClient: null,
				chatSubscription: null,
				focusProduct: null,
				aiBargaining: false,
				connected: false,
				conversationTransferred: false,
				deletingCovId: null,
				contextMenu: {
					visible: false,
					x: 0,
					y: 0,
					item: null
				},
				pollTimer: null,
				scrollTop: 0,
				showEmojiPicker: false,
				activeEmojiGroup: 'face',
				emojiGroups: [
					{ key: 'face', name: '经典', items: ['😀', '😁', '😂', '🤣', '😊', '😇', '🙂', '😉', '😍', '😘', '😋', '😜', '😎', '🤓', '🤔', '🤗', '😳', '🥺', '😭', '😤', '😡', '😱', '😴', '🤒', '😵', '🤯', '🥳', '😅', '😆', '😬', '🙄', '😏', '😌', '😔', '😮', '🤭', '🤫', '🤐', '😷', '🤧', '🥰', '😚', '😛', '😝', '🤤', '😪', '😫', '😈'] },
					{ key: 'hand', name: '手势', items: ['👍', '👎', '👌', '✌️', '🤞', '🤟', '🤙', '👋', '👏', '🙌', '🙏', '💪', '🤝', '🫶', '☝️', '👉', '👈', '👇', '👆', '✋', '🤚', '🖐️', '🫡', '🤲'] },
					{ key: 'trade', name: '交易', items: ['💬', '💰', '💸', '🧾', '📦', '🚚', '🎁', '🏷️', '💳', '✅', '❌', '⚠️', '📌', '🔍', '🛒', '⭐', '🔥', '💡', '📮', '⏰', '🧡', '💯', '📱', '💻', '🎧', '📚', '🏠', '☕'] },
					{ key: 'heart', name: '心情', items: ['❤️', '🧡', '💛', '💚', '💙', '💜', '🤍', '🤎', '🖤', '💔', '💕', '💞', '💓', '💗', '💖', '💘', '💝', '✨', '🌟', '🎉', '🌈', '☀️', '🌙', '🍀'] }
				]
			}
		},
		computed: {
			filteredList() {
				const word = this.keyword.trim().toLowerCase()
				if (!word) return this.list
				return this.list.filter(item => [item.title, item.goodsName].some(value => String(value || '').toLowerCase().includes(word)))
			},
			activeConversation() {
				return this.list.find(item => item.covId === this.covId) || null
			},
			conversationStatus() {
				const active = this.activeConversation
				return active ? active.status : 'ai'
			},
			isAiMode() {
				return this.conversationStatus === 'ai' || this.conversationStatus === 'pending'
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
		onShow() {
			this.loadAssist()
			this.fetchConversationList()
		},
		onHide() {
			this.closeContextMenu()
			this.disconnect()
		},
		onUnload() {
			this.closeContextMenu()
			this.disconnect()
		},
		mounted() {
			// #ifdef H5
			this._onNativeContextMenu = (e) => {
				const target = e.target && e.target.closest ? e.target.closest('.conversation-item') : null
				if (!target) {
					this.closeContextMenu()
					return
				}
				e.preventDefault()
				const covId = Number(target.getAttribute('data-cov-id'))
				const item = this.filteredList.find(row => Number(row.covId) === covId)
					|| this.list.find(row => Number(row.covId) === covId)
				if (item) this.openContextMenu(e, item)
			}
			document.addEventListener('contextmenu', this._onNativeContextMenu)
			// #endif
		},
		beforeUnmount() {
			// #ifdef H5
			if (this._onNativeContextMenu) {
				document.removeEventListener('contextmenu', this._onNativeContextMenu)
			}
			// #endif
		},
		methods: {
			closeFloaters() {
				this.showEmojiPicker = false
				this.closeContextMenu()
			},
			eventPoint(e) {
				if (!e) return { x: 16, y: 16 }
				if (typeof e.clientX === 'number') return { x: e.clientX, y: e.clientY }
				const touch = (e.touches && e.touches[0]) || (e.changedTouches && e.changedTouches[0])
				if (touch && typeof touch.clientX === 'number') return { x: touch.clientX, y: touch.clientY }
				const detail = e.detail || {}
				if (typeof detail.clientX === 'number') return { x: detail.clientX, y: detail.clientY }
				if (typeof detail.x === 'number') return { x: detail.x, y: detail.y }
				return { x: 16, y: 16 }
			},
			openContextMenu(e, item) {
				if (!item) return
				const ev = e && typeof e.preventDefault === 'function' ? e : e
				if (ev && typeof ev.preventDefault === 'function') ev.preventDefault()
				if (ev && typeof ev.stopPropagation === 'function') ev.stopPropagation()
				const point = this.eventPoint(e)
				const menuWidth = 148
				const menuHeight = 44
				const pad = 8
				const maxX = (typeof window !== 'undefined' ? window.innerWidth : 375) - menuWidth - pad
				const maxY = (typeof window !== 'undefined' ? window.innerHeight : 667) - menuHeight - pad
				this.contextMenu = {
					visible: true,
					x: Math.max(pad, Math.min(point.x, maxX)),
					y: Math.max(pad, Math.min(point.y, maxY)),
					item
				}
			},
			closeContextMenu() {
				if (!this.contextMenu.visible) return
				this.contextMenu = { visible: false, x: 0, y: 0, item: null }
			},
			onContextDelete() {
				const item = this.contextMenu.item
				this.closeContextMenu()
				this.confirmDelete(item)
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
			async loadAssist() {
				const active = this.activeConversation
				const productId = active && active.goodsId
				if (!productId) return
				try {
					const body = await requestAiAssist({ productId: String(productId), question: '能便宜一点吗？有没有坏点？', offer: 620 })
					if (body && body.code === 0 && body.data) {
						this.assist = body.data
						if (this.list[0]) {
							this.list[0].sub = body.data.answer
						}
				}
				} catch (e) {}
			},
			async fetchConversationList() {
				try {
					const res = await get('/api/chat/conversations')
					console.log('=== [调试] 后端返回的数据:', res)
					const conversationList = (res.data && res.data.data) ? res.data.data : []
					this.list = conversationList.map(item => ({
						icon: this.avatarText(item.targetName),
						title: item.targetName || '用户',
						sub: this.formatConversationLastMessage(item.lastMessage),
						time: this.formatTime(item.lastTime),
						covId: item.covId,
						unreadCount: item.unreadCount || 0,
						goodsImageUrl: item.goodsImageUrl,
						coverUrl: this.conversationCover(item.goodsImageUrl),
						goodsName: item.goodsName,
						goodsId: item.goodsId,
							status: item.status || 'ai'
				}))
				} catch (e) {
					console.error('加载会话列表失败', e)
				}
			},
			async open(item) {
					// #ifdef MP-WEIXIN
					// 小程序端跳转到独立聊天页
					uni.navigateTo({ url: '/pages/chat/chat?covId=' + item.covId })
					return
					// #endif
					// #ifndef MP-WEIXIN
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
					// 进入会话时自动恢复AI客服模式
					const conv = this.activeConversation
					if (conv && conv.status && conv.status !== 'ai' && conv.status !== 'pending') {
						put(`/api/chat/conversations/${this.covId}/status`, { status: 'ai' }).then(() => {
							if (conv) conv.status = 'ai'
						}).catch(e => console.warn('auto reset ai failed', e))
					}
					this.conversationTransferred = false
					await this.initChat()
					// #endif
				},
			confirmDelete(item) {
				const covId = item && item.covId
				if (!covId || this.deletingCovId) return
				uni.showModal({
					title: '删除对话',
					content: '删除后聊天记录无法恢复，确定删除吗？',
					confirmText: '删除',
					confirmColor: '#b91c1c',
					success: (res) => {
						if (res.confirm) this.deleteConversation(covId)
					}
				})
			},
			async deleteConversation(covId) {
				if (!covId || this.deletingCovId) return
				this.deletingCovId = covId
				try {
					await del(`/api/chat/conversations/${covId}`)
					this.list = this.list.filter(item => Number(item.covId) !== Number(covId))
					if (Number(this.covId) === Number(covId)) {
						this.clearOpenConversation()
					}
					uni.showToast({ title: '对话已删除', icon: 'none' })
				} catch (e) {
					console.error('删除会话失败', e)
					uni.showToast({ title: '删除失败', icon: 'none' })
				} finally {
					this.deletingCovId = null
				}
			},
			clearOpenConversation() {
				this.disconnect()
				this.stopPolling()
				this.connected = false
				this.stompClient = null
				this.covId = null
				this.messages = []
				this.inputContent = ''
				this.focusProduct = null
				this.conversationTransferred = false
				this.closeFloaters()
			},
			navTo(url) {
				if (['/pages/home/home', '/pages/browse/browse', '/pages/cart/cart', '/pages/message/message', '/pages/user/index'].includes(url)) {
					uni.switchTab({ url })
					return
				}
				uni.navigateTo({ url })
			},
			openAiAssistant() {
				uni.navigateTo({ url: '/pages/ai-assistant/ai-assistant' })
			},
			avatarText(name) {
				return String(name || '聊').slice(0, 1).toUpperCase()
			},
			conversationCover(url) {
				return isImageUrl(url) ? resolveImageUrl(url) : ''
			},
			onConversationCoverError(item) {
				if (item) item.coverUrl = ''
			},
			formatTime(dateStr) {
				if (!dateStr) return ''
				const timePart = dateStr.split('T')[1].split('.')[0]
				const [h, m] = timePart.split(':')
				return `${h}:${m}`
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
					console.warn('=== [调试] 成功赋值! 当前 myUserId 为:', this.myUserId)
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
				// #ifdef H5
				const token = getToken()
				const socket = new SockJS(WS_URL)
				this.stompClient = Stomp.over(socket)
				this.stompClient.debug = null
				this.stompClient.connect(
					{ Authorization: token ? 'Bearer ' + token : '' },
					() => {
						this.connected = true
						this.subscribeTopic()
					},
					(error) => {
						console.error('WebSocket failed', error)
						this.startPolling()
					}
				)
				// #endif
				// #ifndef H5
				this.startPolling()
				// #endif
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
						if (chatMessage.type !== 'CHAT_MESSAGE' && chatMessage.type !== 'AI_REPLY') return
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
				} catch (e) { console.error('解析消息失败', e) }
				})
				this.stompClient.subscribe(`/user/queue/chat/read-status`, (message) => {
					const status = JSON.parse(message.body)
					console.log('调试2', status)
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
			startPolling() {
				if (this.pollTimer) return
				this.refreshHistory()
				this.pollTimer = setInterval(() => {
					this.refreshHistory()
				}, 3000)
			},
			stopPolling() {
				if (this.pollTimer) {
					clearInterval(this.pollTimer)
					this.pollTimer = null
				}
			},
			async refreshHistory() {
				if (!this.covId) return
				try {
					const res = await get(`/api/chat/conversations/${this.covId}/messages`)
					const rawList = (res.data && res.data.data) ? res.data.data : []
					this.messages = this.processMessages(rawList)
				} catch (e) {
					console.warn("刷新消息失败", e)
				}
			},
			sendQuick(text) {
				this.inputContent = text
				this.sendMessage()
			},
			async sendChatContent(content) {
				if (!content || !this.covId) return false
				const payload = { covId: this.covId, content, type: 'CHAT_MESSAGE' }
				// 统一走 REST 发送，WebSocket 只负责接收实时推送
				// 避免 STOMP send 静默失败导致消息丢失
				try {
					await post(`/api/chat/conversations/${this.covId}/messages`, payload)
					// REST 已确认写入后立即刷新，避免 WebSocket 尚未连接时界面漏掉自己的消息。
					await this.refreshHistory()
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
				if (!this.covId || this.aiBargaining) return
				this.aiBargaining = true
				uni.showLoading({ title: 'AI 思考中...' })
				try {
					const res = await post(`/api/chat/conversations/${this.covId}/ai-bargain`)
					const data = (res.data && res.data.data) ? res.data.data : {}
					if (data.message) {
						const lastMsg = this.messages[this.messages.length - 1]
						const showTime = this.shouldShowTime(data.message, lastMsg)
						this.messages.push(this.normalizeMessage({
							...data.message,
							showTime
						}, lastMsg))
						this.$nextTick(() => this.scrollToBottom())
				}
					uni.showToast({
						title: data.source === 'ai' ? 'AI 已生成议价建议' : 'AI 不可用，已用兜底建议',
						icon: 'none'
				})
				} catch (e) {
					uni.showToast({ title: 'AI 议价请求失败', icon: 'none' })
				} finally {
					this.aiBargaining = false
					uni.hideLoading()
				}
			},
			transferToHuman() {
				this.doTransferToHuman()
			},
			dismissTransfer() {
				this.conversationTransferred = true
				uni.showToast({ title: '将继续为您提供 AI 解答', icon: 'none' })
			},
				async doTransferToHuman() {
					try {
						const res = await post(`/api/chat/conversations/${this.covId}/transfer`)
						if (res.data && res.data.code === 0) {
						this.conversationTransferred = true
						const conv = this.list.find(c => c.covId === this.covId)
						if (conv) conv.status = 'active'
						uni.showToast({ title: '已转接人工客服', icon: 'success' })
						}
				} catch (e) {
						uni.showToast({ title: '转接失败，请重试', icon: 'none' })
				}
				},
				async resumeAiMode() {
					try {
						const res = await put(`/api/chat/conversations/${this.covId}/status`, { status: 'ai' })
						if (res.data && res.data.code === 0) {
						const conv = this.list.find(c => c.covId === this.covId)
						if (conv) conv.status = 'ai'
						this.conversationTransferred = false
						uni.showToast({ title: '已切换回AI客服', icon: 'success' })
						}
				} catch (e) {
						uni.showToast({ title: '切换失败，请重试', icon: 'none' })
				}
				},
				scrollToBottom() {
					this.$nextTick(() => {
						this.scrollTop = this.scrollTop === 999999 ? 999998 : 999999
				})
				},
			formatChatTime(value) {
				if (!value) {
					return ''
				}
				const date = new Date(value)
				return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
			}
		}
	}
</script>

<style lang="scss" scoped>
	.safe-page {
		height: 100vh;
		overflow: hidden;
		background:
			radial-gradient(circle at 18% 8%, rgba(232, 243, 237, .72), transparent 34%),
			linear-gradient(135deg, #f7f8fb 0%, #eef3ef 100%);
	}
	.topbar {
		position: sticky;
		top: 0;
		z-index: 10;
		background: rgba(255,255,255,.88);
		backdrop-filter: blur(20px);
		border-bottom: 1px solid rgba(226, 232, 240, .8);
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
	}
	.brand-logo {
		width: 100%;
		height: 100%;
	}
	.brand-name,
	.brand-sub {
		display: block;
	}
	.brand-name {
		font-size: 18px;
		font-weight: 900;
		color: #121826;
	}
	.brand-sub {
		margin-top: 2px;
		font-size: 12px;
		color: #667085;
	}
	.web-nav {
		overflow-x: auto;
			white-space: nowrap;
		display: flex;
		justify-self: center;
		align-items: center;
		gap: 4px;
		padding: 5px;
		height: 50px;
		border-radius: 999px;
		background: rgba(255,255,255,.72);
		border: 1px solid rgba(203, 213, 225, .72);
		box-sizing: border-box;
	}
	.nav-link {
		width: auto;
			padding: 0 8px;
			flex-shrink: 0;
		height: 38px;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 999px;
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
	.page-shell {
		height: calc(100vh - 82px);
		padding: 14px;
		box-sizing: border-box;
		overflow: hidden;
	}
	.message-workspace {
		height: 100%;
		display: grid;
		grid-template-columns: 260px 1fr;
		gap: 20px;
		width: 100%;
		max-width: 1200px;
		padding: 0 20px;
		box-sizing: border-box;
	}
	.conversation-pane,
	.chat-pane {
		min-height: 0;
		border: 1px solid rgba(226, 232, 240, .95);
		background: rgba(255, 255, 255, .94);
		box-shadow: 0 18px 50px rgba(15, 23, 42, .08);
		overflow: hidden;
	}
	.chat-pane {
		border-radius: 12px;
		display: flex;
		flex-direction: column;
		background: #f7f8fb;
		position: relative;
	}
	.conversation-pane {
		display: flex;
		flex-direction: column;
		border-radius: 10px;
	}
	.pane-head {
		height: 74px;
		padding: 16px;
		display: flex;
		align-items: center;
		justify-content: space-between;
		box-sizing: border-box;
	}
	.pane-kicker,
	.pane-title {
		display: block;
	}
	.pane-kicker {
		font-size: 11px;
		color: #94a3b8;
		text-transform: uppercase;
	}
	.pane-title {
		margin-top: 3px;
		font-size: 22px;
		font-weight: 950;
		color: #111827;
	}
	.total-pill {
		min-width: 30px;
		height: 30px;
		padding: 0 8px;
		border-radius: 999px;
		display: flex;
		align-items: center;
		justify-content: center;
		background: #e8f3ed;
		color: #12372a;
		font-size: 13px;
		font-weight: 900;
		box-sizing: border-box;
	}
	.search-box {
		height: 40px;
		margin: 0 14px 12px;
		padding: 0 12px;
		display: flex;
		align-items: center;
		gap: 8px;
		border-radius: 8px;
		background: #f3f5f8;
		box-sizing: border-box;
	}
	.search-icon {
		font-size: 18px;
		color: #8a93a3;
	}
	.search-input {
		flex: 1;
		height: 100%;
		font-size: 14px;
		color: #111827;
	}
	.conversation-list {
		flex: 1;
		min-height: 0;
		padding: 0 8px 12px;
		box-sizing: border-box;
	}
	.conversation-item {
		display: flex;
		gap: 10px;
		padding: 12px 10px;
		margin-bottom: 6px;
		border-radius: 8px;
		border: 1px solid transparent;
		box-sizing: border-box;
		user-select: none;
		-webkit-user-select: none;
		transition: background .18s ease, border-color .18s ease, transform .18s ease;
	}
	.conversation-item:hover,
	.conversation-item.active {
		background: linear-gradient(135deg, #f8fbf9, #fff);
		border-color: #cfe6d8;
	}
	.conversation-item.active {
		transform: translateX(2px);
	}
	.avatar-wrap {
		position: relative;
		width: 46px;
		height: 46px;
		flex-shrink: 0;
	}
	.avatar,
	.avatar-img,
	.chat-avatar,
	.mini-avatar {
		display: flex;
		align-items: center;
		justify-content: center;
		background: linear-gradient(135deg, #e8f3ed, #f8fbf9);
		color: #12372a;
		font-weight: 950;
	}
	.avatar,
	.avatar-img {
		width: 46px;
		height: 46px;
		border-radius: 8px;
		overflow: hidden;
		box-sizing: border-box;
	}
	.avatar-letter {
		font-size: 18px;
		font-weight: 900;
		color: #12372a;
		line-height: 1;
	}
	.badge {
		position: absolute;
		top: -5px;
		right: -5px;
		min-width: 18px;
		height: 18px;
		padding: 0 5px;
		border-radius: 999px;
		background: #d66a2c;
		color: #fff;
		font-size: 12px;
		line-height: 18px;
		text-align: center;
		box-sizing: border-box;
	}
	.conversation-main {
		flex: 1;
		min-width: 0;
	}
	.context-menu-mask {
		position: fixed;
		inset: 0;
		z-index: 80;
	}
	.context-menu {
		position: fixed;
		min-width: 148px;
		padding: 6px;
		border-radius: 10px;
		background: #fff;
		border: 1px solid #e5e7eb;
		box-shadow: 0 10px 28px rgba(15, 23, 42, 0.12);
		box-sizing: border-box;
	}
	.context-menu-item {
		height: 36px;
		padding: 0 12px;
		display: flex;
		align-items: center;
		border-radius: 7px;
		font-size: 13px;
		font-weight: 700;
		color: #334155;
	}
	.context-menu-item.danger {
		color: #b91c1c;
	}
	.context-menu-item-active,
	.context-menu-item.danger:hover {
		background: #fef2f2;
	}
	.conversation-head {
		display: flex;
		align-items: center;
		gap: 8px;
	}
	.conversation-name {
		flex: 1;
		min-width: 0;
		font-size: 15px;
		font-weight: 900;
		color: #111827;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.conversation-time {
		font-size: 12px;
		color: #9aa3af;
	}
	.conversation-sub,
	.goods-line {
		display: block;
		margin-top: 5px;
		font-size: 12px;
		color: #8792a2;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.goods-line {
		color: #12372a;
	}
	.chat-header {
		min-height: 72px;
		height: auto;
		padding: 12px 22px;
		display: flex;
		align-items: center;
		justify-content: space-between;
		background: rgba(255,255,255,.96);
		border-bottom: 1px solid #e5e7eb;
		box-sizing: border-box;
		flex-shrink: 0;
		gap: 12px;
	}
	.chat-contact {
		display: flex;
		align-items: center;
		gap: 12px;
		min-width: 0;
	}
	.chat-avatar {
		width: 42px;
		height: 42px;
		border-radius: 10px;
		flex-shrink: 0;
		font-size: 16px;
	}
	.chat-title,
	.chat-subtitle {
		display: block;
	}
	.chat-title {
		font-size: 20px;
		font-weight: 950;
		color: #111827;
	}
	.chat-subtitle {
		margin-top: 4px;
		max-width: 520px;
		font-size: 13px;
		color: #8792a2;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.header-actions {
		display: flex;
		gap: 8px;
		flex-shrink: 0;
	}
	.ghost-btn {
		height: 32px;
		padding: 0 13px;
		border-radius: 8px;
		background: #fff;
		border: 1px solid #e5e7eb;
		color: #334155;
		font-size: 13px;
	}
	.ghost-btn:hover {
		border-color: #cfe6d8;
		color: #12372a;
	}
	.ghost-btn[disabled] {
		opacity: 0.6;
	}
		.human-btn {
			color: #dc2626;
			border-color: #fca5a5;
		}
		.human-btn:hover {
			background: #fef2f2;
			border-color: #dc2626;
			color: #b91c1c;
		}
	button {
		display: flex;
		align-items: center;
		justify-content: center;
		margin: 0;
		padding: 0;
		border: 0;
		line-height: normal;
		text-align: center;
		box-sizing: border-box;
		vertical-align: middle;
	}
	button::after {
		border: none;
	}
	.messages {
		flex: 1;
		height: 0;
		min-height: 0;
		padding: 18px 24px;
		box-sizing: border-box;
		background:
			linear-gradient(90deg, rgba(226,232,240,.55) 1px, transparent 1px),
			linear-gradient(180deg, #f7f8fb, #f4f6fa);
		background-size: 28px 28px, 100% 100%;
	}
	.session-tip {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 12px;
		margin: 0 auto 16px;
		color: #9aa3af;
		font-size: 12px;
	}
	.line {
		width: 84px;
		height: 1px;
		background: #d7dce5;
	}
	.time-divider {
		text-align: center;
		font-size: 12px;
		color: #9aa3af;
		margin: 12px 0 16px;
	}
	.message-row {
		display: flex;
		align-items: flex-start;
		gap: 10px;
		margin-bottom: 16px;
	}
	.message-row.mine {
		justify-content: flex-end;
	}
	.mini-avatar {
		width: 32px;
		height: 32px;
		border-radius: 8px;
		font-size: 14px;
		flex-shrink: 0;
	}
	.bubble-wrap {
		max-width: min(72%, 680px);
	}
	.message-row.mine .bubble-wrap {
		display: flex;
		flex-direction: column;
		align-items: flex-end;
	}
	.bubble {
		padding: 11px 14px;
		border-radius: 10px;
		background: #fff;
		color: #1f2937;
		font-size: 15px;
		line-height: 1.6;
		box-shadow: 0 8px 24px rgba(15, 23, 42, .06);
		word-break: break-word;
	}
	.message-row.mine .bubble {
		background: #1f5c43;
		color: #fff;
	}
	.read-receipt {
		display: block;
		margin-top: 5px;
		font-size: 11px;
		color: #94a3b8;
		text-align: right;
	}
	.message-product-card {
		width: 360px;
		max-width: 100%;
		display: flex;
		gap: 12px;
		padding: 12px;
		border-radius: 12px;
		background: #fff;
		border: 1px solid #e5e7eb;
		box-shadow: 0 12px 30px rgba(15, 23, 42, .08);
		box-sizing: border-box;
	}
	.message-row.mine .message-product-card {
		border-color: #cfe6d8;
	}
	.card-thumb {
		width: 84px;
		height: 84px;
		border-radius: 8px;
		flex-shrink: 0;
	}
	.card-body {
		min-width: 0;
		flex: 1;
	}
	.card-label {
		display: block;
		font-size: 12px;
		color: #12372a;
		font-weight: 900;
	}
	.card-title {
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
		margin-top: 6px;
		font-size: 15px;
		font-weight: 900;
		color: #111827;
		line-height: 1.45;
	}
	.card-meta {
		display: flex;
		align-items: center;
		gap: 8px;
		margin-top: 10px;
	}
	.card-price {
		color: #12372a;
		font-size: 18px;
		font-weight: 950;
	}
	.card-tag {
		padding: 3px 7px;
		border-radius: 999px;
		background: #e8f3ed;
		color: #12372a;
		font-size: 11px;
		font-weight: 800;
	}
	.quick-row {
		width: 100%;
		height: 50px;
		white-space: nowrap;
		padding: 7px 18px;
		background: #fff;
		border-top: 1px solid #e5e7eb;
		box-sizing: border-box;
		flex-shrink: 0;
	}
	.quick-track {
		display: inline-block;
		height: 36px;
		white-space: nowrap;
	}
	.quick-track::after {
		content: "";
		display: inline-block;
		width: 18px;
		height: 1px;
	}
	.quick-btn {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: auto !important;
		min-width: 0;
		height: 34px;
		padding: 0 15px;
		margin-right: 8px;
		border-radius: 999px;
		white-space: nowrap;
		line-height: normal;
		background: #f8fafc;
		color: #334155;
		font-size: 13px;
		font-weight: 750;
		box-shadow: inset 0 0 0 1px #e5e7eb;
	}
	.quick-btn:hover {
		border-color: #cfe6d8;
		color: #12372a;
	}
	.composer {
		position: relative;
		min-height: 142px;
		padding: 10px 18px 12px;
		background: #fff;
		border-top: 1px solid #e5e7eb;
		box-sizing: border-box;
		flex-shrink: 0;
	}
	.tool-row {
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.tool-btn {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 30px;
		height: 30px;
		border-radius: 8px;
		background: #f8fafc;
		color: #111827;
		font-size: 19px;
	}
	.tool-btn.active,
	.tool-btn:hover {
		background: #e8f3ed;
		color: #12372a;
	}
	.counter {
		margin-left: auto;
		color: #9aa3af;
		font-size: 12px;
	}
	.emoji-panel {
		position: absolute;
		left: 14px;
		bottom: 122px;
		width: 520px;
		height: 362px;
		border-radius: 12px;
		background: #fff;
		border: 1px solid #e5e7eb;
		box-shadow: 0 18px 60px rgba(15, 23, 42, .18);
		z-index: 12;
		overflow: hidden;
	}
	.emoji-title {
		height: 44px;
		padding: 0 16px;
		display: flex;
		align-items: center;
		color: #6b7280;
		font-size: 15px;
		font-weight: 850;
		box-sizing: border-box;
	}
	.emoji-tabs {
		height: 54px;
		padding: 6px 12px;
		display: flex;
		gap: 6px;
		border-top: 1px solid #eef2f7;
		box-sizing: border-box;
	}
	.emoji-tab {
		display: flex;
		align-items: center;
		justify-content: center;
		min-width: 76px;
		height: 42px;
		padding: 0 16px;
		border-radius: 8px;
		background: #f8fafc;
		color: #64748b;
		font-size: 15px;
		line-height: normal;
	}
	.emoji-tab.on {
		background: #e8f3ed;
		color: #12372a;
		font-weight: 900;
	}
	.emoji-grid {
		height: 264px;
		padding: 4px 14px 12px;
		box-sizing: border-box;
	}
	.emoji-grid-inner {
		display: grid;
		grid-template-columns: repeat(12, 34px);
		justify-content: space-between;
		gap: 8px 0;
		align-items: center;
		padding-bottom: 10px;
		box-sizing: border-box;
	}
	.emoji-item {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 34px;
		height: 34px;
		margin: 0;
		border-radius: 9px;
		background: #fff;
		font-size: 26px;
		line-height: normal;
	}
	.emoji-item:hover {
		background: #f1f5f9;
	}
	.message-input {
		width: 100%;
		height: 58px;
		margin-top: 7px;
		padding: 0;
		color: #111827;
		font-size: 15px;
		line-height: 1.6;
		box-sizing: border-box;
	}
	.composer-actions {
		display: flex;
		justify-content: flex-end;
		gap: 8px;
	}
	.send-btn {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 72px;
		height: 34px;
		border-radius: 8px;
		background: #12372a;
		color: #fff;
		font-size: 14px;
		font-weight: 950;
	}
	.send-btn.secondary {
		width: 104px;
		background: #f8fafc;
		color: #475569;
		border: 1px solid #e2e8f0;
		box-shadow: none;
	}
	.send-btn.secondary:hover {
		background: #f8fbf9;
		color: #12372a;
		border-color: #cfe6d8;
	}
	.send-btn[disabled] {
		background: #cbd5e1;
	}
	.empty-list,
	.empty-chat,
	.chat-empty-state {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 44px 20px;
		text-align: center;
		color: #94a3b8;
	}
	.chat-empty-state {
		height: 100%;
		box-sizing: border-box;
	}
	.empty-title {
		font-size: 17px;
		font-weight: 950;
		color: #475569;
	}
	.empty-sub {
		margin-top: 8px;
		font-size: 13px;
		color: #94a3b8;
		line-height: 1.6;
	}
	@media (max-width: 920px) {
		.safe-page {
			height: auto;
			overflow: auto;
		}
		.topbar-inner {
			grid-template-columns: 1fr;
			height: auto;
			padding: 12px;
		}
		.web-nav {
		overflow-x: auto;
			white-space: nowrap;
			justify-self: stretch;
			overflow-x: auto;
			white-space: nowrap;
		}
		.page-shell {
			height: auto;
		}
		.message-workspace {
			height: auto;
			grid-template-columns: 1fr;
		}
		.conversation-list {
			max-height: 330px;
		}
		.chat-pane {
			height: 680px;
		}
		.emoji-panel {
			width: calc(100vw - 52px);
		}
	}
	.ai-bubble { background: #f0f7ff !important; border: 1px solid #cce5ff; }
	.ai-tag { display: inline-block; background: #1677ff; color: #fff; font-size: 11px; font-weight: 700; padding: 1px 6px; border-radius: 4px; margin-right: 6px; vertical-align: middle; }
	.transfer-bar { padding: 12px 16px; margin: 8px 0; background: #fff8e6; border: 1px solid #ffe7a3; border-radius: 8px; text-align: center; }
	.transfer-hint { display: block; font-size: 13px; color: #8c6e1a; margin-bottom: 8px; }
	.transfer-actions { display: flex; gap: 10px; justify-content: center; }
	.transfer-btn { padding: 6px 16px; border-radius: 6px; font-size: 13px; font-weight: 700; border: none; cursor: pointer; }
	.transfer-btn.primary { background: #1677ff; color: #fff; }
	.transfer-btn.ghost { background: #fff; color: #666; border: 1px solid #d9d9d9; }

	/* #ifdef MP-WEIXIN */
	.safe-page {
		height: 100vh;
		background: #f7f8fb;
	}
	.topbar {
		display: none;
	}
	.page-shell {
		height: 100vh;
		padding: 0;
	}
	.message-workspace {
		display: block;
		padding: 0;
		max-width: 100%;
	}
	.chat-pane {
		display: none;
	}
	.conversation-pane {
		width: 100%;
		height: 100vh;
		border: none;
		border-radius: 0;
		box-shadow: none;
	}
	.pane-head {
		justify-content: center;
	}
	.pane-title {
		text-align: center;
	}
		.avatar-wrap {
		overflow: visible;
	}
	.badge {
		top: -2px;
		right: -2px;
		z-index: 2;
	}
	.search-box {
		margin: 0 14px 12px;
	}
		.brand-sub {
		display: none;
	}
/* #endif */

</style>
