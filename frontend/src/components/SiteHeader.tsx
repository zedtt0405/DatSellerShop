import { useState } from 'react'
import { Link, NavLink } from 'react-router'

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
        </nav>
      ) : null}
    </header>
  )
}

export default SiteHeader
