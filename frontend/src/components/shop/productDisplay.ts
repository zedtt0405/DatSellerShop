const vndFormat = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
})

export function formatVnd(amount: number): string {
  if (!Number.isFinite(amount)) {
    return 'Không có giá'
  }
  return vndFormat.format(amount)
}

export function hasValidSale(price: number, salePrice: number | null): boolean {
  return salePrice != null && Number.isFinite(salePrice) && Number.isFinite(price) && salePrice < price
}

export function isDisplayableImageUrl(url: string | null | undefined): url is string {
  if (!url || url.trim().length === 0) {
    return false
  }
  try {
    const parsed = new URL(url)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:'
  } catch {
    return false
  }
}

export function isHexColor(value: string | null | undefined): value is string {
  return typeof value === 'string' && /^#[0-9A-Fa-f]{6}$/.test(value)
}
