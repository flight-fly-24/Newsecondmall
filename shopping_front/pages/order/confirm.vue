<template>
	<view class="safe-page">
		<view class="content-wrap page">
			<text class="title">确认订单</text>

			<view class="card">
				<text class="label">收货地址</text>
				<text class="value">{{ addressText }}</text>
			</view>

			<view class="card">
				<text class="label">订单商品</text>
				<view v-for="item in selectedItems" :key="item.id" class="item">
					<view class="item-info">
						<view class="cover" :class="{ 'has-image': isImageUrl(item.cover) }">
							<image v-if="isImageUrl(item.cover)" class="cover-img" :src="resolveImageUrl(item.cover)" mode="aspectFill"></image>
							<text v-else>{{ item.category || '商品' }}</text>
						</view>
						<view class="item-main">
							<text class="item-title">{{ item.title }}</text>
							<text class="item-meta">{{ item.scene === 'new' ? '新品' : '二手' }} · x{{ item.qty }}</text>
						</view>
					</view>
					<text class="item-price">¥{{ (item.price * item.qty).toFixed(2) }}</text>
				</view>
			</view>

			<view class="card">
				<view class="line"><text>商品合计</text><text>¥{{ total }}</text></view>
				<view class="line"><text>运费</text><text>¥0.00</text></view>
				<view class="line total"><text>应付</text><text>¥{{ total }}</text></view>
			</view>

			<button class="pay" :disabled="paying" @click="pay">{{ paying ? '提交中...' : '模拟支付并生成订单' }}</button>
		</view>
	</view>
</template>

<script>
	import { getCartItems, clearCheckedCartItems } from '@/utils/cart.js'
	import { getDefaultAddress } from '@/utils/address.js'
	import { isImageUrl, resolveImageUrl } from '@/utils/media.js'
	import { createOrders } from '@/services/shop.js'
	import { pickErrorMessage } from '@/utils/auth.js'

	export default {
		data() {
			return { items: [], address: null, paying: false }
		},
		computed: {
			selectedItems() {
				return this.items.filter((item) => item.checked)
			},
			total() {
				return this.selectedItems.reduce((sum, item) => sum + item.price * item.qty, 0).toFixed(2)
			},
			addressText() {
				return this.address ? `${this.address.name} ${this.address.phone} ${this.address.region} ${this.address.detail}` : '未设置地址，可在个人中心维护'
			}
		},
		async onShow() {
			try {
				this.items = await getCartItems()
			} catch (e) {
				this.items = []
			}
			this.address = getDefaultAddress()
		},
		methods: {
			isImageUrl,
			resolveImageUrl,
			async pay() {
				if (this.paying) return
				if (!this.selectedItems.length) {
					uni.showToast({ title: '请选择要购买的商品', icon: 'none' })
					return
				}
				this.paying = true
				try {
					await createOrders(this.selectedItems.map((item) => ({ goodsId: item.id, quantity: item.qty || 1 })))
					await clearCheckedCartItems()
					uni.navigateTo({ url: '/pages/order/pay-result' })
				} catch (e) {
					if (e && e.statusCode === 401) {
						uni.showToast({ title: '请先登录后下单', icon: 'none' })
						uni.navigateTo({ url: '/pages/auth/login' })
					} else {
						uni.showToast({ title: pickErrorMessage(e) || '下单失败', icon: 'none' })
					}
				} finally {
					this.paying = false
				}
			}
		}
	}
</script>

<style lang="scss" scoped>
	.page { padding: 28rpx; }
	.title { display: block; font-size: 40rpx; font-weight: 900; color: #17231d; margin-bottom: 22rpx; }
	.card { background: #fff; border: 1rpx solid #e4e9e5; border-radius: 24rpx; padding: 26rpx; margin-bottom: 18rpx; }
	.label, .value, .item-title, .item-meta { display: block; }
	.label { font-size: 24rpx; color: #667085; margin-bottom: 10rpx; }
	.value { font-size: 28rpx; color: #17231d; line-height: 1.6; }
	.item, .line { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; padding: 12rpx 0; font-size: 26rpx; color: #17231d; }
	.item-info { display: flex; align-items: center; gap: 14rpx; min-width: 0; flex: 1; }
	.cover { width: 78rpx; height: 78rpx; border-radius: 12rpx; background: #edf3ef; display: flex; align-items: center; justify-content: center; overflow: hidden; flex-shrink: 0; font-size: 22rpx; color: #667085; }
	.cover-img { width: 100%; height: 100%; display: block; }
	.item-main { min-width: 0; }
	.item-title { font-size: 27rpx; font-weight: 800; color: #17231d; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	.item-meta { margin-top: 6rpx; font-size: 23rpx; color: #667085; }
	.item-price, .total { font-weight: 900; color: #d66a2c; }
	.pay { margin-top: 24rpx; width: 100%; height: 88rpx; border-radius: 18rpx; background: #1f5c43; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 30rpx; font-weight: 900; }
	.pay[disabled] { opacity: .68; }
	button::after { border: 0; }
</style>
