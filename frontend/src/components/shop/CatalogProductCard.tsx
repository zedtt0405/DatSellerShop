import { useState } from 'react'
import { Link } from 'react-router'
import type { ProductCardResponse } from '../../api/catalog.ts'
import { formatVnd, hasValidSale, isDisplayableImageUrl } from './productDisplay.ts'

type CatalogProductCardProps = {
  product: ProductCardResponse
}

function stockLabel(inStock: boolean | null): string {
  if (inStock === true) {
    return 'Còn hàng'
  }
  if (inStock === false) {
    return 'Hết hàng'
  }
  return 'Chưa rõ tồn kho'
}

function ImagePlaceholder() {
  return (
    <div className="flex aspect-[3/4] w-full items-center justify-center bg-line px-3 text-center text-sm text-ash">
      Chưa có ảnh
    </div>
  )
}

function CatalogProductCard({ product }: CatalogProductCardProps) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const imageUrl = isDisplayableImageUrl(product.primaryImageUrl) ? product.primaryImageUrl : null
  const showImage = imageUrl !== null && failedUrl !== imageUrl
  const onSale = hasValidSale(product.price, product.salePrice)

  return (
    <article>
      <Link
        to={`/shop/${encodeURIComponent(product.slug)}`}
        className="block focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
      >
        {showImage ? (
          <div className="overflow-hidden bg-line">
            <img
              src={imageUrl}
              alt={product.name}
              loading="lazy"
              onError={() => setFailedUrl(imageUrl)}
              className="aspect-[3/4] w-full object-cover"
            />
          </div>
        ) : (
          <ImagePlaceholder />
        )}
        <h2 className="mt-3 text-sm font-medium tracking-wide text-ink">{product.name}</h2>
        <p className="mt-1 text-sm font-semibold text-ink">{formatVnd(product.effectivePrice)}</p>
        {onSale ? <p className="text-xs text-ash line-through">{formatVnd(product.price)}</p> : null}
        <p className="mt-1 text-xs text-ash">{stockLabel(product.inStock)}</p>
      </Link>
    </article>
  )
}

export default CatalogProductCard
