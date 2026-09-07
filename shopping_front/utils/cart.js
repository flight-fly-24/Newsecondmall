import { del, get, post, put } from '@/utils/request.js'

function unwrapList(res) {
	const body = res && res.data
	const list = body && Array.isArray(body.data) ? body.data : (Array.isArray(body) ? body : [])
	return list.map(toFrontItem)
}

function toFrontItem(row = {}) {
	return {
		cartId: row.cartId,
		id: String(row.goodsId || ''),
		title: String(row.title || '商品').trim() || '商品',
		price: normalizePrice(row.price),
		qty: normalizeQty(row.quantity),
		cover: row.cover || '',
		tag: '',
		credit: '',
		shopName: row.shopName || '松果集市卖家',
		scene: row.scene || 'used',
		category: String(row.category || '').trim(),
		checked: row.selected !== false,
		valid: row.valid !== false
	}
}

function normalizePrice(value) {
	const n = Number(value)
	return Number.isFinite(n) ? Number(n.toFixed(2)) : 0
}

function normalizeQty(value) {
	const n = parseInt(value, 10)
	return Number.isFinite(n) && n > 0 ? n : 1
}

function goodsIdOf(payload = {}) {
	const n = Number(payload.id || payload.goodsId)
	return Number.isInteger(n) && n > 0 ? n : 0
}

async function findByGoodsId(id) {
	const list = await getCartItems()
	const key = String(id || '')
	return list.find((item) => item.id === key) || null
}

export async function getCartItems() {
	try {
		return unwrapList(await get('/api/cart'))
	} catch (e) {
		if (e && e.statusCode === 401) return []
		throw e
	}
}

export async function addCartItem(payload = {}) {
	const goodsId = goodsIdOf(payload)
	if (goodsId <= 0) {
		throw new Error('商品信息缺失')
	}
	return unwrapList(await post('/api/cart', {
		goodsId,
		quantity: normalizeQty(payload.qty || payload.quantity || 1)
	}))
}

export async function updateCartItem(id, patch = {}) {
	const current = await findByGoodsId(id)
	if (!current || !current.cartId) return getCartItems()
	if (Object.prototype.hasOwnProperty.call(patch, 'qty') || Object.prototype.hasOwnProperty.call(patch, 'quantity')) {
		const quantity = normalizeQty(patch.qty != null ? patch.qty : patch.quantity)
		return unwrapList(await put('/api/cart/' + encodeURIComponent(current.cartId), { quantity }))
	}
	if (Object.prototype.hasOwnProperty.call(patch, 'checked') || Object.prototype.hasOwnProperty.call(patch, 'selected')) {
		const selected = patch.checked != null ? patch.checked !== false : patch.selected !== false
		return unwrapList(await put('/api/cart/' + encodeURIComponent(current.cartId) + '/select', { selected }))
	}
	return getCartItems()
}

export async function removeCartItem(id) {
	const current = await findByGoodsId(id)
	if (!current || !current.cartId) return getCartItems()
	return unwrapList(await del('/api/cart/' + encodeURIComponent(current.cartId)))
}

export async function clearCheckedCartItems() {
	const list = await getCartItems()
	const selected = list.filter((item) => item.checked && item.cartId)
	for (const item of selected) {
		await del('/api/cart/' + encodeURIComponent(item.cartId))
	}
	return getCartItems()
}

export async function getCartCount() {
	const list = await getCartItems()
	return list.reduce((sum, item) => sum + normalizeQty(item.qty), 0)
}

export function groupCartByShop(items = []) {
	const map = {}
	items.forEach((item) => {
		const key = item.shopName || '松果集市卖家'
		if (!map[key]) {
			map[key] = []
		}
		map[key].push(item)
	})
	return Object.keys(map).map((shopName) => ({ shopName, items: map[shopName] }))
}
