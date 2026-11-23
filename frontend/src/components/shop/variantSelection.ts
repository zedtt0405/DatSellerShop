import type { ProductVariantResponse } from '../../api/catalog.ts'
import { isHexColor } from './productDisplay.ts'

export type VariantChoice = {
  id: number | null
  label: string
  swatch: string | null
}

export function variantInStock(variant: ProductVariantResponse): boolean {
  return typeof variant.stockQuantity === 'number' && variant.stockQuantity > 0
}

export function activeVariants(variants: ProductVariantResponse[]): ProductVariantResponse[] {
  return variants.filter((variant) => variant.active !== false)
}

export function pickDefaultVariant(variants: ProductVariantResponse[]): ProductVariantResponse | null {
  return variants.find(variantInStock) ?? variants[0] ?? null
}

export function showColorPicker(variants: ProductVariantResponse[]): boolean {
  return variants.some((variant) => variant.color != null)
}

export function showSizePicker(variants: ProductVariantResponse[]): boolean {
  return variants.some((variant) => variant.size != null)
}

function colorIdOf(variant: ProductVariantResponse): number | null {
  return variant.color?.id ?? null
}

function sizeIdOf(variant: ProductVariantResponse): number | null {
  return variant.size?.id ?? null
}

function preferInStock(variants: ProductVariantResponse[]): ProductVariantResponse | null {
  return variants.find(variantInStock) ?? variants[0] ?? null
}

export function resolveSelectedVariant(
  variants: ProductVariantResponse[],
  slug: string,
  selection: { slug: string; variantId: number } | null,
): ProductVariantResponse | null {
  if (selection?.slug === slug) {
    const chosen = variants.find((variant) => variant.id === selection.variantId)
    if (chosen) {
      return chosen
    }
  }
  return pickDefaultVariant(variants)
}

export function selectVariantByColor(
  variants: ProductVariantResponse[],
  current: ProductVariantResponse | null,
  colorId: number | null,
): ProductVariantResponse | null {
  const sameColor = variants.filter((variant) => colorIdOf(variant) === colorId)
  if (showSizePicker(variants) && current) {
    const keptSize = sameColor.filter((variant) => sizeIdOf(variant) === sizeIdOf(current))
    const kept = preferInStock(keptSize)
    if (kept) {
      return kept
    }
  }
  return preferInStock(sameColor)
}

export function selectVariantBySize(
  variants: ProductVariantResponse[],
  current: ProductVariantResponse | null,
  sizeId: number | null,
): ProductVariantResponse | null {
  const colorFilter = showColorPicker(variants)
  const colorId = current ? colorIdOf(current) : null
  const matches = variants.filter((variant) => {
    if (colorFilter && colorIdOf(variant) !== colorId) {
      return false
    }
    return sizeIdOf(variant) === sizeId
  })
  return preferInStock(matches)
}

export function colorChoices(variants: ProductVariantResponse[]): VariantChoice[] {
  if (!showColorPicker(variants)) {
    return []
  }
  const choices: VariantChoice[] = []
  const seen = new Set<string>()
  for (const variant of variants) {
    const id = colorIdOf(variant)
    const key = String(id)
    if (seen.has(key)) {
      continue
    }
    seen.add(key)
    const hex = variant.color?.hexCode ?? null
    choices.push({
      id,
      label: variant.color?.name ?? 'Không ghi màu',
      swatch: isHexColor(hex) ? hex : null,
    })
  }
  return choices
}

export function sizeChoices(
  variants: ProductVariantResponse[],
  selected: ProductVariantResponse | null,
): VariantChoice[] {
  if (!showSizePicker(variants)) {
    return []
  }
  const filterColor = showColorPicker(variants)
  const colorId = selected ? colorIdOf(selected) : null
  const source = filterColor ? variants.filter((variant) => colorIdOf(variant) === colorId) : variants
  const choices: VariantChoice[] = []
  const seen = new Set<string>()
  for (const variant of source) {
    const id = sizeIdOf(variant)
    const key = String(id)
    if (seen.has(key)) {
      continue
    }
    seen.add(key)
    choices.push({
      id,
      label: variant.size?.name ?? 'Không ghi size',
      swatch: null,
    })
  }
  return choices
}

export function choiceHasStock(
  variants: ProductVariantResponse[],
  selected: ProductVariantResponse | null,
  kind: 'color' | 'size',
  id: number | null,
): boolean {
  return variants.some((variant) => {
    if (kind === 'color' && colorIdOf(variant) !== id) {
      return false
    }
    if (kind === 'size') {
      if (showColorPicker(variants) && colorIdOf(variant) !== (selected ? colorIdOf(selected) : null)) {
        return false
      }
      if (sizeIdOf(variant) !== id) {
        return false
      }
    }
    return variantInStock(variant)
  })
}
