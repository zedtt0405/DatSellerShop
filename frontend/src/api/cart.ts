import { apiUrl } from './catalog.ts'
import type { ColorSummary, SizeSummary } from './catalog.ts'

export class CartApiError extends Error {
  readonly status: number
  readonly serverMessage: string | null

  constructor(message: string, status: number, serverMessage: string | null = null) {
    super(message)
    this.name = 'CartApiError'
    this.status = status
    this.serverMessage = serverMessage
  }
}

export type CartProductSummary = {
  id: number
  name: string
  slug: string
  primaryImageUrl: string | null
}

export type CartItemResponse = {
  id: number
  variantId: number
  sku: string
  product: CartProductSummary
  size: SizeSummary | null
  color: ColorSummary | null
  quantity: number
  unitPrice: number
  lineSubtotal: number
  stockQuantity: number | null
  available: boolean
}

export type CartResponse = {
  id: number | null
  items: CartItemResponse[]
  itemCount: number
  totalQuantity: number
  subtotal: number
}

export function hasCustomerRole(roles: string[] | undefined): boolean {
  return roles?.includes('CUSTOMER') ?? false
}

export function cartErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof CartApiError)) {
    return fallback
  }
  if (error.status === 0) {
    return 'Không kết nối được máy chủ.'
  }
  if (error.status === 401) {
    return 'Phiên đăng nhập đã hết hiệu lực.'
  }
  if (error.status === 403) {
    return 'Bạn không có quyền sử dụng giỏ hàng.'
  }
  if (error.status === 404) {
    if (error.serverMessage === 'Product variant not found') {
      return 'Không tìm thấy biến thể sản phẩm.'
    }
    return 'Không tìm thấy dòng giỏ hàng.'
  }
  if (error.status === 400) {
    if (error.serverMessage === 'Requested quantity exceeds available stock') {
      return 'Số lượng vượt quá tồn kho hiện có.'
    }
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

export async function getCart(token: string, signal?: AbortSignal): Promise<CartResponse> {
  const payload = await requestJson('/api/cart', { method: 'GET', token, signal })
  return readCart(payload)
}

export async function addCartItem(
  token: string,
  variantId: number,
  quantity: number,
  signal?: AbortSignal,
): Promise<CartResponse> {
  const payload = await requestJson('/api/cart/items', {
    method: 'POST',
    token,
    body: JSON.stringify({ variantId, quantity }),
    signal,
  })
  return readCart(payload)
}

export async function updateCartItem(
  token: string,
  itemId: number,
  quantity: number,
  signal?: AbortSignal,
): Promise<CartResponse> {
  const payload = await requestJson(`/api/cart/items/${itemId}`, {
    method: 'PUT',
    token,
    body: JSON.stringify({ quantity }),
    signal,
  })
  return readCart(payload)
}

export async function removeCartItem(token: string, itemId: number, signal?: AbortSignal): Promise<void> {
  await requestEmpty(`/api/cart/items/${itemId}`, token, signal)
}

export async function clearCartItems(token: string, signal?: AbortSignal): Promise<void> {
  await requestEmpty('/api/cart/items', token, signal)
}

type JsonMethod = 'GET' | 'POST' | 'PUT'

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
    throw new CartApiError('Máy chủ trả về dữ liệu không đọc được.', response.status)
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
  throw new CartApiError('Máy chủ trả về dữ liệu không đọc được.', response.status)
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
    throw new CartApiError('Không kết nối được máy chủ.', 0)
  }
}

async function errorFrom(response: Response): Promise<CartApiError> {
  const serverMessage = await readServerMessage(response)
  return new CartApiError(serverMessage ?? `HTTP ${response.status}`, response.status, serverMessage)
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

function readCart(payload: unknown): CartResponse {
  if (!isRecord(payload) || !Array.isArray(payload.items)) {
    throw new CartApiError('Dữ liệu giỏ hàng không đúng định dạng.', 200)
  }
  const id = payload.id === null ? null : readId(payload.id)
  if (payload.id !== null && id == null) {
    throw new CartApiError('Dữ liệu giỏ hàng không đúng định dạng.', 200)
  }
  const itemCount = readInt(payload.itemCount)
  const totalQuantity = readInt(payload.totalQuantity)
  const subtotal = readMoney(payload.subtotal)
  if (itemCount == null || totalQuantity == null || subtotal == null) {
    throw new CartApiError('Dữ liệu giỏ hàng không đúng định dạng.', 200)
  }
  return {
    id,
    items: payload.items.map(readItem),
    itemCount,
    totalQuantity,
    subtotal,
  }
}

function readItem(value: unknown): CartItemResponse {
  if (!isRecord(value) || !isRecord(value.product)) {
    throw new CartApiError('Dữ liệu giỏ hàng không đúng định dạng.', 200)
  }
  const id = readId(value.id)
  const variantId = readId(value.variantId)
  const productId = readId(value.product.id)
  const quantity = readInt(value.quantity)
  const unitPrice = readMoney(value.unitPrice)
  const lineSubtotal = readMoney(value.lineSubtotal)
  const stockQuantity = value.stockQuantity === null ? null : readInt(value.stockQuantity)
  if (
    id == null ||
    variantId == null ||
    productId == null ||
    typeof value.sku !== 'string' ||
    typeof value.product.name !== 'string' ||
    typeof value.product.slug !== 'string' ||
    quantity == null ||
    unitPrice == null ||
    lineSubtotal == null ||
    (value.stockQuantity !== null && stockQuantity == null) ||
    typeof value.available !== 'boolean'
  ) {
    throw new CartApiError('Dữ liệu giỏ hàng không đúng định dạng.', 200)
  }
  const primaryImageUrl = readOptionalString(value.product.primaryImageUrl)
  return {
    id,
    variantId,
    sku: value.sku,
    product: {
      id: productId,
      name: value.product.name,
      slug: value.product.slug,
      primaryImageUrl,
    },
    size: readSize(value.size),
    color: readColor(value.color),
    quantity,
    unitPrice,
    lineSubtotal,
    stockQuantity,
    available: value.available,
  }
}

function readSize(value: unknown): SizeSummary | null {
  if (value == null) {
    return null
  }
  if (!isRecord(value) || readId(value.id) == null || typeof value.name !== 'string') {
    throw new CartApiError('Dữ liệu giỏ hàng không đúng định dạng.', 200)
  }
  return { id: readId(value.id) as number, name: value.name }
}

function readColor(value: unknown): ColorSummary | null {
  if (value == null) {
    return null
  }
  if (!isRecord(value) || readId(value.id) == null || typeof value.name !== 'string') {
    throw new CartApiError('Dữ liệu giỏ hàng không đúng định dạng.', 200)
  }
  return { id: readId(value.id) as number, name: value.name, hexCode: readOptionalString(value.hexCode) }
}

function readOptionalString(value: unknown): string | null {
  if (value == null) {
    return null
  }
  if (typeof value === 'string') {
    return value
  }
  throw new CartApiError('Dữ liệu giỏ hàng không đúng định dạng.', 200)
}

function readId(value: unknown): number | null {
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : null
}

function readInt(value: unknown): number | null {
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
