<template>
    <view class="search-list-page">
        <view class="content-wrap search-main">
        <view class="top-tips">
            <text class="kw-label">{{ resultTitle }}</text>
        </view>

        <view class="tabs-bar" v-if="!similarMode">
            <view class="tab-item" :class="{ active: activeTab === 'goods' }" @click="switchTab('goods')">
                <text>商品</text>
            </view>
            <view class="tab-item" :class="{ active: activeTab === 'user' }" @click="switchTab('user')">
                <text>用户</text>
            </view>
        </view>

        <view class="goods-panel-section" v-if="activeTab === 'goods'">
            <view class="filter-bar">
                <view class="filter-item" :class="{ active: activeScene === 'all' }" @click="activeScene = 'all'">
                    <text>全部</text>
                </view>
                <view class="filter-item" :class="{ active: activeScene === 'new' }" @click="activeScene = 'new'">
                    <text>新品</text>
                </view>
                <view class="filter-item" :class="{ active: activeScene === 'used' }" @click="activeScene = 'used'">
                    <text>闲置</text>
                </view>
            </view>

            <view class="sort-bar">
                <view class="sort-item" :class="{ active: currentSort === 'createTime' }" @click="changeSort('createTime')">
                    <text>时间最新</text>
                </view>
                <view class="sort-item" :class="{ active: currentSort === 'price' }" @click="changeSort('price')">
                    <text>价格</text>
                    <text class="arrow" v-if="currentSort === 'price'">{{ currentOrder === 'ASC' ? '▲' : '▼' }}</text>
                </view>
            </view>

            <view class="list-container">
                <view v-if="displayGoods.length === 0" class="empty-box">
                    <text class="empty-icon">{{ similarMode ? '🛒' : '🤣' }}</text>
                    <text class="empty-text">{{ similarMode ? '暂时没有找到同类商品' : '没有找到相关商品~' }}</text>
                </view>

                <view class="goods-grid" v-else>
                    <view class="goods-card" v-for="item in displayGoods" :key="item.id" @click="goToDetail(item)">
                        <view class="cover" :class="[visualClass(item), { 'has-image': isImageUrl(item.cover) }]">
                            <image v-if="isImageUrl(item.cover)" class="cover-img" :src="resolveImageUrl(item.cover)" mode="aspectFill"></image>
                            <text v-else>{{ item.cover || item.category || '商品' }}</text>
                        </view>
                        <view class="goods-body">
                            <view class="goods-tags">
                                <text class="scene-tag" :class="item.scene">{{ item.scene === 'new' ? '新品' : '二手' }}</text>
                                <text class="light-tag">{{ item.condition || item.category || '商品' }}</text>
                            </view>
                            <text class="goods-title">{{ item.title }}</text>
                            <text v-if="item.subtitle" class="goods-sub">{{ item.subtitle }}</text>
                            <view class="price-row">
                                <text class="price">¥{{ item.price }}</text>
                                <text v-if="item.originPrice" class="origin">¥{{ item.originPrice }}</text>
                            </view>
                            <view class="goods-foot">
                                <text>信用 {{ item.credit || 100 }}</text>
                                <text>{{ item.location || item.shopName || '' }}</text>
                            </view>
                            <view class="goods-actions">
                                <text class="mini-link" @click.stop="favoriteItem(item)">收藏</text>
                                <text class="mini-link" @click.stop="openStore(item)">进店</text>
                            </view>
                        </view>
                    </view>
                </view>
            </view>
        </view>
        <view class="user-panel-section" v-if="activeTab === 'user'">
            <view class="list-container">
                <view v-if="userList.length === 0" class="empty-box">
                    <text class="empty-icon">👥</text>
                    <text class="empty-text">没有找到相关的同学/卖家~</text>
                </view>

                <view class="user-list" v-else>
                    <view class="user-row-card" v-for="user in userList" :key="user.userId" @click="goToUserSpace(user)">
                        <view class="user-avatar">👤</view>
                        <view class="user-meta">
                            <text class="username-text">{{ user.username }}</text>
                            <text class="user-credit-tag">诚信信用分: {{ user.credit || 100 }}</text>
                        </view>
                        <view class="go-space-btn"><text>去主页 →</text></view>
                    </view>
                </view>
            </view>
        </view>
        </view>
    </view>
</template>

<script>
    import { get } from '@/utils/request.js' 
    import { fetchProducts } from '@/services/shop.js'
    import { buildGoodsDetailUrl } from '../../data/catalog.js'
    import { isImageUrl, resolveImageUrl } from '@/utils/media.js'
    import { addBuyerItem } from '@/services/center.js'
    
    export default {
        data() {
            return {
                currentKeyword: '', 
                excludeId: '',
                similarMode: false,
                activeTab: 'goods',   
                goodsList: [],        
                userList: [],          
                activeScene: 'all',     
                currentSort: 'createTime', 
                currentOrder: 'DESC'       
            }
        },
        watch: {
            activeScene() {
                if (this.activeTab === 'goods') {
                    this.fetchGoodsResult();
                }
            }
        },
        computed: {
            resultTitle() {
                if (this.similarMode) return `与“${this.currentKeyword}”同类的商品：`
                return `关于“${this.currentKeyword}”的搜索结果：`
            },
            displayGoods() {
                let list = this.goodsList.slice()
                if (this.excludeId) {
                    list = list.filter(item => String(item.id) !== String(this.excludeId))
                }
                if (this.similarMode && this.currentKeyword) {
                    const sameCategory = list.filter(item => String(item.category || '') === this.currentKeyword)
                    if (sameCategory.length) list = sameCategory
                }
                if (this.activeScene !== 'all') {
                    list = list.filter(item => item.scene === this.activeScene)
                }
                if (this.currentSort === 'price') {
                    return list.sort((a, b) => {
                        const diff = Number(a.price || 0) - Number(b.price || 0);
                        return this.currentOrder === 'ASC' ? diff : -diff;
                    });
                }
                return list;
            }
        },
        onLoad(options) {
            if (options && options.keyword) {
                this.currentKeyword = decodeURIComponent(options.keyword);
            }
            if (options && options.excludeId) {
                this.excludeId = decodeURIComponent(options.excludeId)
            }
            this.similarMode = !!(options && (options.similar === '1' || options.similar === 'true'))
            if (options && (options.scene === 'new' || options.scene === 'used')) {
                this.activeScene = options.scene
            }
            if (this.currentKeyword) {
                this.fetchGoodsResult();
            }
        },
        methods: {
            isImageUrl,
            resolveImageUrl,
            visualClass(item) {
                if (item && item.category === '数码影音') return 'digital'
                if (item && item.category === '图书文创') return 'book'
                return 'life'
            },
            goodsCover(item) {
                const cover = item && item.cover
                if (isImageUrl(cover)) return resolveImageUrl(cover)
                return '/static/goods/viewtop-monitor.jpg'
            },
            switchTab(tabName) {
                this.activeTab = tabName;
                if (tabName === 'goods' && this.goodsList.length === 0) {
                    this.fetchGoodsResult();
                } else if (tabName === 'user' && this.userList.length === 0) {
                    this.fetchUserResult();
                }
            },

            fetchGoodsResult() {
                uni.showLoading({ title: '正在检索商品...' });
                fetchProducts({
                    keyword: this.currentKeyword,
                    scene: this.activeScene === 'all' ? '' : this.activeScene
                }).then(body => {
                    if (body && body.code === 0) {
                        this.goodsList = body.data || [];
                    }
                }).finally(() => { uni.hideLoading(); });
            },

            fetchUserResult() {
                uni.showLoading({ title: '正在寻找用户...' });
                const url = `/api/auth/search-users?keyword=${encodeURIComponent(this.currentKeyword)}`;
                get(url).then(res => {
                    if (res.data && res.data.code === 0) {
                        this.userList = res.data.data || [];
                    }
                }).finally(() => { uni.hideLoading(); });
            },

            changeSort(type) {
                if (this.currentSort === type) {
                    this.currentOrder = this.currentOrder === 'DESC' ? 'ASC' : 'DESC';
                } else {
                    this.currentSort = type;
                    this.currentOrder = 'DESC';
                }
                this.fetchGoodsResult();
            },
            goToDetail(item) {
                uni.navigateTo({ url: buildGoodsDetailUrl(item) });
            },
            async favoriteItem(item) {
                try {
                    await addBuyerItem('favorite', { itemId: item.id, title: item.title, storeName: item.shopName })
                    uni.showToast({ title: '已收藏', icon: 'success' })
                } catch (e) {
                    uni.showToast({ title: '请先登录买家账号', icon: 'none' })
                }
            },
            openStore(item) {
                const query = item.storeId
                    ? '?id=' + encodeURIComponent(item.storeId)
                    : '?name=' + encodeURIComponent(item.shopName || '')
                uni.navigateTo({ url: '/pages/store/store' + query })
            },
            goToUserSpace(user) {
                uni.navigateTo({
                    url: `/pages/user/space?userId=${user.userId}&username=${user.username}`
                });
            }
        }
    }
</script>

<style scoped>
.search-list-page {
    min-height: 100vh;
    background: transparent;
    box-sizing: border-box;
}
.search-main {
    padding: 24px 22px 48px;
}

.top-tips {
    padding: 4px 2px 12px;
    background-color: transparent;
}

.top-tips .kw-label {
    font-size: 26rpx;
    color: #909399;
}

.tabs-bar {
    display: flex;
    background-color: #ffffff;
    border: 1px solid #e4e9e5;
    border-radius: 8px;
    padding: 6px 0;
    margin-bottom: 12px;
}

.tabs-bar .tab-item {
    flex: 1;
    display: flex;
    justify-content: center;
    align-items: center;
    padding: 20rpx 0;
    position: relative;
}

.tabs-bar .tab-item text {
    font-size: 30rpx;
    color: #606266;
    font-weight: 500;
    transition: all 0.2s;
}

.tabs-bar .tab-item.active text {
    color: #1b4332;
    font-weight: bold;
    font-size: 32rpx;
}

.tabs-bar .tab-item.active::after {
    content: '';
    position: absolute;
    bottom: 0;
    width: 60rpx;
    height: 6rpx;
    background-color: #1b4332;
    border-radius: 4rpx;
}

.filter-bar {
    display: flex;
    padding: 12px 16px;
    background-color: #ffffff;
    border: 1px solid #e4e9e5;
    border-radius: 8px 8px 0 0;
    gap: 10px;
}

.filter-bar .filter-item {
    padding: 10rpx 30rpx;
    background-color: #f4f4f5;
    border-radius: 30rpx;
}

.filter-bar .filter-item text {
    font-size: 26rpx;
    color: #606266;
}

.filter-bar .filter-item.active {
    background-color: #1b4332;
}

.filter-bar .filter-item.active text {
    color: #fff;
    font-weight: bold;
}

.sort-bar {
    display: flex;
    padding: 12px 16px 14px;
    background-color: #ffffff;
    border: 1px solid #e4e9e5;
    border-top: 0;
    border-radius: 0 0 8px 8px;
    gap: 24px;
    margin-bottom: 16px;
}

.sort-bar .sort-item {
    display: flex;
    align-items: center;
    font-size: 28rpx;
    color: #909399;
}

.sort-bar .sort-item .arrow {
    font-size: 20rpx;
    margin-left: 6rpx;
}

.sort-bar .sort-item.active {
    color: #111827;
    font-weight: bold;
}

.list-container {
    width: 100%;
}

.empty-box {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding-top: 200rpx;
}

.empty-box .empty-icon {
    font-size: 100rpx;
    margin-bottom: 20rpx;
}

.empty-box .empty-text {
    font-size: 28rpx;
    color: #909399;
}

.goods-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 14px;
    padding: 0;
    align-items: start;
    box-sizing: border-box;
}
.goods-card {
    overflow: hidden;
    align-self: start;
    background: #fff;
    border: 1px solid #e4e9e5;
    border-radius: 8px;
    box-shadow: 0 16px 48px rgba(17, 38, 28, 0.065);
    transition: transform .22s ease, box-shadow .22s ease, border-color .22s ease;
}
.goods-card:hover {
    transform: translateY(-3px);
    box-shadow: 0 24px 70px rgba(17, 38, 28, 0.11);
    border-color: rgba(31, 92, 67, .18);
}
.cover {
    height: 150px;
    background:
        radial-gradient(circle at 50% 42%, rgba(255,255,255,.88), transparent 20%),
        linear-gradient(135deg, #edf5f0 0%, #e4eee8 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 0;
    position: relative;
}
.cover::before {
    content: "";
    width: 76px;
    height: 58px;
    border-radius: 14px;
    background: linear-gradient(135deg, #ffffff, #dfe8e3);
    box-shadow: 0 18px 40px rgba(31, 92, 67, .14);
}
.cover::after {
    content: "";
    position: absolute;
    width: 42px;
    height: 6px;
    border-radius: 999px;
    background: rgba(18, 55, 42, .18);
    bottom: 42px;
}
.cover.digital::before {
    width: 86px;
    height: 54px;
    border-radius: 10px;
    background: linear-gradient(135deg, #dff2ff, #b9d9ea);
    border: 5px solid #3c5260;
}
.cover.book::before {
    width: 66px;
    height: 76px;
    border-radius: 8px 14px 14px 8px;
    background: linear-gradient(90deg, #d9eadf 0 34%, #f7d278 34% 68%, #dbeafe 68%);
}
.cover.life::before {
    width: 64px;
    height: 78px;
    border-radius: 18px 18px 10px 10px;
    background: linear-gradient(135deg, #f6e6ce, #b98f72);
}
.cover.digital {
    background:
        radial-gradient(circle at 50% 42%, rgba(255,255,255,.9), transparent 20%),
        linear-gradient(135deg, #edf5f0 0%, #eaf1ff 100%);
}
.cover.book {
    background:
        radial-gradient(circle at 50% 42%, rgba(255,255,255,.9), transparent 20%),
        linear-gradient(135deg, #fff6ec 0%, #edf3ef 100%);
}
.cover.life {
    background:
        radial-gradient(circle at 50% 42%, rgba(255,255,255,.9), transparent 20%),
        linear-gradient(135deg, #f3f7f4 0%, #f7efe6 100%);
}
.cover.has-image {
    background: #eef7f1;
    overflow: hidden;
}
.cover.has-image::before,
.cover.has-image::after {
    display: none;
}
.cover-img {
    width: 100%;
    height: 100%;
    display: block;
}
.goods-body {
    padding: 16px;
}
.goods-tags {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
}
.scene-tag,
.light-tag {
    font-size: 12px;
    padding: 5px 9px;
    border-radius: 999px;
}
.scene-tag.new {
    background: #f5f7fa;
    color: #12372a;
}
.scene-tag.used {
    background: #fff0e7;
    color: #b95420;
}
.light-tag {
    background: #f4f6f4;
    color: #667085;
}
.goods-title,
.goods-sub {
    display: block;
}
.goods-title {
    margin-top: 12px;
    font-size: 16px;
    font-weight: 850;
    color: #17231d;
    line-height: 1.35;
}
.goods-sub {
    margin-top: 7px;
    font-size: 13px;
    color: #667085;
    line-height: 1.5;
}
.price-row,
.goods-foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
}
.price-row {
    justify-content: flex-start;
    gap: 10px;
    margin-top: 14px;
}
.price {
    font-size: 20px;
    font-weight: 900;
    color: #d66a2c;
}
.origin {
    font-size: 13px;
    color: #9ca3af;
    text-decoration: line-through;
}
.goods-foot {
    margin-top: 12px;
    font-size: 12px;
    color: #667085;
}
.goods-actions {
    display: flex;
    gap: 10px;
    margin-top: 12px;
}
.mini-link {
    padding: 7px 12px;
    border-radius: 999px;
    background: #e8f3ed;
    color: #12372a;
    font-size: 12px;
    font-weight: 900;
}
@media screen and (max-width: 960px) {
    .search-main {
        padding: 16px 16px 36px;
    }
    .goods-grid {
        grid-template-columns: 1fr;
        padding: 0;
    }
    .goods-card {
        width: 100%;
        max-width: 390px;
        margin: 0 auto;
    }
}

.user-list {
    padding: 20rpx;
    display: flex;
    flex-direction: column;
    gap: 20rpx;
}

.user-list .user-row-card {
    display: flex;
    align-items: center;
    background-color: #ffffff;
    padding: 30rpx;
    border-radius: 16rpx;
    box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.03);
}

.user-list .user-row-card .user-avatar {
    width: 90rpx;
    height: 90rpx;
    background-color: #f3f4f6;
    border-radius: 50%;
    display: flex;
    justify-content: center;
    align-items: center;
    font-size: 45rpx;
    margin-right: 24rpx;
    border: 1rpx solid #e5e7eb;
}

.user-list .user-row-card .user-meta {
    display: flex;
    flex-direction: column;
    flex: 1;
    gap: 8rpx;
}

.user-list .user-row-card .user-meta .username-text {
    font-size: 30rpx;
    color: #111827;
    font-weight: bold;
}

.user-list .user-row-card .user-meta .user-credit-tag {
    font-size: 22rpx;
    color: #10b981;
    background-color: #ecfdf5;
    padding: 4rpx 12rpx;
    border-radius: 6rpx;
    align-self: flex-start;
}

.user-list .user-row-card .go-space-btn {
    font-size: 26rpx;
    color: #fff;
    font-weight: 500;
    background-color:#1b4332;
    padding: 12rpx 24rpx;
    border-radius: 30rpx;
}
</style>
