import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { hasCustomerRole } from '../api/cart.ts'
import {
  CheckoutApiError,
  checkoutErrorMessage,
  listOrders,
  type OrdersPage as OrdersPageData,
} from '../api/checkout.ts'
import { useAuth } from '../auth/auth-context.ts'
import { formatVnd } from '../components/shop/productDisplay.ts'

const focusRing = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'
const PAGE_SIZE = 10

const ORDER_STATUS: Record<string, string> = {
  PENDING: 'Chờ xử lý',
  CONFIRMED: 'Đã xác nhận',
  PROCESSING: 'Đang xử lý',
  SHIPPING: 'Đang giao',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy',
  RETURNED: 'Đã trả',
}

function OrdersPage() {
  const { user, isInitializing } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (isInitializing || user) {
      return
    }
    const timer = window.setTimeout(() => {
      if (window.location.pathname === '/orders') {
        navigate('/login', { replace: true, state: { from: '/orders' } })
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
        <h1 className="text-3xl font-semibold tracking-tight text-ink">Đơn hàng</h1>
        <p className="mt-4 max-w-xl text-base leading-7 text-ash">
          Đơn hàng dành cho tài khoản khách hàng. Phiên {user.fullName} vẫn được giữ.
        </p>
      </section>
    )
  }

  return <OrderList key={user.id} />
}

function OrderList() {
  const { getAccessToken, expireSession } = useAuth()
  const [pageIndex, setPageIndex] = useState(0)
  const [reloadTick, setReloadTick] = useState(0)
  const [data, setData] = useState<OrdersPageData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const requestSeq = useRef(0)

  useEffect(() => {
    const token = getAccessToken()
    if (!token) {
      return
    }
    const seq = ++requestSeq.current
    const controller = new AbortController()
    void listOrders(token, pageIndex, PAGE_SIZE, controller.signal)
      .then((next) => {
        if (seq !== requestSeq.current || getAccessToken() !== token) {
          return
        }
        setData(next)
        setError(null)
      })
      .catch((caught: unknown) => {
        if (seq !== requestSeq.current || isAbort(caught)) {
          return
        }
        if (caught instanceof CheckoutApiError && caught.status === 401 && getAccessToken() === token) {
          expireSession(token)
        }
        setError(checkoutErrorMessage(caught, 'Không tải được đơn hàng.'))
      })
      .finally(() => {
        if (seq === requestSeq.current) {
          setLoading(false)
        }
      })
    return () => controller.abort()
  }, [expireSession, getAccessToken, pageIndex, reloadTick])

  return (
    <section className="mx-auto max-w-5xl px-4 py-12 md:px-6 md:py-16">
      <h1 className="text-3xl font-semibold tracking-tight text-ink md:text-4xl">Đơn hàng</h1>
      {error ? (
        <div className="mt-6 flex flex-wrap items-center gap-3" role="alert">
          <p className="text-sm text-ink">{error}</p>
          <button
            type="button"
            className={`inline-flex min-h-11 items-center bg-neon px-4 text-sm font-semibold text-ink ${focusRing}`}
            onClick={() => {
              setLoading(true)
              setReloadTick((current) => current + 1)
            }}
          >
            Thử lại
          </button>
        </div>
      ) : null}
      {loading && !data ? <p className="mt-8 text-sm text-ash">Đang tải đơn hàng...</p> : null}
      {data && data.content.length === 0 ? <p className="mt-8 text-sm text-ink">Bạn chưa có đơn hàng.</p> : null}
      {data && data.content.length > 0 ? (
        <ul className="mt-8 border-t border-line">
          {data.content.map((order) => (
            <li key={order.orderNumber} className="border-b border-line py-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <Link
                    to={`/orders/${encodeURIComponent(order.orderNumber)}`}
                    className={`text-base font-semibold text-ink underline ${focusRing}`}
                  >
                    {order.orderNumber}
                  </Link>
                  <p className="mt-2 text-sm text-ink">{ORDER_STATUS[order.status] ?? order.status}</p>
                  <p className="mt-1 text-sm text-ash">
                    {order.itemCount} mặt hàng · {order.createdAt.replace('T', ' ').slice(0, 16)}
                  </p>
                </div>
                <p className="text-sm font-semibold text-ink">{formatVnd(order.totalAmount)}</p>
              </div>
            </li>
          ))}
        </ul>
      ) : null}
      {data && data.totalPages > 1 ? (
        <div className="mt-6 flex flex-wrap items-center gap-3">
          <button
            type="button"
            className={`inline-flex min-h-11 items-center border border-ink px-4 text-sm font-semibold text-ink disabled:border-line disabled:text-ash ${focusRing}`}
            disabled={data.first || loading}
            onClick={() => {
              setLoading(true)
              setPageIndex((current) => Math.max(0, current - 1))
            }}
          >
            Trang trước
          </button>
          <p className="text-sm text-ash">
            Trang {data.page + 1} / {data.totalPages}
          </p>
          <button
            type="button"
            className={`inline-flex min-h-11 items-center border border-ink px-4 text-sm font-semibold text-ink disabled:border-line disabled:text-ash ${focusRing}`}
            disabled={data.last || loading}
            onClick={() => {
              setLoading(true)
              setPageIndex((current) => current + 1)
            }}
          >
            Trang sau
          </button>
        </div>
      ) : null}
    </section>
  )
}

function isAbort(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError'
}

export default OrdersPage
