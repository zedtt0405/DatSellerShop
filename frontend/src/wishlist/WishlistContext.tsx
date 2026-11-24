import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { hasCustomerRole } from '../api/cart.ts'
import {
  WishlistApiError,
  addWishlistItem,
  getWishlist,
  removeWishlistItem,
  wishlistErrorMessage,
  type WishlistResponse,
} from '../api/wishlist.ts'
import { useAuth } from '../auth/auth-context.ts'
import { WishlistContext, type WishlistContextValue, type WishlistPhase } from './wishlist-context.ts'

function withoutProduct(current: WishlistResponse, productId: number): WishlistResponse {
  const items = current.items.filter((item) => item.productId !== productId)
  return { id: current.id, items, itemCount: items.length }
}

function WishlistProvider({ children }: { children: ReactNode }) {
  const { user, isInitializing, getAccessToken, expireSession } = useAuth()
  const customerId = !isInitializing && user && hasCustomerRole(user.roles) ? user.id : null
  const [sessionOwner, setSessionOwner] = useState<number | null>(customerId)
  const [wishlist, setWishlist] = useState<WishlistResponse | null>(null)
  const [phase, setPhase] = useState<WishlistPhase>(customerId == null ? 'idle' : 'loading')
  const [error, setError] = useState<string | null>(null)
  const [pendingIds, setPendingIds] = useState<number[]>([])
  const [isMutating, setMutating] = useState(false)
  const requestSeq = useRef(0)
  const pendingRef = useRef(new Set<number>())
  const hasList = useRef(false)
  const seenOwner = useRef(customerId)

  useEffect(() => {
    if (seenOwner.current === customerId) {
      return
    }
    seenOwner.current = customerId
    pendingRef.current = new Set()
    hasList.current = false
    setSessionOwner(customerId)
    setWishlist(null)
    setPhase(customerId == null ? 'idle' : 'loading')
    setError(null)
    setPendingIds([])
  }, [customerId])

  const visiblePhase: WishlistPhase = sessionOwner === customerId ? phase : customerId == null ? 'idle' : 'loading'
  const visibleWishlist = visiblePhase === 'ready' ? wishlist : null
  const visibleError = sessionOwner === customerId ? error : null

  const stillCurrent = useCallback(
    (token: string, seq: number) => seq === requestSeq.current && getAccessToken() === token,
    [getAccessToken],
  )

  const acceptWishlist = useCallback(
    (next: WishlistResponse, token: string, seq: number) => {
      if (!stillCurrent(token, seq)) {
        return false
      }
      hasList.current = true
      setWishlist(next)
      setPhase('ready')
      setError(null)
      return true
    },
    [stillCurrent],
  )

  const fail = useCallback(
    (caught: unknown, token: string, seq: number, fallback: string) => {
      if (caught instanceof Error && caught.name === 'AbortError') {
        return 'aborted' as const
      }
      if (!stillCurrent(token, seq)) {
        return 'stale' as const
      }
      if (caught instanceof WishlistApiError && caught.status === 401) {
        expireSession(token)
        return 'unauthorized' as const
      }
      setError(wishlistErrorMessage(caught, fallback))
      return 'failed' as const
    },
    [expireSession, stillCurrent],
  )

  const load = useCallback(
    async (token: string, seq: number, signal?: AbortSignal) => {
      if (!hasList.current) {
        setPhase('loading')
      }
      try {
        const next = await getWishlist(token, signal)
        acceptWishlist(next, token, seq)
      } catch (caught) {
        const result = fail(caught, token, seq, 'Không tải được danh sách yêu thích.')
        if (result === 'failed' && !hasList.current) {
          setWishlist(null)
          setPhase('unknown')
        }
      }
    },
    [acceptWishlist, fail],
  )

  useEffect(() => {
    if (customerId == null) {
      return
    }
    const token = getAccessToken()
    if (!token) {
      return
    }
    const seq = ++requestSeq.current
    const controller = new AbortController()
    let active = true
    queueMicrotask(() => {
      if (!active) {
        return
      }
      void load(token, seq, controller.signal)
    })
    return () => {
      active = false
      controller.abort()
    }
  }, [customerId, getAccessToken, load])

  const beginPending = useCallback((productId: number) => {
    if (pendingRef.current.has(productId)) {
      return false
    }
    pendingRef.current.add(productId)
    setPendingIds([...pendingRef.current])
    return true
  }, [])

  const endPending = useCallback((productId: number) => {
    pendingRef.current.delete(productId)
    setPendingIds([...pendingRef.current])
  }, [])

  const reload = useCallback(async () => {
    const token = getAccessToken()
    if (!token || customerId == null) {
      return
    }
    setError(null)
    const seq = ++requestSeq.current
    await load(token, seq)
  }, [customerId, getAccessToken, load])

  const addProduct = useCallback(
    async (productId: number) => {
      const token = getAccessToken()
      if (!token || customerId == null || !beginPending(productId)) {
        return false
      }
      const seq = ++requestSeq.current
      setMutating(true)
      try {
        const next = await addWishlistItem(token, productId)
        return acceptWishlist(next, token, seq)
      } catch (caught) {
        const result = fail(caught, token, seq, 'Không thêm được vào yêu thích.')
        if (result === 'failed') {
          throw caught
        }
        return false
      } finally {
        endPending(productId)
        if (seq === requestSeq.current) {
          setMutating(false)
        }
      }
    },
    [acceptWishlist, beginPending, customerId, endPending, fail, getAccessToken],
  )

  const removeProduct = useCallback(
    async (productId: number) => {
      const token = getAccessToken()
      if (!token || customerId == null || !beginPending(productId)) {
        return false
      }
      const seq = ++requestSeq.current
      setMutating(true)
      try {
        await removeWishlistItem(token, productId)
        if (!stillCurrent(token, seq)) {
          return false
        }
        setWishlist((current) => {
          if (!current) {
            return current
          }
          hasList.current = true
          return withoutProduct(current, productId)
        })
        setPhase('ready')
        try {
          const next = await getWishlist(token)
          return acceptWishlist(next, token, seq)
        } catch (caught) {
          if (!stillCurrent(token, seq)) {
            return false
          }
          if (caught instanceof WishlistApiError && caught.status === 401) {
            expireSession(token)
            return false
          }
          setError('Đã bỏ yêu thích trên máy chủ, nhưng chưa tải lại được danh sách.')
          return true
        }
      } catch (caught) {
        const result = fail(caught, token, seq, 'Không bỏ được yêu thích.')
        if (result === 'failed') {
          throw caught
        }
        return false
      } finally {
        endPending(productId)
        if (seq === requestSeq.current) {
          setMutating(false)
        }
      }
    },
    [acceptWishlist, beginPending, customerId, endPending, expireSession, fail, getAccessToken, stillCurrent],
  )

  const wishlisted = useCallback(
    (productId: number) => {
      if (visiblePhase !== 'ready' || visibleWishlist == null) {
        return null
      }
      return visibleWishlist.items.some((item) => item.productId === productId)
    },
    [visiblePhase, visibleWishlist],
  )

  const isPending = useCallback((productId: number) => pendingIds.includes(productId), [pendingIds])

  const value = useMemo<WishlistContextValue>(
    () => ({
      wishlist: visibleWishlist,
      phase: visiblePhase,
      isMutating,
      error: visibleError,
      reload,
      wishlisted,
      isPending,
      addProduct,
      removeProduct,
    }),
    [visibleWishlist, visiblePhase, isMutating, visibleError, reload, wishlisted, isPending, addProduct, removeProduct],
  )

  return <WishlistContext.Provider value={value}>{children}</WishlistContext.Provider>
}

export default WishlistProvider
