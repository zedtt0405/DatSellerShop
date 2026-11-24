import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { cartErrorMessage, hasCustomerRole, type CartItemResponse } from '../api/cart.ts'
import { useAuth } from '../auth/auth-context.ts'
import { useCart } from '../cart/cart-context.ts'
import { formatVnd, isDisplayableImageUrl } from '../components/shop/productDisplay.ts'

const focusRing = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'

function optionLabel(item: CartItemResponse): string | null {
  const parts = [item.color?.name, item.size?.name].filter((part): part is string => Boolean(part))
  return parts.length > 0 ? parts.join(' · ') : null
}

function QuantityEditor({
  item,
  disabled,
  onUpdate,
}: {
  item: CartItemResponse
  disabled: boolean
  onUpdate: (quantity: number) => Promise<void>
}) {
  const [draft, setDraft] = useState<string | null>(null)
  const [localError, setLocalError] = useState<string | null>(null)
  const max = item.stockQuantity
  const shown = draft ?? String(item.quantity)

  async function commit(next: number) {
    if (!Number.isInteger(next) || next < 1) {
      setLocalError('Số lượng tối thiểu là 1. Muốn bỏ sản phẩm, hãy xóa dòng.')
      return
    }
    if (max != null && next > max) {
      setLocalError(`Số lượng tối đa hiện có là ${max}.`)
      return
    }
    if (next === item.quantity) {
      setDraft(null)
      setLocalError(null)
      return
    }
    setLocalError(null)
    await onUpdate(next)
    setDraft(null)
  }

  return (
    <div>
      <div className="flex items-center gap-2">
        <button
          type="button"
          className={`inline-flex h-11 w-11 items-center justify-center border border-ink text-lg ${focusRing}`}
          aria-label={`Giảm số lượng ${item.product.name}`}
          disabled={disabled || item.quantity <= 1}
          onClick={() => void commit(item.quantity - 1)}
        >
          −
        </button>
        <label className="sr-only" htmlFor={`qty-${item.id}`}>
          Số lượng {item.product.name}
        </label>
        <input
          id={`qty-${item.id}`}
          inputMode="numeric"
          value={shown}
          disabled={disabled}
          onChange={(event) => setDraft(event.target.value.replace(/[^\d]/g, ''))}
          onBlur={() => {
            const parsed = Number(shown)
            if (shown.length === 0 || !Number.isInteger(parsed)) {
              setDraft(null)
              return
            }
            void commit(parsed)
          }}
          className={`h-11 w-16 border border-line text-center text-sm text-ink ${focusRing}`}
        />
        <button
          type="button"
          className={`inline-flex h-11 w-11 items-center justify-center border border-ink text-lg ${focusRing}`}
          aria-label={`Tăng số lượng ${item.product.name}`}
          disabled={disabled || (max != null && item.quantity >= max)}
          onClick={() => void commit(item.quantity + 1)}
        >
          +
        </button>
      </div>
      {localError ? (
        <p className="mt-2 text-sm text-ink" role="alert">
          {localError}
        </p>
      ) : null}
    </div>
  )
}

function CartLine({
  item,
  disabled,
  onUpdate,
  onRemove,
}: {
  item: CartItemResponse
  disabled: boolean
  onUpdate: (itemId: number, quantity: number) => Promise<void>
  onRemove: (itemId: number) => Promise<void>
}) {
  const [failedImage, setFailedImage] = useState(false)
  const imageUrl = isDisplayableImageUrl(item.product.primaryImageUrl) ? item.product.primaryImageUrl : null
  const options = optionLabel(item)

  return (
    <article className="grid gap-4 border-b border-line py-6 md:grid-cols-[5.5rem_minmax(0,1fr)_auto] md:items-start">
      {imageUrl && !failedImage ? (
        <img
          src={imageUrl}
          alt=""
          onError={() => setFailedImage(true)}
          className="h-28 w-[5.5rem] bg-line object-cover"
        />
      ) : (
        <div className="flex h-28 w-[5.5rem] items-center justify-center bg-line px-2 text-center text-xs text-ash">
          Chưa có ảnh
        </div>
      )}
      <div className="min-w-0">
        <h2 className="text-base font-semibold text-ink">
          <Link to={`/shop/${item.product.slug}`} className={`hover:underline ${focusRing}`}>
            {item.product.name}
          </Link>
        </h2>
        {options ? <p className="mt-1 text-sm text-ash">{options}</p> : null}
        <p className="mt-2 text-sm text-ink">{formatVnd(item.unitPrice)}</p>
        {item.available ? (
          <div className="mt-4">
            <QuantityEditor item={item} disabled={disabled} onUpdate={(quantity) => onUpdate(item.id, quantity)} />
          </div>
        ) : (
          <div className="mt-4">
            <p className="inline-flex min-h-11 items-center bg-sun px-3 text-sm font-semibold text-ink">
              Sản phẩm hiện không khả dụng
            </p>
            <p className="mt-2 text-sm text-ash">Số lượng đang giữ: {item.quantity}</p>
          </div>
        )}
      </div>
      <div className="flex items-start justify-between gap-4 md:flex-col md:items-end">
        <p className="text-sm font-semibold text-ink">{formatVnd(item.lineSubtotal)}</p>
        <button
          type="button"
          className={`inline-flex min-h-11 items-center text-sm font-semibold text-ink underline ${focusRing}`}
          disabled={disabled}
          onClick={() => void onRemove(item.id)}
        >
          Xóa
        </button>
      </div>
    </article>
  )
}

function CartPage() {
  const { user, isInitializing } = useAuth()
  const { cart, isLoading, isMutating, error, reload, updateItem, removeItem, clearItems } = useCart()
  const navigate = useNavigate()
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    if (isInitializing || user) {
      return
    }
    const timer = window.setTimeout(() => {
      if (window.location.pathname === '/cart') {
        navigate('/login', { replace: true, state: { from: '/cart' } })
      }
    }, 0)
    return () => window.clearTimeout(timer)
  }, [isInitializing, user, navigate])

  if (isInitializing) {
    return (
      <section className="mx-auto max-w-5xl px-4 py-12 md:px-6">
        <p className="text-sm text-ash">Đang kiểm tra phiên...</p>
      </section>
    )
  }

  if (!user) {
    return (
      <section className="mx-auto max-w-5xl px-4 py-12 md:px-6">
        <p className="text-sm text-ash">Đang chuyển đến đăng nhập...</p>
      </section>
    )
  }

  if (!hasCustomerRole(user.roles)) {
    return (
      <section className="mx-auto max-w-5xl px-4 py-12 md:px-6 md:py-16">
        <h1 className="text-3xl font-semibold tracking-tight text-ink">Giỏ hàng</h1>
        <p className="mt-4 max-w-xl text-base leading-7 text-ash">
          Giỏ hàng dành cho tài khoản khách hàng. Phiên {user.fullName} vẫn được giữ.
        </p>
        <Link to="/shop" className={`mt-8 inline-flex min-h-11 items-center bg-neon px-6 text-sm font-semibold text-ink ${focusRing}`}>
          Xem sản phẩm
        </Link>
      </section>
    )
  }

  async function commitQuantity(itemId: number, quantity: number) {
    setActionError(null)
    try {
      await updateItem(itemId, quantity)
    } catch (caught) {
      setActionError(cartErrorMessage(caught, 'Không cập nhật được số lượng.'))
    }
  }

  async function commitRemove(itemId: number) {
    setActionError(null)
    try {
      await removeItem(itemId)
    } catch (caught) {
      setActionError(cartErrorMessage(caught, 'Không xóa được dòng giỏ hàng.'))
    }
  }

  async function commitClear() {
    if (!window.confirm('Làm trống toàn bộ giỏ hàng?')) {
      return
    }
    setActionError(null)
    try {
      await clearItems()
    } catch (caught) {
      setActionError(cartErrorMessage(caught, 'Không làm trống được giỏ hàng.'))
    }
  }

  const message = actionError ?? error

  return (
    <section className="mx-auto max-w-5xl px-4 py-12 md:px-6 md:py-16">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-semibold tracking-tight text-ink md:text-4xl">Giỏ hàng</h1>
        <button
          type="button"
          className={`inline-flex min-h-11 items-center border border-ink px-4 text-sm font-semibold text-ink ${focusRing}`}
          onClick={() => {
            setActionError(null)
            void reload()
          }}
        >
          Tải lại
        </button>
      </div>
      {message ? (
        <div className="mt-6 flex flex-wrap items-center gap-3" role="alert">
          <p className="text-sm text-ink">{message}</p>
          <button
            type="button"
            onClick={() => {
              setActionError(null)
              void reload()
            }}
            className={`inline-flex min-h-11 items-center bg-neon px-4 text-sm font-semibold text-ink hover:bg-neon/80 ${focusRing}`}
          >
            Thử lại
          </button>
        </div>
      ) : null}

      {isLoading && !cart ? <p className="mt-8 text-sm text-ash">Đang tải giỏ hàng...</p> : null}

      {cart && cart.items.length === 0 ? (
        <div className="mt-8">
          <p className="text-base text-ink">Giỏ hàng của bạn đang trống.</p>
          <Link
            to="/shop"
            className={`mt-6 inline-flex min-h-11 items-center bg-neon px-6 text-sm font-semibold text-ink hover:bg-neon/80 ${focusRing}`}
          >
            Xem sản phẩm
          </Link>
        </div>
      ) : null}

      {cart && cart.items.length > 0 ? (
        <div className="mt-8">
          {cart.items.map((item) => (
            <CartLine
              key={item.id}
              item={item}
              disabled={isMutating}
              onUpdate={commitQuantity}
              onRemove={commitRemove}
            />
          ))}
          <div className="mt-8 flex flex-col gap-4 border border-ink p-4 md:flex-row md:items-end md:justify-between">
            <div>
              <p className="text-sm text-ash">
                {cart.itemCount} dòng · {cart.totalQuantity} sản phẩm
              </p>
              <p className="mt-2 text-lg font-semibold text-ink">Tổng tiền giỏ hàng {formatVnd(cart.subtotal)}</p>
              <p className="mt-2 max-w-md text-sm text-ash">Tổng tiền giỏ hàng chưa phải tổng thanh toán cuối cùng.</p>
            </div>
            <button
              type="button"
              className={`inline-flex min-h-11 items-center border border-ink px-4 text-sm font-semibold text-ink ${focusRing}`}
              disabled={isMutating}
              onClick={() => void commitClear()}
            >
              Làm trống giỏ
            </button>
          </div>
        </div>
      ) : null}
    </section>
  )
}

export default CartPage
