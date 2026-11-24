import { apiUrl } from './catalog.ts'

export class WishlistApiError extends Error {
  readonly status: number
  readonly serverMessage: string | null

  constructor(message: string, status: number, serverMessage: string | null = null) {
    super(message)
    this.name = 'WishlistApiError'
    this.status = status
    this.serverMessage = serverMessage
  }
}

export type WishlistItemResponse = {
  id: number
  productId: number
  name: string
  slug: string
  price: number
  salePrice: number | null
  effectivePrice: number
  primaryImageUrl: string | null
  available: boolean
  inStock: boolean
  createdAt: string
}

export type WishlistResponse = {
  id: number | null
  items: WishlistItemResponse[]
  itemCount: number
}

export function wishlistErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof WishlistApiError)) {
    return fallback
  }
  if (error.status === 0) {
    return 'Không kết nối được máy chủ.'
  }
  if (error.status === 401) {
    return 'Phiên đăng nhập đã hết hiệu lực.'
  }
  if (error.status === 403) {
    return 'Bạn không có quyền sử dụng danh sách yêu thích.'
  }
  if (error.status === 404) {
    return 'Không tìm thấy sản phẩm.'
  }
  if (error.status === 400) {
    if (error.serverMessage === 'This product is not available') {
      return 'Sản phẩm hiện không khả dụng.'
    }
    return error.serverMessage ? `Dữ liệu không hợp lệ. ${error.serverMessage}` : 'Dữ liệu không hợp lệ.'
  }
  if (error.status >= 500) {
    return 'Máy chủ gặp sự cố. Thử lại sau.'
  }
  return fallback
}

export async function getWishlist(token: string, signal?: AbortSignal): Promise<WishlistResponse> {
  const payload = await requestJson('/api/wishlist', { method: 'GET', token, signal })
  return readWishlist(payload)
}

export async function addWishlistItem(token: string, productId: number, signal?: AbortSignal): Promise<WishlistResponse> {
  const payload = await requestJson('/api/wishlist/items', {
    method: 'POST',
    token,
    body: JSON.stringify({ productId }),
    signal,
  })
  return readWishlist(payload)
}

export async function removeWishlistItem(token: string, productId: number, signal?: AbortSignal): Promise<void> {
  await requestEmpty(`/api/wishlist/items/${productId}`, token, signal)
}

type JsonMethod = 'GET' | 'POST'

async function requestJson(
  path: string,
  options: { method: JsonMethod; token: string; body?: string; signal?: AbortSignal },
): Promise<unknown> {
  const response = await send(path, options)
  if (!response.ok) {
    throw await errorFrom(response)
  }
  try {
    return (await response.json()) as unknown
  } catch {
    throw new WishlistApiError('Máy chủ trả về dữ liệu không đọc được.', response.status)
  }
}

async function requestEmpty(path: string, token: string, signal?: AbortSignal): Promise<void> {
  const response = await send(path, { method: 'DELETE', token, signal })
  if (response.status === 204) {
    return
  }
  if (!response.ok) {
    throw await errorFrom(response)
  }
  throw new WishlistApiError('Máy chủ trả về dữ liệu không đọc được.', response.status)
}

async function send(
  path: string,
  options: { method: JsonMethod | 'DELETE'; token: string; body?: string; signal?: AbortSignal },
): Promise<Response> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${options.token}`,
  }
  if (options.body) {
    headers['Content-Type'] = 'application/json'
  }
  try {
    return await fetch(apiUrl(path), {
      method: options.method,
      headers,
      body: options.body,
      signal: options.signal,
    })
  } catch (error) {
    if (isAbortError(error)) {
      throw error
    }
    throw new WishlistApiError('Không kết nối được máy chủ.', 0)
  }
}

async function errorFrom(response: Response): Promise<WishlistApiError> {
  const serverMessage = await readServerMessage(response)
  return new WishlistApiError(serverMessage ?? `HTTP ${response.status}`, response.status, serverMessage)
}

async function readServerMessage(response: Response): Promise<string | null> {
  try {
    const body: unknown = await response.json()
    if (!isRecord(body) || typeof body.message !== 'string') {
      return null
    }
    return body.message
  } catch {
    return null
  }
}

function readWishlist(payload: unknown): WishlistResponse {
  if (!isRecord(payload) || !Array.isArray(payload.items)) {
    throw new WishlistApiError('Dữ liệu yêu thích không đúng định dạng.', 200)
  }
  const id = payload.id === null ? null : readId(payload.id)
  const itemCount = readCount(payload.itemCount)
  if ((payload.id !== null && id == null) || itemCount == null) {
    throw new WishlistApiError('Dữ liệu yêu thích không đúng định dạng.', 200)
  }
  return {
    id,
    items: payload.items.map(readItem),
    itemCount,
  }
}

function readItem(value: unknown): WishlistItemResponse {
  if (!isRecord(value)) {
    throw new WishlistApiError('Dữ liệu yêu thích không đúng định dạng.', 200)
  }
  const id = readId(value.id)
  const productId = readId(value.productId)
  const price = readMoney(value.price)
  const salePrice = value.salePrice === null ? null : readMoney(value.salePrice)
  const effectivePrice = readMoney(value.effectivePrice)
  const primaryImageUrl = readOptionalString(value.primaryImageUrl)
  if (
    id == null ||
    productId == null ||
    typeof value.name !== 'string' ||
    typeof value.slug !== 'string' ||
    price == null ||
    (value.salePrice !== null && salePrice == null) ||
    effectivePrice == null ||
    (value.primaryImageUrl !== null && primaryImageUrl == null) ||
    typeof value.available !== 'boolean' ||
    typeof value.inStock !== 'boolean' ||
    typeof value.createdAt !== 'string'
  ) {
    throw new WishlistApiError('Dữ liệu yêu thích không đúng định dạng.', 200)
  }
  return {
    id,
    productId,
    name: value.name,
    slug: value.slug,
    price,
    salePrice,
    effectivePrice,
    primaryImageUrl,
    available: value.available,
    inStock: value.inStock,
    createdAt: value.createdAt,
  }
}

function readOptionalString(value: unknown): string | null {
  if (value == null) {
    return null
  }
  if (typeof value === 'string') {
    return value
  }
  throw new WishlistApiError('Dữ liệu yêu thích không đúng định dạng.', 200)
}

function readId(value: unknown): number | null {
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : null
}

function readCount(value: unknown): number | null {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0 ? value : null
}

function readMoney(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && value.trim().length > 0) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError'
}
