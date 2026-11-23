import { useEffect, useRef, useState } from 'react'
import {
  CatalogApiError,
  DEFAULT_PRODUCT_SORT,
  listBrands,
  listCategories,
  listProducts,
  type BrandResponse,
  type CategoryResponse,
  type PageResponse,
  type ProductCardResponse,
  type ProductSort,
} from '../api/catalog.ts'
import CatalogProductCard from '../components/shop/CatalogProductCard.tsx'

const PAGE_SIZE = 12
const SEARCH_DELAY_MS = 300

const SORT_OPTIONS: { value: ProductSort; label: string }[] = [
  { value: 'createdAt,desc', label: 'Mới nhất' },
  { value: 'createdAt,asc', label: 'Cũ nhất' },
  { value: 'name,asc', label: 'Tên A–Z' },
  { value: 'name,desc', label: 'Tên Z–A' },
  { value: 'price,asc', label: 'Giá gốc tăng dần' },
  { value: 'price,desc', label: 'Giá gốc giảm dần' },
]

type Filters = {
  q: string
  category: string
  brand: string
  sort: ProductSort
  page: number
}

type ProductResult =
  | { key: string; status: 'ready'; page: PageResponse<ProductCardResponse> }
  | { key: string; status: 'error'; message: string }

const INITIAL_FILTERS: Filters = {
  q: '',
  category: '',
  brand: '',
  sort: DEFAULT_PRODUCT_SORT,
  page: 0,
}

const controlClass =
  'min-h-11 w-full border border-line bg-white px-3 text-sm text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'

function filterKey(filters: Filters, reloadKey: number): string {
  return [filters.q, filters.category, filters.brand, filters.sort, String(filters.page), String(reloadKey)].join('\n')
}

function sameFilters(left: Filters, right: Filters): boolean {
  return (
    left.q === right.q &&
    left.category === right.category &&
    left.brand === right.brand &&
    left.sort === right.sort &&
    left.page === right.page
  )
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof CatalogApiError) {
    return error.message
  }
  return fallback
}

function ShopPage() {
  const [searchInput, setSearchInput] = useState('')
  const [filters, setFilters] = useState<Filters>(INITIAL_FILTERS)
  const searchTimer = useRef<number | null>(null)

  const [categories, setCategories] = useState<CategoryResponse[]>([])
  const [brands, setBrands] = useState<BrandResponse[]>([])
  const [filtersError, setFiltersError] = useState<string | null>(null)
  const [filtersReloadKey, setFiltersReloadKey] = useState(0)

  const [reloadKey, setReloadKey] = useState(0)
  const [productResult, setProductResult] = useState<ProductResult | null>(null)
  const requestKey = filterKey(filters, reloadKey)
  const visibleResult = productResult?.key === requestKey ? productResult : null

  useEffect(() => {
    return () => {
      if (searchTimer.current !== null) {
        window.clearTimeout(searchTimer.current)
      }
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([listCategories(controller.signal), listBrands(controller.signal)])
      .then(([nextCategories, nextBrands]) => {
        if (controller.signal.aborted) {
          return
        }
        setCategories(nextCategories)
        setBrands(nextBrands)
        setFiltersError(null)
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return
        }
        setFiltersError(errorMessage(error, 'Không tải được danh mục hoặc thương hiệu.'))
      })
    return () => controller.abort()
  }, [filtersReloadKey])

  useEffect(() => {
    const controller = new AbortController()
    const key = requestKey
    listProducts(
      {
        q: filters.q,
        category: filters.category,
        brand: filters.brand,
        sort: filters.sort,
        page: filters.page,
        size: PAGE_SIZE,
      },
      controller.signal,
    )
      .then((page) => {
        if (controller.signal.aborted) {
          return
        }
        setProductResult({ key, status: 'ready', page })
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return
        }
        setProductResult({
          key,
          status: 'error',
          message: errorMessage(error, 'Không tải được danh sách sản phẩm.'),
        })
      })
    return () => controller.abort()
  }, [filters, requestKey])

  function applyFilters(next: Filters) {
    setFilters((current) => (sameFilters(current, next) ? current : next))
  }

  function commitSearch(value: string) {
    const nextQuery = value.trim()
    setFilters((current) => {
      if (current.q === nextQuery) {
        return current
      }
      return { ...current, q: nextQuery, page: 0 }
    })
  }

  function scheduleSearch(value: string) {
    if (searchTimer.current !== null) {
      window.clearTimeout(searchTimer.current)
    }
    searchTimer.current = window.setTimeout(() => {
      searchTimer.current = null
      commitSearch(value)
    }, SEARCH_DELAY_MS)
  }

  const hasActiveQuery = filters.q.length > 0 || filters.category.length > 0 || filters.brand.length > 0
  const readyPage = visibleResult?.status === 'ready' ? visibleResult.page : null
  const showPager = readyPage !== null && readyPage.totalPages > 1

  return (
    <section className="mx-auto max-w-7xl px-4 py-12 md:px-6 md:py-16">
      <h1 className="text-3xl font-semibold tracking-tight text-ink md:text-4xl">Sản phẩm</h1>
      <p className="mt-3 max-w-2xl text-sm leading-6 text-ash">
        Danh sách lấy từ catalog công khai. Giá đang bán là giá hiệu lực. Mục sắp xếp theo giá dùng giá gốc.
      </p>

      <div className="mt-8 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <label className="block text-sm text-ash">
          Tìm sản phẩm
          <input
            value={searchInput}
            onChange={(event) => {
              const value = event.target.value
              setSearchInput(value)
              scheduleSearch(value)
            }}
            onKeyDown={(event) => {
              if (event.key !== 'Enter') {
                return
              }
              event.preventDefault()
              if (searchTimer.current !== null) {
                window.clearTimeout(searchTimer.current)
                searchTimer.current = null
              }
              commitSearch(searchInput)
            }}
            className={`${controlClass} mt-2`}
            type="search"
            placeholder="Tên hoặc slug"
          />
        </label>
        <label className="block text-sm text-ash">
          Danh mục
          <select
            value={filters.category}
            onChange={(event) => {
              applyFilters({ ...filters, category: event.target.value, page: 0 })
            }}
            className={`${controlClass} mt-2`}
          >
            <option value="">Tất cả danh mục</option>
            {categories.map((item) => (
              <option key={item.id} value={item.slug}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-sm text-ash">
          Thương hiệu
          <select
            value={filters.brand}
            onChange={(event) => {
              applyFilters({ ...filters, brand: event.target.value, page: 0 })
            }}
            className={`${controlClass} mt-2`}
          >
            <option value="">Tất cả thương hiệu</option>
            {brands.map((item) => (
              <option key={item.id} value={item.slug}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-sm text-ash">
          Sắp xếp
          <select
            value={filters.sort}
            onChange={(event) => {
              applyFilters({ ...filters, sort: event.target.value as ProductSort, page: 0 })
            }}
            className={`${controlClass} mt-2`}
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <span className="mt-2 block text-xs leading-5 text-ash">
            Giá gốc tăng hoặc giảm dần không sắp theo giá đang bán.
          </span>
        </label>
      </div>

      {filtersError ? (
        <div className="mt-4 flex flex-wrap items-center gap-3" role="alert">
          <p className="text-sm text-ash">{filtersError}</p>
          <button
            type="button"
            onClick={() => setFiltersReloadKey((value) => value + 1)}
            className="inline-flex min-h-11 items-center bg-neon px-4 text-sm font-semibold text-ink hover:bg-neon/80 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
          >
            Tải lại bộ lọc
          </button>
        </div>
      ) : null}

      <div className="mt-10" aria-live="polite">
        {visibleResult === null ? <p className="text-sm text-ash">Đang tải sản phẩm...</p> : null}
        {visibleResult?.status === 'error' ? (
          <div className="flex flex-wrap items-center gap-3" role="alert">
            <p className="text-sm text-ink">{visibleResult.message}</p>
            <button
              type="button"
              onClick={() => setReloadKey((value) => value + 1)}
              className="inline-flex min-h-11 items-center bg-neon px-4 text-sm font-semibold text-ink hover:bg-neon/80 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
            >
              Thử lại
            </button>
          </div>
        ) : null}
        {readyPage && readyPage.content.length === 0 ? (
          <p className="text-sm leading-6 text-ash">
            {hasActiveQuery
              ? 'Không có sản phẩm khớp bộ lọc hiện tại.'
              : 'Chưa có sản phẩm công khai để hiển thị.'}
          </p>
        ) : null}
        {readyPage && readyPage.content.length > 0 ? (
          <div className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 xl:grid-cols-4">
            {readyPage.content.map((product) => (
              <CatalogProductCard key={product.id} product={product} />
            ))}
          </div>
        ) : null}
      </div>

      {showPager && readyPage ? (
        <nav className="mt-10 flex flex-wrap items-center gap-3" aria-label="Phân trang">
          <button
            type="button"
            disabled={readyPage.first}
            onClick={() => applyFilters({ ...filters, page: Math.max(0, readyPage.page - 1) })}
            className="inline-flex min-h-11 items-center border border-ink px-4 text-sm font-semibold text-ink disabled:cursor-not-allowed disabled:border-line disabled:text-ash focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
          >
            Trang trước
          </button>
          <p className="text-sm text-ash">
            Trang {readyPage.page + 1} / {readyPage.totalPages}
          </p>
          <button
            type="button"
            disabled={readyPage.last}
            onClick={() => applyFilters({ ...filters, page: readyPage.page + 1 })}
            className="inline-flex min-h-11 items-center border border-ink px-4 text-sm font-semibold text-ink disabled:cursor-not-allowed disabled:border-line disabled:text-ash focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
          >
            Trang sau
          </button>
        </nav>
      ) : null}
    </section>
  )
}

export default ShopPage
