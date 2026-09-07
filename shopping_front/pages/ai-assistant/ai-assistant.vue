<template>
	<view class="assistant-page">
		<view class="header">
			<text class="header-back" @click="goBack">‹</text>
			<view class="header-icon">🤖</view>
			<view class="header-text">
				<text class="header-title">平台AI助手</text>
				<text class="header-sub">解答平台使用问题 · 买家卖家都适用</text>
			</view>
		</view>

		<scroll-view class="msg-list" scroll-y :scroll-top="scrollTop" :show-scrollbar="false" @click="closeFloaters">
			<view v-if="screen === 'select'" class="welcome">
				<view class="welcome-icon">👋</view>
				<text class="welcome-title">你好！我是松果集市AI助手</text>
				<text class="welcome-desc">我可以帮你解答以下问题：</text>
				<view class="suggestion-grid">
					<view class="sug-item" @click="ask('怎么买东西？')">
						<text class="sug-icon">🛒</text>
						<text class="sug-text">怎么买东西</text>
					</view>
					<view class="sug-item" @click="ask('怎么卖东西？')">
						<text class="sug-icon">📦</text>
						<text class="sug-text">怎么卖东西</text>
					</view>
					<view class="sug-item" @click="ask('平台担保交易是什么？')">
						<text class="sug-icon">🛡️</text>
						<text class="sug-text">担保交易</text>
					</view>
					<view class="sug-item" @click="ask('AI议价怎么用？')">
						<text class="sug-icon">🤖</text>
						<text class="sug-text">AI议价怎么用</text>
					</view>
					<view class="sug-item" @click="ask('怎么联系客服？')">
						<text class="sug-icon">💬</text>
						<text class="sug-text">联系客服</text>
					</view>
					<view class="sug-item" @click="ask('运费和发货怎么算？')">
						<text class="sug-icon">🚚</text>
						<text class="sug-text">运费发货</text>
					</view>
				</view>
			</view>

			<view v-if="screen === 'chat'">
				<view v-for="(msg, i) in messages" :key="i">
					<view class="msg-row question">
						<view class="msg-bubble q-bubble">{{ msg.q }}</view>
						<view class="q-avatar">👤</view>
					</view>
					<view class="msg-row answer">
						<view class="a-avatar">🤖</view>
						<view class="msg-bubble a-bubble">
							<rich-text v-if="isRichText(msg.a)" :nodes="mdToHtml(msg.a)" class="rich-answer"></rich-text>
							<text v-else>{{ msg.a }}</text>
						</view>
					</view>
				</view>

				<view v-if="loading" class="msg-row answer">
					<view class="a-avatar">🤖</view>
					<view class="msg-bubble a-bubble thinking">
						<text class="dot">.</text><text class="dot">.</text><text class="dot">.</text>
					</view>
				</view>
			</view>
		</scroll-view>

		<view class="composer">
			<input
				v-model="inputText"
				class="input"
				placeholder="输入你想了解的问题..."
				maxlength="200"
				:disabled="loading"
				@confirm="sendQuestion"
			/>
			<button class="send-btn" :disabled="loading || !inputText.trim()" @click="sendQuestion">发送</button>
		</view>
	</view>
</template>

<script>
import { post } from '@/utils/request.js'

export default {
	data() {
		return {
			screen: 'select',
			messages: [],
			inputText: '',
			loading: false,
			scrollTop: 0
		}
	},
	onBackPress() {
		if (this.screen === 'chat') {
			this.resetToSelect()
			return true
		}
		return false
	},
	methods: {
		resetToSelect() {
			this.screen = 'select'
			this.messages = []
			this.loading = false
			this.inputText = ''
			this.scrollTop = 0
		},
		async sendQuestion() {
			const text = this.inputText.trim()
			if (!text || this.loading) return
			this.inputText = ''
			await this.ask(text)
		},
		async ask(question) {
			if (this.loading) return
			this.screen = 'chat'
			this.loading = true
			this.$nextTick(() => this.scrollToBottom())
			try {
				const res = await post('/api/ai/assistant', { question })
				const answer = (res.data && res.data.data) ? res.data.data : '抱歉，我现在无法回答这个问题，请稍后再试～'
				this.messages.push({ q: question, a: answer })
			} catch (e) {
				this.messages.push({ q: question, a: '网络开小差了，请稍后再试～' })
			} finally {
				this.loading = false
				this.$nextTick(() => this.scrollToBottom())
			}
		},
		scrollToBottom() {
			this.scrollTop = this.scrollTop === 999999 ? 999998 : 999999
		},
		isRichText(text) {
			return /\*\*|^[-*]\s|^\d+[.]\s|#|`/.test(text)
		},
		mdToHtml(text) {
			if (!text) return ''
			let html = text
				.replace(/&/g, '&amp;')
				.replace(/</g, '&lt;')
				.replace(/>/g, '&gt;')
				.replace(/```([\s\S]*?)```/g, '<pre style="background:#f3f4f6;padding:12px;border-radius:6px;font-size:13px;overflow-x:auto;margin:8px 0;white-space:pre-wrap">$1</pre>')
				.replace(/`([^`]+)`/g, '<code style="background:#f3f4f6;padding:2px 6px;border-radius:4px;font-size:13px;color:#be123c">$1</code>')
				.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
				.replace(/__(.+?)__/g, '<strong>$1</strong>')
				.replace(/\*(.+?)\*/g, '<em>$1</em>')
				.replace(/_(.+?)_/g, '<em>$1</em>')
				.replace(/^### (.+)$/gm, '<div style="font-size:15px;font-weight:700;margin:10px 0 4px;color:#1f2937">$1</div>')
				.replace(/^## (.+)$/gm, '<div style="font-size:16px;font-weight:700;margin:12px 0 4px;color:#111827">$1</div>')
				.replace(/^# (.+)$/gm, '<div style="font-size:17px;font-weight:800;margin:14px 0 4px;color:#111827">$1</div>')
				.replace(/^\d+[.]\s(.+)$/gm, '<div style="padding-left:18px;margin:3px 0;display:flex;gap:6px"><span style="color:#4b5563">•</span><span>$1</span></div>')
				.replace(/^[-*]\s(.+)$/gm, '<div style="padding-left:18px;margin:3px 0;display:flex;gap:6px"><span style="color:#4b5563">•</span><span>$1</span></div>')
				.replace(/\n{3,}/g, '\n\n')
				.replace(/\n/g, '<br/>')
			return '<div style="line-height:1.75;color:#1f2937;font-size:15px">' + html + '</div>'
		},
		closeFloaters() {},
		goBack() {
			if (this.screen === 'chat') {
				this.resetToSelect()
				return
			}
			uni.navigateBack({
				fail() {
					uni.switchTab({ url: '/pages/home/home' })
				}
			})
		}
	}
}
</script>

<style scoped>
.assistant-page {
	display: flex;
	flex-direction: column;
	height: 100vh;
	background: #f7f9f8;
	overflow: hidden;
}

.header {
	display: flex;
	align-items: center;
	gap: 12px;
	padding: 16px 20px;
	background: #1f5c43;
	color: #fff;
	flex-shrink: 0;
}

.header-back {
		font-size: 28px;
		width: 36px;
		height: 36px;
		display: flex;
		align-items: center;
		justify-content: center;
		cursor: pointer;
		flex-shrink: 0;
		color: #fff;
		opacity: .9;
		border-radius: 50%;
		transition: background .2s;
	}
	.header-back:active {
		background: rgba(255,255,255,.15);
	}
.header-icon {
	font-size: 32px;
	width: 48px;
	height: 48px;
	background: rgba(255,255,255,.18);
	border-radius: 12px;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.header-text {
	flex: 1;
	min-width: 0;
}

.header-title {
	font-size: 18px;
	font-weight: 700;
	display: block;
}

.header-sub {
	font-size: 12px;
	opacity: .8;
	margin-top: 2px;
	display: block;
}

.msg-list {
	flex: 1;
	padding: 16px;
	min-height: 0;
	overflow-y: auto;
}

.welcome {
	text-align: center;
	padding: 20px 0 10px;
}

.welcome-icon {
	font-size: 48px;
	margin-bottom: 8px;
}

.welcome-title {
	font-size: 18px;
	font-weight: 600;
	color: #1f2937;
	display: block;
}

.welcome-desc {
	font-size: 14px;
	color: #6b7280;
	margin: 8px 0 16px;
	display: block;
}

.suggestion-grid {
	display: grid;
	grid-template-columns: 1fr 1fr 1fr;
	gap: 10px;
	padding: 0 10px;
}

.sug-item {
	background: #fff;
	border: 1px solid #e5e7eb;
	border-radius: 10px;
	padding: 12px 8px;
	text-align: center;
	cursor: pointer;
	transition: all .2s;
}

.sug-item:active {
	transform: scale(.96);
	background: #f0fdf4;
	border-color: #86efac;
}

.sug-icon {
	font-size: 24px;
	display: block;
	margin-bottom: 4px;
}

.sug-text {
	font-size: 12px;
	color: #374151;
	display: block;
}

.msg-row {
	margin-bottom: 16px;
	display: flex;
}

.msg-row.question {
	justify-content: flex-end;
	align-items: center;
	gap: 10px;
	padding-right: 40px;
}

.msg-row.answer {
	justify-content: flex-start;
	align-items: flex-start;
	gap: 10px;
}

.a-avatar {
	width: 36px;
	min-width: 36px;
	height: 36px;
	background: #e8f5e9;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 20px;
	flex-shrink: 0;
	overflow: hidden;
	box-sizing: border-box;
}

.q-avatar {
		width: 36px;
		min-width: 36px;
		height: 36px;
		background: #e5e7eb;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 18px;
		flex-shrink: 0;
		overflow: hidden;
		box-sizing: border-box;
	}

.msg-bubble {
	max-width: 78%;
	padding: 12px 16px;
	border-radius: 12px;
	font-size: 15px;
	line-height: 1.55;
	word-break: break-word;
	overflow: hidden;
}

.q-bubble {
	background: #1a5c3e;
	color: #fff;
	border-bottom-right-radius: 4px;
}

.a-bubble {
	background: #fff;
	color: #1f2937;
	border: 1px solid #e5e7eb;
	border-bottom-left-radius: 4px;
}

.a-bubble .rich-answer {
	overflow: hidden;
	width: 100%;
}

.thinking {
	color: #9ca3af;
}

.dot {
	animation: blink 1.4s infinite;
	font-size: 24px;
	line-height: 1;
}

.dot:nth-child(2) { animation-delay: .2s; }
.dot:nth-child(3) { animation-delay: .4s; }

@keyframes blink {
	0%, 20% { opacity: 0; }
	50% { opacity: 1; }
	100% { opacity: 0; }
}

.composer {
	display: flex;
	align-items: center;
	gap: 10px;
	padding: 12px 16px;
	background: #fff;
	border-top: 1px solid #e5e7eb;
	flex-shrink: 0;
}

.input {
	flex: 1;
	height: 44px;
	padding: 0 14px;
	border-radius: 10px;
	background: #f3f4f6;
	font-size: 15px;
	color: #111827;
	border: none;
	outline: none;
	box-sizing: border-box;
}

.send-btn {
	height: 44px;
	padding: 0 22px;
	border-radius: 10px;
	background: #1a5c3e;
	color: #fff;
	font-size: 15px;
	font-weight: 600;
	border: none;
	display: flex;
	align-items: center;
	justify-content: center;
	white-space: nowrap;
}

.send-btn[disabled] {
	background: #cbd5e1;
}

button {
	margin: 0;
	padding: 0;
	border: none;
}

button::after {
	border: none;
}
</style>
