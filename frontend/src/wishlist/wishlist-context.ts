import { createContext, useContext } from 'react'
import type { WishlistResponse } from '../api/wishlist.ts'

export type WishlistPhase = 'idle' | 'loading' | 'ready' | 'unknown'

export type WishlistContextValue = {
  wishlist: WishlistResponse | null
  phase: WishlistPhase
  isMutating: boolean
  error: string | null
  reload: () => Promise<void>
  wishlisted: (productId: number) => boolean | null
  isPending: (productId: number) => boolean
  addProduct: (productId: number) => Promise<boolean>
  removeProduct: (productId: number) => Promise<boolean>
}

export const WishlistContext = createContext<WishlistContextValue | null>(null)

export function useWishlist(): WishlistContextValue {
  const value = useContext(WishlistContext)
  if (!value) {
    throw new Error('useWishlist phải nằm trong WishlistProvider.')
  }
  return value
}
