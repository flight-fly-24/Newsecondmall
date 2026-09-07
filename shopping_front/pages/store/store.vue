<template>
	<view class="safe-page">
		<view class="content-wrap page">
			<view class="store-shell">
				<view class="store-top">
					<view class="store-title-block">
						<text class="store-name">{{ store.name || '店铺' }}</text>
						<view class="rating-row">
							<view class="stars" :title="`评分 ${store.score || '4.8'}`">
								<text v-for="item in starItems" :key="item" class="star" :class="{ filled: item <= roundedScore }">★</text>
							</view>
							<text class="score">{{ store.score || '4.8' }}</text>
							<text class="fans">{{ store.fans || '0' }} 关注</text>
							<text class="badge">{{ store.badge || '信用店铺' }}</text>
						</view>
					</view>

					<view class="search-box">
						<text class="search-icon">⌕</text>
						<input v-model="keyword" class="search-input" placeholder="搜索本店商品" confirm-type="search" />
					</view>

					<button class="follow-btn" :class="{ followed: store.followed }" :disabled="following" @click="toggleFollow">
						{{ store.followed ? '已关注' : '关注店铺' }}
					</button>
				</view>

				<view class="intro-card">
					<view class="intro-main">
						<text class="intro-label">店铺介绍</text>
						<text class="intro-title">每一家店铺，都是卖家的经营主页</text>
						<text class="intro-desc">{{ store.desc || '卖家暂未填写店铺介绍。' }}</text>
					</view>
					<view class="service-tags">
						<text v-for="item in services" :key="item">{{ item }}</text>
					</view>
				</view>

				<view class="stats-row">
					<view class="stat-card">
						<text class="stat-value">{{ store.productCount || 0 }}</text>
						<text class="stat-label">在售商品</text>
					</view>
					<view class="stat-card">
						<text class="stat-value">{{ store.newCount || 0 }}</text>
						<text class="stat-label">新品</text>
					</view>
					<view class="stat-card">
						<text class="stat-value">{{ store.usedCount || 0 }}</text>
						<text class="stat-label">二手闲置</text>
					</view>
					<view class="stat-card">
						<text class="stat-value">{{ store.creditScore || 100 }}</text>
						<text class="stat-label">店铺信用</text>
					</view>
				</view>

				<view class="goods-section">
					<view class="section-head">
						<view>
							<text class="section-title">店铺商品</text>
							<text class="section-sub">按类型和分类筛选，所有商品来自数据库</text>
						</view>
						<view class="scene-tabs">
							<button v-for="tab in sceneTabs" :key="tab.key" class="scene-tab" :class="{ on: activeScene === tab.key }" @click="activeScene = tab.key">{{ tab.label }}</button>
						</view>
					</view>

					<scroll-view class="category-line" scroll-x :show-scrollbar="false">
						<view class="category-track">
							<button v-for="item in categories" :key="item" class="category-chip" :class="{ on: activeCategory === item }" @click="activeCategory = item">{{ item }}</button>
						</view>
					</scroll-view>

					<view class="goods-grid">
						<view v-for="item in filteredGoods" :key="item.id" class="goods-card" @click="open(item)">
							<view class="cover">
								<image class="cover-img" :src="resolveImageUrl(item.cover)" mode="aspectFill"></image>
								<text class="scene-tag" :class="item.scene">{{ item.scene === 'new' ? '新品' : '二手' }}</text>
							</view>
							<view class="goods-body">
								<view class="goods-meta">
									<text>{{ item.category || '未分类' }}</text>
									<text>信用 {{ item.credit || store.creditScore || 100 }}</text>
								</view>
								<text class="goods-title">{{ item.title }}</text>
								<text class="goods-desc">{{ item.subtitle || item.description || '卖家暂未填写更多说明' }}</text>
								<view class="price-row">
									<text class="price">¥{{ item.price }}</text>
									<text class="location">{{ item.location || '未知地区' }}</text>
								</view>
							</view>
						</view>
					</view>

					<view v-if="!loading && filteredGoods.length === 0" class="empty">
						<text class="empty-title">没有找到商品</text>
						<text class="empty-sub">可以换个关键词，或切换新品/二手分类看看。</text>
					</view>
				</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { fetchStore, fetchStoreProducts, followStore, unfollowStore } from '@/services/shop.js'
	import { resolveImageUrl } from '@/utils/media.js'

	export default {
		data() {
			return {
				storeId: '',
				store: {},
				goodsList: [],
				keyword: '',
				activeScene: 'all',
				activeCategory: '全部',
				following: false,
				loading: false,
				starItems: [1, 2, 3, 4, 5],
				sceneTabs: [
					{ key: 'all', label: '全部' },
					{ key: 'new', label: '新品' },
					{ key: 'used', label: '二手' }
				]
			}
		},
		computed: {
			roundedScore() {
				const score = Number(this.store.score || 4.8)
				return Math.max(0, Math.min(5, Math.round(score)))
			},
			services() {
				return this.store.service && this.store.service.length ? this.store.service : ['平台担保', '信用卖家', '真实商品']
			},
			categories() {
				return ['全部'].concat(Array.from(new Set(this.goodsList.map(item => item.category).filter(Boolean))))
			},
			filteredGoods() {
				const word = this.keyword.trim().toLowerCase()
				return this.goodsList.filter(item => {
					const sceneOk = this.activeScene === 'all' || item.scene === this.activeScene
					const categoryOk = this.activeCategory === '全部' || item.category === this.activeCategory
					const keywordOk = !word || [item.title, item.subtitle, item.description, item.category].join(' ').toLowerCase().includes(word)
					return sceneOk && categoryOk && keywordOk
				})
			}
		},
		async onLoad(query) {
			this.storeId = query && query.id ? decodeURIComponent(query.id) : ''
			if (!this.storeId && query && query.name) this.storeId = decodeURIComponent(query.name)
			await this.loadStore()
		},
		methods: {
			resolveImageUrl,
			async loadStore() {
				this.loading = true
				try {
					const id = this.storeId || '1'
					const [storeBody, productsBody] = await Promise.all([
						fetchStore(id),
						fetchStoreProducts(id)
					])
					this.store = storeBody.data || {}
					this.goodsList = productsBody.data || []
					this.storeId = this.store.id || id
				} catch (e) {
					console.error('加载店铺失败', e)
					uni.showToast({ title: '店铺加载失败', icon: 'none' })
				} finally {
					this.loading = false
				}
			},
			async toggleFollow() {
				if (!this.storeId || this.following) return
				this.following = true
				try {
					const body = this.store.followed ? await unfollowStore(this.storeId) : await followStore(this.storeId)
					this.store = body.data || this.store
					uni.showToast({ title: this.store.followed ? '已关注店铺' : '已取消关注', icon: 'none' })
				} catch (e) {
					uni.showToast({ title: '请先登录买家账号', icon: 'none' })
				} finally {
					this.following = false
				}
			},
			open(item) {
				uni.navigateTo({ url: `/pages/goods/detail?id=${item.id}` })
			}
		}
	}
</script>

<style lang="scss" scoped>
	.safe-page {
		min-height: 100vh;
		background: linear-gradient(180deg, #f4f8f5 0%, #f6f8fb 240px, #f6f8fb 100%);
	}
	.page {
		padding: 22px;
		box-sizing: border-box;
	}
	.store-shell {
		max-width: 1220px;
		margin: 0 auto;
	}
	.store-top {
		display: grid;
		grid-template-columns: minmax(240px, 1fr) minmax(280px, 460px) 116px;
		align-items: center;
		gap: 18px;
		padding: 22px;
		border-radius: 8px;
		background: rgba(255, 255, 255, .94);
		border: 1px solid #dfe8e3;
		box-shadow: 0 18px 50px rgba(31, 92, 67, .09);
	}
	.store-name,
	.intro-label,
	.intro-title,
	.intro-desc,
	.section-title,
	.section-sub,
	.stat-value,
	.stat-label,
	.goods-title,
	.goods-desc,
	.price,
	.location,
	.empty-title,
	.empty-sub {
		display: block;
	}
	.store-name {
		font-size: 30px;
		font-weight: 950;
		color: #12372a;
		letter-spacing: 0;
	}
	.rating-row {
		display: flex;
		align-items: center;
		gap: 10px;
		flex-wrap: wrap;
		margin-top: 10px;
	}
	.stars {
		display: flex;
		gap: 2px;
	}
	.star {
		color: #d5d9df;
		font-size: 17px;
		line-height: 1;
	}
	.star.filled {
		color: #f5a524;
	}
	.score {
		color: #1f5c43;
		font-weight: 900;
	}
	.fans,
	.badge {
		padding: 5px 9px;
		border-radius: 999px;
		background: #eef5f0;
		color: #1f5c43;
		font-size: 12px;
		font-weight: 800;
	}
	.search-box {
		height: 44px;
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 0 14px;
		border-radius: 999px;
		background: #fff;
		border: 1px solid #dfe8e3;
		box-shadow: inset 0 0 0 1px rgba(255,255,255,.7);
		box-sizing: border-box;
	}
	.search-icon {
		color: #1f5c43;
		font-size: 20px;
	}
	.search-input {
		flex: 1;
		height: 100%;
		color: #12372a;
		font-size: 14px;
	}
	button {
		display: flex;
		align-items: center;
		justify-content: center;
		margin: 0;
		padding: 0;
		border: 0;
		line-height: normal;
		box-sizing: border-box;
	}
	button::after {
		border: none;
	}
	.follow-btn {
		width: 116px;
		height: 42px;
		border-radius: 999px;
		background: #1f5c43;
		color: #fff;
		font-size: 14px;
		font-weight: 950;
		box-shadow: 0 12px 24px rgba(31, 92, 67, .22);
	}
	.follow-btn.followed {
		background: #fff;
		color: #1f5c43;
		border: 1px solid #cfe0d6;
		box-shadow: none;
	}
	.intro-card {
		display: grid;
		grid-template-columns: minmax(0, 1fr) 360px;
		gap: 18px;
		margin-top: 18px;
		padding: 24px;
		border-radius: 8px;
		background: #fff;
		border: 1px solid #dfe8e3;
		box-shadow: 0 14px 42px rgba(31, 92, 67, .06);
	}
	.intro-label {
		color: #1f5c43;
		font-size: 13px;
		font-weight: 900;
	}
	.intro-title {
		margin-top: 8px;
		color: #12372a;
		font-size: 22px;
		font-weight: 950;
	}
	.intro-desc {
		margin-top: 10px;
		color: #5a6b62;
		font-size: 14px;
		line-height: 1.8;
	}
	.service-tags {
		display: flex;
		align-content: center;
		justify-content: flex-end;
		flex-wrap: wrap;
		gap: 10px;
	}
	.service-tags text {
		padding: 8px 12px;
		border-radius: 999px;
		background: #eef6f1;
		color: #1f5c43;
		font-size: 13px;
		font-weight: 850;
	}
	.stats-row {
		display: grid;
		grid-template-columns: repeat(4, minmax(0, 1fr));
		gap: 14px;
		margin-top: 18px;
	}
	.stat-card {
		padding: 18px;
		border-radius: 8px;
		background: #fff;
		border: 1px solid #dfe8e3;
		box-shadow: 0 12px 34px rgba(31, 92, 67, .05);
	}
	.stat-value {
		color: #12372a;
		font-size: 24px;
		font-weight: 950;
	}
	.stat-label {
		margin-top: 6px;
		color: #5a6b62;
		font-size: 13px;
	}
	.goods-section {
		margin-top: 18px;
		padding: 22px;
		border-radius: 8px;
		background: #fff;
		border: 1px solid #dfe8e3;
		box-shadow: 0 16px 46px rgba(31, 92, 67, .06);
	}
	.section-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16px;
	}
	.section-title {
		color: #12372a;
		font-size: 22px;
		font-weight: 950;
	}
	.section-sub {
		margin-top: 5px;
		color: #8a94a6;
		font-size: 13px;
	}
	.scene-tabs {
		display: flex;
		gap: 8px;
	}
	.scene-tab,
	.category-chip {
		height: 34px;
		padding: 0 15px;
		border-radius: 999px;
		background: #eef5f0;
		color: #5a6b62;
		font-size: 13px;
		font-weight: 850;
	}
	.scene-tab.on,
	.category-chip.on {
		background: #1f5c43;
		color: #fff;
	}
	.category-line {
		width: 100%;
		white-space: nowrap;
		margin-top: 16px;
	}
	.category-track {
		display: inline-flex;
		gap: 8px;
		min-width: max-content;
	}
	.goods-grid {
		display: grid;
		grid-template-columns: repeat(4, minmax(0, 1fr));
		gap: 16px;
		margin-top: 18px;
	}
	.goods-card {
		border-radius: 8px;
		background: #fff;
		border: 1px solid #dfe8e3;
		overflow: hidden;
		box-shadow: 0 10px 28px rgba(31, 92, 67, .05);
		transition: transform .2s ease, box-shadow .2s ease;
	}
	.goods-card:hover {
		transform: translateY(-2px);
		box-shadow: 0 16px 36px rgba(31, 92, 67, .08);
	}
	.cover {
		position: relative;
		aspect-ratio: 1 / .78;
		background: #edf3ef;
		overflow: hidden;
	}
	.cover-img {
		width: 100%;
		height: 100%;
		display: block;
	}
	.scene-tag {
		position: absolute;
		top: 10px;
		left: 10px;
		padding: 5px 9px;
		border-radius: 999px;
		color: #fff;
		font-size: 12px;
		font-weight: 900;
	}
	.scene-tag.new {
		background: #1f5c43;
	}
	.scene-tag.used {
		background: #3d7a5c;
	}
	.goods-body {
		padding: 14px;
	}
	.goods-meta {
		display: flex;
		justify-content: space-between;
		gap: 8px;
		color: #7a8b82;
		font-size: 12px;
	}
	.goods-title {
		margin-top: 10px;
		min-height: 42px;
		color: #12372a;
		font-size: 15px;
		font-weight: 950;
		line-height: 1.4;
	}
	.goods-desc {
		margin-top: 7px;
		height: 38px;
		color: #5a6b62;
		font-size: 12px;
		line-height: 1.55;
		overflow: hidden;
	}
	.price-row {
		display: flex;
		align-items: flex-end;
		justify-content: space-between;
		gap: 8px;
		margin-top: 12px;
	}
	.price {
		color: #ef4444;
		font-size: 20px;
		font-weight: 950;
	}
	.location {
		color: #7a8b82;
		font-size: 12px;
	}
	.empty {
		padding: 48px 16px;
		text-align: center;
	}
	.empty-title {
		color: #12372a;
		font-size: 17px;
		font-weight: 950;
	}
	.empty-sub {
		margin-top: 8px;
		color: #7a8b82;
		font-size: 13px;
	}
	@media screen and (max-width: 960px) {
		.store-top,
		.intro-card {
			grid-template-columns: 1fr;
		}
		.service-tags {
			justify-content: flex-start;
		}
		.stats-row,
		.goods-grid {
			grid-template-columns: repeat(2, minmax(0, 1fr));
		}
		.section-head {
			align-items: flex-start;
			flex-direction: column;
		}
	}
</style>
