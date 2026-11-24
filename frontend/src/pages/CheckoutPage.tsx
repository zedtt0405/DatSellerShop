import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { AccountApiError, accountErrorMessage, listAddresses, type AddressResponse } from '../api/account.ts'
import { getCart, hasCustomerRole, type CartItemResponse } from '../api/cart.ts'
import {
  CheckoutApiError,
  checkoutErrorMessage,
  createCheckout,
  createPayment,
  findCertainOrder,
  getOrder,
  getPayment,
  listRecentOrders,
  translateServer,
  validateVoucher,
  type CheckoutAttempt,
  type OrderDetail,
  type PaymentDetail,
  type PaymentMethod,
  type VoucherPreview,
} from '../api/checkout.ts'
import { useAuth } from '../auth/auth-context.ts'
import { useCart } from '../cart/cart-context.ts'
import { formatVnd } from '../components/shop/productDisplay.ts'

const focusRing = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'
const fieldClass =
  'mt-2 min-h-11 w-full border border-line bg-white px-3 text-sm text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'

type Phase = 'form' | 'placing' | 'paying' | 'payment' | 'done' | 'unknown'

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

function optionLabel(item: CartItemResponse): string | null {
  const parts = [item.color?.name, item.size?.name].filter((part): part is string => Boolean(part))
  return parts.length > 0 ? parts.join(' · ') : null
}

function addressLabel(address: AddressResponse): string {
  return `${address.addressLine}, ${address.ward}, ${address.district}, ${address.province}`
}

function CheckoutPage() {
  const { user, isInitializing } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (isInitializing || user) {
      return
    }
    const timer = window.setTimeout(() => {
      if (window.location.pathname === '/checkout') {
        navigate('/login', { replace: true, state: { from: '/checkout' } })
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
        <h1 className="text-3xl font-semibold tracking-tight text-ink">Thanh toán</h1>
        <p className="mt-4 max-w-xl text-base leading-7 text-ash">
          Đặt hàng dành cho tài khoản khách hàng. Phiên {user.fullName} vẫn được giữ.
        </p>
        <Link to="/shop" className={`mt-8 inline-flex min-h-11 items-center bg-neon px-6 text-sm font-semibold text-ink ${focusRing}`}>
          Xem sản phẩm
        </Link>
      </section>
    )
  }

  return <CheckoutDesk key={user.id} />
}

function CheckoutDesk() {
  const { getAccessToken, expireSession } = useAuth()
  const { cart, isLoading, reload } = useCart()
  const [addresses, setAddresses] = useState<AddressResponse[] | null>(null)
  const [addressError, setAddressError] = useState<string | null>(null)
  const [addressId, setAddressId] = useState<number | null>(null)
  const [method, setMethod] = useState<PaymentMethod | null>(null)
  const [voucherCode, setVoucherCode] = useState('')
  const [voucherPreview, setVoucherPreview] = useState<VoucherPreview | null>(null)
  const [voucherError, setVoucherError] = useState<string | null>(null)
  const [checkingVoucher, setCheckingVoucher] = useState(false)
  const [note, setNote] = useState('')
  const [phase, setPhase] = useState<Phase>('form')
  const [formError, setFormError] = useState<string | null>(null)
  const [order, setOrder] = useState<OrderDetail | null>(null)
  const [payment, setPayment] = useState<PaymentDetail | null>(null)
  const [paymentError, setPaymentError] = useState<string | null>(null)
  const [unknownNote, setUnknownNote] = useState<string | null>(null)
  const [checkingOrder, setCheckingOrder] = useState(false)
  const checkoutLock = useRef(false)
  const attemptRef = useRef<CheckoutAttempt | null>(null)
  const voucherSeq = useRef(0)

  useEffect(() => {
    const token = getAccessToken()
    if (!token) {
      return
    }
    const controller = new AbortController()
    let active = true
    void listAddresses(token, controller.signal)
      .then((next) => {
        if (!active || getAccessToken() !== token) {
          return
        }
        setAddresses(next)
        setAddressId((current) => current ?? next.find((item) => item.isDefault)?.id ?? next[0]?.id ?? null)
        setAddressError(null)
      })
      .catch((caught: unknown) => {
        if (!active || isAbort(caught)) {
          return
        }
        if (caught instanceof AccountApiError && caught.status === 401 && getAccessToken() === token) {
          expireSession(token)
        }
        setAddressError(accountErrorMessage(caught, 'Không tải được địa chỉ.'))
      })
    return () => {
      active = false
      controller.abort()
    }
  }, [expireSession, getAccessToken])

  const selected = addresses?.find((item) => item.id === addressId) ?? null
  const cartReady = cart != null && !isLoading
  const hasItems = cartReady && cart.items.length > 0
  const noteTooLong = note.trim().length > 500
  const formLocked = phase !== 'form'

  async function checkVoucher() {
    const code = voucherCode.trim()
    if (!code || checkingVoucher) {
      return
    }
    const token = getAccessToken()
    if (!token) {
      return
    }
    const seq = ++voucherSeq.current
    setCheckingVoucher(true)
    setVoucherError(null)
    try {
      const preview = await validateVoucher(token, code)
      if (seq !== voucherSeq.current || getAccessToken() !== token) {
        return
      }
      setVoucherPreview(preview)
    } catch (caught) {
      if (seq !== voucherSeq.current || isAbort(caught)) {
        return
      }
      if (caught instanceof CheckoutApiError && caught.status === 401 && getAccessToken() === token) {
        expireSession(token)
      }
      setVoucherPreview(null)
      setVoucherError(checkoutErrorMessage(caught, 'Không kiểm tra được mã giảm giá.'))
    } finally {
      if (seq === voucherSeq.current) {
        setCheckingVoucher(false)
      }
    }
  }

  async function placeOrder() {
    if (checkoutLock.current || phase !== 'form') {
      return
    }
    const token = getAccessToken()
    if (!token || !selected || !method || !cart || cart.items.length === 0 || noteTooLong) {
      setFormError('Chọn địa chỉ, giữ sản phẩm trong giỏ và chọn phương thức thanh toán trước khi đặt hàng.')
      return
    }
    const code = voucherCode.trim()
    const customerNote = note.trim()
    checkoutLock.current = true
    setFormError(null)
    setPhase('placing')
    attemptRef.current = {
      startedAtMs: Date.now(),
      address: {
        recipientName: selected.recipientName,
        phoneNumber: selected.phoneNumber,
        province: selected.province,
        district: selected.district,
        ward: selected.ward,
        addressLine: selected.addressLine,
      },
      lines: cart.items.map((item) => ({ variantId: item.variantId, quantity: item.quantity })),
      voucherCode: code.length > 0 ? code : null,
      customerNote: customerNote.length > 0 ? customerNote : null,
    }
    try {
      const created = await createCheckout(token, {
        addressId: selected.id,
        voucherCode: code.length > 0 ? code : undefined,
        customerNote: customerNote.length > 0 ? customerNote : undefined,
      })
      if (getAccessToken() !== token) {
        return
      }
      setOrder(created)
      void reload()
      await pay(token, created.orderNumber, method)
    } catch (caught) {
      if (getAccessToken() !== token && !(caught instanceof CheckoutApiError && caught.status === 401)) {
        return
      }
      if (caught instanceof CheckoutApiError && caught.status === 401 && getAccessToken() === token) {
        expireSession(token)
      }
      if (caught instanceof CheckoutApiError && caught.status === 0) {
        setPhase('unknown')
        setUnknownNote('Chưa xác định. Không gửi lại đơn.')
        return
      }
      checkoutLock.current = false
      setPhase('form')
      setFormError(checkoutErrorMessage(caught, 'Không đặt được đơn hàng.'))
    }
  }

  async function pay(token: string, orderNumber: string, nextMethod: PaymentMethod) {
    setPhase('paying')
    setPaymentError(null)
    try {
      const created = await createPayment(token, orderNumber, nextMethod)
      if (getAccessToken() !== token) {
        return
      }
      setPayment(created)
      setPhase('done')
    } catch (caught) {
      if (caught instanceof CheckoutApiError && caught.status === 401 && getAccessToken() === token) {
        expireSession(token)
      }
      setPaymentError(checkoutErrorMessage(caught, 'Đơn đã tạo, nhưng chưa tạo được thanh toán. Chỉ thử lại thanh toán.'))
      setPhase('payment')
    }
  }

  async function retryPayment() {
    const token = getAccessToken()
    if (!token || !order || !method || phase !== 'payment') {
      return
    }
    await pay(token, order.orderNumber, method)
  }

  async function reconcile() {
    const token = getAccessToken()
    const attempt = attemptRef.current
    if (!token || !attempt || checkingOrder || phase !== 'unknown') {
      return
    }
    setCheckingOrder(true)
    setUnknownNote(null)
    try {
      const summaries = await listRecentOrders(token)
      const details = await Promise.all(summaries.map((item) => getOrder(token, item.orderNumber)))
      const currentCart = await reloadCart(token)
      if (getAccessToken() !== token) {
        return
      }
      const certain = findCertainOrder(details, currentCart, attempt, Date.now())
      if (!certain) {
        setUnknownNote(
          currentCart > 0
            ? 'Chưa xác định. Giỏ vẫn còn hàng và không có một đơn khớp đủ chắc chắn. Không gửi lại đơn và không tạo thanh toán.'
            : 'Chưa xác định. Không đủ bằng chứng để gắn với một đơn. Không tạo thanh toán cho đơn suy đoán.',
        )
        return
      }
      setOrder(certain)
      const existing = await getPayment(token, certain.orderNumber)
      if (getAccessToken() !== token) {
        return
      }
      void reload()
      if (existing) {
        setPayment(existing)
        setPhase('done')
        return
      }
      setPhase('payment')
      setPaymentError('Đã xác định đơn đã tạo. Thanh toán chưa có. Chỉ tạo thanh toán, không đặt đơn mới.')
    } catch (caught) {
      if (caught instanceof CheckoutApiError && caught.status === 401 && getAccessToken() === token) {
        expireSession(token)
      }
      setUnknownNote(checkoutErrorMessage(caught, 'Chưa đối chiếu được đơn hàng. Không gửi lại đơn.'))
    } finally {
      setCheckingOrder(false)
    }
  }

  async function resumeAfterMiss() {
    const token = getAccessToken()
    const attempt = attemptRef.current
    if (!token || !attempt || phase !== 'unknown') {
      return
    }
    try {
      const summaries = await listRecentOrders(token)
      const details = await Promise.all(summaries.map((item) => getOrder(token, item.orderNumber)))
      const currentCart = await reloadCart(token)
      const certain = findCertainOrder(details, currentCart, attempt, Date.now())
      if (certain || currentCart === 0) {
        setUnknownNote('Chưa đặt lại. Giỏ trống hoặc đã có đơn khớp. Dùng đối chiếu, không gửi Checkout lần nữa.')
        return
      }
      checkoutLock.current = false
      attemptRef.current = null
      setPhase('form')
      setUnknownNote(null)
    } catch (caught) {
      if (caught instanceof CheckoutApiError && caught.status === 401 && getAccessToken() === token) {
        expireSession(token)
      }
      setUnknownNote('Chưa xác định. Không đặt lại khi chưa đối chiếu được giỏ và đơn.')
    }
  }

  return (
    <section className="mx-auto max-w-5xl px-4 py-12 md:px-6 md:py-16">
      <h1 className="text-3xl font-semibold tracking-tight text-ink md:text-4xl">Thanh toán</h1>
      <p className="mt-3 max-w-2xl text-sm leading-6 text-ash">
        Tạm tính lấy từ giỏ hàng. Phí vận chuyển, giảm giá và tổng thanh toán cuối cùng chỉ hiện khi máy chủ xác nhận đơn.
      </p>

      {phase === 'form' || phase === 'placing' ? (
        <div className="mt-8 grid gap-8 lg:grid-cols-[minmax(0,1fr)_18rem] lg:items-start">
          <div className="min-w-0">
            <h2 className="text-lg font-semibold text-ink">Sản phẩm</h2>
            {isLoading && !cart ? <p className="mt-4 text-sm text-ash">Đang tải giỏ hàng...</p> : null}
            {cart && cart.items.length === 0 ? (
              <div className="mt-4">
                <p className="text-sm text-ink">Giỏ hàng đang trống.</p>
                <Link to="/shop" className={`mt-4 inline-flex min-h-11 items-center bg-neon px-4 text-sm font-semibold text-ink ${focusRing}`}>
                  Xem sản phẩm
                </Link>
              </div>
            ) : null}
            {cart && cart.items.length > 0 ? (
              <ul className="mt-4 border-t border-line">
                {cart.items.map((item) => (
                  <li key={item.id} className="border-b border-line py-4">
                    <p className="text-sm font-semibold text-ink">{item.product.name}</p>
                    {optionLabel(item) ? <p className="mt-1 text-sm text-ash">{optionLabel(item)}</p> : null}
                    <p className="mt-1 text-sm text-ink">
                      {item.quantity} × {formatVnd(item.unitPrice)}
                    </p>
                    <p className="mt-1 text-sm font-semibold text-ink">{formatVnd(item.lineSubtotal)}</p>
                    {item.available ? null : (
                      <p className="mt-2 inline-flex min-h-11 items-center bg-sun px-3 text-sm font-semibold text-ink">
                        Sản phẩm hiện không khả dụng
                      </p>
                    )}
                  </li>
                ))}
              </ul>
            ) : null}

            <h2 className="mt-8 text-lg font-semibold text-ink">Địa chỉ giao hàng</h2>
            {addressError ? (
              <p className="mt-3 text-sm text-ink" role="alert">
                {addressError}
              </p>
            ) : null}
            {addresses && addresses.length === 0 ? (
              <p className="mt-3 text-sm text-ink">Bạn chưa có địa chỉ giao hàng.</p>
            ) : null}
            {addresses && addresses.length > 0 ? (
              <fieldset className="mt-3 space-y-3">
                <legend className="sr-only">Chọn địa chỉ đã lưu</legend>
                {addresses.map((item) => (
                  <label key={item.id} className="flex min-h-11 items-start gap-3 border border-line px-3 py-3 text-sm text-ink">
                    <input
                      type="radio"
                      name="address"
                      className="mt-1"
                      checked={addressId === item.id}
                      disabled={formLocked}
                      onChange={() => setAddressId(item.id)}
                    />
                    <span>
                      <span className="font-semibold">{item.recipientName}</span>
                      <span className="mt-1 block text-ash">{item.phoneNumber}</span>
                      <span className="mt-1 block">{addressLabel(item)}</span>
                      {item.isDefault ? <span className="mt-2 inline-flex bg-neon px-2 py-1 text-xs font-semibold">Mặc định</span> : null}
                    </span>
                  </label>
                ))}
              </fieldset>
            ) : null}
            <Link to="/addresses" className={`mt-3 inline-flex min-h-11 items-center text-sm font-semibold text-ink underline ${focusRing}`}>
              Quản lý địa chỉ
            </Link>

            <h2 className="mt-8 text-lg font-semibold text-ink">Mã giảm giá</h2>
            <div className="mt-3 flex flex-col gap-3 sm:flex-row">
              <label className="min-w-0 flex-1 text-sm text-ink">
                Mã
                <input
                  value={voucherCode}
                  maxLength={40}
                  disabled={formLocked}
                  onChange={(event) => {
                    setVoucherCode(event.target.value)
                    setVoucherPreview(null)
                    setVoucherError(null)
                  }}
                  className={fieldClass}
                />
              </label>
              <button
                type="button"
                className={`inline-flex min-h-11 items-center border border-ink px-4 text-sm font-semibold text-ink sm:mt-6 ${focusRing}`}
                disabled={formLocked || checkingVoucher || voucherCode.trim().length === 0}
                onClick={() => void checkVoucher()}
              >
                {checkingVoucher ? 'Đang kiểm tra...' : 'Kiểm tra mã'}
              </button>
            </div>
            {voucherError ? (
              <p className="mt-3 text-sm text-ink" role="alert">
                {voucherError}
              </p>
            ) : null}
            {voucherPreview ? (
              <div className="mt-3 border border-line p-3 text-sm text-ink">
                <p>{translateServer(voucherPreview.message) ?? voucherPreview.message}</p>
                <p className="mt-2">Tạm tính xem trước {formatVnd(voucherPreview.subtotal)}</p>
                <p>Giảm giá xem trước {formatVnd(voucherPreview.discountAmount)}</p>
                <p>Sau giảm giá xem trước {formatVnd(voucherPreview.totalAfterDiscount)}</p>
                <p className="mt-2 text-ash">Đây là xem trước. Máy chủ tính lại khi đặt đơn. Chưa có phí vận chuyển trong bước này.</p>
              </div>
            ) : null}

            <h2 className="mt-8 text-lg font-semibold text-ink">Thanh toán</h2>
            <fieldset className="mt-3 space-y-3">
              <legend className="sr-only">Phương thức thanh toán</legend>
              {(['COD', 'BANK_QR'] as const).map((value) => (
                <label key={value} className="flex min-h-11 items-center gap-3 border border-line px-3 text-sm text-ink">
                  <input
                    type="radio"
                    name="payment-method"
                    checked={method === value}
                    disabled={formLocked}
                    onChange={() => setMethod(value)}
                  />
                  {value === 'COD' ? 'COD — trả khi nhận hàng' : 'BANK_QR — nội dung chuyển khoản demo'}
                </label>
              ))}
            </fieldset>

            <label className="mt-8 block text-sm text-ink">
              Ghi chú
              <textarea
                value={note}
                maxLength={500}
                disabled={formLocked}
                onChange={(event) => setNote(event.target.value)}
                className={`${fieldClass} min-h-24 py-3`}
              />
            </label>
            {noteTooLong ? <p className="mt-2 text-sm text-ink">Ghi chú tối đa 500 ký tự.</p> : null}
          </div>

          <aside className="border border-ink p-4">
            <h2 className="text-lg font-semibold text-ink">Tạm tính</h2>
            {cart ? (
              <p className="mt-3 text-sm text-ink">
                {cart.itemCount} dòng · {cart.totalQuantity} sản phẩm
              </p>
            ) : null}
            <p className="mt-3 text-base font-semibold text-ink">
              Tạm tính giỏ hàng {cart ? formatVnd(cart.subtotal) : 'Đang tải'}
            </p>
            <p className="mt-3 text-sm leading-6 text-ash">
              Tổng thanh toán cuối cùng do máy chủ xác nhận khi tạo đơn. Trang này không tự tính phí vận chuyển hay tổng cuối.
            </p>
            {formError ? (
              <p className="mt-4 text-sm text-ink" role="alert">
                {formError}
              </p>
            ) : null}
            <button
              type="button"
              className={`mt-4 inline-flex min-h-11 w-full items-center justify-center bg-neon px-4 text-sm font-semibold text-ink hover:bg-neon/80 disabled:cursor-not-allowed disabled:bg-line ${focusRing}`}
              disabled={formLocked || !hasItems || !selected || !method || noteTooLong}
              onClick={() => void placeOrder()}
            >
              {phase === 'placing' ? 'Đang đặt hàng...' : 'Đặt hàng'}
            </button>
          </aside>
        </div>
      ) : null}

      {phase === 'paying' ? <p className="mt-8 text-sm text-ash">Đơn đã tạo. Đang tạo thanh toán...</p> : null}

      {order && (phase === 'payment' || phase === 'done' || phase === 'paying') ? (
        <OrderResult order={order} payment={payment} paymentError={paymentError} busy={phase === 'paying'} onRetry={() => void retryPayment()} />
      ) : null}

      {phase === 'unknown' ? (
        <div className="mt-8 max-w-2xl border border-ink p-4" role="status">
          <h2 className="text-lg font-semibold text-ink">Chưa xác định</h2>
          <p className="mt-3 text-sm leading-6 text-ink">
            Không nhận được kết quả đặt hàng. Đơn không được gửi lại tự động.
          </p>
          {unknownNote ? <p className="mt-3 text-sm text-ink">{unknownNote}</p> : null}
          <div className="mt-4 flex flex-wrap gap-3">
            <button
              type="button"
              className={`inline-flex min-h-11 items-center bg-neon px-4 text-sm font-semibold text-ink ${focusRing}`}
              disabled={checkingOrder}
              onClick={() => void reconcile()}
            >
              {checkingOrder ? 'Đang đối chiếu...' : 'Đối chiếu đơn hàng'}
            </button>
            <button
              type="button"
              className={`inline-flex min-h-11 items-center border border-ink px-4 text-sm font-semibold text-ink ${focusRing}`}
              onClick={() => void resumeAfterMiss()}
            >
              Quay lại biểu mẫu khi giỏ còn hàng
            </button>
          </div>
        </div>
      ) : null}
    </section>
  )
}

async function reloadCart(token: string): Promise<number> {
  const next = await getCart(token)
  return next.items.length
}

function OrderResult({
  order,
  payment,
  paymentError,
  busy,
  onRetry,
}: {
  order: OrderDetail
  payment: PaymentDetail | null
  paymentError: string | null
  busy: boolean
  onRetry: () => void
}) {
  return (
    <div className="mt-8 max-w-3xl border border-ink p-4">
      <h2 className="text-lg font-semibold text-ink">Đơn đã được máy chủ xác nhận</h2>
      <p className="mt-3 text-sm text-ink">Mã đơn {order.orderNumber}</p>
      <p className="mt-1 text-sm text-ink">Trạng thái đơn {ORDER_STATUS[order.status] ?? order.status}</p>
      <p className="mt-4 text-sm text-ink">Tạm tính {formatVnd(order.subtotal)}</p>
      <p className="mt-1 text-sm text-ink">Giảm giá {formatVnd(order.discountAmount)}</p>
      <p className="mt-1 text-sm text-ink">Phí vận chuyển {formatVnd(order.shippingFee)}</p>
      <p className="mt-2 text-base font-semibold text-ink">Tổng thanh toán {formatVnd(order.totalAmount)}</p>
      <p className="mt-4 text-sm text-ink">
        Giao đến {order.shippingAddress.recipientName} · {order.shippingAddress.recipientPhone}
      </p>
      <p className="mt-1 text-sm text-ash">
        {order.shippingAddress.addressLine}, {order.shippingAddress.ward}, {order.shippingAddress.district}, {order.shippingAddress.province}
      </p>
      {payment ? (
        <div className="mt-4 border-t border-line pt-4">
          <p className="text-sm text-ink">Thanh toán {payment.method}</p>
          <p className="mt-1 text-sm text-ink">Trạng thái thanh toán {PAYMENT_STATUS[payment.status] ?? payment.status}</p>
          <p className="mt-1 text-sm text-ink">Số tiền {formatVnd(payment.amount)}</p>
          <p className="mt-1 text-sm text-ash">Mã thanh toán {payment.paymentReference}</p>
          {payment.method === 'BANK_QR' && payment.qrContent ? (
            <p className="mt-3 break-all border border-line p-3 text-sm text-ink">{payment.qrContent}</p>
          ) : null}
        </div>
      ) : null}
      {paymentError ? (
        <p className="mt-4 text-sm text-ink" role="alert">
          {paymentError}
        </p>
      ) : null}
      {!payment ? (
        <button
          type="button"
          className={`mt-4 inline-flex min-h-11 items-center bg-neon px-4 text-sm font-semibold text-ink disabled:bg-line ${focusRing}`}
          disabled={busy}
          onClick={onRetry}
        >
          {busy ? 'Đang tạo thanh toán...' : 'Thử lại thanh toán'}
        </button>
      ) : null}
      <Link to="/cart" className={`mt-4 inline-flex min-h-11 items-center px-1 text-sm font-semibold text-ink underline ${focusRing}`}>
        Xem giỏ hàng
      </Link>
    </div>
  )
}

function isAbort(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError'
}

export default CheckoutPage
