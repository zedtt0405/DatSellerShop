export type CategorySummary = {
  id: number
  name: string
  slug: string
}

export type BrandSummary = {
  id: number
  name: string
  slug: string
}

export type ProductCardResponse = {
  id: number
  name: string
  slug: string
  price: number
  salePrice: number | null
  effectivePrice: number
  category: CategorySummary
  brand: BrandSummary
  primaryImageUrl: string | null
  featured: boolean | null
  inStock: boolean | null
}

export type SizeSummary = {
  id: number
  name: string
}

export type ColorSummary = {
  id: number
  name: string
  hexCode: string | null
}

export type ProductResponse = {
  id: number
  name: string
  slug: string
  description: string | null
  price: number
  salePrice: number | null
  effectivePrice: number
  category: CategorySummary
  brand: BrandSummary
  status: string
  featured: boolean | null
  createdAt: string
  updatedAt: string
}

export type ProductImageResponse = {
  id: number
  productId: number
  imageUrl: string | null
  altText: string | null
  isPrimary: boolean | null
  sortOrder: number | null
}

export type ProductVariantResponse = {
  id: number
  productId: number
  sku: string
  stockQuantity: number | null
  active: boolean | null
  size: SizeSummary | null
  color: ColorSummary | null
  createdAt: string
  updatedAt: string
}

export type ProductDetailResponse = {
  product: ProductResponse
  images: ProductImageResponse[]
  variants: ProductVariantResponse[]
}

export type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export type CategoryResponse = {
  id: number
  name: string
  slug: string
  description: string | null
  status: string
  parentCategoryId: number | null
  createdAt: string
  updatedAt: string
}

export type BrandResponse = {
  id: number
  name: string
  slug: string
  description: string | null
  logoUrl: string | null
  status: string
  createdAt: string
  updatedAt: string
}

export const PRODUCT_SORTS = [
  'createdAt,desc',
  'createdAt,asc',
  'name,asc',
  'name,desc',
  'price,asc',
  'price,desc',
] as const

export type ProductSort = (typeof PRODUCT_SORTS)[number]

export const DEFAULT_PRODUCT_SORT: ProductSort = 'createdAt,desc'

export type ProductListQuery = {
  q?: string
  category?: string
  brand?: string
  sort?: ProductSort
  page: number
  size: number
}

const API_PREFIX = '/api'

export class CatalogApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'CatalogApiError'
    this.status = status
  }
}

export function apiUrl(path: string): string {
  const suffix = path.startsWith('/') ? path : `/${path}`
  if (suffix !== API_PREFIX && !suffix.startsWith(`${API_PREFIX}/`)) {
    throw new Error('Đường dẫn catalog phải bắt đầu bằng /api.')
  }
  const raw = import.meta.env.VITE_API_BASE_URL
  const base = (typeof raw === 'string' ? raw : '').trim().replace(/\/+$/, '')
  if (base.length === 0) {
    return suffix
  }
  if (base.endsWith(API_PREFIX)) {
    return `${base}${suffix.slice(API_PREFIX.length)}`
  }
  return `${base}${suffix}`
}

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError'
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const body: unknown = await response.json()
    if (isRecord(body) && typeof body.message === 'string' && body.message.length > 0) {
      return `Không tải được dữ liệu. ${body.message}`
    }
  } catch {
    // The error body is not JSON.
  }
  return `Không tải được dữ liệu (${response.status}).`
}

async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  let response: Response
  try {
    response = await fetch(apiUrl(path), {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal,
    })
  } catch (error) {
    if (isAbortError(error)) {
      throw error
    }
    throw new CatalogApiError('Không kết nối được máy chủ.', 0)
  }
  if (!response.ok) {
    throw new CatalogApiError(await readErrorMessage(response), response.status)
  }
  return (await response.json()) as T
}

function isProductPage(value: unknown): value is PageResponse<ProductCardResponse> {
  if (!isRecord(value) || !Array.isArray(value.content)) {
    return false
  }
  return (
    typeof value.page === 'number' &&
    typeof value.size === 'number' &&
    typeof value.totalElements === 'number' &&
    typeof value.totalPages === 'number' &&
    typeof value.first === 'boolean' &&
    typeof value.last === 'boolean'
  )
}

export async function listProducts(
  query: ProductListQuery,
  signal?: AbortSignal,
): Promise<PageResponse<ProductCardResponse>> {
  const params = new URLSearchParams()
  const q = query.q?.trim()
  if (q) {
    params.set('q', q)
  }
  if (query.category) {
    params.set('category', query.category)
  }
  if (query.brand) {
    params.set('brand', query.brand)
  }
  if (query.sort && query.sort !== DEFAULT_PRODUCT_SORT) {
    params.set('sort', query.sort)
  }
  params.set('page', String(query.page))
  params.set('size', String(query.size))
  const page = await getJson<unknown>(`/api/products?${params.toString()}`, signal)
  if (!isProductPage(page)) {
    throw new CatalogApiError('Dữ liệu sản phẩm không đúng định dạng.', 200)
  }
  return page
}

export async function listCategories(signal?: AbortSignal): Promise<CategoryResponse[]> {
  const categories = await getJson<unknown>('/api/categories', signal)
  if (!Array.isArray(categories)) {
    throw new CatalogApiError('Dữ liệu danh mục không đúng định dạng.', 200)
  }
  return categories as CategoryResponse[]
}

export async function listBrands(signal?: AbortSignal): Promise<BrandResponse[]> {
  const brands = await getJson<unknown>('/api/brands', signal)
  if (!Array.isArray(brands)) {
    throw new CatalogApiError('Dữ liệu thương hiệu không đúng định dạng.', 200)
  }
  return brands as BrandResponse[]
}

function isProductDetail(value: unknown): value is ProductDetailResponse {
  if (!isRecord(value) || !isRecord(value.product)) {
    return false
  }
  return Array.isArray(value.images) && Array.isArray(value.variants) && typeof value.product.slug === 'string'
}

export async function getProduct(slug: string, signal?: AbortSignal): Promise<ProductDetailResponse> {
  const detail = await getJson<unknown>(`/api/products/${encodeURIComponent(slug)}`, signal)
  if (!isProductDetail(detail)) {
    throw new CatalogApiError('Dữ liệu sản phẩm không đúng định dạng.', 200)
  }
  return detail
}
