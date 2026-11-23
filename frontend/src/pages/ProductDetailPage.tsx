import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import {
  CatalogApiError,
  getProduct,
  type ProductDetailResponse,
  type ProductImageResponse,
} from '../api/catalog.ts'
import { formatVnd, hasValidSale, isDisplayableImageUrl } from '../components/shop/productDisplay.ts'
import {
  activeVariants,
  choiceHasStock,
  colorChoices,
  resolveSelectedVariant,
  selectVariantByColor,
  selectVariantBySize,
  showColorPicker,
  showSizePicker,
  sizeChoices,
  variantInStock,
} from '../components/shop/variantSelection.ts'

type DetailResult =
  | { key: string; status: 'ready'; detail: ProductDetailResponse }
  | { key: string; status: 'missing' }
  | { key: string; status: 'error'; message: string }

const controlFocus =
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'

function sortImages(images: ProductImageResponse[]): ProductImageResponse[] {
  return [...images].sort((left, right) => {
    const order = (left.sortOrder ?? 0) - (right.sortOrder ?? 0)
    if (order !== 0) {
      return order
    }
    return left.id - right.id
  })
}

function preferredImage(images: ProductImageResponse[]): ProductImageResponse | null {
  return images.find((image) => image.isPrimary === true) ?? images[0] ?? null
}

function descriptionText(value: string | null): string {
  if (value == null || value.trim().length === 0) {
    return 'Chưa có mô tả.'
  }
  return value
}

function errorMessage(error: unknown): string {
  if (error instanceof CatalogApiError) {
    return error.message
  }
  return 'Không tải được sản phẩm.'
}

function ImagePlaceholder() {
  return (
    <div className="flex aspect-[3/4] w-full items-center justify-center bg-line px-3 text-center text-sm text-ash">
      Chưa có ảnh
    </div>
  )
}

function ProductDetailPage() {
  const slug = useParams().slug ?? ''
  const [reloadKey, setReloadKey] = useState(0)
  const [result, setResult] = useState<DetailResult | null>(null)
  const [selection, setSelection] = useState<{ slug: string; variantId: number } | null>(null)
  const [imageSelection, setImageSelection] = useState<{ slug: string; imageId: number } | null>(null)
  const [failedImageIds, setFailedImageIds] = useState<number[]>([])
  const requestKey = `${slug}\n${reloadKey}`
  const visible = result?.key === requestKey ? result : null

  useEffect(() => {
    if (slug.length === 0) {
      return
    }
    const controller = new AbortController()
    const key = requestKey
    getProduct(slug, controller.signal)
      .then((detail) => {
        if (controller.signal.aborted) {
          return
        }
        setResult({ key, status: 'ready', detail })
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return
        }
        if (error instanceof CatalogApiError && error.status === 404) {
          setResult({ key, status: 'missing' })
          return
        }
        setResult({ key, status: 'error', message: errorMessage(error) })
      })
    return () => controller.abort()
  }, [slug, requestKey])

  if (slug.length === 0 || visible?.status === 'missing') {
    return (
      <section className="mx-auto max-w-5xl px-4 py-12 md:px-6 md:py-16">
        <h1 className="text-3xl font-semibold tracking-tight text-ink md:text-4xl">Không tìm thấy sản phẩm</h1>
        <p className="mt-4 max-w-xl text-base leading-7 text-ash">
          Sản phẩm này không có trong catalog công khai.
        </p>
        <Link
          to="/shop"
          className={`mt-8 inline-flex min-h-11 items-center bg-neon px-6 text-sm font-semibold text-ink hover:bg-neon/80 ${controlFocus}`}
        >
          Quay lại sản phẩm
        </Link>
      </section>
    )
  }

  if (visible === null) {
    return (
      <section className="mx-auto max-w-7xl px-4 py-12 md:px-6 md:py-16">
        <p className="text-sm text-ash">Đang tải sản phẩm...</p>
      </section>
    )
  }

  if (visible.status === 'error') {
    return (
      <section className="mx-auto max-w-7xl px-4 py-12 md:px-6 md:py-16">
        <div className="flex flex-wrap items-center gap-3" role="alert">
          <p className="text-sm text-ink">{visible.message}</p>
          <button
            type="button"
            onClick={() => setReloadKey((value) => value + 1)}
            className={`inline-flex min-h-11 items-center bg-neon px-4 text-sm font-semibold text-ink hover:bg-neon/80 ${controlFocus}`}
          >
            Thử lại
          </button>
        </div>
        <Link to="/shop" className={`mt-6 inline-flex min-h-11 items-center text-sm font-semibold text-ink underline ${controlFocus}`}>
          Quay lại sản phẩm
        </Link>
      </section>
    )
  }

  const product = visible.detail.product
  const variants = activeVariants(visible.detail.variants)
  const images = sortImages(visible.detail.images)
  const selectedVariant = resolveSelectedVariant(variants, slug, selection)
  const selectedImage =
    (imageSelection?.slug === slug ? images.find((image) => image.id === imageSelection.imageId) : null) ??
    preferredImage(images)
  const colors = colorChoices(variants)
  const sizes = sizeChoices(variants, selectedVariant)
  const onSale = hasValidSale(product.price, product.salePrice)
  const inStock = selectedVariant != null && variantInStock(selectedVariant)
  const mainUrl = isDisplayableImageUrl(selectedImage?.imageUrl) ? selectedImage.imageUrl : null
  const mainFailed = selectedImage != null && failedImageIds.includes(selectedImage.id)

  function chooseColor(colorId: number | null) {
    const next = selectVariantByColor(variants, selectedVariant, colorId)
    if (next) {
      setSelection({ slug, variantId: next.id })
    }
  }

  function chooseSize(sizeId: number | null) {
    const next = selectVariantBySize(variants, selectedVariant, sizeId)
    if (next) {
      setSelection({ slug, variantId: next.id })
    }
  }

  return (
    <section className="mx-auto max-w-7xl px-4 py-12 md:px-6 md:py-16">
      <nav aria-label="Đường dẫn" className="text-sm text-ash">
        <ol className="flex flex-wrap items-center gap-2">
          <li>
            <Link to="/" className={`text-ink ${controlFocus}`}>
              Trang chủ
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          <li>
            <Link to="/shop" className={`text-ink ${controlFocus}`}>
              Sản phẩm
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          <li>{product.category.name}</li>
          <li aria-hidden="true">/</li>
          <li className="text-ink">{product.name}</li>
        </ol>
      </nav>

      <div className="mt-8 grid gap-10 md:grid-cols-2">
        <div>
          {selectedImage && mainUrl && !mainFailed ? (
            <div className="overflow-hidden bg-line">
              <img
                src={mainUrl}
                alt={selectedImage.altText?.trim() || product.name}
                onError={() =>
                  setFailedImageIds((current) =>
                    current.includes(selectedImage.id) ? current : [...current, selectedImage.id],
                  )
                }
                className="aspect-[3/4] w-full object-cover"
              />
            </div>
          ) : (
            <ImagePlaceholder />
          )}
          {images.length > 1 ? (
            <div className="mt-4 grid grid-cols-4 gap-3">
              {images.map((image) => {
                const thumbUrl = isDisplayableImageUrl(image.imageUrl) ? image.imageUrl : null
                const thumbFailed = failedImageIds.includes(image.id)
                const selected = selectedImage?.id === image.id
                return (
                  <button
                    key={image.id}
                    type="button"
                    aria-pressed={selected}
                    aria-label={image.altText?.trim() || product.name}
                    onClick={() => setImageSelection({ slug, imageId: image.id })}
                    className={`overflow-hidden border-2 bg-line ${selected ? 'border-neon' : 'border-transparent'} ${controlFocus}`}
                  >
                    {thumbUrl && !thumbFailed ? (
                      <img
                        src={thumbUrl}
                        alt=""
                        loading="lazy"
                        onError={() => setFailedImageIds((current) => (current.includes(image.id) ? current : [...current, image.id]))}
                        className="aspect-[3/4] w-full object-cover"
                      />
                    ) : (
                      <span className="flex aspect-[3/4] items-center justify-center px-1 text-center text-[11px] text-ash">
                        Chưa có ảnh
                      </span>
                    )}
                  </button>
                )
              })}
            </div>
          ) : null}
        </div>

        <div className="min-w-0">
          <p className="text-sm text-ash">{product.brand.name}</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight text-ink md:text-4xl">{product.name}</h1>
          <p className="mt-2 text-sm text-ash">{product.category.name}</p>
          <p className="mt-6 text-lg font-semibold text-ink">{formatVnd(product.effectivePrice)}</p>
          {onSale ? <p className="text-sm text-ash line-through">{formatVnd(product.price)}</p> : null}
          <p className="mt-6 max-w-xl text-sm leading-6 whitespace-pre-line text-ash">{descriptionText(product.description)}</p>

          {showColorPicker(variants) ? (
            <div className="mt-8">
              <p id="product-color-label" className="text-sm text-ash">
                Màu
              </p>
              <div className="mt-2 flex flex-wrap gap-2" role="group" aria-labelledby="product-color-label">
                {colors.map((choice) => {
                  const selected = (selectedVariant?.color?.id ?? null) === choice.id
                  const available = choiceHasStock(variants, selectedVariant, 'color', choice.id)
                  return (
                    <button
                      key={String(choice.id)}
                      type="button"
                      aria-pressed={selected}
                      onClick={() => chooseColor(choice.id)}
                      className={`inline-flex min-h-11 items-center gap-2 border px-3 text-sm ${
                        selected
                          ? 'border-neon bg-neon font-semibold text-ink'
                          : available
                            ? 'border-ink text-ink'
                            : 'border-line text-ash'
                      } ${controlFocus}`}
                    >
                      {choice.swatch ? (
                        <span className="h-4 w-4 border border-line" style={{ backgroundColor: choice.swatch }} aria-hidden="true" />
                      ) : null}
                      {choice.label}
                    </button>
                  )
                })}
              </div>
            </div>
          ) : null}

          {showSizePicker(variants) ? (
            <div className="mt-6">
              <p id="product-size-label" className="text-sm text-ash">
                Kích thước
              </p>
              <div className="mt-2 flex flex-wrap gap-2" role="group" aria-labelledby="product-size-label">
                {sizes.map((choice) => {
                  const selected = (selectedVariant?.size?.id ?? null) === choice.id
                  const available = choiceHasStock(variants, selectedVariant, 'size', choice.id)
                  return (
                    <button
                      key={String(choice.id)}
                      type="button"
                      aria-pressed={selected}
                      onClick={() => chooseSize(choice.id)}
                      className={`inline-flex min-h-11 items-center border px-3 text-sm ${
                        selected
                          ? 'border-neon bg-neon font-semibold text-ink'
                          : available
                            ? 'border-ink text-ink'
                            : 'border-line text-ash'
                      } ${controlFocus}`}
                    >
                      {choice.label}
                    </button>
                  )
                })}
              </div>
            </div>
          ) : null}

          <p className={`mt-6 inline-flex min-h-11 items-center px-3 text-sm font-semibold text-ink ${inStock ? 'border border-ink' : 'bg-sun'}`}>
            {inStock ? 'Còn hàng' : 'Hết hàng'}
          </p>
          <Link to="/shop" className={`mt-8 inline-flex min-h-11 items-center text-sm font-semibold text-ink underline ${controlFocus}`}>
            Quay lại sản phẩm
          </Link>
        </div>
      </div>
    </section>
  )
}

export default ProductDetailPage
