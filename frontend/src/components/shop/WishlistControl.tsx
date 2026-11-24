import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { hasCustomerRole } from '../../api/cart.ts'
import { wishlistErrorMessage } from '../../api/wishlist.ts'
import { useAuth } from '../../auth/auth-context.ts'
import { useWishlist } from '../../wishlist/wishlist-context.ts'

const focusRing = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'

function HeartIcon({ filled }: { filled: boolean }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="h-5 w-5">
      <path
        d="M12 20s-7-4.4-7-9a4 4 0 0 1 7-2 4 4 0 0 1 7 2c0 4.6-7 9-7 9z"
        fill={filled ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth="1.8"
      />
    </svg>
  )
}

function WishlistControl({ productId, variant }: { productId: number; variant: 'icon' | 'label' }) {
  const { user, isInitializing } = useAuth()
  const { phase, wishlisted, isPending, addProduct, removeProduct, reload } = useWishlist()
  const navigate = useNavigate()
  const location = useLocation()
  const [localError, setLocalError] = useState<string | null>(null)
  const known = wishlisted(productId)
  const pending = isPending(productId)
  const customer = !isInitializing && user != null && hasCustomerRole(user.roles)

  if (isInitializing) {
    return null
  }

  if (user && !customer) {
    if (variant === 'icon') {
      return null
    }
    return <p className="mt-4 text-sm leading-6 text-ash">Yêu thích dành cho tài khoản khách hàng.</p>
  }

  async function toggle() {
    if (!customer || known === null || pending) {
      return
    }
    setLocalError(null)
    try {
      if (known) {
        await removeProduct(productId)
      } else {
        await addProduct(productId)
      }
    } catch (caught) {
      setLocalError(wishlistErrorMessage(caught, 'Không cập nhật được yêu thích.'))
    }
  }

  function goToLogin() {
    navigate('/login', { state: { from: `${location.pathname}${location.search}` } })
  }

  const neutral = customer && (known === null || phase === 'loading' || phase === 'unknown')
  const selected = known === true
  const label = pending
    ? 'Đang cập nhật yêu thích'
    : neutral
      ? 'Chưa xác định trạng thái yêu thích'
      : selected
        ? 'Bỏ yêu thích'
        : 'Thêm vào yêu thích'
  const className = [
    'inline-flex min-h-11 items-center justify-center gap-2 text-sm font-semibold text-ink',
    focusRing,
    variant === 'icon' ? 'absolute top-3 right-3 z-10 h-11 w-11 border' : 'mt-4 border px-4',
    selected ? 'border-neon bg-neon' : neutral ? 'border-line bg-white text-ash' : 'border-ink bg-white',
    pending ? 'cursor-wait' : '',
  ].join(' ')

  const button = !customer ? (
    <button type="button" className={className} aria-label="Đăng nhập để yêu thích" onClick={goToLogin}>
      <HeartIcon filled={false} />
      {variant === 'label' ? <span>Yêu thích</span> : null}
    </button>
  ) : neutral && phase === 'unknown' ? (
    <button type="button" className={className} aria-label="Thử tải lại trạng thái yêu thích" onClick={() => void reload()}>
      <HeartIcon filled={false} />
      {variant === 'label' ? <span>Thử lại yêu thích</span> : null}
    </button>
  ) : (
    <button
      type="button"
      className={className}
      aria-pressed={known === null ? undefined : selected}
      aria-label={label}
      disabled={pending || known === null}
      onClick={() => void toggle()}
    >
      <HeartIcon filled={selected} />
      {variant === 'label' ? <span>{pending ? 'Đang cập nhật...' : selected ? 'Đã yêu thích' : 'Yêu thích'}</span> : null}
    </button>
  )

  return (
    <>
      {button}
      {localError ? (
        <p className="mt-2 text-sm text-ink" role="alert">
          {localError}
        </p>
      ) : null}
    </>
  )
}

export default WishlistControl
