import { createContext, useContext } from 'react'
import type { CartResponse } from '../api/cart.ts'

export type CartContextValue = {
  cart: CartResponse | null
  isLoading: boolean
  isMutating: boolean
  error: string | null
  reload: () => Promise<void>
  addItem: (variantId: number, quantity: number) => Promise<boolean>
  updateItem: (itemId: number, quantity: number) => Promise<boolean>
  removeItem: (itemId: number) => Promise<boolean>
  clearItems: () => Promise<boolean>
}

export const CartContext = createContext<CartContextValue | null>(null)

export function useCart(): CartContextValue {
  const value = useContext(CartContext)
  if (!value) {
    throw new Error('useCart phải nằm trong CartProvider.')
  }
  return value
}
