import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import {
  CartApiError,
  addCartItem,
  cartErrorMessage,
  clearCartItems,
  getCart,
  hasCustomerRole,
  removeCartItem,
  updateCartItem,
  type CartResponse,
} from '../api/cart.ts'
import { useAuth } from '../auth/auth-context.ts'
import { CartContext, type CartContextValue } from './cart-context.ts'

function withoutItem(current: CartResponse, itemId: number): CartResponse {
  const items = current.items.filter((item) => item.id !== itemId)
  return summarize(current.id, items)
}

function emptyCart(id: number | null): CartResponse {
  return { id, items: [], itemCount: 0, totalQuantity: 0, subtotal: 0 }
}

function summarize(id: number | null, items: CartResponse['items']): CartResponse {
  const subtotal = items.reduce((sum, item) => sum + item.lineSubtotal, 0)
  return {
    id,
    items,
    itemCount: items.length,
    totalQuantity: items.reduce((sum, item) => sum + item.quantity, 0),
    subtotal: Math.round(subtotal * 100) / 100,
  }
}

function CartProvider({ children }: { children: ReactNode }) {
  const { user, isInitializing, getAccessToken, expireSession } = useAuth()
  const customerId = !isInitializing && user && hasCustomerRole(user.roles) ? user.id : null
  const [cart, setCart] = useState<CartResponse | null>(null)
  const [ownerId, setOwnerId] = useState<number | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const [isMutating, setMutating] = useState(false)
  const [errorState, setErrorState] = useState<{ ownerId: number; message: string } | null>(null)
  const requestSeq = useRef(0)

  const error = errorState && errorState.ownerId === customerId ? errorState.message : null
  const visibleCart = customerId != null && ownerId === customerId ? cart : null
  const isLoading = customerId != null && (refreshing || (ownerId !== customerId && error == null))

  const stillCurrent = useCallback(
    (token: string, seq: number) => seq === requestSeq.current && getAccessToken() === token,
    [getAccessToken],
  )

  const rememberError = useCallback((owner: number, message: string) => {
    setErrorState({ ownerId: owner, message })
  }, [])

  const acceptCart = useCallback(
    (next: CartResponse, token: string, seq: number, owner: number) => {
      if (!stillCurrent(token, seq)) {
        return false
      }
      setCart(next)
      setOwnerId(owner)
      setErrorState((current) => (current?.ownerId === owner ? null : current))
      return true
    },
    [stillCurrent],
  )

  const fail = useCallback(
    (caught: unknown, token: string, seq: number, owner: number, fallback: string) => {
      if (caught instanceof Error && caught.name === 'AbortError') {
        return 'aborted' as const
      }
      if (!stillCurrent(token, seq)) {
        return 'stale' as const
      }
      if (caught instanceof CartApiError && caught.status === 401) {
        expireSession(token)
        return 'unauthorized' as const
      }
      rememberError(owner, cartErrorMessage(caught, fallback))
      return 'failed' as const
    },
    [expireSession, rememberError, stillCurrent],
  )

  const load = useCallback(
    async (token: string, owner: number, seq: number, signal?: AbortSignal) => {
      setRefreshing(true)
      try {
        const next = await getCart(token, signal)
        acceptCart(next, token, seq, owner)
      } catch (caught) {
        fail(caught, token, seq, owner, 'Không tải được giỏ hàng.')
      } finally {
        if (seq === requestSeq.current) {
          setRefreshing(false)
        }
      }
    },
    [acceptCart, fail],
  )

  useEffect(() => {
    if (customerId == null) {
      return
    }
    const token = getAccessToken()
    if (!token) {
      return
    }
    const owner = customerId
    const seq = ++requestSeq.current
    const controller = new AbortController()
    let active = true
    queueMicrotask(() => {
      if (!active) {
        return
      }
      void load(token, owner, seq, controller.signal)
    })
    return () => {
      active = false
      controller.abort()
    }
  }, [customerId, getAccessToken, load])

  const reload = useCallback(async () => {
    const token = getAccessToken()
    const owner = customerId
    if (!token || owner == null) {
      return
    }
    setErrorState((current) => (current?.ownerId === owner ? null : current))
    const seq = ++requestSeq.current
    await load(token, owner, seq)
  }, [customerId, getAccessToken, load])

  const addItem = useCallback(
    async (variantId: number, quantity: number) => {
      const token = getAccessToken()
      const owner = customerId
      if (!token || owner == null) {
        return false
      }
      const seq = ++requestSeq.current
      setMutating(true)
      try {
        const next = await addCartItem(token, variantId, quantity)
        return acceptCart(next, token, seq, owner)
      } catch (caught) {
        const result = fail(caught, token, seq, owner, 'Không thêm được sản phẩm vào giỏ.')
        if (result === 'failed') {
          throw caught
        }
        return false
      } finally {
        if (seq === requestSeq.current) {
          setMutating(false)
        }
      }
    },
    [acceptCart, customerId, fail, getAccessToken],
  )

  const updateItem = useCallback(
    async (itemId: number, quantity: number) => {
      const token = getAccessToken()
      const owner = customerId
      if (!token || owner == null) {
        return false
      }
      const seq = ++requestSeq.current
      setMutating(true)
      try {
        const next = await updateCartItem(token, itemId, quantity)
        return acceptCart(next, token, seq, owner)
      } catch (caught) {
        const result = fail(caught, token, seq, owner, 'Không cập nhật được số lượng.')
        if (result === 'failed') {
          throw caught
        }
        return false
      } finally {
        if (seq === requestSeq.current) {
          setMutating(false)
        }
      }
    },
    [acceptCart, customerId, fail, getAccessToken],
  )

  const removeItem = useCallback(
    async (itemId: number) => {
      const token = getAccessToken()
      const owner = customerId
      if (!token || owner == null) {
        return false
      }
      const seq = ++requestSeq.current
      setMutating(true)
      try {
        await removeCartItem(token, itemId)
        if (!stillCurrent(token, seq)) {
          return false
        }
        try {
          const next = await getCart(token)
          return acceptCart(next, token, seq, owner)
        } catch (caught) {
          if (!stillCurrent(token, seq)) {
            return false
          }
          if (caught instanceof CartApiError && caught.status === 401) {
            expireSession(token)
            return false
          }
          setCart((current) => (current ? withoutItem(current, itemId) : current))
          setOwnerId(owner)
          rememberError(owner, 'Đã xóa dòng trên máy chủ, nhưng chưa tải lại được giỏ hàng.')
          return true
        }
      } catch (caught) {
        const result = fail(caught, token, seq, owner, 'Không xóa được dòng giỏ hàng.')
        if (result === 'failed') {
          throw caught
        }
        return false
      } finally {
        if (seq === requestSeq.current) {
          setMutating(false)
        }
      }
    },
    [acceptCart, customerId, expireSession, fail, getAccessToken, rememberError, stillCurrent],
  )

  const clearItems = useCallback(async () => {
    const token = getAccessToken()
    const owner = customerId
    if (!token || owner == null) {
      return false
    }
    const seq = ++requestSeq.current
    setMutating(true)
    try {
      await clearCartItems(token)
      if (!stillCurrent(token, seq)) {
        return false
      }
      try {
        const next = await getCart(token)
        return acceptCart(next, token, seq, owner)
      } catch (caught) {
        if (!stillCurrent(token, seq)) {
          return false
        }
        if (caught instanceof CartApiError && caught.status === 401) {
          expireSession(token)
          return false
        }
        setCart((current) => emptyCart(current?.id ?? null))
        setOwnerId(owner)
        rememberError(owner, 'Đã làm trống giỏ trên máy chủ, nhưng chưa tải lại được giỏ hàng.')
        return true
      }
    } catch (caught) {
      const result = fail(caught, token, seq, owner, 'Không làm trống được giỏ hàng.')
      if (result === 'failed') {
        throw caught
      }
      return false
    } finally {
      if (seq === requestSeq.current) {
        setMutating(false)
      }
    }
  }, [acceptCart, customerId, expireSession, fail, getAccessToken, rememberError, stillCurrent])

  const value = useMemo<CartContextValue>(
    () => ({
      cart: visibleCart,
      isLoading,
      isMutating,
      error,
      reload,
      addItem,
      updateItem,
      removeItem,
      clearItems,
    }),
    [visibleCart, isLoading, isMutating, error, reload, addItem, updateItem, removeItem, clearItems],
  )

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export default CartProvider
