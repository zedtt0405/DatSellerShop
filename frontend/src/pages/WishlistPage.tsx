import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { hasCustomerRole } from '../api/cart.ts'
import { wishlistErrorMessage, type WishlistItemResponse } from '../api/wishlist.ts'
import { useAuth } from '../auth/auth-context.ts'
import { formatVnd, hasValidSale, isDisplayableImageUrl } from '../components/shop/productDisplay.ts'
import { useWishlist } from '../wishlist/wishlist-context.ts'

const focusRing = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'

function WishlistLine({
  item,
  pending,
  onRemove,
}: {
  item: WishlistItemResponse
  pending: boolean
  onRemove: (productId: number) => Promise<void>
}) {
  const [failedImage, setFailedImage] = useState(false)
  const imageUrl = isDisplayableImageUrl(item.primaryImageUrl) ? item.primaryImageUrl : null
  const onSale = hasValidSale(item.price, item.salePrice)

  return (
    <article className="grid gap-4 border-b border-line py-6 sm:grid-cols-[5.5rem_minmax(0,1fr)]">
      {imageUrl && !failedImage ? (
        <img
          src={imageUrl}
          alt=""
          onError={() => setFailedImage(true)}
          className="h-28 w-full bg-line object-cover sm:w-[5.5rem]"
        />
      ) : (
        <div className="flex h-28 w-full items-center justify-center bg-line px-2 text-center text-xs text-ash sm:w-[5.5rem]">
          Chưa có ảnh
        </div>
      )}
      <div className="min-w-0">
        {item.available ? (
          <h2 className="text-base font-semibold text-ink">
            <Link to={`/shop/${encodeURIComponent(item.slug)}`} className={`hover:underline ${focusRing}`}>
              {item.name}
            </Link>
          </h2>
        ) : (
          <h2 className="text-base font-semibold text-ink">{item.name}</h2>
        )}
        <p className="mt-2 text-sm font-semibold text-ink">{formatVnd(item.effectivePrice)}</p>
        {onSale ? <p className="text-xs text-ash line-through">{formatVnd(item.price)}</p> : null}
        {item.available && !item.inStock ? <p className="mt-2 text-sm text-ash">Hết hàng</p> : null}
        {!item.available ? (
          <p className="mt-3 inline-flex min-h-11 items-center bg-sun px-3 text-sm font-semibold text-ink">
            Sản phẩm hiện không khả dụng
          </p>
        ) : null}
        <button
          type="button"
          className={`mt-3 inline-flex min-h-11 items-center text-sm font-semibold text-ink underline disabled:text-ash ${focusRing}`}
          disabled={pending}
          onClick={() => void onRemove(item.productId)}
        >
          {pending ? 'Đang bỏ...' : 'Bỏ yêu thích'}
        </button>
      </div>
    </article>
  )
}

function WishlistPage() {
  const { user, isInitializing } = useAuth()
  const { wishlist, phase, error, reload, removeProduct, isPending } = useWishlist()
  const navigate = useNavigate()
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    if (isInitializing || user) {
      return
    }
    const timer = window.setTimeout(() => {
      if (window.location.pathname === '/wishlist') {
        navigate('/login', { replace: true, state: { from: '/wishlist' } })
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
        <h1 className="text-3xl font-semibold tracking-tight text-ink">Yêu thích</h1>
        <p className="mt-4 max-w-xl text-base leading-7 text-ash">
          Danh sách yêu thích dành cho tài khoản khách hàng. Phiên {user.fullName} vẫn được giữ.
        </p>
        <Link to="/shop" className={`mt-8 inline-flex min-h-11 items-center bg-neon px-6 text-sm font-semibold text-ink ${focusRing}`}>
          Xem sản phẩm
        </Link>
      </section>
    )
  }

  async function commitRemove(productId: number) {
    setActionError(null)
    try {
      await removeProduct(productId)
    } catch (caught) {
      setActionError(wishlistErrorMessage(caught, 'Không bỏ được yêu thích.'))
    }
  }

  const message = actionError ?? error

  return (
    <section className="mx-auto max-w-5xl px-4 py-12 md:px-6 md:py-16">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-semibold tracking-tight text-ink md:text-4xl">Yêu thích</h1>
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
      {phase === 'loading' ? <p className="mt-8 text-sm text-ash">Đang tải danh sách yêu thích...</p> : null}
      {phase === 'unknown' ? (
        <p className="mt-8 text-sm text-ink">Chưa xác định được danh sách yêu thích.</p>
      ) : null}
      {phase === 'ready' && wishlist && wishlist.items.length === 0 ? (
        <div className="mt-8">
          <p className="text-base text-ink">Bạn chưa có sản phẩm yêu thích.</p>
          <Link
            to="/shop"
            className={`mt-6 inline-flex min-h-11 items-center bg-neon px-6 text-sm font-semibold text-ink hover:bg-neon/80 ${focusRing}`}
          >
            Xem sản phẩm
          </Link>
        </div>
      ) : null}
      {phase === 'ready' && wishlist && wishlist.items.length > 0 ? (
        <div className="mt-8">
          {wishlist.items.map((item) => (
            <WishlistLine
              key={item.id}
              item={item}
              pending={isPending(item.productId)}
              onRemove={commitRemove}
            />
          ))}
        </div>
      ) : null}
    </section>
  )
}

export default WishlistPage
