<template>
    <view class="safe-page">
        <view class="content-wrap page">
            <view class="page-head">
                <view>
                    <text class="title">发布商品</text>
                    <text class="desc">支持新品店铺发布，也支持二手闲置的成色、故事和 AI 估价。</text>
                </view>
                <view class="ai-pill" :class="{ disabled: aiLoading }" @click="fillByAi">{{ aiLoading ? 'AI 生成中' : 'AI 帮我生成' }}</view>
            </view>

            <view class="layout">
                <view class="form-card">
                    <view class="section">
                        <text class="section-title">商品类型</text>
                        <view class="segmented">
                            <view class="seg" :class="{ on: form.scene === 'new' }" @click="form.scene = 'new'">新品</view>
                            <view class="seg" :class="{ on: form.scene === 'used' }" @click="form.scene = 'used'">二手闲置</view>
                        </view>
                    </view>

                    <view class="section">
                        <text class="section-title">图片</text>
                        <view class="upload-row">
                            <view v-if="form.image" class="upload" @click="uploadImage">
                                <image :src="form.image" mode="aspectFill" style="width: 100%; height: 100%; border-radius: 20rpx;"></image>
                            </view>
                            <view v-else class="upload add" @click="uploadImage">{{ uploadLoading ? '...' : '+' }}</view>
                        </view>
                        <text class="upload-tip">{{ uploadStatus }}</text>
                    </view>

                    <view class="grid">
                        <view class="field">
                            <text class="label">标题</text>
                            <input v-model="form.goods_name" class="input" placeholder="例如：[北航] 考研数学资料" />
                        </view>
                        <view class="field">
                            <text class="label">关键词</text>
                            <input v-model="form.keyword" class="input" placeholder="品牌 / 型号 / 课程名" />
                        </view>
                        <view class="field">
                            <text class="label">分类</text>
                            <input v-model="form.category" class="input" placeholder="数码影音 / 学习资料" />
                        </view>
                        <view class="field">
                            <text class="label">价格</text>
                            <input v-model="form.price" class="input" type="digit" placeholder="0.00" />
                        </view>
                        <view class="field">
                            <text class="label">成色/状态</text>
                            <input v-model="form.condition" class="input" placeholder="全新 / 9成新 / 有瑕疵" />
                        </view>
                    </view>

                    <view class="field">
                        <text class="label">商品描述</text>
                        <textarea v-model="form.goods_desc" class="textarea" placeholder="详细描述：说明新旧程度、使用情况、转手原因等..." />
                    </view>

                    <view class="field">
                        <text class="label">{{ form.scene === 'used' ? '二手故事' : '新品卖点' }}</text>
                        <textarea v-model="form.story" class="textarea small" placeholder="二手商品可以写它的前世今生，新品可以写核心卖点..." />
                    </view>

                    <view class="grid">
                        <view class="field">
                            <text class="label">所在位置</text>
                            <input v-model="form.address" class="input" placeholder="城市或学校" />
                        </view>
                        <view class="field">
                            <text class="label">最低可接受价</text>
                            <input v-model="form.floor_price" class="input" type="digit" placeholder="用于 AI 议价保底" />
                        </view>
                    </view>

                    <view class="actions">
                        <button class="submit" :disabled="loading" @click="handlePublish">发布商品</button>
                    </view>
                </view>

                <view class="side-card">
                    <text class="side-title">AI 发布助手</text>
                    <view v-if="aiStatus" class="ai-status">{{ aiStatus }}</view>
                    <view v-for="item in aiSuggestions" :key="item.title" class="suggestion">
                        <text class="suggest-title">{{ item.title }}</text>
                        <text class="suggest-desc">{{ item.desc }}</text>
                    </view>
                    <view class="audit-box">
                        <text class="audit-title">审核规则</text>
                        <text class="audit-desc">发布后会模拟检测违禁词、图片合规和价格异常；通过后自动上架。</text>
                    </view>
                </view>
            </view>
        </view>
    </view>
</template>

<script>
    import { publishProduct, requestPublishSuggestion } from '@/services/shop.js'
    import { buildRequestUrl } from '../../config/env.js'
    import { resolveImageUrl } from '@/utils/media.js'
    import { getCachedUser, normalizeRole, pickErrorMessage } from '@/utils/auth.js'

    export default {
        data() {
            return {
                loading: false,
                uploadLoading: false,
                uploadStatus: '请选择一张真实商品图片，上传成功后会自动用于发布。',
                aiLoading: false,
                aiStatus: '',
                form: {
                    goods_name: '',
                    goods_desc: '',
                    price: '',
                    image: '',
                    address: '北京航空航天大学(学院路校区)',
                    scene: 'used',       
                    category: '',        
                    keyword: '',
                    condition: '',       
                    story: '',           
                    floor_price: ''      
                },
                aiSuggestions: [
                    { title: '标题建议', desc: '突出品牌、型号、成色和关键卖点，控制在 20 字左右。' },
                    { title: '估价建议', desc: '参考同类成交价、成色、配件和信用分，给出合理区间。' },
                    { title: '瑕疵说明', desc: '二手商品建议主动说明划痕、维修史和验货方式。' }
                ]
            }
        },

        onShow() {
            this.ensureSeller()
        },

        methods: {
            ensureSeller() {
                const user = getCachedUser()
                if (normalizeRole(user && user.role) === 'seller') return true
                uni.showToast({ title: '请使用卖家账号发布商品', icon: 'none' })
                setTimeout(() => {
                    uni.switchTab({ url: '/pages/user/index' })
                }, 300)
                return false
            },

            uploadImage() {
                if (!this.ensureSeller()) return;
                if (this.uploadLoading) return;
                uni.chooseImage({
                    count: 1,
                    success: (res) => {
                        const tempFilePath = res.tempFilePaths[0];
                        if (!tempFilePath) {
                            return uni.showToast({ title: '未选择图片', icon: 'none' });
                        }
                        uni.showLoading({ title: '上传中...' });
                        this.uploadLoading = true;
                        this.uploadStatus = '图片上传中，请稍候...';
            
                        uni.uploadFile({
                            url: buildRequestUrl('/api/upload/image'),
                            filePath: tempFilePath,
                            name: 'file', 
                            success: (uploadRes) => {
                                let data = {};
                                try {
                                    data = typeof uploadRes.data === 'string' ? JSON.parse(uploadRes.data) : uploadRes.data;
                                } catch (e) {
                                    data = {};
                                }
                                if (uploadRes.statusCode >= 200 && uploadRes.statusCode < 300 && (data.code === 200 || data.code === 0) && data.data) {
                                    this.form.image = resolveImageUrl(data.data);
                                    this.uploadStatus = '图片上传成功，可点击图片重新选择。';
                                    uni.showToast({ title: '上传成功' });
                                    return;
                                }
                                const message = data.message || `上传失败(${uploadRes.statusCode || '未知状态'})`;
                                this.uploadStatus = message;
                                uni.showToast({ title: message, icon: 'none' });
                            },
                            fail: (err) => {
                                const message = err && err.errMsg ? err.errMsg : '网络连接失败';
                                this.uploadStatus = message;
                                uni.showToast({ title: '图片上传失败', icon: 'none' });
                            },
                            complete: () => {
                                this.uploadLoading = false;
                                uni.hideLoading();
                            }
                        });
                    }
                });
            },

            validateForm() {
                const required = [
                    ['goods_name', '标题不能为空'],
                    ['category', '分类不能为空'],
                    ['price', '价格不能为空'],
                    ['goods_desc', '描述不能为空'],
                    ['image', '图片不能为空']
                ];
                const missing = required.find(([key]) => !String(this.form[key] || '').trim());
                if (missing) return missing[1];
                const price = Number(this.form.price);
                if (!Number.isFinite(price) || price <= 0) return '价格必须大于 0';
                if (this.form.scene === 'used') {
                    if (!String(this.form.condition || '').trim()) return '二手商品必须填写成色';
                    if (!String(this.form.story || '').trim()) return '二手商品必须填写故事';
                }
                return '';
            },

            handlePublish() {
                if (!this.ensureSeller()) return;
                const error = this.validateForm();
                if (error) {
                    return uni.showToast({ title: error, icon: 'none' });
                }

                uni.showLoading({ title: '发布中...', mask: true });
                this.loading = true;

                publishProduct({
                    scene: this.form.scene,
                    title: this.form.goods_name,
                    image: this.form.image,
                    category: this.form.category,
                    price: parseFloat(this.form.price),
                    condition: this.form.condition,
                    description: this.form.goods_desc,
                    story: this.form.story,
                    floorPrice: this.form.floor_price ? parseFloat(this.form.floor_price) : null,
                    location: this.form.address
                })
                .then(res => {
                    uni.showToast({ title: '提交审核成功', icon: 'success' });
                    setTimeout(() => {
                        uni.redirectTo({ url: '/pages/user/published' });
                    }, 1500);
                })
                .catch(err => {
                    console.error('发布失败错误日志：', err);
                    uni.showToast({ title: pickErrorMessage(err) || '发布失败，请重试', icon: 'none' });
                })
                .finally(() => {
                    this.loading = false;
                    uni.hideLoading();
                });
            },

            async fillByAi() {
                if (!this.ensureSeller()) return;
                if (this.aiLoading) return;
                this.aiLoading = true;
                this.aiStatus = '正在调用后端 AI 发布建议接口...';
                uni.showLoading({ title: 'AI 生成中...', mask: true });
                try {
                    const body = await requestPublishSuggestion({
                        scene: this.form.scene,
                        category: this.form.category,
                        condition: this.form.condition,
                        keyword: this.form.keyword || this.form.goods_name
                    });
                    if (body && body.code === 0 && body.data) {
                        this.applyAiSuggestion(body.data);
                        this.aiStatus = this.aiStatusText(body.data.source);
                    } else {
                        this.applyAiSuggestion(this.mockAiSuggestion());
                        this.aiStatus = `后端 AI 返回异常，已使用本地兜底建议：${body && body.message ? body.message : '未知错误'}`;
                    }
                } catch (e) {
                    this.applyAiSuggestion(this.mockAiSuggestion());
                    this.aiStatus = '后端 AI 接口暂不可用，已使用本地兜底建议。';
                } finally {
                    this.aiLoading = false;
                    uni.hideLoading();
                }
                uni.showToast({ title: '已生成发布建议', icon: 'success' });
            },

            applyAiSuggestion(data) {
                this.form.goods_name = data.title || this.form.goods_name;
                this.form.price = data.price ? String(data.price) : this.form.price;
                this.form.goods_desc = data.description || this.form.goods_desc;
                this.form.story = data.story || this.form.story;
                if (!this.form.category) this.form.category = '数码影音';
                if (!this.form.condition) this.form.condition = this.form.scene === 'used' ? '9 成新' : '全新';
                if (!this.form.floor_price && this.form.price) {
                    this.form.floor_price = String(Math.max(Math.floor(Number(this.form.price) * 0.9), 1));
                }
            },

            aiStatusText(source) {
                if (source === 'dashscope') return '已调用真实 DashScope / 阿里云百炼 API 并填充发布建议。';
                if (source === 'openai') return '已调用真实 OpenAI API 并填充发布建议。';
                if (source === 'mock_missing_key') return '后端未配置 DASHSCOPE_API_KEY 或 OPENAI_API_KEY，当前使用后端兜底建议。';
                if (source === 'mock_dashscope_failed') return 'DashScope API 调用失败，当前使用后端兜底建议。';
                if (source === 'mock_api_failed') return 'OpenAI API 调用失败，当前使用后端兜底建议。';
                return '已生成发布建议。';
            },

            mockAiSuggestion() {
                const keyword = this.form.keyword || this.form.goods_name || '27 英寸 2K 显示器';
                const used = this.form.scene === 'used';
                return {
                    title: `${used ? '[二手]' : '[新品]'} ${keyword} · ${used ? (this.form.condition || '9 成新') : '现货严选'}`,
                    price: used ? 680 : 699,
                    description: used
                        ? '补充说明：功能正常，外观保持良好，支持当面验货。建议主动补充配件、瑕疵和转手原因，让买家更放心。'
                        : '补充说明：适合学习、办公和日常使用，建议突出规格、质保、发货时效和售后服务。',
                    story: used
                        ? '它陪我完成了一段稳定使用的日常，现在整理出来转给需要的人，希望继续被好好使用。'
                        : '新品卖点可以围绕品质、服务和适用场景展开，让买家快速判断是否适合自己。'
                };
            }
        }
    }
</script>

<style lang="scss" scoped>
	.page {
		padding: 28rpx;
	}
	.page-head,
	.actions {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 18rpx;
	}
	.title {
		display: block;
		font-size: 40rpx;
		font-weight: 900;
		color: #17231d;
	}
	.desc {
		display: block;
		margin-top: 8rpx;
		font-size: 25rpx;
		color: #667085;
	}
	.ai-pill,
	.submit,
	.draft {
		border-radius: 16rpx;
		padding: 18rpx 28rpx;
		font-size: 26rpx;
		font-weight: 900;
	}
	.ai-pill,
	.submit {
		background: #1f5c43;
		color: #fff;
	}
	.ai-pill.disabled {
		opacity: 0.7;
		pointer-events: none;
	}
	.layout {
		display: grid;
		grid-template-columns: minmax(0, 1fr) 330px;
		gap: 24rpx;
		margin-top: 24rpx;
		align-items: start;
	}
	.form-card,
	.side-card {
		background: #fff;
		border: 1rpx solid #e4e9e5;
		border-radius: 24rpx;
		box-shadow: 0 14rpx 36rpx rgba(15, 35, 26, 0.06);
		padding: 28rpx;
	}
	.section + .section,
	.field {
		margin-top: 24rpx;
	}
	.section-title,
	.side-title,
	.label,
	.suggest-title,
	.audit-title {
		display: block;
		font-weight: 900;
		color: #17231d;
	}
	.section-title,
	.side-title {
		font-size: 30rpx;
		margin-bottom: 16rpx;
	}
	.segmented {
		display: flex;
		background: #e8f0eb;
		border-radius: 18rpx;
		padding: 6rpx;
		width: fit-content;
	}
	.seg {
		min-width: 150rpx;
		text-align: center;
		padding: 16rpx 22rpx;
		border-radius: 14rpx;
		font-size: 25rpx;
		color: #667085;
	}
	.seg.on {
		background: #fff;
		color: #1f5c43;
		font-weight: 900;
	}
	.upload-row {
		display: flex;
		flex-wrap: wrap;
		gap: 14rpx;
	}
	.upload {
		width: 132rpx;
		height: 132rpx;
		border-radius: 20rpx;
		background: #edf3ef;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 48rpx;
		color: #1f5c43;
	}
	.upload.add {
		border: 2rpx dashed #cbd5d0;
		background: #fff;
	}
	.upload-tip,
	.ai-status {
		display: block;
		margin-top: 12rpx;
		font-size: 23rpx;
		color: #667085;
		line-height: 1.5;
	}
	.ai-status {
		padding: 14rpx 16rpx;
		border-radius: 14rpx;
		background: #eef7f1;
		color: #1f5c43;
		font-weight: 800;
	}
	.grid {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 20rpx;
	}
	.label {
		font-size: 24rpx;
		margin-bottom: 10rpx;
	}
	.input,
	.textarea {
		background: #f8faf8;
		border-radius: 16rpx;
		padding: 0 20rpx;
		font-size: 28rpx;
		box-sizing: border-box;
		width: 100%;
	}
	.input {
		height: 82rpx;
	}
	.textarea {
		height: 180rpx;
		padding-top: 18rpx;
		line-height: 1.6;
	}
	.textarea.small {
		height: 130rpx;
	}
	.actions {
		margin-top: 30rpx;
		justify-content: flex-end;
	}
	.draft {
		background: #e8f3ed;
		color: #1f5c43;
	}
	.suggestion {
		padding: 18rpx 0;
		border-top: 1rpx solid #eef1ee;
	}
	.suggest-title {
		font-size: 26rpx;
	}
	.suggest-desc,
	.audit-desc {
		display: block;
		margin-top: 8rpx;
		font-size: 24rpx;
		color: #667085;
		line-height: 1.6;
	}
	.audit-box {
		margin-top: 24rpx;
		background: #fff0e7;
		border-radius: 18rpx;
		padding: 20rpx;
	}
	.audit-title {
		font-size: 26rpx;
		color: #b95420;
	}
	@media screen and (max-width: 900px) {
		.layout,
		.grid {
			grid-template-columns: 1fr;
		}
		.page-head {
			align-items: flex-start;
			flex-direction: column;
		}
	}
</style>
