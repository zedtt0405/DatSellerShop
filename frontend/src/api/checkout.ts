import { apiUrl } from './catalog.ts'

export class CheckoutApiError extends Error {
  readonly status: number
  readonly serverMessage: string | null

  constructor(message: string, status: number, serverMessage: string | null = null) {
    super(message)
    this.name = 'CheckoutApiError'
    this.status = status
    this.serverMessage = serverMessage
  }
}

export type PaymentMethod = 'COD' | 'BANK_QR'

export type VoucherPreview = {
  code: string
  valid: boolean
  subtotal: number
  discountAmount: number
  totalAfterDiscount: number
  message: string
}

export type OrderShipping = {
  recipientName: string
  recipientPhone: string
  province: string
  district: string
  ward: string
  addressLine: string
}

export type OrderVoucher = {
  code: string
  type: string
  discountValue: number
  discountAmount: number
}

export type OrderLine = {
  id: number
  productId: number
  variantId: number
  productName: string
  productSlug: string
  sku: string
  sizeName: string | null
  colorName: string | null
  productImageUrl: string | null
  unitPrice: number
  quantity: number
  lineTotal: number
}

export type OrderDetail = {
  id: number
  orderNumber: string
  status: string
  subtotal: number
  discountAmount: number
  shippingFee: number
  totalAmount: number
  voucher: OrderVoucher | null
  shippingAddress: OrderShipping
  customerNote: string | null
  items: OrderLine[]
  createdAt: string
  updatedAt: string
}

export type OrderSummary = {
  orderNumber: string
  status: string
  subtotal: number
  discountAmount: number
  shippingFee: number
  totalAmount: number
  itemCount: number
  createdAt: string
}

export type PaymentDetail = {
  paymentReference: string
  orderNumber: string
  method: PaymentMethod
  status: string
  amount: number
  qrContent: string | null
  providerReference: string | null
  paidAt: string | null
  createdAt: string
  updatedAt: string
}

export type CheckoutAttempt = {
  startedAtMs: number
  address: {
    recipientName: string
    phoneNumber: string
    province: string
    district: string
    ward: string
    addressLine: string
  }
  lines: Array<{ variantId: number; quantity: number }>
  voucherCode: string | null
  customerNote: string | null
}

const SERVER_TEXT: Record<string, string> = {
  'Cart is empty': 'Giỏ hàng đang trống.',
  'This product is not available': 'Sản phẩm hiện không khả dụng.',
  'Requested quantity exceeds available stock': 'Số lượng vượt quá tồn kho hiện có.',
  'Address not found': 'Không tìm thấy địa chỉ giao hàng.',
  'Voucher is invalid': 'Mã giảm giá không hợp lệ.',
  'Voucher is not active': 'Mã giảm giá không còn hiệu lực.',
  'Voucher is not yet valid': 'Mã giảm giá chưa đến thời gian sử dụng.',
  'Voucher has expired': 'Mã giảm giá đã hết hạn.',
  'Voucher usage limit has been reached': 'Mã giảm giá đã hết lượt dùng.',
  'Minimum order amount not reached': 'Đơn chưa đạt giá trị tối thiểu của mã giảm giá.',
  'Cart contains unavailable items': 'Giỏ có sản phẩm không khả dụng.',
  'Voucher applied successfully': 'Mã giảm giá hợp lệ theo dữ liệu máy chủ.',
  'Payment method cannot be changed': 'Không đổi được phương thức thanh toán đã tạo.',
  'Payment cannot be created for this order': 'Đơn này không thể tạo thanh toán.',
  'Order not found': 'Không tìm thấy đơn hàng.',
  'Payment not found': 'Đơn chưa có thanh toán.',
}

export function checkoutErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof CheckoutApiError)) {
    return fallback
  }
  if (error.status === 0) {
    return 'Không kết nối được máy chủ.'
  }
  if (error.status === 401) {
    return 'Phiên đăng nhập đã hết hiệu lực.'
  }
  if (error.status === 403) {
    return 'Bạn không có quyền đặt hàng.'
  }
  if (error.status === 404) {
    return translateServer(error.serverMessage) ?? 'Không tìm thấy dữ liệu.'
  }
  if (error.status === 409) {
    return translateServer(error.serverMessage) ?? 'Dữ liệu đã thay đổi. Thử lại thao tác thanh toán.'
  }
  if (error.status === 400) {
    return translateServer(error.serverMessage) ?? (error.serverMessage ? `Dữ liệu không hợp lệ. ${error.serverMessage}` : 'Dữ liệu không hợp lệ.')
  }
  if (error.status >= 500) {
    return 'Máy chủ gặp sự cố. Thử lại sau.'
  }
  return fallback
}

export function translateServer(message: string | null | undefined): string | null {
  if (!message) {
    return null
  }
  return SERVER_TEXT[message] ?? null
}

export async function validateVoucher(token: string, code: string, signal?: AbortSignal): Promise<VoucherPreview> {
  const payload = await requestJson('/api/vouchers/validate', {
    method: 'POST',
    token,
    body: JSON.stringify({ code }),
    signal,
    ok: new Set([200]),
  })
  return readPreview(payload)
}

export async function createCheckout(
  token: string,
  input: { addressId: number; voucherCode?: string; customerNote?: string },
): Promise<OrderDetail> {
  const body: { addressId: number; voucherCode?: string; customerNote?: string } = {
    addressId: input.addressId,
  }
  if (input.voucherCode) {
    body.voucherCode = input.voucherCode
  }
  if (input.customerNote) {
    body.customerNote = input.customerNote
  }
  const payload = await requestJson('/api/checkout', {
    method: 'POST',
    token,
    body: JSON.stringify(body),
    ok: new Set([201]),
  })
  return readOrder(payload)
}

export async function createPayment(
  token: string,
  orderNumber: string,
  method: PaymentMethod,
): Promise<PaymentDetail> {
  const payload = await requestJson(`/api/orders/${encodeURIComponent(orderNumber)}/payment`, {
    method: 'POST',
    token,
    body: JSON.stringify({ method }),
    ok: new Set([200, 201]),
  })
  return readPayment(payload)
}

export async function getOrder(token: string, orderNumber: string, signal?: AbortSignal): Promise<OrderDetail> {
  const payload = await requestJson(`/api/orders/${encodeURIComponent(orderNumber)}`, {
    method: 'GET',
    token,
    signal,
    ok: new Set([200]),
  })
  return readOrder(payload)
}

export async function getPayment(token: string, orderNumber: string, signal?: AbortSignal): Promise<PaymentDetail | null> {
  try {
    const payload = await requestJson(`/api/orders/${encodeURIComponent(orderNumber)}/payment`, {
      method: 'GET',
      token,
      signal,
      ok: new Set([200]),
    })
    return readPayment(payload)
  } catch (error) {
    if (error instanceof CheckoutApiError && error.status === 404) {
      return null
    }
    throw error
  }
}

export async function listRecentOrders(token: string, signal?: AbortSignal): Promise<OrderSummary[]> {
  const payload = await requestJson('/api/orders?page=0&size=5', {
    method: 'GET',
    token,
    signal,
    ok: new Set([200]),
  })
  if (!isRecord(payload) || !Array.isArray(payload.content)) {
    throw new CheckoutApiError('Dữ liệu đơn hàng không đúng định dạng.', 200)
  }
  return payload.content.map(readSummary)
}

const MATCH_WINDOW_MS = 3 * 60 * 1000

export function findCertainOrder(
  orders: OrderDetail[],
  cartItemCount: number,
  attempt: CheckoutAttempt,
  nowMs: number,
): OrderDetail | null {
  if (cartItemCount !== 0) {
    return null
  }
  const matches = orders.filter((order) => orderMatchesAttempt(order, attempt, nowMs))
  return matches.length === 1 ? matches[0] : null
}

function orderMatchesAttempt(order: OrderDetail, attempt: CheckoutAttempt, nowMs: number): boolean {
  const created = Date.parse(order.createdAt)
  if (!Number.isFinite(created)) {
    return false
  }
  if (created < attempt.startedAtMs - MATCH_WINDOW_MS || created > nowMs + MATCH_WINDOW_MS) {
    return false
  }
  if (!sameAddress(order.shippingAddress, attempt.address)) {
    return false
  }
  if (!sameNote(order.customerNote, attempt.customerNote)) {
    return false
  }
  if (!sameVoucher(order.voucher?.code ?? null, attempt.voucherCode)) {
    return false
  }
  return sameLines(order.items, attempt.lines)
}

function sameAddress(snapshot: OrderShipping, address: CheckoutAttempt['address']): boolean {
  return (
    snapshot.recipientName.trim() === address.recipientName.trim() &&
    snapshot.recipientPhone.trim() === address.phoneNumber.trim() &&
    snapshot.province.trim() === address.province.trim() &&
    snapshot.district.trim() === address.district.trim() &&
    snapshot.ward.trim() === address.ward.trim() &&
    snapshot.addressLine.trim() === address.addressLine.trim()
  )
}

function sameNote(actual: string | null, expected: string | null): boolean {
  return (actual ?? '').trim() === (expected ?? '').trim()
}

function sameVoucher(actual: string | null, expected: string | null): boolean {
  const left = (actual ?? '').trim().toUpperCase()
  const right = (expected ?? '').trim().toUpperCase()
  return left === right
}

function sameLines(items: OrderLine[], lines: CheckoutAttempt['lines']): boolean {
  if (items.length !== lines.length) {
    return false
  }
  const left = [...items].sort((a, b) => a.variantId - b.variantId || a.quantity - b.quantity)
  const right = [...lines].sort((a, b) => a.variantId - b.variantId || a.quantity - b.quantity)
  return left.every((item, index) => item.variantId === right[index].variantId && item.quantity === right[index].quantity)
}

type JsonMethod = 'GET' | 'POST'

async function requestJson(
  path: string,
  options: { method: JsonMethod; token: string; body?: string; signal?: AbortSignal; ok: Set<number> },
): Promise<unknown> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${options.token}`,
  }
  if (options.body) {
    headers['Content-Type'] = 'application/json'
  }
  let response: Response
  try {
    response = await fetch(apiUrl(path), {
      method: options.method,
      headers,
      body: options.body,
      signal: options.signal,
    })
  } catch (error) {
    if (isAbortError(error)) {
      throw error
    }
    throw new CheckoutApiError('Không kết nối được máy chủ.', 0)
  }
  if (!options.ok.has(response.status)) {
    throw await errorFrom(response)
  }
  try {
    return (await response.json()) as unknown
  } catch {
    throw new CheckoutApiError('Máy chủ trả về dữ liệu không đọc được.', response.status)
  }
}

async function errorFrom(response: Response): Promise<CheckoutApiError> {
  const serverMessage = await readServerMessage(response)
  return new CheckoutApiError(serverMessage ?? `HTTP ${response.status}`, response.status, serverMessage)
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

function readPreview(payload: unknown): VoucherPreview {
  if (
    !isRecord(payload) ||
    typeof payload.code !== 'string' ||
    typeof payload.valid !== 'boolean' ||
    typeof payload.message !== 'string'
  ) {
    throw new CheckoutApiError('Dữ liệu mã giảm giá không đúng định dạng.', 200)
  }
  const subtotal = readMoney(payload.subtotal)
  const discountAmount = readMoney(payload.discountAmount)
  const totalAfterDiscount = readMoney(payload.totalAfterDiscount)
  if (subtotal == null || discountAmount == null || totalAfterDiscount == null) {
    throw new CheckoutApiError('Dữ liệu mã giảm giá không đúng định dạng.', 200)
  }
  return {
    code: payload.code,
    valid: payload.valid,
    subtotal,
    discountAmount,
    totalAfterDiscount,
    message: payload.message,
  }
}

function readOrder(payload: unknown): OrderDetail {
  if (!isRecord(payload) || !Array.isArray(payload.items) || !isRecord(payload.shippingAddress)) {
    throw new CheckoutApiError('Dữ liệu đơn hàng không đúng định dạng.', 200)
  }
  const id = readId(payload.id)
  const subtotal = readMoney(payload.subtotal)
  const discountAmount = readMoney(payload.discountAmount)
  const shippingFee = readMoney(payload.shippingFee)
  const totalAmount = readMoney(payload.totalAmount)
  const shippingAddress = readShipping(payload.shippingAddress)
  if (
    id == null ||
    typeof payload.orderNumber !== 'string' ||
    typeof payload.status !== 'string' ||
    subtotal == null ||
    discountAmount == null ||
    shippingFee == null ||
    totalAmount == null ||
    !shippingAddress ||
    typeof payload.createdAt !== 'string' ||
    typeof payload.updatedAt !== 'string'
  ) {
    throw new CheckoutApiError('Dữ liệu đơn hàng không đúng định dạng.', 200)
  }
  const customerNote = payload.customerNote == null ? null : readRequiredString(payload.customerNote)
  if (payload.customerNote != null && customerNote == null) {
    throw new CheckoutApiError('Dữ liệu đơn hàng không đúng định dạng.', 200)
  }
  return {
    id,
    orderNumber: payload.orderNumber,
    status: payload.status,
    subtotal,
    discountAmount,
    shippingFee,
    totalAmount,
    voucher: readVoucher(payload.voucher),
    shippingAddress,
    customerNote,
    items: payload.items.map(readLine),
    createdAt: payload.createdAt,
    updatedAt: payload.updatedAt,
  }
}

function readSummary(payload: unknown): OrderSummary {
  if (!isRecord(payload)) {
    throw new CheckoutApiError('Dữ liệu đơn hàng không đúng định dạng.', 200)
  }
  const subtotal = readMoney(payload.subtotal)
  const discountAmount = readMoney(payload.discountAmount)
  const shippingFee = readMoney(payload.shippingFee)
  const totalAmount = readMoney(payload.totalAmount)
  const itemCount = readInt(payload.itemCount)
  if (
    typeof payload.orderNumber !== 'string' ||
    typeof payload.status !== 'string' ||
    subtotal == null ||
    discountAmount == null ||
    shippingFee == null ||
    totalAmount == null ||
    itemCount == null ||
    typeof payload.createdAt !== 'string'
  ) {
    throw new CheckoutApiError('Dữ liệu đơn hàng không đúng định dạng.', 200)
  }
  return {
    orderNumber: payload.orderNumber,
    status: payload.status,
    subtotal,
    discountAmount,
    shippingFee,
    totalAmount,
    itemCount,
    createdAt: payload.createdAt,
  }
}

function readPayment(payload: unknown): PaymentDetail {
  if (!isRecord(payload)) {
    throw new CheckoutApiError('Dữ liệu thanh toán không đúng định dạng.', 200)
  }
  const amount = readMoney(payload.amount)
  if (
    typeof payload.paymentReference !== 'string' ||
    typeof payload.orderNumber !== 'string' ||
    (payload.method !== 'COD' && payload.method !== 'BANK_QR') ||
    typeof payload.status !== 'string' ||
    amount == null ||
    typeof payload.createdAt !== 'string' ||
    typeof payload.updatedAt !== 'string'
  ) {
    throw new CheckoutApiError('Dữ liệu thanh toán không đúng định dạng.', 200)
  }
  return {
    paymentReference: payload.paymentReference,
    orderNumber: payload.orderNumber,
    method: payload.method,
    status: payload.status,
    amount,
    qrContent: readOptionalString(payload.qrContent),
    providerReference: readOptionalString(payload.providerReference),
    paidAt: readOptionalString(payload.paidAt),
    createdAt: payload.createdAt,
    updatedAt: payload.updatedAt,
  }
}

function readShipping(payload: Record<string, unknown>): OrderShipping | null {
  if (
    typeof payload.recipientName !== 'string' ||
    typeof payload.recipientPhone !== 'string' ||
    typeof payload.province !== 'string' ||
    typeof payload.district !== 'string' ||
    typeof payload.ward !== 'string' ||
    typeof payload.addressLine !== 'string'
  ) {
    return null
  }
  return {
    recipientName: payload.recipientName,
    recipientPhone: payload.recipientPhone,
    province: payload.province,
    district: payload.district,
    ward: payload.ward,
    addressLine: payload.addressLine,
  }
}

function readVoucher(payload: unknown): OrderVoucher | null {
  if (payload == null) {
    return null
  }
  if (!isRecord(payload) || typeof payload.code !== 'string' || typeof payload.type !== 'string') {
    throw new CheckoutApiError('Dữ liệu đơn hàng không đúng định dạng.', 200)
  }
  const discountValue = readMoney(payload.discountValue)
  const discountAmount = readMoney(payload.discountAmount)
  if (discountValue == null || discountAmount == null) {
    throw new CheckoutApiError('Dữ liệu đơn hàng không đúng định dạng.', 200)
  }
  return { code: payload.code, type: payload.type, discountValue, discountAmount }
}

function readLine(payload: unknown): OrderLine {
  if (!isRecord(payload)) {
    throw new CheckoutApiError('Dữ liệu đơn hàng không đúng định dạng.', 200)
  }
  const id = readId(payload.id)
  const productId = readId(payload.productId)
  const variantId = readId(payload.variantId)
  const unitPrice = readMoney(payload.unitPrice)
  const quantity = readInt(payload.quantity)
  const lineTotal = readMoney(payload.lineTotal)
  if (
    id == null ||
    productId == null ||
    variantId == null ||
    typeof payload.productName !== 'string' ||
    typeof payload.productSlug !== 'string' ||
    typeof payload.sku !== 'string' ||
    unitPrice == null ||
    quantity == null ||
    lineTotal == null
  ) {
    throw new CheckoutApiError('Dữ liệu đơn hàng không đúng định dạng.', 200)
  }
  return {
    id,
    productId,
    variantId,
    productName: payload.productName,
    productSlug: payload.productSlug,
    sku: payload.sku,
    sizeName: readOptionalString(payload.sizeName),
    colorName: readOptionalString(payload.colorName),
    productImageUrl: readOptionalString(payload.productImageUrl),
    unitPrice,
    quantity,
    lineTotal,
  }
}

function readOptionalString(value: unknown): string | null {
  if (value == null) {
    return null
  }
  if (typeof value === 'string') {
    return value
  }
  throw new CheckoutApiError('Dữ liệu không đúng định dạng.', 200)
}

function readRequiredString(value: unknown): string | null {
  return typeof value === 'string' ? value : null
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
