import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { hasCustomerRole } from '../api/cart.ts'
import {
  CheckoutApiError,
  cancelOrder,
  checkoutErrorMessage,
  getOrder,
  getPayment,
  type OrderDetail,
  type PaymentDetail,
} from '../api/checkout.ts'
import { useAuth } from '../auth/auth-context.ts'
import { formatVnd } from '../components/shop/productDisplay.ts'

const focusRing = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'

const ORDER_STATUS: Record<string, string> = {
  PENDING: 'Chờ xử lý',
  CONFIRMED: 'Đã xác nhận',
  PROCESSING: 'Đang xử lý',
  SHIPPING: 'Đang giao',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy',
  RETURNED: 'Đã trả',
}

const PAYMENT_STATUS: Record<string, string> = {
  PENDING: 'Chờ thanh toán',
  PAID: 'Đã thanh toán',
  FAILED: 'Thanh toán thất bại',
  CANCELLED: 'Đã hủy thanh toán',
}

const PAYMENT_METHOD: Record<string, string> = {
  COD: 'COD — trả khi nhận hàng',
  BANK_QR: 'BANK_QR — chuyển khoản demo',
}

// Mirrors OrderController#cancelMine: PENDING/CONFIRMED only, and a PAID payment blocks cancel (409).
function canCancel(status: string, payment: PaymentDetail | null): boolean {
  return (status === 'PENDING' || status === 'CONFIRMED') && payment?.status !== 'PAID'
}

function formatDateTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

function OrderDetailPage() {
  const { user, isInitializing } = useAuth()
  const navigate = useNavigate()
  const { orderNumber } = useParams()
  const decoded = orderNumber ? decodeURIComponent(orderNumber) : ''

  useEffect(() => {
    if (isInitializing || user) {
      return
    }
    const timer = window.setTimeout(() => {
      if (window.location.pathname.startsWith('/orders/')) {
        navigate('/login', { replace: true, state: { from: `/orders/${encodeURIComponent(decoded)}` } })
      }
    }, 0)
    return () => window.clearTimeout(timer)
  }, [decoded, isInitializing, user, navigate])

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
        <h1 className="text-3xl font-semibold tracking-tight text-ink">Chi tiết đơn</h1>
        <p className="mt-4 max-w-xl text-base leading-7 text-ash">
          Đơn hàng dành cho tài khoản khách hàng. Phiên {user.fullName} vẫn được giữ.
        </p>
      </section>
    )
  }

  if (!decoded) {
    return null
  }

  return <OrderDetailView key={`${user.id}:${decoded}`} orderNumber={decoded} />
}

function OrderDetailView({ orderNumber }: { orderNumber: string }) {
  const { getAccessToken, expireSession } = useAuth()
  const [order, setOrder] = useState<OrderDetail | null>(null)
  const [payment, setPayment] = useState<PaymentDetail | null>(null)
  const [paymentNote, setPaymentNote] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [confirming, setConfirming] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [reloadTick, setReloadTick] = useState(0)

  useEffect(() => {
    const token = getAccessToken()
    if (!token) {
      return
    }
    const controller = new AbortController()
    let active = true
    void getOrder(token, orderNumber, controller.signal)
      .then(async (next) => {
        if (!active || getAccessToken() !== token) {
          return
        }
        setOrder(next)
        setError(null)
        try {
          const existing = await getPayment(token, orderNumber, controller.signal)
          if (!active || getAccessToken() !== token) {
            return
          }
          setPayment(existing)
          setPaymentNote(existing ? null : 'Chưa có thanh toán.')
        } catch (caught) {
          if (!active || isAbort(caught)) {
            return
          }
          if (caught instanceof CheckoutApiError && caught.status === 401 && getAccessToken() === token) {
            expireSession(token)
          }
          setPayment(null)
          setPaymentNote(checkoutErrorMessage(caught, 'Không tải được thanh toán.'))
        }
      })
      .catch((caught: unknown) => {
        if (!active || isAbort(caught)) {
          return
        }
        if (caught instanceof CheckoutApiError && caught.status === 401 && getAccessToken() === token) {
          expireSession(token)
        }
        setOrder(null)
        setError(checkoutErrorMessage(caught, 'Không tải được đơn hàng.'))
      })
      .finally(() => {
        if (active) {
          setLoading(false)
        }
      })
    return () => {
      active = false
      controller.abort()
    }
  }, [expireSession, getAccessToken, orderNumber, reloadTick])

  async function cancel() {
    const token = getAccessToken()
    if (!token || cancelling) {
      return
    }
    setCancelling(true)
    setNotice(null)
    try {
      const next = await cancelOrder(token, orderNumber)
      if (getAccessToken() !== token) {
        return
      }
      setOrder(next)
      setConfirming(false)
      setNotice(next.status === 'CANCELLED' ? 'Đơn đã được hủy theo phản hồi của máy chủ.' : null)
      // Cancelling also moves a PENDING payment to CANCELLED server-side; refetch order + payment.
      setReloadTick((current) => current + 1)
    } catch (caught) {
      if (caught instanceof CheckoutApiError && caught.status === 401) {
        if (getAccessToken() === token) {
          expireSession(token)
        }
        return
      }
      setConfirming(false)
      setNotice(checkoutErrorMessage(caught, 'Không hủy được đơn hàng.'))
      if (caught instanceof CheckoutApiError && (caught.status === 400 || caught.status === 409)) {
        // Status or payment may have changed since this page loaded; resync from the server.
        setReloadTick((current) => current + 1)
      }
    } finally {
      setCancelling(false)
    }
  }

  return (
    <section className="mx-auto max-w-5xl px-4 py-12 md:px-6 md:py-16">
      <Link to="/orders" className={`text-sm font-semibold text-ink underline ${focusRing}`}>
        Tất cả đơn hàng
      </Link>
      <h1 className="mt-4 text-3xl font-semibold tracking-tight text-ink md:text-4xl">Chi tiết đơn</h1>
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
      {loading && !order ? <p className="mt-8 text-sm text-ash">Đang tải đơn hàng...</p> : null}
      {order ? (
        <div className="mt-8 grid gap-8 lg:grid-cols-[minmax(0,1fr)_18rem]">
          <div>
            <p className="break-all text-sm font-semibold text-ink">{order.orderNumber}</p>
            <p className="mt-1 text-sm text-ash">Đặt lúc {formatDateTime(order.createdAt)}</p>
            <p className="mt-2 inline-flex items-center bg-sun px-2 py-1 text-sm font-semibold text-ink">
              {ORDER_STATUS[order.status] ?? order.status}
            </p>
            <ul className="mt-6 border-t border-line">
              {order.items.map((item) => (
                <li key={item.id} className="border-b border-line py-4">
                  <Link
                    to={`/shop/${encodeURIComponent(item.productSlug)}`}
                    className={`text-sm font-semibold text-ink underline ${focusRing}`}
                  >
                    {item.productName}
                  </Link>
                  <p className="mt-1 text-sm text-ash">
                    {[item.colorName, item.sizeName, item.sku].filter(Boolean).join(' · ')}
                  </p>
                  <p className="mt-1 text-sm text-ink">
                    {item.quantity} × {formatVnd(item.unitPrice)}
                  </p>
                  <p className="mt-1 text-sm font-semibold text-ink">{formatVnd(item.lineTotal)}</p>
                </li>
              ))}
            </ul>
            <h2 className="mt-8 text-lg font-semibold text-ink">Giao hàng</h2>
            <p className="mt-3 text-sm text-ink">
              {order.shippingAddress.recipientName} · {order.shippingAddress.recipientPhone}
            </p>
            <p className="mt-1 text-sm text-ash">
              {order.shippingAddress.addressLine}, {order.shippingAddress.ward}, {order.shippingAddress.district},{' '}
              {order.shippingAddress.province}
            </p>
            {order.customerNote ? <p className="mt-3 text-sm text-ink">Ghi chú {order.customerNote}</p> : null}
          </div>
          <aside className="border border-ink p-4">
            <h2 className="text-lg font-semibold text-ink">Thanh toán</h2>
            <p className="mt-3 text-sm text-ink">Tạm tính {formatVnd(order.subtotal)}</p>
            <p className="mt-1 text-sm text-ink">Giảm giá {formatVnd(order.discountAmount)}</p>
            {order.voucher ? (
              <p className="mt-1 text-sm text-ash">
                Voucher {order.voucher.code} · −{formatVnd(order.voucher.discountAmount)}
              </p>
            ) : null}
            <p className="mt-1 text-sm text-ink">Phí vận chuyển {formatVnd(order.shippingFee)}</p>
            <p className="mt-2 text-base font-semibold text-ink">Tổng thanh toán {formatVnd(order.totalAmount)}</p>
            {payment ? (
              <div className="mt-4 border-t border-line pt-4">
                <p className="text-sm text-ink">Phương thức {PAYMENT_METHOD[payment.method] ?? payment.method}</p>
                <p className="mt-1 text-sm text-ink">Trạng thái {PAYMENT_STATUS[payment.status] ?? payment.status}</p>
                <p className="mt-1 text-sm text-ink">Số tiền {formatVnd(payment.amount)}</p>
                <p className="mt-1 break-all text-sm text-ash">Mã thanh toán {payment.paymentReference}</p>
                {payment.paidAt ? <p className="mt-1 text-sm text-ash">Thanh toán lúc {formatDateTime(payment.paidAt)}</p> : null}
                {payment.method === 'BANK_QR' && payment.status === 'PENDING' && payment.qrContent ? (
                  <p className="mt-3 break-all border border-line p-3 text-sm text-ink">{payment.qrContent}</p>
                ) : null}
              </div>
            ) : null}
            {payment?.status === 'PAID' && canCancel(order.status, null) ? (
              <p className="mt-4 text-sm text-ash">Đơn đã thanh toán không tự hủy được. Liên hệ cửa hàng để hoàn tiền.</p>
            ) : null}
            {paymentNote ? <p className="mt-4 text-sm text-ash">{paymentNote}</p> : null}
            {notice ? (
              <p className="mt-4 text-sm text-ink" role="status">
                {notice}
              </p>
            ) : null}
            {canCancel(order.status, payment) ? (
              confirming ? (
                <div className="mt-4 flex flex-wrap gap-3">
                  <button
                    type="button"
                    className={`inline-flex min-h-11 items-center bg-ink px-4 text-sm font-semibold text-white disabled:bg-line ${focusRing}`}
                    disabled={cancelling}
                    onClick={() => void cancel()}
                  >
                    {cancelling ? 'Đang hủy...' : 'Xác nhận hủy'}
                  </button>
                  <button
                    type="button"
                    className={`inline-flex min-h-11 items-center border border-ink px-4 text-sm font-semibold text-ink ${focusRing}`}
                    disabled={cancelling}
                    onClick={() => setConfirming(false)}
                  >
                    Giữ đơn
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  className={`mt-4 inline-flex min-h-11 items-center border border-ink px-4 text-sm font-semibold text-ink ${focusRing}`}
                  onClick={() => setConfirming(true)}
                >
                  Hủy đơn
                </button>
              )
            ) : null}
          </aside>
        </div>
      ) : null}
    </section>
  )
}

function isAbort(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError'
}

export default OrderDetailPage
