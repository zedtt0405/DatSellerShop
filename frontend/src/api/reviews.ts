import { apiUrl } from './catalog.ts'

export class ReviewApiError extends Error {
  readonly status: number
  readonly serverMessage: string | null

  constructor(message: string, status: number, serverMessage: string | null = null) {
    super(message)
    this.name = 'ReviewApiError'
    this.status = status
    this.serverMessage = serverMessage
  }
}

export type PublicReview = {
  id: number
  rating: number
  comment: string | null
  reviewerName: string
  createdAt: string
  updatedAt: string
}

export type PublicReviewPage = {
  content: PublicReview[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  averageRating: number | null
  reviewCount: number
}

export type MyReview = {
  id: number
  productId: number
  productName: string
  productSlug: string
  rating: number
  comment: string | null
  status: string
  createdAt: string
  updatedAt: string
}

const SERVER_TEXT: Record<string, string> = {
  'Product can only be reviewed after delivery': 'Chỉ đánh giá được sản phẩm trong đơn đã giao.',
  'Product already reviewed': 'Bạn đã đánh giá sản phẩm này.',
  'Review not found': 'Không tìm thấy đánh giá.',
  'Product not found': 'Không tìm thấy sản phẩm.',
}

export function reviewErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof ReviewApiError)) {
    return fallback
  }
  if (error.status === 0) {
    return 'Không kết nối được máy chủ.'
  }
  if (error.status === 401) {
    return 'Phiên đăng nhập đã hết hiệu lực.'
  }
  if (error.status === 403) {
    return 'Bạn không có quyền đánh giá.'
  }
  if (error.status === 404) {
    return SERVER_TEXT[error.serverMessage ?? ''] ?? 'Không tìm thấy dữ liệu.'
  }
  if (error.status === 409) {
    return SERVER_TEXT[error.serverMessage ?? ''] ?? 'Đánh giá đã tồn tại.'
  }
  if (error.status === 400) {
    return SERVER_TEXT[error.serverMessage ?? ''] ?? (error.serverMessage ? `Dữ liệu không hợp lệ. ${error.serverMessage}` : 'Dữ liệu không hợp lệ.')
  }
  if (error.status >= 500) {
    return 'Máy chủ gặp sự cố. Thử lại sau.'
  }
  return fallback
}

export async function listPublicReviews(slug: string, page: number, signal?: AbortSignal): Promise<PublicReviewPage> {
  const payload = await requestJson(`/api/products/${encodeURIComponent(slug)}/reviews?page=${page}&size=10`, {
    method: 'GET',
    ok: new Set([200]),
    signal,
  })
  return readPublicPage(payload)
}

const MY_REVIEWS_PAGE_SIZE = 100
const MY_REVIEWS_MAX_PAGES = 20

export async function listMyReviews(token: string, signal?: AbortSignal): Promise<MyReview[]> {
  const all: MyReview[] = []
  for (let page = 0; page < MY_REVIEWS_MAX_PAGES; page += 1) {
    const payload = await requestJson(`/api/reviews/me?page=${page}&size=${MY_REVIEWS_PAGE_SIZE}`, {
      method: 'GET',
      token,
      ok: new Set([200]),
      signal,
    })
    if (!isRecord(payload) || !Array.isArray(payload.content) || typeof payload.last !== 'boolean') {
      throw new ReviewApiError('Dữ liệu đánh giá không đúng định dạng.', 200)
    }
    all.push(...payload.content.map(readMine))
    if (payload.last) {
      break
    }
  }
  return all
}

export async function createReview(
  token: string,
  input: { productId: number; rating: number; comment?: string },
): Promise<MyReview> {
  const body: { productId: number; rating: number; comment?: string } = {
    productId: input.productId,
    rating: input.rating,
  }
  if (input.comment) {
    body.comment = input.comment
  }
  const payload = await requestJson('/api/reviews', {
    method: 'POST',
    token,
    body: JSON.stringify(body),
    ok: new Set([201]),
  })
  return readMine(payload)
}

export async function updateReview(
  token: string,
  reviewId: number,
  input: { rating: number; comment?: string },
): Promise<MyReview> {
  const body: { rating: number; comment?: string } = { rating: input.rating }
  if (input.comment) {
    body.comment = input.comment
  }
  const payload = await requestJson(`/api/reviews/${reviewId}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(body),
    ok: new Set([200]),
  })
  return readMine(payload)
}

export async function deleteReview(token: string, reviewId: number): Promise<void> {
  const response = await send(`/api/reviews/${reviewId}`, { method: 'DELETE', token })
  if (response.status === 204) {
    return
  }
  throw await errorFrom(response)
}

type JsonMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'

async function requestJson(
  path: string,
  options: { method: JsonMethod; token?: string; body?: string; signal?: AbortSignal; ok: Set<number> },
): Promise<unknown> {
  const response = await send(path, options)
  if (!options.ok.has(response.status)) {
    throw await errorFrom(response)
  }
  try {
    return (await response.json()) as unknown
  } catch {
    throw new ReviewApiError('Máy chủ trả về dữ liệu không đọc được.', response.status)
  }
}

async function send(
  path: string,
  options: { method: JsonMethod; token?: string; body?: string; signal?: AbortSignal },
): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.token) {
    headers.Authorization = `Bearer ${options.token}`
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
    if (error instanceof Error && error.name === 'AbortError') {
      throw error
    }
    throw new ReviewApiError('Không kết nối được máy chủ.', 0)
  }
}

async function errorFrom(response: Response): Promise<ReviewApiError> {
  let serverMessage: string | null = null
  try {
    const body: unknown = await response.json()
    if (isRecord(body) && typeof body.message === 'string') {
      serverMessage = body.message
    }
  } catch {
    serverMessage = null
  }
  return new ReviewApiError(serverMessage ?? `HTTP ${response.status}`, response.status, serverMessage)
}

function readPublicPage(payload: unknown): PublicReviewPage {
  if (!isRecord(payload) || !Array.isArray(payload.content)) {
    throw new ReviewApiError('Dữ liệu đánh giá không đúng định dạng.', 200)
  }
  const page = readInt(payload.page)
  const size = readInt(payload.size)
  const totalPages = readInt(payload.totalPages)
  const totalElements = readCount(payload.totalElements)
  const reviewCount = readCount(payload.reviewCount)
  if (
    page == null ||
    size == null ||
    totalPages == null ||
    totalElements == null ||
    reviewCount == null ||
    typeof payload.first !== 'boolean' ||
    typeof payload.last !== 'boolean'
  ) {
    throw new ReviewApiError('Dữ liệu đánh giá không đúng định dạng.', 200)
  }
  return {
    content: payload.content.map(readPublic),
    page,
    size,
    totalElements,
    totalPages,
    first: payload.first,
    last: payload.last,
    averageRating: payload.averageRating == null ? null : readMoney(payload.averageRating),
    reviewCount,
  }
}

function readPublic(payload: unknown): PublicReview {
  if (!isRecord(payload) || typeof payload.reviewerName !== 'string' || typeof payload.createdAt !== 'string') {
    throw new ReviewApiError('Dữ liệu đánh giá không đúng định dạng.', 200)
  }
  const id = readId(payload.id)
  const rating = readRating(payload.rating)
  if (id == null || rating == null || typeof payload.updatedAt !== 'string') {
    throw new ReviewApiError('Dữ liệu đánh giá không đúng định dạng.', 200)
  }
  return {
    id,
    rating,
    comment: readOptionalString(payload.comment),
    reviewerName: payload.reviewerName,
    createdAt: payload.createdAt,
    updatedAt: payload.updatedAt,
  }
}

function readMine(payload: unknown): MyReview {
  if (!isRecord(payload) || !isRecord(payload.product) || typeof payload.status !== 'string') {
    throw new ReviewApiError('Dữ liệu đánh giá không đúng định dạng.', 200)
  }
  const id = readId(payload.id)
  const productId = readId(payload.product.id)
  const rating = readRating(payload.rating)
  if (
    id == null ||
    productId == null ||
    rating == null ||
    typeof payload.product.name !== 'string' ||
    typeof payload.product.slug !== 'string' ||
    typeof payload.createdAt !== 'string' ||
    typeof payload.updatedAt !== 'string'
  ) {
    throw new ReviewApiError('Dữ liệu đánh giá không đúng định dạng.', 200)
  }
  return {
    id,
    productId,
    productName: payload.product.name,
    productSlug: payload.product.slug,
    rating,
    comment: readOptionalString(payload.comment),
    status: payload.status,
    createdAt: payload.createdAt,
    updatedAt: payload.updatedAt,
  }
}

function readOptionalString(value: unknown): string | null {
  if (value == null) {
    return null
  }
  if (typeof value === 'string') {
    return value
  }
  throw new ReviewApiError('Dữ liệu đánh giá không đúng định dạng.', 200)
}

function readId(value: unknown): number | null {
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : null
}

function readInt(value: unknown): number | null {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0 ? value : null
}

function readCount(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : null
}

function readRating(value: unknown): number | null {
  return typeof value === 'number' && Number.isInteger(value) && value >= 1 && value <= 5 ? value : null
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
