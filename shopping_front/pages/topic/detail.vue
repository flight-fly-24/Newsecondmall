<template>
	<view class="safe-page topic-page">
		<view class="topbar">
			<view class="content-wrap topbar-inner">
				<view class="brand" @click="navTo('/pages/home/home')">
					<view class="brand-mark">
						<image class="brand-logo" src="/static/logo.png" mode="aspectFit"></image>
					</view>
					<view>
						<text class="brand-name">松果集市</text>
						<text class="brand-sub">话题、经验和真实商品讨论</text>
					</view>
				</view>
				<view class="web-nav">
					<text class="nav-link" @click="navTo('/pages/home/home')">首页</text>
					<text class="nav-link on" @click="navTo('/pages/browse/browse')">发现</text>
					<text class="nav-link" @click="navTo('/pages/cart/cart')">购物车</text>
					<text class="nav-link" @click="navTo('/pages/message/message')">消息</text>
						<text class="nav-link" @click="navTo('/pages/ai-assistant/ai-assistant')">AI 助手</text>
					<text class="nav-link" @click="navTo('/pages/user/index')">我的</text>
				</view>
				<view class="top-actions">
					<view class="search">
						<text class="search-icon">⌕</text>
						<input v-model="keyword" class="search-input" placeholder="搜索话题、标签、经验" confirm-type="search" @confirm="searchTopics" />
						<view class="search-action" @click="searchTopics">搜索</view>
					</view>
					<view class="create-shortcut" @click="openCreateTopic">创建话题</view>
				</view>
			</view>
		</view>
		<scroll-view scroll-y class="scroll">
			<view class="content-wrap page">
				<view class="back-row" @click="goBrowse">
					<text class="back-icon">‹</text>
					<text class="back-text">返回发现</text>
				</view>
				<view class="hero">
					<view class="cover" :class="{ 'has-image': isImageUrl(topic.cover) }">
						<image v-if="isImageUrl(topic.cover)" class="cover-img" :src="resolveImageUrl(topic.cover)" mode="aspectFill"></image>
						<text v-else>{{ topic.type || '话题' }}</text>
					</view>
					<view class="hero-main">
						<view class="hero-line">
							<text class="badge">{{ topic.type || '话题' }}</text>
							<text class="heat">{{ formatTopicHeat(topic) }}</text>
						</view>
						<text class="title">{{ topic.title || '话题讨论' }}</text>
						<text class="desc">{{ topic.desc || '一起讨论这个话题。' }}</text>
						<view class="tags">
							<text v-for="tag in topic.tags || []" :key="tag" class="tag">{{ tag }}</text>
						</view>
						<view class="topic-actions">
							<view class="topic-stat">
								<text class="stat-num">{{ topic.followCount || 0 }}</text>
								<text class="stat-label">关注</text>
							</view>
							<view class="topic-stat">
								<text class="stat-num">{{ topic.postCount || 0 }}</text>
								<text class="stat-label">帖子</text>
							</view>
							<button class="follow-topic-btn" :class="{ followed: topic.followed }" :loading="following" @click="toggleTopicFollow">
								{{ topic.followed ? '已关注' : '关注话题' }}
							</button>
						</view>
					</view>
					<button class="topic-fab" @click="openPostModal">+</button>
				</view>

				<view class="layout">
					<view class="feed">
						<view class="post-section">
							<view class="post-section-head">
								<view>
									<text class="toolbar-title">话题讨论</text>
									<text class="toolbar-sub">{{ currentPostSub }}</text>
								</view>
								<view class="post-sort-tabs">
									<button class="post-sort-tab" :class="{ on: postSort === 'latest' }" @click="postSort = 'latest'">最新发布</button>
									<button class="post-sort-tab" :class="{ on: postSort === 'hot' }" @click="postSort = 'hot'">热门讨论</button>
								</view>
							</view>

						<view class="post-grid">
							<view v-for="(column, columnIndex) in masonryColumns" :key="postSort + '-column-' + columnIndex" class="post-column">
						<view v-for="post in column" :key="postSort + '-' + post.id" class="post-card">
							<view class="post-head">
								<view class="avatar">
									<image v-if="post.authorAvatar" class="avatar-img" :src="resolveImageUrl(post.authorAvatar)" mode="aspectFill"></image>
									<text v-else>{{ (post.author || '松').slice(0, 1) }}</text>
								</view>
								<view class="post-user">
									<text class="post-author">{{ post.author }}</text>
									<text class="post-time">{{ post.createdAt }}</text>
								</view>
							</view>
							<rich-text class="post-text" :nodes="renderMarkdown(post.content)"></rich-text>

							<view v-if="post.images && post.images.length" class="post-images" :class="'cols-' + Math.min(post.images.length, 3)">
								<image v-for="img in post.images" :key="img" class="post-image" :src="resolveImageUrl(img)" mode="aspectFill"></image>
							</view>

							<view v-if="post.product" class="post-attach" @click="openGoods(post.product)">
								<view class="attach-cover" :class="{ 'has-image': isImageUrl(post.product.cover) }">
									<image v-if="isImageUrl(post.product.cover)" class="cover-img" :src="resolveImageUrl(post.product.cover)" mode="aspectFill"></image>
									<text v-else>商品</text>
								</view>
								<view class="attach-main">
									<text class="attach-type">帖子推荐商品</text>
									<text class="attach-title">{{ post.product.title }}</text>
									<text class="attach-meta">¥{{ post.product.price }} · {{ post.product.category }}</text>
								</view>
								<text class="open-link">查看</text>
							</view>

							<view v-if="post.store" class="post-attach store-preview" @click="openStore(post.store)">
								<view class="store-mark">{{ (post.store.name || '店').slice(0, 1) }}</view>
								<view class="attach-main">
									<text class="attach-type">帖子推荐店铺</text>
									<text class="attach-title">{{ post.store.name }}</text>
									<text class="attach-meta">评分 {{ post.store.score }} · {{ post.store.fans }} 关注</text>
								</view>
								<text class="open-link">进店</text>
							</view>

							<view class="post-actions">
								<button class="action" :class="{ on: post.liked }" @click="likePost(post)">赞 {{ post.likeCount || 0 }}</button>
								<button class="action" :class="{ on: post.wanted }" @click="toggleAction(post, 'want')">种草 {{ post.wantCount || 0 }}</button>
								<button class="action" :class="{ on: post.collected }" @click="toggleAction(post, 'collect')">收藏 {{ post.collectCount || 0 }}</button>
								<button class="action" @click="toggleComment(post)">评论 {{ post.commentCount || 0 }}</button>
							</view>

							<view v-if="post.comments && post.comments.length" class="comment-list">
								<view v-for="comment in post.comments" :key="comment.id" class="comment">
									<text class="comment-author">{{ comment.author }}：</text>
									<text class="comment-text">{{ comment.content }}</text>
								</view>
							</view>
							<view v-if="commentingPostId === post.id" class="comment-box">
								<input v-model="commentText" class="comment-input" placeholder="写下你的评论" />
								<button class="comment-btn" @click="sendComment(post)">发送</button>
							</view>
						</view>
							</view>
						</view>
						</view>

						<view v-if="posts.length === 0" class="empty">暂无帖子，来发布第一条讨论吧。</view>
					</view>
				</view>
			</view>
		</scroll-view>
		<view v-if="showPostModal" class="modal-mask" @click="closePostModal">
			<view class="post-modal" @click.stop>
				<view class="modal-head">
					<view>
						<text class="modal-kicker">发布到：{{ topic.title || '话题讨论' }}</text>
						<text class="modal-title">写下你的讨论</text>
						<text class="modal-subtitle">分享真实体验、避坑点、购买建议或者晒单心得</text>
					</view>
					<view class="modal-close" @click="closePostModal">×</view>
				</view>
				<view class="modal-body">
					<view class="modal-main">
						<view class="writer-card">
							<view class="writer-head">
								<view class="avatar writer-avatar">我</view>
								<view>
									<text class="field-label">发布到当前话题</text>
									<text class="writer-sub">将被加入「{{ topic.title || '话题讨论' }}」话题</text>
								</view>
								<text class="draft-state">可保存草稿</text>
							</view>
							<view class="editor-title-row">
								<input v-model="postTitle" class="post-title-input" maxlength="80" placeholder="给你的讨论起个吸引人的标题吧..." />
								<text class="title-count">{{ postTitle.length }}/80</text>
							</view>
							<view class="editor-toolbar">
								<text class="tool-icon" @click="applyInlineFormat('bold')">B</text>
								<text class="tool-icon italic" @click="applyInlineFormat('italic')">I</text>
								<text class="tool-icon underline" @click="applyInlineFormat('underline')">U</text>
								<text class="tool-sep"></text>
								<text class="tool-icon" @click="applyBlockFormat('list')">≡</text>
								<text class="tool-icon" @click="applyBlockFormat('quote')">“</text>
								<text class="tool-icon" @click="applyInlineFormat('code')">&lt;/&gt;</text>
								<text class="tool-icon" :class="{ on: showEmojiPanel }" @click="showEmojiPanel = !showEmojiPanel">☺</text>
								<text class="tool-icon" @click="chooseImages">▧</text>
								<text class="tool-icon" @click="chooseImages">▣</text>
								<text class="ai-helper">AI 助手</text>
							</view>
							<view v-if="showEmojiPanel" class="post-emoji-panel">
								<view class="emoji-title">{{ currentEmojiGroupName }}</view>
								<scroll-view class="emoji-grid" scroll-y :show-scrollbar="false">
									<view class="emoji-grid-inner">
										<button v-for="emoji in currentEmojiOptions" :key="emoji" class="emoji-item" @click="choosePostEmoji(emoji)">{{ emoji }}</button>
									</view>
								</scroll-view>
								<view class="emoji-tabs">
									<button v-for="group in emojiGroups" :key="group.key" class="emoji-tab" :class="{ on: activeEmojiGroup === group.key }" @click="activeEmojiGroup = group.key">{{ group.name }}</button>
								</view>
							</view>
							<view class="editor-body-wrap">
								<textarea v-model="postContent" class="post-input modal-input" maxlength="5000" placeholder="详细描述你的使用体验、优缺点、适用场景等，帮助更多人做出选择..." @input="handlePostInput" />
								<text class="body-count">{{ postContent.length }}/5000</text>
							</view>
							<view v-if="postContent.trim()" class="markdown-preview">
								<text class="preview-label">效果预览</text>
								<rich-text class="preview-content" :nodes="renderMarkdown(postContent)"></rich-text>
							</view>

							<view class="tag-editor">
								<view>
									<text class="field-label">添加标签（可选）</text>
									<text class="writer-sub">为你的讨论添加标签，帮助更多感兴趣的人看到</text>
								</view>
								<view class="draft-tags">
									<text v-for="tag in draftTagOptions" :key="tag" class="draft-tag" :class="{ on: selectedDraftTags.includes(tag) }" @click="toggleDraftTag(tag)"># {{ tag }}</text>
									<text class="draft-tag add">+ 添加标签</text>
									<text class="tag-count">{{ selectedDraftTags.length }}/5</text>
								</view>
							</view>

						</view>
					</view>
					<view class="modal-side">
						<view class="modal-side-card">
							<text class="side-title">附件</text>
							<text class="modal-tip">添加图片或文件，让你的讨论更生动</text>
							<view class="upload-drop" :class="{ disabled: uploading }" @click="chooseImages">
								<text class="upload-cloud">☁</text>
								<text class="upload-title">{{ uploading ? '上传中' : '点击上传或拖拽文件到此处' }}</text>
								<text class="upload-desc">支持图片，最多 6 张</text>
							</view>
							<view class="upload-added">
								<text class="modal-pick-title">已添加（最多 6 张）</text>
								<view class="thumb-row">
									<view v-for="(img, index) in postImages" :key="img" class="thumb-wrap">
										<image class="thumb-img" :src="resolveImageUrl(img)" mode="aspectFill"></image>
										<text class="thumb-remove" @click.stop="removeImage(index)">×</text>
									</view>
									<view v-if="postImages.length < 6" class="thumb-add" @click.stop="chooseImages">+</view>
								</view>
							</view>
						</view>

					</view>
				</view>
				<view class="modal-actions">
					<button class="modal-settings">更多设置⌄</button>
					<view class="modal-action-right">
						<button class="modal-ghost" @click="saveDraft">保存草稿</button>
						<button class="modal-submit" :disabled="posting" :loading="posting" @click="publishPost">✈ {{ posting ? '发布中...' : '发布讨论' }}</button>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { buildGoodsDetailUrl, formatTopicHeat, withTopicHeat } from '../../data/catalog.js'
	import { buildRequestUrl } from '@/config/env.js'
	import { createTopicComment, createTopicPost, fetchTopic, fetchTopicPosts, followTopic, toggleTopicPostAction, toggleTopicPostLike, unfollowTopic } from '@/services/shop.js'
	import { isImageUrl, resolveImageUrl } from '@/utils/media.js'
	import { pickErrorMessage } from '@/utils/auth.js'

	export default {
		data() {
			return {
				topicId: '',
				keyword: '',
				topic: {},
				posts: [],
				postTitle: '',
				postContent: '',
				postImages: [],
				selectedDraftTags: [],
				draftTagOptions: ['数码配件', '学习效率', '开学必备', '避坑指南', '真实体验'],
				postSort: 'latest',
				showPostModal: false,
				showEmojiPanel: false,
				activeEmojiGroup: 'face',
				editorCursor: 0,
				following: false,
				posting: false,
				uploading: false,
				commentingPostId: '',
				commentText: '',
				emojiGroups: [
					{ key: 'face', name: '经典', items: ['😀', '😁', '😂', '🤣', '😊', '😇', '🙂', '😉', '😍', '😘', '😋', '😜', '😎', '🤓', '🤔', '🤗', '😳', '🥺', '😭', '😤', '😡', '😱', '😴', '🤒', '😵', '🤯', '🥳', '😅', '😆', '😬', '🙄', '😏', '😌', '😔', '😮', '🤭', '🤫', '🤐', '😷', '🤧', '🥰', '😚', '😛', '😝', '🤤', '😪', '😫', '😈'] },
					{ key: 'hand', name: '手势', items: ['👍', '👎', '👌', '✌️', '🤞', '🤟', '🤙', '👋', '👏', '🙌', '🙏', '💪', '🤝', '🫶', '☝️', '👉', '👈', '👇', '👆', '✋', '🤚', '🖐️', '🫡', '🤲'] },
					{ key: 'trade', name: '交易', items: ['💬', '💰', '💸', '🧾', '📦', '🚚', '🎁', '🏷️', '💳', '✅', '❌', '⚠️', '📌', '🔍', '🛒', '⭐', '🔥', '💡', '📮', '⏰', '🧡', '💯', '📱', '💻', '🎧', '📚', '🏠', '☕'] },
					{ key: 'heart', name: '心情', items: ['❤️', '🧡', '💛', '💚', '💙', '💜', '🤍', '🤎', '🖤', '💔', '💕', '💞', '💓', '💗', '💖', '💘', '💝', '✨', '🌟', '🎉', '🌈', '☀️', '🌙', '🍀'] }
				]
			}
		},
		computed: {
			latestPosts() {
				const list = this.posts.slice()
				const toTime = (post) => {
					const parsed = Date.parse(post.createdAt || '')
					if (!Number.isNaN(parsed)) return parsed
					return Number(post.id || 0)
				}
				return list.sort((a, b) => toTime(b) - toTime(a))
			},
			hotPosts() {
				const list = this.posts.slice()
				const score = (post) => (post.likeCount || 0) * 3 + (post.commentCount || 0) * 2 + (post.collectCount || 0) + (post.wantCount || 0)
				return list.sort((a, b) => score(b) - score(a) || Number(b.id || 0) - Number(a.id || 0))
			},
			sortedPosts() {
				return this.postSort === 'hot' ? this.hotPosts : this.latestPosts
			},
			masonryColumns() {
				return this.sortedPosts.reduce((columns, post, index) => {
					columns[index % 2].push(post)
					return columns
				}, [[], []])
			},
			currentPostSub() {
				return this.postSort === 'hot'
					? `${this.posts.length} 条讨论按点赞、评论、收藏综合排序`
					: `${this.posts.length} 条讨论按发布时间展示`
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
		onLoad(query) {
			this.topicId = query && query.id ? decodeURIComponent(query.id) : ''
			this.loadAll()
		},
		methods: {
			isImageUrl,
			resolveImageUrl,
			formatTopicHeat,
			applyTopic(topic) {
				this.topic = withTopicHeat(topic)
			},
			syncPostCount() {
				this.applyTopic({ ...this.topic, postCount: this.posts.length })
			},
			async loadAll() {
				await Promise.all([this.loadTopic(), this.loadPosts()])
			},
			async loadTopic() {
				try {
					const body = await fetchTopic(this.topicId)
					this.applyTopic(body && body.code === 0 ? body.data : {})
				} catch (e) {
					uni.showToast({ title: pickErrorMessage(e) || '话题加载失败', icon: 'none' })
				}
			},
			async loadPosts() {
				try {
					const body = await fetchTopicPosts(this.topicId)
					this.posts = body && body.code === 0 && Array.isArray(body.data) ? body.data : []
				} catch (e) {
					this.posts = []
				}
			},
			chooseImages() {
				if (this.uploading) return
				if (this.postImages.length >= 6) {
					uni.showToast({ title: '最多上传 6 张图片', icon: 'none' })
					return
				}
				uni.chooseImage({
					count: 6 - this.postImages.length,
					success: async (res) => {
						const files = res.tempFilePaths || []
						if (!files.length) return
						this.uploading = true
						uni.showLoading({ title: '上传中...' })
						try {
							for (const filePath of files) {
								const url = await this.uploadOne(filePath)
								if (url) this.postImages.push(url)
							}
							uni.showToast({ title: '上传成功' })
						} catch (e) {
							uni.showToast({ title: '图片上传失败', icon: 'none' })
						} finally {
							this.uploading = false
							uni.hideLoading()
						}
					}
				})
			},
			uploadOne(filePath) {
				return new Promise((resolve, reject) => {
					uni.uploadFile({
						url: buildRequestUrl('/api/upload/image'),
						filePath,
						name: 'file',
						success: (uploadRes) => {
							let data = {}
							try {
								data = typeof uploadRes.data === 'string' ? JSON.parse(uploadRes.data) : uploadRes.data
							} catch (e) {
								data = {}
							}
							if (uploadRes.statusCode >= 200 && uploadRes.statusCode < 300 && (data.code === 200 || data.code === 0) && data.data) {
								resolve(data.data)
								return
							}
							reject(new Error(data.message || '上传失败'))
						},
						fail: reject
					})
				})
			},
			removeImage(index) {
				this.postImages.splice(index, 1)
			},
			handlePostInput(e) {
				const cursor = e && e.detail ? e.detail.cursor : null
				if (typeof cursor === 'number' && cursor >= 0) {
					this.editorCursor = cursor
				}
			},
			openPostModal() {
				this.restoreDraft()
				this.showPostModal = true
			},
			closePostModal() {
				if (this.posting || this.uploading) return
				this.showPostModal = false
			},
			toggleDraftTag(tag) {
				const index = this.selectedDraftTags.indexOf(tag)
				if (index >= 0) {
					this.selectedDraftTags.splice(index, 1)
					return
				}
				if (this.selectedDraftTags.length >= 5) {
					uni.showToast({ title: '最多添加 5 个标签', icon: 'none' })
					return
				}
				this.selectedDraftTags.push(tag)
			},
			saveDraft() {
				uni.setStorageSync(this.draftStorageKey(), {
					title: this.postTitle,
					content: this.postContent,
					images: this.postImages,
					tags: this.selectedDraftTags
				})
				uni.showToast({ title: '草稿已保存', icon: 'none' })
			},
			restoreDraft() {
				const draft = uni.getStorageSync(this.draftStorageKey())
				if (!draft || typeof draft !== 'object') return
				this.postTitle = draft.title || ''
				this.postContent = draft.content || ''
				this.postImages = Array.isArray(draft.images) ? draft.images : []
				this.selectedDraftTags = Array.isArray(draft.tags) ? draft.tags : []
			},
			draftStorageKey() {
				return `topic-post-draft-${this.topicId || 'new'}`
			},
			insertAtCursor(text) {
				const value = this.postContent || ''
				const cursor = Math.max(0, Math.min(this.editorCursor || value.length, value.length))
				this.postContent = value.slice(0, cursor) + text + value.slice(cursor)
				this.editorCursor = cursor + text.length
			},
			applyInlineFormat(type) {
				const formats = {
					bold: ['**', '**', '加粗文字'],
					italic: ['*', '*', '斜体文字'],
					underline: ['<u>', '</u>', '下划线文字'],
					code: ['`', '`', '代码']
				}
				const format = formats[type]
				if (!format) return
				this.insertAtCursor(`${format[0]}${format[2]}${format[1]}`)
			},
			applyBlockFormat(type) {
				if (type === 'quote') {
					this.insertAtCursor('\n> 引用内容\n')
				} else if (type === 'list') {
					this.insertAtCursor('\n- 列表项\n- 列表项\n')
				}
			},
			choosePostEmoji(emoji) {
				this.insertAtCursor(emoji)
			},
			escapeHtml(text) {
				return String(text || '')
					.replace(/&/g, '&amp;')
					.replace(/</g, '&lt;')
					.replace(/>/g, '&gt;')
			},
			renderMarkdown(text) {
				if (!text) return ''
				let html = this.escapeHtml(text)
					.replace(/```([\s\S]*?)```/g, '<pre style="margin:10px 0;padding:12px;border-radius:8px;background:#edf2ef;color:#17231d;font-size:13px;line-height:1.65;white-space:pre-wrap;overflow-x:auto">$1</pre>')
					.replace(/^&gt;\s?(.+)$/gm, '<div style="margin:8px 0;padding:8px 12px;border-left:3px solid #12372a;background:#eef6f1;color:#365046">$1</div>')
					.replace(/^### (.+)$/gm, '<div style="margin:12px 0 6px;color:#17231d;font-size:16px;font-weight:900">$1</div>')
					.replace(/^## (.+)$/gm, '<div style="margin:14px 0 7px;color:#17231d;font-size:17px;font-weight:900">$1</div>')
					.replace(/^# (.+)$/gm, '<div style="margin:16px 0 8px;color:#12372a;font-size:18px;font-weight:900">$1</div>')
					.replace(/`([^`]+)`/g, '<code style="padding:2px 6px;border-radius:6px;background:#edf2ef;color:#b42318;font-size:13px">$1</code>')
					.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
					.replace(/__(.+?)__/g, '<strong>$1</strong>')
					.replace(/\*(.+?)\*/g, '<em>$1</em>')
					.replace(/_(.+?)_/g, '<em>$1</em>')
					.replace(/&lt;u&gt;([\s\S]*?)&lt;\/u&gt;/g, '<span style="text-decoration:underline">$1</span>')
					.replace(/^\d+[.]\s(.+)$/gm, '<div style="display:flex;gap:7px;margin:4px 0;padding-left:12px"><span style="color:#12372a;font-weight:900">•</span><span>$1</span></div>')
					.replace(/^[-*]\s(.+)$/gm, '<div style="display:flex;gap:7px;margin:4px 0;padding-left:12px"><span style="color:#12372a;font-weight:900">•</span><span>$1</span></div>')
					.replace(/\n{3,}/g, '\n\n')
					.replace(/\n/g, '<br/>')
				return `<div style="line-height:1.78;color:#2f3a35;font-size:14px;word-break:break-word">${html}</div>`
			},
			async toggleTopicFollow() {
				if (!this.topicId || this.following) return
				this.following = true
				try {
					const body = this.topic.followed ? await unfollowTopic(this.topicId) : await followTopic(this.topicId)
					if (body && body.code === 0 && body.data) {
						this.applyTopic(body.data)
						uni.showToast({ title: this.topic.followed ? '已关注话题' : '已取消关注', icon: 'none' })
					}
				} catch (e) {
					if (e && e.statusCode === 401) uni.navigateTo({ url: '/pages/auth/login' })
					uni.showToast({ title: pickErrorMessage(e) || '操作失败', icon: 'none' })
				} finally {
					this.following = false
				}
			},
			async publishPost() {
				if (!this.postTitle.trim() && !this.postContent.trim()) {
					uni.showToast({ title: '请先写点内容', icon: 'none' })
					return
				}
				this.posting = true
				try {
					const contentParts = []
					if (this.postTitle.trim()) contentParts.push(this.postTitle.trim())
					if (this.postContent.trim()) contentParts.push(this.postContent.trim())
					if (this.selectedDraftTags.length) contentParts.push(this.selectedDraftTags.map(tag => `#${tag}`).join(' '))
					const body = await createTopicPost(this.topicId, {
						content: contentParts.join('\n\n'),
						images: this.postImages,
						productId: '',
						storeId: ''
					})
					this.posts = body.data || []
					this.postTitle = ''
					this.postContent = ''
					this.postImages = []
					this.selectedDraftTags = []
					this.showPostModal = false
					uni.removeStorageSync(this.draftStorageKey())
					this.syncPostCount()
					this.loadTopic()
					uni.showToast({ title: '已发布', icon: 'success' })
				} catch (e) {
					if (e && e.statusCode === 401) uni.navigateTo({ url: '/pages/auth/login' })
					uni.showToast({ title: pickErrorMessage(e) || '发布失败', icon: 'none' })
				} finally {
					this.posting = false
				}
			},
			async likePost(post) {
				try {
					const body = await toggleTopicPostLike(post.id)
					this.posts = body.data || []
					this.loadTopic()
				} catch (e) {
					if (e && e.statusCode === 401) uni.navigateTo({ url: '/pages/auth/login' })
					uni.showToast({ title: pickErrorMessage(e) || '点赞失败', icon: 'none' })
				}
			},
			async toggleAction(post, actionType) {
				try {
					const body = await toggleTopicPostAction(post.id, actionType)
					this.posts = body.data || []
				} catch (e) {
					if (e && e.statusCode === 401) uni.navigateTo({ url: '/pages/auth/login' })
					uni.showToast({ title: pickErrorMessage(e) || '操作失败', icon: 'none' })
				}
			},
			toggleComment(post) {
				this.commentingPostId = this.commentingPostId === post.id ? '' : post.id
				this.commentText = ''
			},
			async sendComment(post) {
				if (!this.commentText.trim()) {
					uni.showToast({ title: '请输入评论', icon: 'none' })
					return
				}
				try {
					const body = await createTopicComment(post.id, { content: this.commentText.trim() })
					this.posts = body.data || []
					this.commentText = ''
					this.commentingPostId = ''
					this.loadTopic()
				} catch (e) {
					if (e && e.statusCode === 401) uni.navigateTo({ url: '/pages/auth/login' })
					uni.showToast({ title: pickErrorMessage(e) || '评论失败', icon: 'none' })
				}
			},
			openGoods(item) {
				uni.navigateTo({ url: buildGoodsDetailUrl(item) })
			},
			openStore(store) {
				uni.navigateTo({ url: '/pages/store/store?name=' + encodeURIComponent(store.name) })
			},
			navTo(url) {
				if (['/pages/home/home', '/pages/browse/browse', '/pages/cart/cart', '/pages/message/message', '/pages/user/index'].includes(url)) {
					uni.switchTab({ url })
					return
				}
				uni.navigateTo({ url })
			},
			goBrowse() {
				uni.navigateBack({
					fail() {
						uni.switchTab({ url: '/pages/browse/browse' })
					}
				})
			},
			searchTopics() {
				uni.switchTab({ url: '/pages/browse/browse' })
			},
			openCreateTopic() {
				uni.switchTab({ url: '/pages/browse/browse' })
			}
		}
	}
</script>

<style lang="scss" scoped>
	.topic-page { min-height: 100vh; background: #f3f5f7; }
	.topbar { position: sticky; top: 0; z-index: 10; background: rgba(255,255,255,.9); backdrop-filter: blur(22px); border-bottom: 1px solid rgba(203,213,225,.55); box-shadow: 0 10px 40px rgba(60,64,67,.06); }
	.topbar-inner { display: grid; grid-template-columns: 300px 350px minmax(0, 1fr); align-items: center; gap: 18px; height: 82px; padding: 0 22px; }
	.brand { display: flex; align-items: center; gap: 12px; }
	.brand-mark { width: 42px; height: 42px; border-radius: 8px; overflow: hidden; flex-shrink: 0; }
	.brand-logo { width: 100%; height: 100%; }
	.brand-name, .brand-sub { display: block; }
	.brand-name { font-size: 20px; font-weight: 900; color: #202124; }
	.brand-sub { margin-top: 2px; font-size: 12px; color: #667085; }
	.web-nav { justify-self: center; display: flex; align-items: center; gap: 4px; padding: 5px; height: 50px; border-radius: 999px; background: rgba(255,255,255,.72); border: 1px solid rgba(203,213,225,.72); box-sizing: border-box; box-shadow: 0 14px 38px rgba(60,64,67,.08); overflow-x: auto; white-space: nowrap; }
	.nav-link { width: auto; padding: 0 8px; flex-shrink: 0; height: 38px; border-radius: 999px; display: flex; align-items: center; justify-content: center; color: #5f6b85; font-size: 12px; font-weight: 800; }
	.nav-link.on, .nav-link:hover { background: linear-gradient(135deg, #ffffff, #f5f7fa); color: #12372a; box-shadow: 0 10px 26px rgba(18, 55, 42, .14); }
	.top-actions { justify-self: end; display: flex; align-items: center; gap: 12px; min-width: 0; }
	.search { width: clamp(220px, 20vw, 300px); height: 44px; border-radius: 12px; background: rgba(255,255,255,.82); border: 1px solid rgba(203,213,225,.78); display: flex; align-items: center; padding: 0 8px 0 14px; min-width: 0; box-shadow: 0 12px 30px rgba(60,64,67,.06); transition: box-shadow .22s ease, border-color .22s ease; }
	.search:focus-within { border-color: rgba(66,133,244,.32); box-shadow: 0 16px 38px rgba(60,64,67,.1); }
	.search-icon { font-size: 22px; color: #667085; margin-right: 8px; }
	.search-input { flex: 1; min-width: 0; font-size: 14px; color: #17231d; }
	.search-action, .create-shortcut { display: flex; align-items: center; justify-content: center; border-radius: 8px; font-size: 14px; font-weight: 900; }
	.search-action { width: 70px; height: 34px; background: #12372a; color: #fff; }
	.create-shortcut { height: 44px; padding: 0 18px; background: #12372a; color: #fff; flex-shrink: 0; box-shadow: 0 14px 34px rgba(18,55,42,.16); }
	.scroll { height: calc(100vh - 82px); }
	.page { position: relative; padding: 28rpx; }
	.back-row {
		display: flex;
		align-items: center;
		gap: 4px;
		margin-bottom: 16rpx;
		color: #1f5c43;
		width: fit-content;
		cursor: pointer;
	}
	.back-icon {
		font-size: 36rpx;
		line-height: 1;
		font-weight: 700;
	}
	.back-text {
		font-size: 26rpx;
		font-weight: 800;
	}
	.hero, .composer, .post-card, .side-card { background: #fff; border: 1rpx solid #e5e9ef; border-radius: 8px; box-shadow: 0 14rpx 34rpx rgba(18, 32, 46, .06); }
	.hero { position: relative; display: grid; grid-template-columns: 330rpx minmax(0, 1fr); gap: 30rpx; padding: 30rpx 118rpx 30rpx 30rpx; align-items: center; background: linear-gradient(135deg, #ffffff 0%, #f6faf8 58%, #fff8f0 100%); }
	.cover { height: 240rpx; border-radius: 8px; overflow: hidden; background: linear-gradient(135deg, #eaf3ef, #f7efe4); display: flex; align-items: center; justify-content: center; color: #12372a; font-size: 38rpx; font-weight: 900; }
	.cover-img, .draft-image { width: 100%; height: 100%; display: block; }
	.hero-line, .tags, .composer-head, .composer-tools, .post-head, .post-actions { display: flex; align-items: center; gap: 12rpx; flex-wrap: wrap; }
	.hero-line { justify-content: space-between; }
	.badge, .tag { display: inline-flex; align-items: center; justify-content: center; min-height: 42rpx; padding: 0 16rpx; border-radius: 999rpx; background: #eef6f1; color: #12372a; font-size: 22rpx; font-weight: 900; }
	.heat, .desc, .composer-sub, .post-time, .attach-type, .attach-meta, .pick-meta, .count { color: #667085; }
	.title, .desc, .composer-title, .composer-sub, .toolbar-title, .toolbar-sub, .post-author, .post-time, .post-text, .attach-type, .attach-title, .attach-meta, .side-title, .pick-title, .pick-meta, .modal-kicker, .modal-title, .field-label, .modal-tip, .preview-label { display: block; }
	.title { margin-top: 16rpx; font-size: 42rpx; line-height: 1.22; font-weight: 900; color: #111827; }
	.desc { margin-top: 12rpx; font-size: 26rpx; line-height: 1.7; }
	.tags { margin-top: 18rpx; }
	.topic-actions { display: flex; align-items: center; gap: 14rpx; flex-wrap: wrap; margin-top: 22rpx; }
	.topic-stat { min-width: 110rpx; height: 68rpx; padding: 0 16rpx; border-radius: 8px; background: rgba(255,255,255,.86); border: 1rpx solid #e4ece7; display: flex; flex-direction: column; align-items: center; justify-content: center; box-sizing: border-box; }
	.stat-num, .stat-label { display: block; }
	.stat-num { color: #12372a; font-size: 26rpx; font-weight: 900; line-height: 1; }
	.stat-label { margin-top: 5rpx; color: #667085; font-size: 20rpx; font-weight: 800; }
	.follow-topic-btn { margin: 0; width: 170rpx; height: 68rpx; border-radius: 8px; background: #12372a; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 24rpx; font-weight: 900; box-shadow: 0 14rpx 28rpx rgba(18,55,42,.16); }
	.follow-topic-btn.followed { background: #eef5f0; color: #12372a; border: 1rpx solid #cfe0d6; box-shadow: none; }
	.follow-topic-btn::after { border: 0; }
	.topic-fab { position: absolute; right: 30rpx; top: 50%; transform: translateY(-50%); z-index: 4; width: 76rpx; height: 76rpx; border-radius: 50%; background: #12372a; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 44rpx; font-weight: 700; line-height: 1; box-shadow: 0 18rpx 38rpx rgba(18,55,42,.24); }
	.topic-fab::after { border: 0; }
	.layout { display: block; margin-top: 20rpx; }
	.feed, .side { display: grid; gap: 18rpx; }
	.composer, .post-card, .side-card { padding: 24rpx; }
	.post-section { display: grid; gap: 16rpx; }
	.post-section-head { padding: 24rpx; background: #fff; border: 1rpx solid #e5e9ef; border-radius: 8px; box-shadow: 0 14rpx 34rpx rgba(18, 32, 46, .05); display: flex; align-items: center; justify-content: space-between; gap: 18rpx; flex-wrap: wrap; }
	.post-sort-tabs { display: flex; align-items: center; gap: 8rpx; padding: 6rpx; border-radius: 999rpx; background: #eef3f0; border: 1rpx solid #dfe8e3; }
	.post-sort-tab { margin: 0; height: 54rpx; padding: 0 22rpx; border-radius: 999rpx; background: transparent; color: #667085; display: flex; align-items: center; justify-content: center; font-size: 23rpx; font-weight: 900; }
	.post-sort-tab.on { background: #fff; color: #12372a; box-shadow: 0 8rpx 22rpx rgba(17,38,28,.10); }
	.post-sort-tab::after { border: 0; }
	.post-grid { display: flex; align-items: flex-start; gap: 20rpx; width: 100%; }
	.post-column { width: calc((100% - 20rpx) / 2); min-width: 0; display: flex; flex-direction: column; gap: 20rpx; }
	.toolbar-title { color: #17231d; font-size: 30rpx; font-weight: 900; }
	.toolbar-sub { margin-top: 4rpx; color: #667085; font-size: 22rpx; }
	.avatar { width: 70rpx; height: 70rpx; border-radius: 8px; background: #12372a; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 28rpx; font-weight: 900; flex: 0 0 auto; }
	.avatar-img { width: 100%; height: 100%; display: block; border-radius: inherit; }
	.composer-title, .post-author { color: #17231d; font-size: 28rpx; font-weight: 900; }
	.composer-sub, .post-time { margin-top: 4rpx; font-size: 22rpx; }
	.post-input { width: 100%; min-height: 170rpx; margin-top: 18rpx; padding: 20rpx; border-radius: 8px; background: #f8faf9; box-sizing: border-box; font-size: 27rpx; line-height: 1.6; }
	.draft-images, .post-images { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10rpx; margin-top: 18rpx; }
	.draft-image-wrap { position: relative; height: 150rpx; border-radius: 8px; overflow: hidden; background: #edf3ef; }
	.remove-image { position: absolute; top: 8rpx; right: 8rpx; width: 36rpx; height: 36rpx; border-radius: 999rpx; background: rgba(17, 24, 39, .72); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 28rpx; }
	.attach-preview, .post-attach { display: grid; grid-template-columns: 112rpx minmax(0, 1fr) 72rpx; gap: 14rpx; align-items: center; margin-top: 16rpx; padding: 14rpx; border-radius: 8px; background: #f8faf9; border: 1rpx solid #e7ece8; }
	.modal-main > .draft-images,
	.modal-main > .attach-preview { margin-top: 14rpx; }
	.attach-cover, .store-mark, .pick-cover { border-radius: 8px; background: #edf3ef; overflow: hidden; display: flex; align-items: center; justify-content: center; color: #12372a; font-weight: 900; }
	.attach-cover, .store-mark { width: 112rpx; height: 112rpx; }
	.store-mark { background: #12372a; color: #fff; font-size: 34rpx; }
	.attach-title { margin-top: 5rpx; color: #17231d; font-size: 26rpx; font-weight: 900; line-height: 1.35; }
	.attach-meta { margin-top: 4rpx; font-size: 22rpx; }
	.clear-attach, .open-link { height: 54rpx; border-radius: 8px; background: #fff; color: #12372a; display: flex; align-items: center; justify-content: center; font-size: 22rpx; font-weight: 900; border: 1rpx solid #dbe5df; }
	.composer-tools { margin-top: 18rpx; }
	button { margin: 0; padding: 0; border: 0; }
	button::after { border: 0; }
	.tool-btn, .post-btn, .action, .comment-btn { height: 60rpx; border-radius: 8px; display: flex; align-items: center; justify-content: center; box-sizing: border-box; font-weight: 900; }
	.tool-btn { min-width: 116rpx; padding: 0 18rpx; background: #eef3f0; color: #12372a; font-size: 24rpx; }
	.count { margin-left: auto; font-size: 22rpx; }
	.post-btn { width: 116rpx; background: #12372a; color: #fff; font-size: 25rpx; }
	.post-card { position: relative; overflow: hidden; width: 100%; min-width: 0; min-height: 360rpx; background: linear-gradient(180deg, #ffffff 0%, #fbfdfb 100%); border-color: #dfe8e3; box-sizing: border-box; transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease; }
	.post-card::before { content: ""; position: absolute; left: 0; top: 0; right: 0; height: 6rpx; background: linear-gradient(90deg, #12372a, #d66a2c); opacity: .82; }
	.post-card:hover { transform: translateY(-2px); border-color: #cbded1; box-shadow: 0 20rpx 46rpx rgba(17,38,28,.09); }
	.post-head { position: relative; padding: 12rpx; border-radius: 8px; background: #f8fbf9; border: 1rpx solid #edf1ee; justify-content: space-between; }
	.post-user { min-width: 0; flex: 1; }
	.post-author { color: #12372a; }
	.post-text { margin-top: 18rpx; color: #2f3a35; font-size: 29rpx; line-height: 1.78; background: #fbfdfc; border: 1rpx solid #edf1ee; border-radius: 8px; padding: 18rpx; }
	.post-images.cols-1 { grid-template-columns: minmax(0, 460rpx); }
	.post-images.cols-2 { grid-template-columns: repeat(2, minmax(0, 240rpx)); }
	.post-image { width: 100%; height: 200rpx; border-radius: 8px; background: #edf3ef; }
	.post-actions { margin-top: 18rpx; padding-top: 16rpx; border-top: 1rpx solid #edf0f3; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10rpx; }
	.action { min-width: 0; padding: 0 12rpx; background: #f3f6f4; color: #475467; font-size: 23rpx; }
	.action.on { background: #12372a; color: #fff; }
	.comment-list { margin-top: 16rpx; padding: 16rpx; border-radius: 8px; background: #f8faf9; display: grid; gap: 10rpx; }
	.comment { font-size: 24rpx; line-height: 1.5; }
	.comment-author { color: #12372a; font-weight: 900; }
	.comment-text { color: #475467; }
	.comment-box { display: grid; grid-template-columns: minmax(0, 1fr) 116rpx; gap: 10rpx; margin-top: 14rpx; }
	.comment-input { height: 60rpx; padding: 0 18rpx; border-radius: 8px; background: #f8faf9; font-size: 25rpx; }
	.comment-btn { background: #12372a; color: #fff; }
	.empty { padding: 42rpx; text-align: center; color: #98a2b3; background: #fff; border-radius: 8px; border: 1rpx solid #e5e9ef; }
	.side-title { color: #17231d; font-size: 28rpx; font-weight: 900; }
	.pick-list { max-height: 620rpx; margin-top: 12rpx; }
	.pick-item { display: grid; grid-template-columns: 88rpx minmax(0, 1fr); gap: 12rpx; align-items: center; padding: 14rpx; margin-top: 12rpx; border-radius: 8px; border: 1rpx solid #e7ece8; background: #fff; }
	.pick-item.on { border-color: #12372a; background: #f1f7f3; }
	.pick-cover { width: 88rpx; height: 88rpx; font-size: 20rpx; }
	.pick-title { color: #17231d; font-size: 24rpx; font-weight: 900; line-height: 1.35; }
	.pick-meta { margin-top: 4rpx; font-size: 21rpx; }
	.store-pick .store-mark { width: 88rpx; height: 88rpx; font-size: 28rpx; }
	.modal-mask { position: fixed; inset: 0; z-index: 50; padding: 18rpx; box-sizing: border-box; background: rgba(15, 23, 42, .28); backdrop-filter: blur(12px); display: flex; align-items: center; justify-content: center; }
	.post-modal { width: min(1280px, 100%); max-height: calc(100vh - 36rpx); overflow: hidden; border-radius: 8px; background: #f8faf9; border: 1px solid rgba(226,232,240,.96); box-shadow: 0 34rpx 110rpx rgba(15,23,42,.24); display: flex; flex-direction: column; }
	.modal-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18rpx; padding: 34rpx 38rpx 28rpx; background: #fff; border-bottom: 1rpx solid #eef1ee; }
	.modal-kicker { max-width: 720rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #667085; font-size: 22rpx; font-weight: 800; }
	.modal-title { margin-top: 8rpx; color: #12372a; font-size: 42rpx; font-weight: 900; }
	.modal-subtitle { display: block; margin-top: 10rpx; color: #8a94a6; font-size: 24rpx; }
	.modal-close { width: 66rpx; height: 66rpx; border-radius: 50%; background: #eef2f1; color: #344054; display: flex; align-items: center; justify-content: center; font-size: 34rpx; font-weight: 900; flex-shrink: 0; }
	.modal-body { display: grid; grid-template-columns: minmax(0, 1fr) 460rpx; gap: 20rpx; padding: 18rpx 36rpx 20rpx; overflow: auto; }
	.modal-main { min-width: 0; }
	.writer-card { border-radius: 8px; background: #fff; border: 1rpx solid #e7ece8; box-shadow: 0 14rpx 34rpx rgba(17,38,28,.055); overflow: hidden; }
	.writer-head { display: grid; grid-template-columns: 62rpx minmax(0, 1fr) auto; gap: 14rpx; align-items: center; padding: 18rpx 20rpx; border-bottom: 1rpx solid #edf1ee; }
	.writer-avatar { width: 62rpx; height: 62rpx; font-size: 24rpx; }
	.writer-sub { display: block; margin-top: 4rpx; color: #98a2b3; font-size: 21rpx; }
	.draft-state { color: #4f8270; font-size: 22rpx; font-weight: 800; }
	.field-line { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
	.field-label { color: #344054; font-size: 24rpx; font-weight: 900; }
	.editor-title-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 12rpx; padding: 18rpx 20rpx 0; }
	.post-title-input { height: 58rpx; padding: 0 16rpx; border-radius: 8px; background: #fbfdfc; border: 1rpx solid #e5ebe7; font-size: 26rpx; font-weight: 850; color: #17231d; box-sizing: border-box; }
	.title-count, .body-count, .tag-count { color: #667085; font-size: 21rpx; font-weight: 800; }
	.editor-toolbar { display: flex; align-items: center; gap: 14rpx; flex-wrap: wrap; margin: 16rpx 20rpx 0; padding: 12rpx 14rpx; border-radius: 8px; border: 1rpx solid #e5ebe7; background: #fff; }
	.tool-icon { min-width: 34rpx; height: 34rpx; border-radius: 6px; color: #64748b; display: flex; align-items: center; justify-content: center; font-size: 24rpx; font-weight: 900; }
	.tool-icon.on, .tool-icon:hover { background: #edf5f1; color: #12372a; }
	.tool-icon.italic { font-style: italic; }
	.tool-icon.underline { text-decoration: underline; }
	.tool-sep { width: 1rpx; height: 30rpx; background: #e5ebe7; }
	.ai-helper { margin-left: auto; color: #3b5bdc; font-size: 23rpx; font-weight: 900; }
	.post-emoji-panel { margin-top: 10rpx; padding: 14rpx; border: 1rpx solid #e3ebe6; border-radius: 8px; background: #ffffff; box-shadow: 0 16rpx 34rpx rgba(18, 32, 46, .08); }
	.emoji-title { display: block; color: #17231d; font-size: 22rpx; font-weight: 900; }
	.emoji-grid { margin-top: 10rpx; max-height: 176rpx; overflow-y: auto; }
	.emoji-grid-inner { display: grid; grid-template-columns: repeat(12, 1fr); gap: 8rpx; }
	.emoji-item { height: 44rpx; border-radius: 6px; display: flex; align-items: center; justify-content: center; font-size: 28rpx; background: #f7faf8; }
	.emoji-item:active { background: #e7f2ec; transform: scale(.96); }
	.emoji-tabs { display: flex; gap: 8rpx; margin-top: 12rpx; flex-wrap: wrap; }
	.emoji-tab { height: 42rpx; padding: 0 16rpx; border-radius: 999rpx; background: #f3f6f4; color: #64748b; display: flex; align-items: center; justify-content: center; font-size: 22rpx; font-weight: 800; }
	.emoji-tab.on { background: #12372a; color: #fff; }
	.editor-body-wrap { position: relative; margin: 12rpx 20rpx 0; }
	.modal-input { margin-top: 0; min-height: 230rpx; border-radius: 8px; background: #fff; border: 1rpx solid #e5ebe7; padding: 18rpx; padding-bottom: 48rpx; box-sizing: border-box; font-size: 26rpx; line-height: 1.7; }
	.body-count { position: absolute; right: 18rpx; bottom: 14rpx; }
	.markdown-preview { margin: 14rpx 20rpx 0; padding: 16rpx 18rpx; border-radius: 8px; background: #f8faf9; border: 1rpx solid #e5ebe7; }
	.preview-label { margin-bottom: 10rpx; color: #667085; font-size: 21rpx; font-weight: 900; }
	.preview-content { display: block; }
	.tag-editor, .product-card-picker { padding: 22rpx 20rpx; border-top: 1rpx solid #edf1ee; }
	.draft-tags { display: flex; align-items: center; gap: 10rpx; flex-wrap: wrap; margin-top: 14rpx; }
	.draft-tag { min-height: 44rpx; padding: 0 16rpx; border-radius: 999rpx; background: #f3f7f5; color: #667085; display: flex; align-items: center; justify-content: center; font-size: 22rpx; font-weight: 850; }
	.draft-tag.on, .draft-tag.add { background: #e8f4ee; color: #12372a; }
	.product-card-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 14rpx; margin-top: 16rpx; }
	.product-card-grid.selected-only { grid-template-columns: repeat(auto-fill, minmax(150rpx, 1fr)); }
	.mini-product-card { position: relative; min-height: 190rpx; padding: 12rpx; border-radius: 8px; background: #fbfdfc; border: 1rpx solid #e5ebe7; box-sizing: border-box; }
	.mini-product-card.on { border-color: #12372a; box-shadow: inset 0 0 0 1rpx rgba(18,55,42,.12); }
	.mini-check { position: absolute; top: 10rpx; right: 10rpx; width: 28rpx; height: 28rpx; border-radius: 6rpx; border: 1rpx solid #d2ddd6; background: #fff; color: #12372a; display: flex; align-items: center; justify-content: center; font-size: 18rpx; font-weight: 900; z-index: 1; }
	.mini-cover { height: 88rpx; border-radius: 8px; background: #eef3f0; overflow: hidden; display: flex; align-items: center; justify-content: center; color: #12372a; font-size: 20rpx; font-weight: 900; }
	.mini-title, .mini-sub, .mini-price { display: block; }
	.mini-title { margin-top: 10rpx; color: #17231d; font-size: 22rpx; font-weight: 900; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.mini-sub { margin-top: 4rpx; color: #667085; font-size: 20rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.mini-price { margin-top: 6rpx; color: #12372a; font-size: 22rpx; font-weight: 900; }
	.more-card { display: flex; flex-direction: column; align-items: center; justify-content: center; border-style: dashed; }
	.more-plus { color: #12372a; font-size: 38rpx; font-weight: 700; line-height: 1; }
	.empty-selected-products { min-height: 210rpx; border: 1rpx dashed #d5e2da; border-radius: 8px; background: #f8fbf9; display: flex; align-items: center; justify-content: center; text-align: center; color: #7b8794; font-size: 24rpx; font-weight: 800; }
	.selected-preview-row { display: grid; gap: 12rpx; padding: 0 20rpx 22rpx; }
	.attach-preview.compact { margin-top: 0; }
	.modal-side { display: grid; gap: 16rpx; align-content: start; }
	.modal-side-card { padding: 18rpx; border-radius: 8px; background: #fff; border: 1rpx solid #e7ece8; box-shadow: 0 14rpx 34rpx rgba(17,38,28,.045); }
	.tool-btn.wide { width: 100%; }
	.upload-drop { min-height: 112rpx; margin-top: 16rpx; padding: 18rpx; border-radius: 8px; background: #fbfdfc; border: 1rpx dashed #c9dccf; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; }
	.upload-drop.disabled { opacity: .62; }
	.upload-cloud { color: #12372a; font-size: 34rpx; font-weight: 900; }
	.upload-icon { width: 48rpx; height: 48rpx; border-radius: 8px; background: #12372a; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 30rpx; font-weight: 900; }
	.upload-title, .upload-desc { display: block; }
	.upload-title { color: #12372a; font-size: 24rpx; font-weight: 900; }
	.upload-desc { margin-top: 4rpx; color: #98a2b3; font-size: 20rpx; }
	.upload-added { margin-top: 18rpx; }
	.thumb-row { display: flex; flex-wrap: wrap; gap: 10rpx; margin-top: 12rpx; }
	.thumb-wrap, .thumb-add { position: relative; width: 66rpx; height: 66rpx; border-radius: 8px; background: #f3f7f5; overflow: hidden; border: 1rpx solid #e5ebe7; }
	.thumb-img { width: 100%; height: 100%; display: block; }
	.thumb-remove { position: absolute; top: -4rpx; right: -4rpx; width: 28rpx; height: 28rpx; border-radius: 50%; background: rgba(17,24,39,.76); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 20rpx; font-weight: 900; }
	.thumb-add { display: flex; align-items: center; justify-content: center; color: #12372a; font-size: 34rpx; font-weight: 700; border-style: dashed; }
	.modal-tip { color: #667085; font-size: 22rpx; line-height: 1.65; }
	.modal-pick-section { display: grid; gap: 8rpx; padding-top: 12rpx; border-top: 1rpx solid #edf1ee; }
	.modal-pick-title { color: #344054; font-size: 22rpx; font-weight: 900; }
	.modal-pick-list { max-height: 250rpx; }
	.modal-pick-item { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10rpx; align-items: center; padding: 12rpx; border-radius: 8px; background: #f8faf9; border: 1rpx solid transparent; }
	.modal-pick-item.on { border-color: #12372a; background: #eef6f1; box-shadow: inset 0 0 0 1rpx rgba(18,55,42,.08); }
	.modal-pick-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #17231d; font-size: 22rpx; font-weight: 900; }
	.modal-pick-meta { color: #667085; font-size: 20rpx; }
	.quick-card-list { display: grid; gap: 12rpx; margin-top: 14rpx; }
	.quick-card-item { display: grid; grid-template-columns: 52rpx minmax(0, 1fr) 104rpx; gap: 10rpx; align-items: center; }
	.quick-cover, .quick-store-mark { width: 48rpx; height: 48rpx; border-radius: 8px; overflow: hidden; background: #eef3f0; display: flex; align-items: center; justify-content: center; color: #12372a; font-size: 18rpx; font-weight: 900; }
	.quick-store-mark { background: #12372a; color: #fff; }
	.quick-main { min-width: 0; }
	.quick-add { margin: 0; height: 42rpx; border-radius: 8px; background: #eef5f0; color: #12372a; display: flex; align-items: center; justify-content: center; font-size: 20rpx; font-weight: 900; }
	.quick-add.on { background: #12372a; color: #fff; }
	.quick-add::after { border: 0; }
	.modal-actions { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; padding: 18rpx 36rpx; background: #fff; border-top: 1rpx solid #eef1ee; }
	.modal-action-right { display: flex; align-items: center; gap: 14rpx; }
	.modal-settings, .modal-ghost, .modal-submit { margin: 0; height: 64rpx; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 24rpx; font-weight: 900; box-sizing: border-box; }
	.modal-settings { width: 150rpx; background: #eef3f0; color: #344054; }
	.modal-ghost, .modal-submit { width: 150rpx; }
	.modal-ghost { background: #f8faf9; color: #12372a; border: 1rpx solid #dfe6e2; }
	.modal-submit { width: 170rpx; background: #12372a; color: #fff; border: 0; box-shadow: 0 14rpx 28rpx rgba(18,55,42,.18); }
	.modal-settings::after, .modal-ghost::after, .modal-submit::after { border: 0; }
	@media screen and (max-width: 900px) {
		.topbar-inner, .hero, .layout, .top-actions { display: flex; flex-direction: column; height: auto;
		padding: 10px 18px;
		gap: 10px; }
		.web-nav, .brand-sub { display: none; }
		.cover { width: 100%; }
		.side { order: -1; }
		.hero { padding: 18rpx; }
		.topic-actions { align-items: stretch; }
		.follow-topic-btn { width: 100%; }
		.topic-fab { position: fixed; right: 28rpx; bottom: 110rpx; top: auto; transform: none; width: 86rpx; height: 86rpx; z-index: 30; }
		.modal-mask { padding: 18rpx; align-items: flex-end; }
		.post-modal { max-height: calc(100vh - 36rpx); }
		.modal-body { grid-template-columns: 1fr; }
		.post-grid { flex-direction: column; }
		.post-column { width: 100%; }
		.product-card-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
		.modal-actions { padding: 18rpx; }
		.modal-action-right { flex: 1; }
		.modal-settings { display: none; }
		.modal-ghost, .modal-submit { width: 50%; }
	}
</style>
