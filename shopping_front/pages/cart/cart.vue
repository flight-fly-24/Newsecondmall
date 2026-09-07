<template>
	<view class="safe-page cart-page">
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
					<text class="nav-link on">购物车</text>
					<text class="nav-link" @click="navTo('/pages/message/message')">消息</text>
					<text class="nav-link" @click="navTo('/pages/ai-assistant/ai-assistant')">AI 助手</text>
					<text class="nav-link" @click="navTo('/pages/user/index')">我的</text>
				</view>
			</view>
		</view>
		<view class="content-wrap page">
			<view class="page-head">
				<view>
					<text class="kicker">Cart</text>
					<text class="title">购物车</text>
					<text class="desc">按店铺合并结算，保留信用分、担保交易和二手验货提示。</text>
				</view>
				<view class="head-action" @click="goBrowse">继续逛逛</view>
			</view>

			<view v-if="items.length" class="cart-layout">
				<view class="shop-list">
					<view v-for="group in groups" :key="group.shopName" class="shop-card">
						<view class="shop-head">
							<text class="shop-name">{{ group.shopName }}</text>
							<text class="shop-tip">平台担保 · 同店订单自动合并</text>
						</view>
						<view v-for="item in group.items" :key="item.id" class="cart-item">
							<view class="check" :class="{ on: item.checked }" @click="toggleChecked(item.id)">
								{{ item.checked ? '✓' : '' }}
							</view>
							<view class="cover" :class="{ 'has-image': isImageUrl(resolvedCover(item)) }">
								<image v-if="isImageUrl(resolvedCover(item))" class="cover-img" :src="resolveImageUrl(resolvedCover(item))" mode="aspectFill"></image>
								<text v-else>{{ resolvedCover(item) }}</text>
							</view>
							<view class="item-main">
								<view class="item-top">
									<text class="item-title">{{ item.title }}</text>
									<text class="scene-tag" :class="item.scene">{{ item.scene === 'new' ? '新品' : '二手' }}</text>
								</view>
								<view class="meta">
									<text v-if="item.tag" class="tag">{{ item.tag }}</text>
									<text v-if="item.credit" class="tag orange">信用 {{ item.credit }}</text>
								</view>
								<view class="bottom">
									<text class="price">¥{{ item.price }}</text>
									<view class="qty">
										<text class="op" @click="changeQty(item, -1)">-</text>
										<text class="num">{{ item.qty }}</text>
										<text class="op" @click="changeQty(item, 1)">+</text>
									</view>
								</view>
								<view class="item-actions">
									<text @click.stop="findSimilar(item)">找相似</text>
									<text @click="remove(item.id)">删除</text>
								</view>
							</view>
						</view>
					</view>
				</view>

				<view class="summary">
					<text class="summary-title">结算摘要</text>
					<view class="row">
						<text>已选商品</text>
						<text>{{ selectedCount }} 件</text>
					</view>
					<view class="row">
						<text>商品金额</text>
						<text>¥{{ totalPrice }}</text>
					</view>
					<view class="row">
						<text>平台保障</text>
						<text>担保交易</text>
					</view>
					<view class="coupon">确认订单页会模拟选择最优优惠，并保留二手交易验货提示。</view>
					<view class="total">
						<text>应付</text>
						<text>¥{{ totalPrice }}</text>
					</view>
					<view class="checkout" @click="checkout">去结算</view>
				</view>
			</view>

			<view v-else class="empty">
				<image class="empty-icon logo-empty" src="/static/logo.png" mode="aspectFit"></image>
				<text class="empty-title">购物车还是空的</text>
				<text class="empty-desc">去看看新品严选，或者淘一件有故事的闲置。</text>
				<view class="empty-btn" @click="goBrowse">去逛逛</view>
			</view>
		</view>
	</view>
</template>

<script>
	import { getCartItems, updateCartItem, removeCartItem, groupCartByShop } from '@/utils/cart.js'
	import { isImageUrl, resolveImageUrl } from '@/utils/media.js'
	import { fetchProduct } from '@/services/shop.js'
	import { getToken, pickErrorMessage } from '@/utils/auth.js'

	export default {
		data() {
			return {
				items: [],
				loginHinted: false
			}
		},
		computed: {
			groups() {
				return groupCartByShop(this.items)
			},
			selectedItems() {
				return this.items.filter((item) => item.checked)
			},
			selectedCount() {
				return this.selectedItems.reduce((sum, item) => sum + item.qty, 0)
			},
			totalPrice() {
				const total = this.selectedItems.reduce((sum, item) => sum + item.price * item.qty, 0)
				return total.toFixed(2)
			}
		},
		onShow() {
			this.loadData()
		},
		methods: {
			isImageUrl,
			resolveImageUrl,
			resolvedCover(item) {
				return item.cover
			},
			async loadData() {
				try {
					this.items = await getCartItems()
					if (!getToken() && !this.loginHinted) {
						this.loginHinted = true
						uni.showToast({ title: '登录后查看购物车', icon: 'none' })
					}
				} catch (e) {
					this.items = []
					uni.showToast({ title: pickErrorMessage(e) || '购物车加载失败', icon: 'none' })
				}
			},
			goBrowse() {
				uni.switchTab({ url: '/pages/browse/browse' })
			},
			navTo(url) {
				if (['/pages/home/home', '/pages/browse/browse', '/pages/cart/cart', '/pages/message/message', '/pages/user/index'].includes(url)) {
					uni.switchTab({ url })
					return
				}
				uni.navigateTo({ url })
			},
			async toggleChecked(id) {
				const current = this.items.find((item) => item.id === id)
				if (!current) return
				try {
					this.items = await updateCartItem(id, { checked: !current.checked })
				} catch (e) {
					uni.showToast({ title: pickErrorMessage(e) || '更新失败', icon: 'none' })
				}
			},
			async changeQty(item, delta) {
				const nextQty = item.qty + delta
				if (nextQty < 1) {
					this.remove(item.id)
					return
				}
				try {
					this.items = await updateCartItem(item.id, { qty: nextQty })
				} catch (e) {
					uni.showToast({ title: pickErrorMessage(e) || '更新失败', icon: 'none' })
				}
			},
			async remove(id) {
				try {
					this.items = await removeCartItem(id)
					uni.showToast({ title: '已删除商品', icon: 'none' })
				} catch (e) {
					uni.showToast({ title: pickErrorMessage(e) || '删除失败', icon: 'none' })
				}
			},
			async findSimilar(item) {
				if (!item) return
				let category = String(item.category || '').trim()
				if (!category && item.id) {
					try {
						const body = await fetchProduct(item.id)
						const detail = body && body.code === 0 ? body.data : null
						if (detail) {
							category = String(detail.category || '').trim()
							if (detail.scene) item.scene = detail.scene
						}
					} catch (e) {
						console.warn('读取商品分类失败', e)
					}
				}
				const keyword = category || String(item.title || '').trim()
				if (!keyword) {
					uni.showToast({ title: '暂无法找到相似商品', icon: 'none' })
					return
				}
				const query = [
					'keyword=' + encodeURIComponent(keyword),
					'similar=1',
					item.id ? 'excludeId=' + encodeURIComponent(item.id) : ''
				].filter(Boolean).join('&')
				uni.navigateTo({ url: '/pages/search/searchList?' + query })
			},
			checkout() {
				if (!this.selectedItems.length) {
					uni.showToast({ title: '请先选择商品', icon: 'none' })
					return
				}
				uni.navigateTo({ url: '/pages/order/confirm' })
			}
		}
	}
</script>

<style lang="scss" scoped>
	.page {
		padding: 24px 22px 40px;
	}
	.topbar {
		position: sticky;
		top: 0;
		z-index: 10;
		background: rgba(255,255,255,.88);
		backdrop-filter: blur(22px);
		border-bottom: 1px solid rgba(203, 213, 225, .55);
		box-shadow: 0 10px 40px rgba(60, 64, 67, .06);
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
		width: 40px;
		height: 40px;
		border-radius: 8px;
		display: flex;
		align-items: center;
		justify-content: center;
		overflow: hidden;
		flex-shrink: 0;
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
		display: flex;
		justify-self: center;
		align-items: center;
		gap: 4px;
		padding: 5px;
		box-sizing: border-box;
		height: 50px;
		border-radius: 999px;
		background: rgba(255,255,255,.72);
		border: 1px solid rgba(203, 213, 225, .72);
		box-shadow: 0 14px 38px rgba(60, 64, 67, .08);
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
		box-sizing: border-box;
		line-height: 1;
		text-align: center;
		color: #5f6b85;
		font-size: 12px;
		font-weight: 750;
	}
	.nav-link.on,
	.nav-link:hover {
		background: #fff;
		background: linear-gradient(135deg, #ffffff, #f5f7fa);
		color: #12372a;
		box-shadow: 0 10px 26px rgba(18, 55, 42, .14);
	}
	.page-head,
	.shop-head,
	.item-top,
	.bottom,
	.row,
	.total {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16px;
	}
	.page-head {
		margin-bottom: 18px;
	}
	.kicker,
	.title,
	.desc,
	.summary-title,
	.empty-title,
	.empty-desc,
	.shop-name,
	.shop-tip,
	.item-title {
		display: block;
	}
	.kicker {
		color: #1f5c43;
		font-size: 13px;
		font-weight: 900;
	}
	.title,
	.summary-title,
	.empty-title {
		font-size: 30px;
		font-weight: 900;
		color: #17231d;
	}
	.title {
		margin-top: 5px;
	}
	.desc,
	.shop-tip,
	.empty-desc {
		margin-top: 8px;
		font-size: 14px;
		color: #667085;
	}
	.head-action,
	.checkout,
	.empty-btn {
		border-radius: 8px;
		background: #1f5c43;
		color: #fff;
		font-size: 14px;
		font-weight: 900;
		padding: 12px 18px;
	}
	.cart-layout {
		display: grid;
		grid-template-columns: minmax(0, 1fr) 330px;
		gap: 18px;
		align-items: start;
	}
	.shop-list {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.shop-card,
	.summary,
	.empty {
		background: #fff;
		border: 1px solid #e4e9e5;
		border-radius: 8px;
		box-shadow: 0 18px 55px rgba(17, 38, 28, 0.08);
	}
	.shop-card {
		padding: 20px;
	}
	.shop-name {
		font-size: 18px;
		font-weight: 900;
		color: #17231d;
	}
	.cart-item {
		display: flex;
		gap: 14px;
		padding: 18px 0;
		border-top: 1px solid #eef1ee;
	}
	.check {
		width: 28px;
		height: 28px;
		border-radius: 50%;
		border: 2px solid #cbd5d0;
		margin-top: 42px;
		display: flex;
		align-items: center;
		justify-content: center;
		color: #fff;
		font-size: 14px;
		font-weight: 900;
		flex-shrink: 0;
	}
	.check.on {
		background: #1f5c43;
		border-color: #1f5c43;
	}
	.cover {
		width: 112px;
		height: 112px;
		border-radius: 8px;
		background:
			radial-gradient(circle at 50% 42%, rgba(255,255,255,.9), transparent 22%),
			linear-gradient(135deg, #edf5f0 0%, #e4eee8 100%);
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 0;
		flex-shrink: 0;
		overflow: hidden;
	}
	.cover.has-image {
		background: #eef7f1;
	}
	.cover.has-image::before {
		display: none;
	}
	.cover-img {
		width: 100%;
		height: 100%;
		display: block;
	}
	.cover::before {
		content: "";
		width: 58px;
		height: 44px;
		border-radius: 10px;
		background: linear-gradient(135deg, #ffffff, #dfe8e3);
		box-shadow: 0 14px 28px rgba(31, 92, 67, .14);
	}
	.item-main {
		flex: 1;
		min-width: 0;
	}
	.item-title {
		font-size: 18px;
		font-weight: 900;
		color: #17231d;
		line-height: 1.35;
	}
	.scene-tag,
	.tag {
		font-size: 12px;
		padding: 5px 9px;
		border-radius: 999px;
		flex-shrink: 0;
	}
	.scene-tag.new,
	.tag {
		background: #e8f3ed;
		color: #1f5c43;
	}
	.scene-tag.used,
	.tag.orange {
		background: #fff0e7;
		color: #b95420;
	}
	.meta {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
		margin-top: 10px;
	}
	.bottom {
		margin-top: 14px;
	}
	.price {
		font-size: 22px;
		font-weight: 900;
		color: #d66a2c;
	}
	.qty {
		display: flex;
		align-items: center;
		background: #f4f6f4;
		border-radius: 999px;
		overflow: hidden;
	}
	.op,
	.num {
		width: 38px;
		height: 38px;
		line-height: 38px;
		text-align: center;
		font-size: 16px;
	}
	.item-actions {
		display: flex;
		gap: 16px;
		margin-top: 12px;
		font-size: 13px;
		color: #667085;
	}
	.summary {
		padding: 22px;
		position: sticky;
		top: 18px;
	}
	.summary-title {
		font-size: 22px;
	}
	.row {
		font-size: 14px;
		color: #4b5563;
		padding: 14px 0;
		border-bottom: 1px solid #eef1ee;
	}
	.coupon {
		margin-top: 16px;
		border-radius: 8px;
		background: #fff0e7;
		color: #b95420;
		font-size: 13px;
		line-height: 1.55;
		padding: 14px;
	}
	.total {
		margin-top: 18px;
		font-size: 20px;
		font-weight: 900;
		color: #17231d;
	}
	.total text:last-child {
		color: #d66a2c;
	}
	.checkout {
		margin-top: 18px;
		text-align: center;
	}
	.empty {
		text-align: center;
		padding: 72px 32px;
	}
	.empty-icon {
		width: 68px;
		height: 68px;
		border-radius: 8px;
		margin: 0 auto 18px;
		display: block;
	}
	.empty-btn {
		display: inline-flex;
		margin-top: 24px;
	}
	@media screen and (max-width: 900px) {
		.topbar-inner {
			align-items: flex-start;
			flex-direction: column;
		}
		.web-nav,
		.brand-sub {
			display: none;
		}
		.cart-layout {
			grid-template-columns: 1fr;
		}
		.summary {
			position: static;
		}
		.page-head {
			align-items: flex-start;
			flex-direction: column;
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
	.page-head {
		padding: 14px;
	}
	.page-title {
		font-size: 22px;
	}
	.cart-layout {
		padding: 0 14px 24px;
	}
	.goods-item {
		padding: 10px 12px;
	}
		.brand-sub {
		display: none;
	}
/* #endif */

</style>
