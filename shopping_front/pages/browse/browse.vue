<template>
  <view class="safe-page browse-page">
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
          <text class="nav-link on">发现</text>
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
          <view class="create-shortcut" @click="openCreateModal">创建话题</view>
        </view>
      </view>
    </view>

    <view class="content-wrap page">
      <view class="intro">
        <view>
          <text class="kicker">Community Topics</text>
          <text class="title">发现值得参与的话题合集</text>
          <text class="desc">新品推荐、宿舍好物、防晒避雷、学习资料等话题都来自数据库。点进话题后可以像朋友圈一样发帖、评论和点赞。</text>
        </view>
        <view class="intro-side">
          <text class="side-value">{{ topics.length }}</text>
          <text class="side-desc">个话题合集</text>
        </view>
      </view>

      <view class="browse-layout">
        <view class="main-feed">
          <view class="tabs">
            <view v-for="tab in tabs" :key="tab.key" class="tab" :class="{ on: activeTab === tab.key }" @click="activeTab = tab.key">
              {{ tab.label }}
            </view>
          </view>
          <view v-if="activeTab === 'topics'" class="topic-toolbar">
            <view>
              <text class="toolbar-title">话题广场</text>
              <text class="toolbar-sub">{{ keyword || activeTag ? '正在展示筛选结果' : '全部话题来自数据库' }}</text>
            </view>
            <view class="toolbar-reset" @click="resetTopicFilters">重置筛选</view>
          </view>

          <view v-if="activeTab === 'topics'" class="topic-grid">
            <view v-for="item in filteredTopics" :key="item.id" class="topic-card" @click="openTopic(item)">
              <view class="topic-cover" :class="{ 'has-image': isImageUrl(item.cover) }">
                <image v-if="isImageUrl(item.cover)" class="cover-img" :src="resolveImageUrl(item.cover)" mode="aspectFill"></image>
                <text v-else>{{ item.type }}</text>
              </view>
              <view class="topic-main">
                <view class="article-line">
                  <text class="badge">{{ item.type }}</text>
                  <text class="heat">{{ formatTopicHeat(item) }}</text>
                </view>
                <text class="article-title">{{ item.title }}</text>
                <text class="article-desc">{{ item.desc }}</text>
                <view class="tags">
                  <text v-for="tag in item.tags || []" :key="tag" class="tag">{{ tag }}</text>
                </view>
              </view>
            </view>
            <view v-if="filteredTopics.length === 0" class="empty-topics">没有找到匹配的话题，可以创建一个新的讨论合集。</view>
          </view>

          <view v-else-if="activeTab === 'stores'" class="feed-list">
            <view v-for="store in hotStores" :key="store.id" class="article-card" @click="openStore(store)">
              <view class="store-avatar" :class="{ 'has-image': isImageUrl(store.avatar) }">
                <image v-if="isImageUrl(store.avatar)" class="cover-img" :src="resolveImageUrl(store.avatar)" mode="aspectFill"></image>
                <text v-else>{{ (store.name || '店').slice(0, 1) }}</text>
              </view>
              <view class="article-main">
                <view class="article-line">
                  <text class="badge orange">{{ store.badge }}</text>
                  <text class="heat">评分 {{ store.score }}</text>
                </view>
                <text class="article-title">{{ store.name }}</text>
                <text class="article-desc">{{ store.desc }}</text>
                <text class="store-meta">{{ store.fans }} 关注</text>
              </view>
            </view>
          </view>

          <view v-else class="feed-list">
            <view v-for="item in storyGoods" :key="item.id" class="article-card" @click="openGoods(item)">
              <view class="article-cover" :class="{ 'has-image': isImageUrl(item.cover) }">
                <image v-if="isImageUrl(item.cover)" class="cover-img" :src="resolveImageUrl(item.cover)" mode="aspectFill"></image>
                <text v-else>{{ item.category || '商品' }}</text>
              </view>
              <view class="article-main">
                <view class="article-line">
                  <text class="badge orange">物品故事</text>
                  <text class="heat">{{ item.location }}</text>
                </view>
                <text class="article-title">{{ item.title }}</text>
                <text class="article-desc">{{ item.story || item.description }}</text>
              </view>
            </view>
          </view>
        </view>

        <view class="aside">
          <view class="aside-card">
            <text class="aside-title">标签筛选</text>
            <view class="tag-cloud">
              <text class="cloud-tag" :class="{ on: activeTag === '' }" @click="setTopicTag('')">全部</text>
              <text v-for="tag in hotTags" :key="tag" class="cloud-tag" :class="{ on: activeTag === tag }" @click="setTopicTag(tag)">{{ tag }}</text>
            </view>
          </view>
          <view class="aside-card create-card">
            <text class="aside-title">创建买家话题</text>
            <text class="create-desc">把新品推荐、避坑经验或宿舍好物整理成一个可讨论的话题合集。</text>
            <view class="create-toggle" @click="openCreateModal">开始创建</view>
          </view>
          <view class="aside-card">
            <text class="aside-title">话题玩法</text>
            <view class="mission-line">
              <text class="mission-index">1</text>
              <text class="mission-text">选择一个合集进入讨论页</text>
            </view>
            <view class="mission-line">
              <text class="mission-index">2</text>
              <text class="mission-text">发布自己的购买经验或避坑记录</text>
            </view>
            <view class="mission-line">
              <text class="mission-index">3</text>
              <text class="mission-text">给有用帖子点赞或评论补充</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view v-if="showCreatePanel" class="modal-mask" @click="closeCreateModal">
      <view class="topic-modal" @click.stop>
        <view class="modal-head">
          <view>
            <text class="modal-kicker">Create Topic</text>
            <text class="modal-title">创建买家话题</text>
          </view>
          <view class="modal-close" @click="closeCreateModal">×</view>
        </view>
        <view class="modal-body">
          <view class="modal-main">
            <view class="field">
              <text class="field-label">话题标题</text>
              <input v-model="topicForm.title" class="form-input" maxlength="40" placeholder="例如：宿舍桌搭避坑清单" />
            </view>
            <view class="field">
              <view class="field-line">
                <text class="field-label">话题简介</text>
                <text class="field-count">{{ topicForm.desc.length }}/300</text>
              </view>
              <textarea v-model="topicForm.desc" class="form-textarea" maxlength="300" placeholder="说明这个话题适合讨论什么、能帮大家解决什么问题" />
            </view>
            <view class="modal-grid">
              <view class="field">
                <text class="field-label">话题类型</text>
                <input v-model="topicForm.type" class="form-input" placeholder="例如 新品推荐" />
              </view>
              <view class="field">
                <text class="field-label">封面图片</text>
                <view class="cover-picker" @click="chooseCoverImage">
                  <view class="cover-thumb" :class="{ 'has-image': isImageUrl(topicForm.cover) }">
                    <image v-if="isImageUrl(topicForm.cover)" class="cover-img" :src="resolveImageUrl(topicForm.cover)" mode="aspectFill"></image>
                    <text v-else>+</text>
                  </view>
                  <view class="cover-picker-copy">
                    <text class="cover-picker-title">{{ topicForm.cover ? '更换封面图片' : '选择本地图片' }}</text>
                    <text class="cover-picker-desc">{{ uploadingCover ? '上传中...' : '支持相册或拍照，自动上传为话题封面' }}</text>
                  </view>
                </view>
                <view v-if="topicForm.cover" class="cover-remove" @click="clearCoverImage">移除封面</view>
              </view>
            </view>
            <view class="field">
              <text class="field-label">标签</text>
              <input v-model="topicForm.tagsText" class="form-input" placeholder="用逗号或空格分隔，最多 6 个" />
              <view v-if="topicTags().length" class="preview-tags">
                <text v-for="tag in topicTags()" :key="tag" class="tag">{{ tag }}</text>
              </view>
            </view>
          </view>
          <view class="modal-preview">
            <text class="preview-label">预览</text>
            <view class="preview-card">
              <view class="preview-cover" :class="{ 'has-image': isImageUrl(topicForm.cover) }">
                <image v-if="isImageUrl(topicForm.cover)" class="cover-img" :src="resolveImageUrl(topicForm.cover)" mode="aspectFill"></image>
                <text v-else>{{ topicForm.type || '话题' }}</text>
              </view>
              <view class="preview-content">
                <view class="article-line">
                  <text class="badge">{{ topicForm.type || '买家话题' }}</text>
                  <text class="heat">新话题</text>
                </view>
                <text class="article-title">{{ topicForm.title || '给话题起一个清楚的名字' }}</text>
                <text class="article-desc">{{ topicForm.desc || '写一句简介，让大家知道这个话题适合讨论什么。' }}</text>
              </view>
            </view>
            <text class="preview-tip">发布后会进入话题详情，继续补充讨论内容。</text>
          </view>
        </view>
        <view class="modal-actions">
          <button class="modal-ghost" @click="closeCreateModal">取消</button>
          <button class="modal-submit" :disabled="creating" :loading="creating" @click="submitTopic">{{ creating ? '创建中...' : '发布话题' }}</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
  import { buildGoodsDetailUrl, buildTopicDetailUrl, formatTopicHeat } from '../../data/catalog.js'
  import { createTopic, fetchProducts, fetchStores, fetchTopics, uploadImage } from '@/services/shop.js'
  import { isImageUrl, resolveImageUrl } from '@/utils/media.js'
  import { getCachedUser, normalizeRole, pickErrorMessage } from '@/utils/auth.js'

  export default {
    data() {
      return {
        activeTab: 'topics',
        activeTag: '',
        keyword: '',
        topics: [],
        allTopics: [],
        hotStores: [],
        storyGoodsList: [],
        showCreatePanel: false,
        creating: false,
        uploadingCover: false,
        currentUser: null,
        topicForm: {
          title: '',
          desc: '',
          type: '买家话题',
          tagsText: '',
          cover: ''
        }
      }
    },
    computed: {
      tabs() {
        return [
          { key: 'topics', label: '话题' },
          { key: 'stories', label: '故事' },
          { key: 'stores', label: '店铺' }
        ]
      },
      filteredTopics() {
        return this.topics
      },
      hotTags() {
        const set = new Set()
        this.allTopics.forEach(item => (item.tags || []).forEach(tag => set.add(tag)))
        return Array.from(set)
      },
      isBuyer() {
        return normalizeRole(this.currentUser && this.currentUser.role) === 'buyer'
      },
      storyGoods() {
        return this.storyGoodsList.filter((item) => item.scene === 'used').slice(0, 8)
      }
    },
    onLoad() {
      this.currentUser = getCachedUser()
      this._skipTopicRefresh = true
      this.loadAll()
    },
    onShow() {
      this.currentUser = getCachedUser()
      if (uni.getStorageSync('open_topic_create')) {
        uni.removeStorageSync('open_topic_create')
        this.activeTab = 'topics'
        this.openCreateModal()
      }
      if (this._skipTopicRefresh) {
        this._skipTopicRefresh = false
        return
      }
      this.refreshTopicCounts()
    },
    methods: {
      isImageUrl,
      resolveImageUrl,
      formatTopicHeat,
      async refreshTopicCounts() {
        await Promise.all([this.loadTopics(), this.loadAllTopics()])
      },
      async loadAll() {
        await Promise.all([this.loadTopics(), this.loadAllTopics(), this.loadStores(), this.loadStoryGoods()])
      },
      async loadTopics() {
        try {
          const body = await fetchTopics({ tag: this.activeTag, keyword: this.keyword.trim() })
          this.topics = body && body.code === 0 && Array.isArray(body.data) ? body.data : []
        } catch (e) {
          this.topics = []
        }
      },
      async loadAllTopics() {
        try {
          const body = await fetchTopics()
          this.allTopics = body && body.code === 0 && Array.isArray(body.data) ? body.data : []
        } catch (e) {
          this.allTopics = []
        }
      },
      async loadStores() {
        try {
          const body = await fetchStores()
          this.hotStores = body && body.code === 0 && Array.isArray(body.data) ? body.data : []
        } catch (e) {
          this.hotStores = []
        }
      },
      async loadStoryGoods() {
        try {
          const body = await fetchProducts({ scene: 'used' })
          this.storyGoodsList = body && body.code === 0 && Array.isArray(body.data) ? body.data : []
        } catch (e) {
          this.storyGoodsList = []
        }
      },
      navTo(url) {
        if (['/pages/home/home', '/pages/browse/browse', '/pages/cart/cart', '/pages/message/message', '/pages/user/index'].includes(url)) {
          uni.switchTab({ url })
          return
        }
        uni.navigateTo({ url })
      },
      searchTopics() {
        this.activeTab = 'topics'
        this.loadTopics()
      },
      setTopicTag(tag) {
        this.activeTab = 'topics'
        this.activeTag = tag
        this.loadTopics()
      },
      resetTopicFilters() {
        this.keyword = ''
        this.activeTag = ''
        this.loadTopics()
      },
      openCreateModal() {
        this.activeTab = 'topics'
        this.showCreatePanel = true
      },
      closeCreateModal() {
        if (this.creating) return
        this.showCreatePanel = false
      },
      chooseCoverImage() {
        if (this.uploadingCover) return
        uni.chooseImage({
          count: 1,
          sizeType: ['compressed'],
          sourceType: ['album', 'camera'],
          success: async (res) => {
            const filePath = res.tempFilePaths && res.tempFilePaths[0]
            if (!filePath) return
            await this.uploadCoverImage(filePath)
          }
        })
      },
      async uploadCoverImage(filePath) {
        this.uploadingCover = true
        uni.showLoading({ title: '上传封面中' })
        try {
          const body = await uploadImage(filePath)
          if (!body || body.code !== 0 || !body.data) {
            throw new Error(body && body.message ? body.message : 'upload failed')
          }
          this.topicForm.cover = body.data
          uni.showToast({ title: '封面已上传', icon: 'success' })
        } catch (e) {
          uni.showToast({ title: pickErrorMessage(e) || '封面上传失败', icon: 'none' })
        } finally {
          this.uploadingCover = false
          uni.hideLoading()
        }
      },
      clearCoverImage() {
        if (this.uploadingCover) return
        this.topicForm.cover = ''
      },
      topicTags() {
        return this.topicForm.tagsText
          .split(/[,，\s]+/)
          .map(item => item.trim())
          .filter(Boolean)
          .slice(0, 6)
      },
      async submitTopic() {
        if (!this.isBuyer) {
          uni.showToast({ title: '请使用买家账号创建话题', icon: 'none' })
          return
        }
        if (!this.topicForm.title.trim() || !this.topicForm.desc.trim()) {
          uni.showToast({ title: '请填写标题和简介', icon: 'none' })
          return
        }
        this.creating = true
        try {
          const body = await createTopic({
            title: this.topicForm.title.trim(),
            desc: this.topicForm.desc.trim(),
            type: this.topicForm.type.trim() || '买家话题',
            cover: this.topicForm.cover.trim(),
            tags: this.topicTags()
          })
          const topic = body && body.code === 0 ? body.data : null
          this.topicForm = { title: '', desc: '', type: '买家话题', tagsText: '', cover: '' }
          this.showCreatePanel = false
          await Promise.all([this.loadTopics(), this.loadAllTopics()])
          if (topic) {
            uni.navigateTo({ url: buildTopicDetailUrl(topic) })
          }
        } catch (e) {
          if (e && e.statusCode === 401) {
            uni.navigateTo({ url: '/pages/auth/login' })
          }
          uni.showToast({ title: pickErrorMessage(e) || '创建失败', icon: 'none' })
        } finally {
          this.creating = false
        }
      },
      openGoods(item) {
        uni.navigateTo({ url: buildGoodsDetailUrl(item) })
      },
      openTopic(item) {
        uni.navigateTo({ url: buildTopicDetailUrl(item) })
      },
      openStore(store) {
        uni.navigateTo({ url: '/pages/store/store?name=' + encodeURIComponent(store.name) })
      }
    }
  }
</script>

<style lang="scss" scoped>
  .browse-page { padding-bottom: 48px; background: #f5f6f8; }
  .topbar { position: sticky; top: 0; z-index: 10; background: rgba(255,255,255,.9); backdrop-filter: blur(22px); border-bottom: 1px solid rgba(203,213,225,.55); box-shadow: 0 10px 40px rgba(60,64,67,.06); }
  .topbar-inner { display: grid; grid-template-columns: 300px 350px minmax(0, 1fr); align-items: center; gap: 18px; height: 82px; padding: 0 22px; }
  .brand { display: flex; align-items: center; gap: 12px; }
  .brand-mark { width: 42px; height: 42px; border-radius: 8px; overflow: hidden; flex-shrink: 0; }
  .brand-logo { width: 100%; height: 100%; }
  .brand-name, .brand-sub, .kicker, .title, .desc, .side-value, .side-desc, .article-title, .article-desc, .store-meta, .aside-title, .mission-text, .toolbar-title, .toolbar-sub, .create-desc, .modal-kicker, .modal-title, .field-label, .field-count, .preview-label, .preview-tip { display: block; }
  .brand-name { font-size: 20px; font-weight: 900; color: #202124; }
  .brand-sub { margin-top: 2px; font-size: 12px; color: #667085; }
  .web-nav { justify-self: center; display: flex; align-items: center; gap: 4px; padding: 5px; height: 50px; border-radius: 999px; background: rgba(255,255,255,.72); border: 1px solid rgba(203,213,225,.72); box-sizing: border-box; box-shadow: 0 14px 38px rgba(60,64,67,.08);
		overflow-x: auto;
		white-space: nowrap;
	}
  		.nav-link { width: auto;
		padding: 0 8px;
			flex-shrink: 0; height: 38px; border-radius: 999px; display: flex; align-items: center; justify-content: center; color: #5f6b85; font-size: 12px; font-weight: 800; }
  .nav-link.on, .nav-link:hover { background: linear-gradient(135deg, #ffffff, #f5f7fa); color: #12372a; box-shadow: 0 10px 26px rgba(18, 55, 42, .14); }
  .top-actions { justify-self: end; display: flex; align-items: center; gap: 12px; min-width: 0; }
  .search { width: clamp(220px, 20vw, 300px); height: 44px; border-radius: 12px; background: rgba(255,255,255,.82); border: 1px solid rgba(203,213,225,.78); display: flex; align-items: center; padding: 0 8px 0 14px; min-width: 0; box-shadow: 0 12px 30px rgba(60,64,67,.06); transition: box-shadow .22s ease, border-color .22s ease; }
  .search:focus-within { border-color: rgba(66,133,244,.32); box-shadow: 0 16px 38px rgba(60,64,67,.1); }
  .search-icon { font-size: 22px; color: #667085; margin-right: 8px; }
  .search-input { flex: 1; min-width: 0; font-size: 14px; color: #17231d; }
  .search-action, .create-shortcut { display: flex; align-items: center; justify-content: center; border-radius: 8px; font-size: 14px; font-weight: 900; }
  .search-action { width: 70px; height: 34px; background: #12372a; color: #fff; }
  .create-shortcut { height: 44px; padding: 0 18px; background: #12372a; color: #fff; flex-shrink: 0; box-shadow: 0 14px 34px rgba(18,55,42,.16); }
  .page { padding: 26px 22px 0; }
  .intro, .topic-card, .article-card, .aside-card { background: #fff; border: 1px solid #e4e9e5; border-radius: 8px; box-shadow: 0 14px 38px rgba(17,38,28,.055); }
  .intro { display: grid; grid-template-columns: minmax(0, 1fr) 180px; gap: 20px; align-items: end; padding: 34px 38px; }
  .kicker { color: #1f5c43; font-size: 13px; font-weight: 900; }
  .title { margin-top: 10px; color: #12372a; font-size: 32px; line-height: 1.22; font-weight: 900; }
  .desc { margin-top: 12px; max-width: 760px; color: #667085; font-size: 14px; line-height: 1.8; }
  .intro-side { padding: 18px; border-radius: 8px; background: #f4f8f5; }
  .side-value { color: #12372a; font-size: 34px; font-weight: 900; }
  .side-desc { margin-top: 4px; color: #667085; font-size: 12px; }
  .browse-layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 18px; margin-top: 18px; }
  .tabs { display: flex; gap: 4px; width: fit-content; padding: 4px; border-radius: 999px; background: #e8f0eb; }
  .tab { min-width: 82px; height: 36px; border-radius: 999px; display: flex; align-items: center; justify-content: center; color: #667085; font-size: 13px; }
  .tab.on { background: #fff; color: #12372a; font-weight: 900; box-shadow: 0 8px 22px rgba(17,38,28,.08); }
  .topic-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-top: 14px; padding: 14px 16px; border-radius: 8px; background: #fff; border: 1px solid #e4e9e5; }
  .toolbar-title { color: #12372a; font-size: 18px; font-weight: 900; }
  .toolbar-sub { margin-top: 4px; color: #667085; font-size: 12px; }
  .toolbar-reset { height: 34px; padding: 0 8px; border-radius: 999px; background: #eef3f0; color: #12372a; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 900; }
  .topic-grid, .feed-list { display: grid; gap: 14px; margin-top: 14px; }
  .topic-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .topic-card { overflow: hidden; }
  .topic-cover { height: 190px; background: #edf3ef; display: flex; align-items: center; justify-content: center; color: #12372a; font-size: 28px; font-weight: 900; overflow: hidden; }
  .cover-img { width: 100%; height: 100%; display: block; }
  .topic-main, .article-main { padding: 18px; }
  .article-card { display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 18px; padding: 18px; }
  .article-cover, .store-avatar { height: 128px; border-radius: 8px; background: #edf3ef; display: flex; align-items: center; justify-content: center; overflow: hidden; color: #12372a; font-size: 34px; font-weight: 900; }
  .store-avatar { position: relative; background: linear-gradient(135deg, #eef6f1, #fff4e8); border: 1px solid #dfe8e3; box-shadow: inset 0 0 0 1px rgba(255,255,255,.6); }
  .store-avatar.has-image { background: #edf3ef; border-color: #e4e9e5; }
  .article-line { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
  .badge, .tag, .cloud-tag { display: inline-flex; align-items: center; border-radius: 999px; background: #f5f7fa; color: #12372a; font-size: 12px; font-weight: 800; padding: 5px 10px; }
  .badge.orange { background: #fff0e7; color: #b95420; }
  .heat { color: #667085; font-size: 12px; }
  .article-title { margin-top: 12px; color: #12372a; font-size: 19px; line-height: 1.38; font-weight: 900; }
  .article-desc { margin-top: 8px; color: #667085; font-size: 13px; line-height: 1.7; }
  .tags, .tag-cloud { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
  .tag { background: #f4f6f4; color: #667085; }
  .aside { display: grid; gap: 14px; align-content: start; margin-top: 52px; }
  .aside-card { padding: 18px; }
  .aside-title { color: #12372a; font-size: 17px; font-weight: 900; }
  .create-card { border-color: #d8e8dd; background: #fbfdfb; }
  .create-desc { margin-top: 8px; color: #667085; font-size: 13px; line-height: 1.65; }
  .create-toggle { margin-top: 12px; height: 38px; border-radius: 8px; background: #12372a; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 900; }
  .form-input, .form-textarea { width: 100%; box-sizing: border-box; border-radius: 8px; background: #fff; border: 1px solid #dfe6e2; color: #17231d; font-size: 13px; }
  .form-input { height: 42px; padding: 0 12px; }
  .form-textarea { height: 132px; padding: 10px 12px; line-height: 1.55; }
  .cover-picker { min-height: 68px; padding: 8px; border-radius: 8px; border: 1px dashed #cfded4; background: #f8fbf9; display: grid; grid-template-columns: 58px minmax(0, 1fr); gap: 12px; align-items: center; box-sizing: border-box; }
  .cover-picker:hover { border-color: #9bc5a9; background: #f3f8f5; }
  .cover-thumb { width: 58px; height: 52px; border-radius: 8px; background: #e8f0eb; color: #12372a; display: flex; align-items: center; justify-content: center; overflow: hidden; font-size: 24px; font-weight: 900; }
  .cover-picker-copy { min-width: 0; }
  .cover-picker-title, .cover-picker-desc { display: block; }
  .cover-picker-title { color: #12372a; font-size: 13px; font-weight: 900; }
  .cover-picker-desc { margin-top: 5px; color: #667085; font-size: 12px; line-height: 1.45; }
  .cover-remove { width: fit-content; margin-top: 8px; color: #b95420; font-size: 12px; font-weight: 900; }
  .modal-mask { position: fixed; inset: 0; z-index: 50; padding: 28px; box-sizing: border-box; background: rgba(15, 23, 42, .42); backdrop-filter: blur(10px); display: flex; align-items: center; justify-content: center; }
  .topic-modal { width: min(960px, 100%); max-height: calc(100vh - 56px); overflow: auto; border-radius: 8px; background: #fff; border: 1px solid rgba(226,232,240,.96); box-shadow: 0 28px 90px rgba(15,23,42,.24); }
  .modal-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 22px 24px 18px; border-bottom: 1px solid #eef1ee; }
  .modal-kicker { color: #1f5c43; font-size: 12px; font-weight: 900; letter-spacing: 0; }
  .modal-title { margin-top: 4px; color: #12372a; font-size: 24px; font-weight: 900; }
  .modal-close { width: 36px; height: 36px; border-radius: 50%; background: #f3f6f4; color: #344054; display: flex; align-items: center; justify-content: center; font-size: 22px; font-weight: 700; flex-shrink: 0; }
  .modal-body { display: grid; grid-template-columns: minmax(0, 1fr) 310px; gap: 18px; padding: 22px 24px; }
  .modal-main { display: grid; gap: 14px; min-width: 0; }
  .field { display: grid; gap: 8px; }
  .field-line { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
  .field-label { color: #344054; font-size: 13px; font-weight: 900; }
  .field-count { color: #98a2b3; font-size: 12px; }
  .modal-grid { display: grid; grid-template-columns: minmax(0, .8fr) minmax(0, 1.2fr); gap: 12px; }
  .preview-tags { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 2px; }
  .modal-preview { min-width: 0; }
  .preview-label { color: #344054; font-size: 13px; font-weight: 900; margin-bottom: 8px; }
  .preview-card { overflow: hidden; border-radius: 8px; background: #fff; border: 1px solid #e4e9e5; box-shadow: 0 14px 38px rgba(17,38,28,.06); }
  .preview-cover { height: 160px; background: #edf3ef; color: #12372a; display: flex; align-items: center; justify-content: center; overflow: hidden; font-size: 26px; font-weight: 900; }
  .preview-content { padding: 16px; }
  .preview-tip { margin-top: 12px; color: #667085; font-size: 12px; line-height: 1.6; }
  .modal-actions { display: flex; align-items: center; justify-content: flex-end; gap: 12px; padding: 18px 24px 22px; border-top: 1px solid #eef1ee; }
  .modal-ghost, .modal-submit { margin: 0; width: 112px; height: 40px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 900; box-sizing: border-box; }
  .modal-ghost { background: #fff; color: #12372a; border: 1px solid #dfe6e2; }
  .modal-submit { background: #12372a; color: #fff; border: 0; }
  .modal-ghost::after, .modal-submit::after { border: 0; }
  .empty-topics { grid-column: 1 / -1; padding: 38px; text-align: center; color: #667085; background: #fff; border: 1px dashed #d8e8dd; border-radius: 8px; }
  .cloud-tag.on { background: #12372a; color: #fff; }
  .mission-line { display: grid; grid-template-columns: 26px minmax(0, 1fr); gap: 10px; align-items: start; padding: 12px 0; border-top: 1px solid #eef1ee; }
  .mission-line:first-of-type { margin-top: 8px; }
  .mission-index { width: 24px; height: 24px; border-radius: 8px; background: #f4f8f5; color: #12372a; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 900; }
  .mission-text { color: #475467; font-size: 13px; line-height: 1.65; }
  @media screen and (max-width: 900px) {
    .topbar-inner, .intro, .browse-layout, .article-card, .top-actions { display: flex; flex-direction: column; height: auto;
		padding: 10px 18px;
		gap: 10px; }
    .web-nav, .brand-sub { display: none; }
    .aside { margin-top: 0; }
    .topic-grid { grid-template-columns: 1fr; }
    .title { font-size: 26px; }
    .modal-mask { padding: 14px; align-items: flex-end; }
    .topic-modal { max-height: calc(100vh - 28px); }
    .modal-body, .modal-grid { grid-template-columns: 1fr; }
    .modal-body { padding: 18px; }
    .modal-actions { padding: 14px 18px 18px; }
    .modal-ghost, .modal-submit { width: 50%; }
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
		justify-self: center;
		justify-content: center;
		width: 100%;
	}
	.top-actions {
		width: 100%;
		flex-direction: row;
		flex-wrap: nowrap;
		gap: 8px;
	}
	.search {
		width: calc(100vw - 110px);
		min-width: 0;
		flex: 1;
		padding: 0 4px 0 10px;
		height: 40px;
	}
	.search-icon {
		font-size: 16px;
		margin-right: 4px;
	}
	.search-input {
		font-size: 13px;
		height: 28px;
	}
	.search-action {
		width: 52px;
		height: 32px;
		font-size: 12px;
		flex-shrink: 0;
	}
	.create-shortcut {
		height: 40px;
		padding: 0 14px;
		font-size: 12px;
		white-space: nowrap;
		flex-shrink: 0;
	}
	.intro {
		padding: 16px 14px;
	}
	.title {
		font-size: 24px;
	}
	.page {
		padding: 0 14px 24px;
	}
		.brand-sub {
		display: none;
	}
/* #endif */

</style>
