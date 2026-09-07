<template>
	<view class="safe-page detail-page">
		<scroll-view scroll-y class="scroll">
			<view class="content-wrap detail-shell">
				<view class="crumb-row">
					<text class="crumb" @click="goHome">首页</text>
					<text class="crumb-sep">/</text>
					<text class="crumb">{{ detail.category || '商品详情' }}</text>
					<text class="crumb-sep">/</text>
					<text class="crumb current">{{ sceneLabel(detail.scene) }}</text>
				</view>

				<view class="hero-card">
					<view class="gallery">
						<view class="cover" :class="{ 'has-image': isImageUrl(detail.cover) }">
							<image v-if="isImageUrl(detail.cover)" class="cover-img" :src="resolveImageUrl(detail.cover)" mode="aspectFill"></image>
							<text v-else class="cover-fallback">{{ coverFallback }}</text>
						</view>
						<view class="thumb-row">
							<view class="thumb active" :class="{ 'has-image': isImageUrl(detail.cover) }">
								<image v-if="isImageUrl(detail.cover)" class="cover-img" :src="resolveImageUrl(detail.cover)" mode="aspectFill"></image>
								<text v-else>{{ coverFallback }}</text>
							</view>
							<view class="thumb text-thumb">
								<text>{{ detail.category || '分类' }}</text>
							</view>
							<view class="thumb text-thumb">
								<text>{{ detail.condition || '成色' }}</text>
							</view>
						</view>
					</view>

					<view class="summary">
						<view class="tag-row">
							<text class="scene-tag" :class="detail.scene">{{ sceneLabel(detail.scene) }}</text>
							<text v-if="detail.condition" class="soft-tag">{{ detail.condition }}</text>
							<text v-if="detail.category" class="soft-tag">{{ detail.category }}</text>
							<text class="soft-tag">信用 {{ detail.credit || 100 }}</text>
						</view>

						<view class="price-row">
							<text class="price">{{ priceLabel(detail.price) }}</text>
							<text v-if="Number(detail.originPrice) > Number(detail.price)" class="origin">{{ priceLabel(detail.originPrice) }}</text>
							<text v-if="savingPrice > 0" class="save">省 {{ priceLabel(savingPrice) }}</text>
						</view>

						<text class="title">{{ detail.title || '商品加载中' }}</text>
						<text class="subtitle">{{ detail.subtitle || detail.description || '卖家暂未补充更多说明' }}</text>

						<view class="quick-grid">
							<view class="quick-item">
								<text class="quick-label">交易地点</text>
								<text class="quick-value">{{ detail.location || '待沟通' }}</text>
							</view>
							<view class="quick-item">
								<text class="quick-label">发布状态</text>
								<text class="quick-value">{{ detail.tag || statusLabel }}</text>
							</view>
							<view class="quick-item">
								<text class="quick-label">发布时间</text>
								<text class="quick-value">{{ detail.publishedAt || '近期发布' }}</text>
							</view>
							<view class="quick-item">
								<text class="quick-label">配送方式</text>
								<text class="quick-value">{{ detail.delivery || '与卖家协商' }}</text>
							</view>
						</view>

						<view class="service-list">
							<text v-for="item in services" :key="item" class="service">{{ item }}</text>
						</view>

						<view class="seller-card" @click="openStore">
							<view class="seller-avatar">
								<text>{{ sellerInitial }}</text>
							</view>
							<view class="seller-main">
								<view class="seller-title-row">
									<text class="seller-name">{{ detail.shopName || detail.publisherName || '个人卖家' }}</text>
									<view class="stars">
										<text v-for="n in 5" :key="n" class="star" :class="{ on: n <= sellerStars }">★</text>
									</view>
								</view>
								<text class="seller-desc">{{ sellerDesc }}</text>
							</view>
							<view class="seller-actions">
								<button class="store-btn" @click.stop="openStore">进店</button>
								<button class="follow-btn" @click.stop="followStore">关注</button>
							</view>
						</view>

						<view class="consult-card">
							<view>
								<text class="consult-title">想确认细节？</text>
								<text class="consult-desc">{{ consultTips }}</text>
							</view>
							<button class="consult-btn" @click="goMessage">去问问</button>
						</view>
					</view>
				</view>

				<view class="section-tabs">
					<view
						v-for="tab in tabs"
						:key="tab.key"
						class="tab"
						:class="{ active: activeTab === tab.key }"
						@click="activeTab = tab.key"
					>
						{{ tab.label }}
					</view>
				</view>

				<view class="content-layout">
					<view class="left-column">
						<view v-if="activeTab === 'intro'" class="panel">
							<view class="panel-head">
								<text class="panel-title">{{ detail.scene === 'used' ? '物品故事' : '商品介绍' }}</text>
								<text class="panel-subtitle">来自卖家的真实描述</text>
							</view>
							<text class="story">{{ detail.story || detail.description || '卖家暂未填写详细介绍。' }}</text>
							<view class="highlight-list">
								<text v-for="item in highlights" :key="item" class="highlight">{{ item }}</text>
							</view>
						</view>

						<view v-if="activeTab === 'params'" class="panel">
							<view class="panel-head">
								<text class="panel-title">参数信息</text>
								<text class="panel-subtitle">下单前建议逐项确认</text>
							</view>
							<view class="params">
								<view v-for="row in normalizedParams" :key="row.key" class="param">
									<text class="param-key">{{ row.key }}</text>
									<text class="param-value">{{ row.value }}</text>
								</view>
							</view>
						</view>

						<view v-if="activeTab === 'trade'" class="panel">
							<view class="panel-head">
								<text class="panel-title">交易须知</text>
								<text class="panel-subtitle">平台担保，先沟通再交易</text>
							</view>
							<view class="notice-list">
								<view v-for="item in tradeNotices" :key="item.title" class="notice">
									<text class="notice-title">{{ item.title }}</text>
									<text class="notice-text">{{ item.text }}</text>
								</view>
							</view>
						</view>

						<view v-if="detail.scene === 'used'" class="panel timeline-panel">
							<view class="panel-head">
								<text class="panel-title">流转记录</text>
								<text class="panel-subtitle">帮助判断二手物品状态</text>
							</view>
							<wandering-timeline :nodes="usedTimeline" />
						</view>

						<view class="panel">
							<view class="panel-head">
								<text class="panel-title">买家评价</text>
								<text class="panel-subtitle">{{ reviewSummary }}</text>
							</view>
							<view v-if="detail.reviews && detail.reviews.length" class="review-list">
								<view v-for="review in detail.reviews" :key="review.user + review.text" class="review-card">
									<view class="review-top">
										<text class="review-user">{{ review.user || '买家' }}</text>
										<view class="review-stars">
											<text v-for="n in 5" :key="n" class="review-star" :class="{ on: n <= Number(review.score || 5) }">★</text>
										</view>
									</view>
									<text class="review-text">{{ review.text }}</text>
									<view class="review-tags">
										<text v-for="tag in review.tags || []" :key="tag" class="review-tag">{{ tag }}</text>
									</view>
								</view>
							</view>
							<view v-else class="empty-box">暂无买家评价，完成订单后即可评价商品和卖家信用。</view>
						</view>

						<view class="panel">
							<view class="panel-head">
								<text class="panel-title">同类相似商品</text>
								<text class="panel-subtitle">来自当前数据库商品</text>
							</view>
							<view class="recommend-grid">
								<view v-for="item in sameCategoryRecommends" :key="item.id" class="product-card" @click="openRecommend(item)">
									<view class="product-img" :class="{ 'has-image': isImageUrl(item.cover) }">
										<image v-if="isImageUrl(item.cover)" class="cover-img" :src="resolveImageUrl(item.cover)" mode="aspectFill"></image>
										<text v-else>{{ item.category || '商品' }}</text>
									</view>
									<text class="product-title">{{ item.title }}</text>
									<view class="product-meta">
										<text>{{ sceneLabel(item.scene) }}</text>
										<text>{{ item.condition || item.category }}</text>
									</view>
									<text class="product-price">{{ priceLabel(item.price) }}</text>
								</view>
								<view v-if="sameCategoryRecommends.length === 0" class="empty-box">暂无同类商品</view>
							</view>
						</view>
					</view>

					<view class="right-column">
						<view class="side-card">
							<text class="side-title">购买动作</text>
							<button class="side-primary" @click="goMessage">联系卖家</button>
							<button data-testid="favorite-product" class="side-secondary" @click="favoriteCurrent">收藏商品</button>
							<button class="side-secondary" @click="openStore">进入店铺</button>
						</view>

						<view class="side-card">
							<text class="side-title">同店铺商品</text>
							<view v-for="item in sameStoreRecommends" :key="item.id" class="side-product" @click="openRecommend(item)">
								<view class="side-img" :class="{ 'has-image': isImageUrl(item.cover) }">
									<image v-if="isImageUrl(item.cover)" class="cover-img" :src="resolveImageUrl(item.cover)" mode="aspectFill"></image>
									<text v-else>{{ item.category || '商品' }}</text>
								</view>
								<view class="side-info">
									<text class="side-product-title">{{ item.title }}</text>
									<text class="side-product-price">{{ priceLabel(item.price) }}</text>
								</view>
							</view>
							<view v-if="sameStoreRecommends.length === 0" class="side-empty">暂无更多同店商品</view>
						</view>
					</view>
				</view>
			</view>
		</scroll-view>

		<view class="bottom-bar">
			<view class="bottom-inner">
				<button class="bottom-mini" @click="goHome">首页</button>
				<button class="bottom-mini" @click="openStore">进店</button>
				<button class="bottom-mini" @click="favoriteCurrent">收藏</button>
				<button class="bottom-action cart" @click="addToCart">加入购物车</button>
				<button class="bottom-action ghost" @click="goMessage">去问问</button>
				<button class="bottom-action buy" @click="buyNow">{{ detail.scene === 'used' ? '担保下单' : '立即购买' }}</button>
			</view>
		</view>
	</view>
</template>

<script>
	import { buildGoodsDetailUrl } from '../../data/catalog.js'
	import { addCartItem } from '../../utils/cart.js'
	import { addBuyerItem } from '@/services/center.js'
	import { fetchProduct, fetchProducts } from '@/services/shop.js'
	import WanderingTimeline from '@/components/wandering-timeline/wandering-timeline.vue'
	import { isImageUrl, resolveImageUrl } from '@/utils/media.js'
	import { post } from '@/utils/request.js'
	import { pickErrorMessage } from '@/utils/auth.js'

	const EMPTY_DETAIL = {
		id: '',
		scene: 'used',
		category: '',
		title: '商品加载中',
		subtitle: '',
		price: 0,
		originPrice: 0,
		cover: '',
		tag: '',
		condition: '',
		credit: 100,
		location: '',
		shopName: '',
		storeId: '',
		delivery: '',
		service: [],
		highlights: [],
		story: '',
		description: '',
		params: [],
		reviews: [],
		timeline: [],
		aiTips: [],
		status: '',
		publishedAt: ''
	}

	export default {
		components: { WanderingTimeline },
		data() {
			return {
				detail: Object.assign({}, EMPTY_DETAIL),
				recommendList: [],
				activeTab: 'intro',
				tabs: [
					{ key: 'intro', label: '商品介绍' },
					{ key: 'params', label: '参数信息' },
					{ key: 'trade', label: '交易须知' }
				]
			}
		},
		computed: {
			coverFallback() {
				return (this.detail.category || '商品').slice(0, 2)
			},
			savingPrice() {
				const origin = Number(this.detail.originPrice || 0)
				const price = Number(this.detail.price || 0)
				return origin > price ? origin - price : 0
			},
			statusLabel() {
				if (this.detail.status === 'pending') return '待审核'
				if (this.detail.status === 'rejected') return '未通过'
				return '在售'
			},
			sellerInitial() {
				const name = this.detail.shopName || this.detail.publisherName || '卖家'
				return String(name).slice(0, 1)
			},
			sellerStars() {
				const credit = Number(this.detail.credit || 100)
				if (credit >= 98) return 5
				if (credit >= 92) return 4
				return 3
			},
			sellerDesc() {
				const type = this.detail.scene === 'new' ? '新品店铺' : '二手卖家'
				return `${type} · ${this.detail.location || '位置待沟通'} · 信用 ${this.detail.credit || 100}`
			},
			services() {
				const base = this.detail.service && this.detail.service.length ? this.detail.service : ['平台担保', '站内沟通']
				return Array.from(new Set(base.concat(this.detail.scene === 'used' ? ['支持验货'] : ['正品保障'])))
			},
			highlights() {
				const list = this.detail.highlights && this.detail.highlights.length ? this.detail.highlights : []
				if (list.length) return list
				return [
					this.detail.scene === 'used' ? '卖家已填写成色信息' : '新品现货销售',
					this.detail.category || '分类明确',
					this.detail.delivery || '交易方式可沟通'
				]
			},
			consultTips() {
				const tips = this.detail.aiTips && this.detail.aiTips.length ? this.detail.aiTips : ['询问成色和配件', '确认交易方式', '发送商品卡片']
				return tips.slice(0, 3).join(' · ')
			},
			reviewSummary() {
				const list = this.detail.reviews || []
				if (!list.length) return '来自完成订单后的真实买家评价'
				const avg = list.reduce((sum, item) => sum + Number(item.score || 5), 0) / list.length
				return `${list.length} 条评价 · 商品均分 ${avg.toFixed(1)}`
			},
			normalizedParams() {
				const rows = (this.detail.params || []).map((row) => {
					if (Array.isArray(row)) return { key: row[0], value: row[1] }
					return { key: row.key, value: row.value }
				}).filter((row) => row.key)
				const defaults = [
					{ key: '分类', value: this.detail.category || '未分类' },
					{ key: '类型', value: this.sceneLabel(this.detail.scene) },
					{ key: '成色', value: this.detail.condition || '待沟通' },
					{ key: '地点', value: this.detail.location || '待沟通' }
				]
				const keys = new Set(rows.map((row) => row.key))
				return rows.concat(defaults.filter((row) => !keys.has(row.key)))
			},
			tradeNotices() {
				return [
					{ title: '沟通确认', text: '下单前建议先联系卖家确认库存、成色、配件和交易方式。' },
					{ title: '平台担保', text: '推荐使用平台担保交易，确认收货后再完成款项流转。' },
					{ title: '二手验货', text: '二手商品建议当面验货或要求卖家补充细节照片。' }
				]
			},
			usedTimeline() {
				if (this.detail.scene !== 'used') return []
				const existing = (this.detail.timeline || []).map((node, index) => ({
					date: node.date || node.time || `记录 ${index + 1}`,
					title: node.title || '流转记录',
					text: node.text || node.desc || '',
					icon: node.icon || String(index + 1)
				}))
				if (existing.length) return existing.slice(0, 5)
				return [
					{ date: '入手', title: '开始使用', text: '卖家已说明物品来源和使用经历。', icon: '1' },
					{ date: '整理', title: '清洁整理', text: '发布前完成基础检查，交易前可继续确认细节。', icon: '2' },
					{ date: '当前', title: '等待新主人', text: '支持站内沟通后进行担保交易。', icon: '3' }
				]
			},
			sameStoreRecommends() {
				const shopName = this.detail.shopName
				return this.recommendList
					.filter((item) => String(item.id) !== String(this.detail.id))
					.filter((item) => item.shopName && item.shopName === shopName)
					.slice(0, 4)
			},
			sameCategoryRecommends() {
				const picked = []
				const append = (items) => {
					items.forEach((item) => {
						if (String(item.id) !== String(this.detail.id) && !picked.some((current) => String(current.id) === String(item.id))) {
							picked.push(item)
						}
					})
				}
				append(this.recommendList.filter((item) => item.category === this.detail.category))
				append(this.recommendList.filter((item) => item.scene === this.detail.scene))
				return picked.slice(0, 6)
			}
		},
		onLoad(q) {
			const id = q && q.id ? decodeURIComponent(q.id) : ''
			this.loadDetail(id)
		},
		methods: {
			isImageUrl,
			resolveImageUrl,
			sceneLabel(scene) {
				return scene === 'new' ? '新品' : '二手'
			},
			priceLabel(value) {
				const num = Number(value || 0)
				return `¥${Number.isInteger(num) ? num : num.toFixed(2)}`
			},
			async loadDetail(id) {
				if (!id) {
					uni.showToast({ title: '商品 ID 缺失', icon: 'none' })
					return
				}
				try {
					const body = await fetchProduct(id)
					if (body && body.code === 0 && body.data) {
						this.detail = Object.assign({}, EMPTY_DETAIL, body.data)
						this.recordBrowse()
						this.loadRecommendations()
						return
					}
					uni.showToast({ title: '商品不存在', icon: 'none' })
				} catch (e) {
					uni.showToast({ title: pickErrorMessage(e) || '商品加载失败', icon: 'none' })
				}
			},
			async loadRecommendations() {
				try {
					const body = await fetchProducts()
					if (body && body.code === 0 && Array.isArray(body.data)) {
						this.recommendList = body.data
					}
				} catch (e) {
					this.recommendList = []
				}
			},
			async recordBrowse() {
				try {
					await addBuyerItem('history', { itemId: this.detail.id, title: this.detail.title, storeName: this.detail.shopName })
				} catch (e) {}
			},
			async favoriteCurrent() {
				try {
					await addBuyerItem('favorite', { itemId: this.detail.id, title: this.detail.title, storeName: this.detail.shopName })
					uni.showToast({ title: '已收藏', icon: 'success' })
				} catch (e) {
					uni.showToast({ title: '请先登录买家账号', icon: 'none' })
				}
			},
			async addToCart() {
				try {
					await addCartItem({ id: this.detail.id, qty: 1 })
					uni.showToast({ title: '已加入购物车', icon: 'success' })
					return true
				} catch (e) {
					if (e && e.statusCode === 401) {
						uni.showToast({ title: '请先登录', icon: 'none' })
						uni.navigateTo({ url: '/pages/auth/login' })
					} else {
						uni.showToast({ title: pickErrorMessage(e) || '加入购物车失败', icon: 'none' })
					}
					return false
				}
			},
			async buyNow() {
				const added = await this.addToCart()
				if (added) {
					uni.navigateTo({ url: '/pages/order/confirm' })
				}
			},
			goHome() {
				uni.switchTab({ url: '/pages/home/home' })
			},
			async goMessage() {
				try {
					const goodsId = Number(this.detail.id)
					if (!Number.isInteger(goodsId) || goodsId <= 0) {
						uni.showToast({ title: '商品数据未就绪', icon: 'none' })
						return
					}
					const sellerId = Number(this.detail.publisherId)
					const res = await post('/api/chat/conversations', { goodsId, sellerId })
					if (res.statusCode === 200 && res.data && res.data.data && res.data.data.covId) {
						uni.setStorageSync('pending_message_focus', {
							covId: res.data.data.covId,
							goodsId,
							product: {
								id: this.detail.id,
								title: this.detail.title,
								price: this.detail.price,
								cover: this.detail.cover,
								category: this.detail.category,
								scene: this.detail.scene,
								storeId: this.detail.storeId,
								shopName: this.detail.shopName,
								publisherName: this.detail.publisherName
							},
							time: Date.now()
						})
						uni.switchTab({ url: '/pages/message/message' })
						return
					}
					uni.showToast({ title: '无法创建会话', icon: 'none' })
				} catch (e) {
					if (e && e.statusCode === 401) {
						uni.showToast({ title: '请先登录后联系卖家', icon: 'none' })
						uni.navigateTo({ url: '/pages/auth/login' })
						return
					}
					uni.showToast({ title: pickErrorMessage(e) || '请求失败', icon: 'none' })
				}
			},
			openStore() {
				const query = this.detail.storeId
					? '?id=' + encodeURIComponent(this.detail.storeId)
					: '?name=' + encodeURIComponent(this.detail.shopName || this.detail.publisherName || '')
				uni.navigateTo({ url: '/pages/store/store' + query })
			},
			async followStore() {
				try {
					await addBuyerItem('follow', { itemId: this.detail.id, title: this.detail.shopName, storeName: this.detail.shopName })
					uni.showToast({ title: '已关注店铺', icon: 'success' })
				} catch (e) {
					uni.showToast({ title: '请先登录买家账号', icon: 'none' })
				}
			},
			openRecommend(item) {
				uni.navigateTo({ url: buildGoodsDetailUrl(item) })
			}
		}
	}
</script>

<style lang="scss" scoped>
	.detail-page {
		padding-bottom: 132rpx;
		background: #f5f6f3;
		color: #1f2933;
	}

	.scroll {
		height: calc(100vh - 120rpx);
	}

	.detail-shell {
		padding: 26rpx 28rpx 42rpx;
	}

	.crumb-row {
		display: flex;
		align-items: center;
		gap: 10rpx;
		margin-bottom: 18rpx;
		color: #7b8492;
		font-size: 24rpx;
	}

	.crumb {
		max-width: 360rpx;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.crumb.current {
		color: #315d49;
		font-weight: 800;
	}

	.hero-card,
	.panel,
	.side-card {
		background: #fff;
		border: 1rpx solid #e5e8e2;
		box-shadow: 0 18rpx 48rpx rgba(35, 47, 39, 0.07);
	}

	.hero-card {
		display: grid;
		grid-template-columns: minmax(340px, 0.92fr) minmax(0, 1.08fr);
		gap: 30rpx;
		padding: 28rpx;
		border-radius: 18rpx;
	}

	.gallery {
		min-width: 0;
	}

	.cover {
		height: 600rpx;
		border-radius: 14rpx;
		background: linear-gradient(135deg, #edf4ef, #f9faf7);
		display: flex;
		align-items: center;
		justify-content: center;
		overflow: hidden;
		border: 1rpx solid #e2e8e3;
	}

	.cover.has-image,
	.thumb.has-image,
	.product-img.has-image,
	.side-img.has-image {
		background: #eef2ef;
	}

	.cover-img {
		width: 100%;
		height: 100%;
		display: block;
	}

	.cover-fallback {
		font-size: 72rpx;
		font-weight: 900;
		color: #8a9a90;
	}

	.thumb-row {
		display: flex;
		gap: 14rpx;
		margin-top: 16rpx;
	}

	.thumb {
		width: 112rpx;
		height: 112rpx;
		border-radius: 12rpx;
		overflow: hidden;
		border: 2rpx solid transparent;
		background: #f1f5f2;
		display: flex;
		align-items: center;
		justify-content: center;
		color: #667085;
		font-size: 22rpx;
		text-align: center;
		padding: 8rpx;
		box-sizing: border-box;
	}

	.thumb.active {
		border-color: #315d49;
	}

	.summary {
		min-width: 0;
		display: flex;
		flex-direction: column;
	}

	.tag-row,
	.price-row,
	.service-list,
	.seller-title-row {
		display: flex;
		align-items: center;
		gap: 12rpx;
		flex-wrap: wrap;
	}

	.scene-tag,
	.soft-tag,
	.save,
	.service {
		min-height: 42rpx;
		padding: 0 16rpx;
		border-radius: 999rpx;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		font-size: 22rpx;
		font-weight: 800;
		box-sizing: border-box;
	}

	.scene-tag {
		background: #e8f2ec;
		color: #315d49;
	}

	.scene-tag.used,
	.save {
		background: #fff0e6;
		color: #c35d24;
	}

	.soft-tag {
		background: #f1f3f1;
		color: #5c6670;
	}

	.price-row {
		margin-top: 28rpx;
	}

	.price {
		font-size: 58rpx;
		line-height: 1;
		font-weight: 900;
		color: #d45722;
	}

	.origin {
		font-size: 26rpx;
		color: #9aa3ad;
		text-decoration: line-through;
	}

	.title {
		display: block;
		margin-top: 24rpx;
		font-size: 42rpx;
		line-height: 1.25;
		font-weight: 900;
		color: #111827;
	}

	.subtitle {
		display: block;
		margin-top: 14rpx;
		font-size: 26rpx;
		line-height: 1.55;
		color: #667085;
	}

	.quick-grid {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 14rpx;
		margin-top: 26rpx;
	}

	.quick-item {
		min-height: 86rpx;
		padding: 16rpx;
		border-radius: 12rpx;
		background: #f7f8f5;
		border: 1rpx solid #e7ebe5;
		box-sizing: border-box;
	}

	.quick-label,
	.quick-value,
	.seller-name,
	.seller-desc,
	.consult-title,
	.consult-desc,
	.panel-title,
	.panel-subtitle,
	.story,
	.param-key,
	.param-value,
	.notice-title,
	.notice-text,
	.side-title,
	.product-title,
	.product-price {
		display: block;
	}

	.quick-label {
		font-size: 21rpx;
		color: #8a94a1;
	}

	.quick-value {
		margin-top: 8rpx;
		font-size: 25rpx;
		font-weight: 800;
		color: #26332d;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.service-list {
		margin-top: 22rpx;
	}

	.service {
		background: #edf5ef;
		color: #315d49;
	}

	.seller-card,
	.consult-card {
		margin-top: 24rpx;
		border-radius: 14rpx;
		border: 1rpx solid #e1e7df;
	}

	.seller-card {
		display: grid;
		grid-template-columns: 84rpx minmax(0, 1fr) auto;
		align-items: center;
		gap: 16rpx;
		padding: 18rpx;
		background: #f8faf6;
	}

	.seller-avatar {
		width: 84rpx;
		height: 84rpx;
		border-radius: 50%;
		background: #315d49;
		color: #fff;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 34rpx;
		font-weight: 900;
	}

	.seller-main {
		min-width: 0;
	}

	.seller-name {
		max-width: 420rpx;
		font-size: 29rpx;
		font-weight: 900;
		color: #111827;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.stars {
		display: flex;
		gap: 2rpx;
	}

	.star {
		color: #d2d7d1;
		font-size: 24rpx;
	}

	.star.on {
		color: #f5a524;
	}

	.seller-desc {
		margin-top: 8rpx;
		font-size: 23rpx;
		color: #667085;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.seller-actions {
		display: flex;
		gap: 10rpx;
	}

	button {
		margin: 0;
		padding: 0;
		border: 0;
		background: transparent;
		line-height: 1;
	}

	button::after {
		border: 0;
	}

	.store-btn,
	.follow-btn,
	.consult-btn,
	.side-primary,
	.side-secondary,
	.bottom-mini,
	.bottom-action {
		display: flex;
		align-items: center;
		justify-content: center;
		text-align: center;
		box-sizing: border-box;
		white-space: nowrap;
	}

	.store-btn,
	.follow-btn {
		width: 92rpx;
		height: 54rpx;
		border-radius: 999rpx;
		font-size: 23rpx;
		font-weight: 900;
	}

	.store-btn {
		background: #eef3ef;
		color: #315d49;
	}

	.follow-btn {
		background: #315d49;
		color: #fff;
	}

	.consult-card {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 18rpx;
		padding: 22rpx;
		background: #17231d;
	}

	.consult-title {
		font-size: 29rpx;
		font-weight: 900;
		color: #fff;
	}

	.consult-desc {
		margin-top: 8rpx;
		font-size: 23rpx;
		line-height: 1.45;
		color: rgba(255, 255, 255, 0.72);
	}

	.consult-btn {
		width: 144rpx;
		height: 68rpx;
		border-radius: 999rpx;
		background: #fff;
		color: #315d49;
		font-size: 26rpx;
		font-weight: 900;
		flex-shrink: 0;
	}

	.section-tabs {
		display: flex;
		gap: 10rpx;
		margin-top: 24rpx;
		padding: 10rpx;
		border-radius: 16rpx;
		background: #fff;
		border: 1rpx solid #e5e8e2;
	}

	.tab {
		height: 62rpx;
		padding: 0 28rpx;
		border-radius: 12rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 25rpx;
		font-weight: 800;
		color: #667085;
	}

	.tab.active {
		background: #315d49;
		color: #fff;
	}

	.content-layout {
		display: grid;
		grid-template-columns: minmax(0, 1fr) 340rpx;
		gap: 24rpx;
		margin-top: 24rpx;
		align-items: start;
	}

	.left-column,
	.right-column {
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 24rpx;
	}

	.right-column {
		position: sticky;
		top: 20rpx;
	}

	.panel,
	.side-card {
		border-radius: 16rpx;
		padding: 28rpx;
	}

	.panel-head {
		margin-bottom: 20rpx;
	}

	.panel-title {
		font-size: 32rpx;
		font-weight: 900;
		color: #111827;
	}

	.panel-subtitle {
		margin-top: 6rpx;
		font-size: 23rpx;
		color: #8a94a1;
	}

	.story {
		font-size: 27rpx;
		line-height: 1.8;
		color: #34423a;
	}

	.highlight-list {
		display: flex;
		flex-wrap: wrap;
		gap: 12rpx;
		margin-top: 24rpx;
	}

	.highlight {
		padding: 13rpx 16rpx;
		border-radius: 12rpx;
		background: #f1f6f2;
		color: #315d49;
		font-size: 24rpx;
		font-weight: 800;
	}

	.params {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 14rpx;
	}

	.param {
		min-height: 94rpx;
		padding: 18rpx;
		border-radius: 12rpx;
		background: #f8faf6;
		border: 1rpx solid #e6ece4;
	}

	.param-key {
		font-size: 22rpx;
		color: #8a94a1;
	}

	.param-value {
		margin-top: 8rpx;
		font-size: 26rpx;
		font-weight: 900;
		color: #1f2933;
	}

	.notice-list {
		display: grid;
		gap: 14rpx;
	}

	.notice {
		padding: 20rpx;
		border-radius: 12rpx;
		background: #f8faf6;
		border: 1rpx solid #e6ece4;
	}

	.notice-title {
		font-size: 27rpx;
		font-weight: 900;
		color: #111827;
	}

	.notice-text {
		margin-top: 8rpx;
		font-size: 24rpx;
		line-height: 1.6;
		color: #667085;
	}

	.review-list {
		display: grid;
		gap: 16rpx;
	}

	.review-card {
		padding: 20rpx;
		border-radius: 14rpx;
		background: #f8faf6;
		border: 1rpx solid #e6ece4;
	}

	.review-top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 14rpx;
	}

	.review-user {
		font-size: 26rpx;
		font-weight: 900;
		color: #17231d;
	}

	.review-stars {
		display: flex;
		gap: 2rpx;
	}

	.review-star {
		color: #d7dce0;
		font-size: 24rpx;
	}

	.review-star.on {
		color: #f5a524;
	}

	.review-text {
		display: block;
		margin-top: 12rpx;
		font-size: 25rpx;
		line-height: 1.65;
		color: #4b5563;
	}

	.review-tags {
		display: flex;
		flex-wrap: wrap;
		gap: 8rpx;
		margin-top: 14rpx;
	}

	.review-tag {
		padding: 8rpx 12rpx;
		border-radius: 999rpx;
		background: #eef3ef;
		color: #315d49;
		font-size: 22rpx;
	}

	.timeline-panel {
		background: linear-gradient(180deg, #fffaf3 0%, #fff 70%);
		border-color: #f0dfc7;
		overflow: hidden;
	}

	.recommend-grid {
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 18rpx;
	}

	.product-card {
		min-width: 0;
		padding: 14rpx;
		border-radius: 14rpx;
		background: #f8faf6;
		border: 1rpx solid #e6ece4;
	}

	.product-img {
		aspect-ratio: 1 / 1;
		border-radius: 12rpx;
		background: #eef2ef;
		overflow: hidden;
		display: flex;
		align-items: center;
		justify-content: center;
		color: #7b8492;
		font-size: 24rpx;
	}

	.product-title {
		margin-top: 12rpx;
		min-height: 68rpx;
		font-size: 24rpx;
		line-height: 1.4;
		font-weight: 800;
		color: #202a24;
		overflow: hidden;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
	}

	.product-meta {
		display: flex;
		gap: 8rpx;
		margin-top: 8rpx;
		color: #8a94a1;
		font-size: 21rpx;
		overflow: hidden;
		white-space: nowrap;
	}

	.product-price {
		margin-top: 8rpx;
		color: #d45722;
		font-size: 28rpx;
		font-weight: 900;
	}

	.empty-box,
	.side-empty {
		padding: 30rpx 0;
		color: #98a2b3;
		font-size: 24rpx;
		text-align: center;
	}

	.side-card {
		padding: 22rpx;
	}

	.side-title {
		margin-bottom: 16rpx;
		font-size: 28rpx;
		font-weight: 900;
		color: #111827;
	}

	.side-primary,
	.side-secondary {
		width: 100%;
		height: 70rpx;
		border-radius: 12rpx;
		font-size: 25rpx;
		font-weight: 900;
		margin-top: 12rpx;
	}

	.side-primary {
		background: #315d49;
		color: #fff;
	}

	.side-secondary {
		background: #f1f5f2;
		color: #315d49;
	}

	.side-product {
		display: flex;
		gap: 12rpx;
		padding: 14rpx 0;
		border-top: 1rpx solid #edf0eb;
	}

	.side-img {
		width: 88rpx;
		height: 88rpx;
		border-radius: 10rpx;
		background: #eef2ef;
		overflow: hidden;
		display: flex;
		align-items: center;
		justify-content: center;
		color: #7b8492;
		font-size: 20rpx;
		flex-shrink: 0;
	}

	.side-info {
		min-width: 0;
	}

	.side-product-title,
	.side-product-price {
		display: block;
	}

	.side-product-title {
		font-size: 23rpx;
		line-height: 1.35;
		font-weight: 800;
		color: #1f2933;
		overflow: hidden;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
	}

	.side-product-price {
		margin-top: 8rpx;
		font-size: 25rpx;
		font-weight: 900;
		color: #d45722;
	}

	.bottom-bar {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		z-index: 30;
		padding: 16rpx 28rpx calc(16rpx + env(safe-area-inset-bottom));
		background: rgba(245, 246, 243, 0.9);
		backdrop-filter: blur(18rpx);
		box-shadow: 0 -12rpx 34rpx rgba(35, 47, 39, 0.12);
	}

	.bottom-inner {
		width: min(100%, 1680rpx);
		margin: 0 auto;
		display: grid;
		grid-template-columns: 0.62fr 0.62fr 0.62fr 1fr 0.92fr 1.05fr;
		gap: 12rpx;
		padding: 10rpx;
		border-radius: 18rpx;
		border: 1rpx solid #e1e6de;
		background: #fff;
	}

	.bottom-mini,
	.bottom-action {
		height: 76rpx;
		border-radius: 13rpx;
		font-size: 25rpx;
		font-weight: 900;
	}

	.bottom-mini {
		background: #f1f5f2;
		color: #315d49;
	}

	.bottom-action.ghost {
		background: #edf4ef;
		color: #315d49;
	}

	.bottom-action.cart {
		background: #fff0e6;
		color: #c35d24;
	}

	.bottom-action.buy {
		background: #315d49;
		color: #fff;
	}

	@media screen and (max-width: 900px) {
		.detail-shell {
			padding: 18rpx 18rpx 36rpx;
		}

		.hero-card,
		.content-layout,
		.params,
		.recommend-grid {
			grid-template-columns: 1fr;
		}

		.hero-card {
			padding: 18rpx;
			gap: 20rpx;
		}

		.cover {
			height: 460rpx;
		}

		.quick-grid {
			grid-template-columns: 1fr;
		}

		.seller-card {
			grid-template-columns: 76rpx minmax(0, 1fr);
		}

		.seller-avatar {
			width: 76rpx;
			height: 76rpx;
		}

		.seller-actions {
			grid-column: 1 / -1;
		}

		.store-btn,
		.follow-btn {
			flex: 1;
			width: auto;
		}

		.right-column {
			display: none;
		}

		.section-tabs {
			overflow-x: auto;
		}

		.tab {
			flex-shrink: 0;
		}

		.bottom-bar {
			padding-left: 12rpx;
			padding-right: 12rpx;
		}

		.bottom-inner {
			grid-template-columns: repeat(3, 1fr);
		}

		.bottom-action {
			grid-column: span 1;
		}
	}
</style>
