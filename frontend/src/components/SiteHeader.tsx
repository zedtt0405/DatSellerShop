import { useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router'
import { hasCustomerRole } from '../api/cart.ts'
import { useAuth } from '../auth/auth-context.ts'
import { useCart } from '../cart/cart-context.ts'
import { useWishlist } from '../wishlist/wishlist-context.ts'

const links = [
  { to: '/', label: 'Trang chủ', end: true },
  { to: '/shop', label: 'Sản phẩm', end: false },
]

const focusRing =
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'

function navClassName({ isActive }: { isActive: boolean }) {
  return [
    'block min-h-11 px-3 py-2 text-sm font-medium text-ink',
    'border-b-2',
    focusRing,
    isActive ? 'border-neon' : 'border-transparent hover:border-neon',
  ].join(' ')
}

function CartLink({ onNavigate }: { onNavigate: () => void }) {
  const { user, isInitializing } = useAuth()
  const { cart } = useCart()
  const count = !isInitializing && user && hasCustomerRole(user.roles) && cart ? cart.totalQuantity : null

  return (
    <NavLink to="/cart" className={navClassName} onClick={onNavigate}>
      <span className="inline-flex items-center gap-2">
        Giỏ hàng
        {count != null ? (
          <span className="inline-flex min-h-6 min-w-6 items-center justify-center bg-neon px-1.5 text-xs font-semibold text-ink">
            {count}
          </span>
        ) : null}
      </span>
    </NavLink>
  )
}

function WishlistLink({ onNavigate }: { onNavigate: () => void }) {
  const { user, isInitializing } = useAuth()
  const { wishlist, phase } = useWishlist()
  const count =
    !isInitializing && user && hasCustomerRole(user.roles) && phase === 'ready' && wishlist ? wishlist.itemCount : null

  return (
    <NavLink to="/wishlist" className={navClassName} onClick={onNavigate}>
      <span className="inline-flex items-center gap-2">
        Yêu thích
        {count != null ? (
          <span className="inline-flex min-h-6 min-w-6 items-center justify-center bg-neon px-1.5 text-xs font-semibold text-ink">
            {count}
          </span>
        ) : null}
      </span>
    </NavLink>
  )
}

function AuthActions({ onNavigate }: { onNavigate: () => void }) {
  const { user, isInitializing, sessionError, logout, retrySession } = useAuth()
  const navigate = useNavigate()

  if (isInitializing) {
    return null
  }

  if (user) {
    return (
      <>
        <span className="block max-w-40 truncate px-3 py-2 text-sm font-semibold text-ink" title={user.fullName}>
          {user.fullName}
        </span>
        <button
          type="button"
          className={`block min-h-11 px-3 py-2 text-left text-sm font-medium text-ink ${focusRing}`}
          onClick={() => {
            onNavigate()
            navigate('/', { replace: true })
            logout()
          }}
        >
          Đăng xuất
        </button>
      </>
    )
  }

  return (
    <>
      {sessionError ? (
        <button
          type="button"
          className={`block min-h-11 px-3 py-2 text-left text-sm font-medium text-ink ${focusRing}`}
          onClick={() => {
            onNavigate()
            void retrySession()
          }}
        >
          Thử lại phiên
        </button>
      ) : null}
      <NavLink to="/login" className={navClassName} onClick={onNavigate}>
        Đăng nhập
      </NavLink>
      <NavLink to="/register" className={navClassName} onClick={onNavigate}>
        Đăng ký
      </NavLink>
    </>
  )
}

function SiteHeader() {
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <header className="sticky top-0 z-20 border-b border-line bg-white">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3 md:px-6">
        <Link
          to="/"
          className={`text-lg font-semibold tracking-tight text-ink ${focusRing}`}
          onClick={() => setMenuOpen(false)}
        >
          DatSellerShop
        </Link>

        <nav className="hidden items-center gap-2 md:flex" aria-label="Chính">
          {links.map((link) => (
            <NavLink key={link.to} to={link.to} end={link.end} className={navClassName}>
              {link.label}
            </NavLink>
          ))}
          <CartLink onNavigate={() => setMenuOpen(false)} />
          <WishlistLink onNavigate={() => setMenuOpen(false)} />
          <AuthActions onNavigate={() => setMenuOpen(false)} />
        </nav>

        <button
          type="button"
          className={`min-h-11 rounded-sm px-3 text-sm font-medium text-ink md:hidden ${focusRing}`}
          aria-expanded={menuOpen}
          aria-controls="mobile-nav"
          onClick={() => setMenuOpen((open) => !open)}
        >
          {menuOpen ? 'Đóng' : 'Menu'}
        </button>
      </div>

      {menuOpen ? (
        <nav
          id="mobile-nav"
          className="space-y-1 border-t border-line px-4 py-3 md:hidden"
          aria-label="Di động"
        >
          {links.map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.end}
              className={navClassName}
              onClick={() => setMenuOpen(false)}
            >
              {link.label}
            </NavLink>
          ))}
          <CartLink onNavigate={() => setMenuOpen(false)} />
          <WishlistLink onNavigate={() => setMenuOpen(false)} />
          <AuthActions onNavigate={() => setMenuOpen(false)} />
        </nav>
      ) : null}
    </header>
  )
}

export default SiteHeader
