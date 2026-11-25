import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { hasCustomerRole } from '../../api/cart.ts'
import {
  ReviewApiError,
  createReview,
  deleteReview,
  listMyReviews,
  listPublicReviews,
  reviewErrorMessage,
  updateReview,
  type MyReview,
  type PublicReviewPage,
} from '../../api/reviews.ts'
import { useAuth } from '../../auth/auth-context.ts'

const focusRing = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'
const COMMENT_MAX = 2000

const REVIEW_STATUS: Record<string, string> = {
  PUBLISHED: 'Đang hiển thị',
  HIDDEN: 'Đang bị ẩn',
}

function Stars({ value }: { value: number }) {
  return (
    <span aria-label={`${value} trên 5 sao`} className="tracking-tight">
      <span aria-hidden="true" className="text-ink">
        {'★'.repeat(value)}
      </span>
      <span aria-hidden="true" className="text-line">
        {'★'.repeat(5 - value)}
      </span>
    </span>
  )
}

function formatDate(value: string): string {
  return value.slice(0, 10).split('-').reverse().join('/')
}

type ProductReviewsProps = { productId: number; slug: string }

// Remount per product + account so a previous user's review/draft never leaks after logout,
// account switch, or client-side navigation to another product.
function ProductReviews({ productId, slug }: ProductReviewsProps) {
  const { user } = useAuth()
  return <ProductReviewsSection key={`${productId}:${user?.id ?? 'guest'}`} productId={productId} slug={slug} />
}

function ProductReviewsSection({ productId, slug }: ProductReviewsProps) {
  const { user, isInitializing, getAccessToken, expireSession } = useAuth()
  const location = useLocation()
  const customer = !isInitializing && user != null && hasCustomerRole(user.roles)
  const [page, setPage] = useState(0)
  const [reloadTick, setReloadTick] = useState(0)
  const [publicPage, setPublicPage] = useState<PublicReviewPage | null>(null)
  const [publicError, setPublicError] = useState<string | null>(null)
  const [mine, setMine] = useState<MyReview | null>(null)
  const [ownerId, setOwnerId] = useState<number | null>(null)
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [editing, setEditing] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const submitLock = useRef(false)

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    void listPublicReviews(slug, page, controller.signal)
      .then((next) => {
        if (!active) {
          return
        }
        setPublicPage(next)
        setPublicError(null)
      })
      .catch((caught: unknown) => {
        if (!active || isAbort(caught)) {
          return
        }
        setPublicError(reviewErrorMessage(caught, 'Không tải được đánh giá.'))
      })
    return () => {
      active = false
      controller.abort()
    }
  }, [page, reloadTick, slug])

  useEffect(() => {
    if (!customer) {
      return
    }
    const token = getAccessToken()
    if (!token) {
      return
    }
    const controller = new AbortController()
    let active = true
    void listMyReviews(token, controller.signal)
      .then((reviews) => {
        if (!active || getAccessToken() !== token || user == null) {
          return
        }
        const found = reviews.find((review) => review.productId === productId) ?? null
        setMine(found)
        setOwnerId(user.id)
        if (found) {
          setRating(found.rating)
          setComment(found.comment ?? '')
        }
      })
      .catch((caught: unknown) => {
        if (!active || isAbort(caught)) {
          return
        }
        if (caught instanceof ReviewApiError && caught.status === 401 && getAccessToken() === token) {
          expireSession(token)
          return
        }
        if (user != null) {
          setOwnerId(user.id)
        }
        setFormError(reviewErrorMessage(caught, 'Không tải được đánh giá của bạn.'))
      })
    return () => {
      active = false
      controller.abort()
    }
  }, [customer, expireSession, getAccessToken, productId, reloadTick, user])

  async function submit() {
    const token = getAccessToken()
    if (!token || submitLock.current || rating < 1 || rating > 5 || comment.trim().length > COMMENT_MAX) {
      return
    }
    submitLock.current = true
    setSubmitting(true)
    setFormError(null)
    setNotice(null)
    const text = comment.trim()
    try {
      const saved = ownReview
        ? await updateReview(token, ownReview.id, { rating, comment: text || undefined })
        : await createReview(token, { productId, rating, comment: text || undefined })
      if (getAccessToken() !== token) {
        return
      }
      setMine(saved)
      setOwnerId(user?.id ?? null)
      setEditing(false)
      setNotice(ownReview ? 'Đã cập nhật đánh giá.' : 'Đã gửi đánh giá.')
      setReloadTick((current) => current + 1)
    } catch (caught) {
      if (caught instanceof ReviewApiError && caught.status === 401) {
        if (getAccessToken() === token) {
          expireSession(token)
        }
        return
      }
      setFormError(reviewErrorMessage(caught, 'Không gửi được đánh giá.'))
      if (caught instanceof ReviewApiError && (caught.status === 409 || caught.status === 404)) {
        // Already reviewed (e.g. from another tab) or own review removed: resync with the server.
        setEditing(false)
        setReloadTick((current) => current + 1)
      }
    } finally {
      submitLock.current = false
      setSubmitting(false)
    }
  }

  async function remove() {
    const token = getAccessToken()
    if (!token || !ownReview || submitLock.current) {
      return
    }
    submitLock.current = true
    setSubmitting(true)
    setFormError(null)
    setNotice(null)
    try {
      await deleteReview(token, ownReview.id)
      if (getAccessToken() !== token) {
        return
      }
      setMine(null)
      setRating(0)
      setComment('')
      setConfirmDelete(false)
      setNotice('Đã xóa đánh giá.')
      setReloadTick((current) => current + 1)
    } catch (caught) {
      if (caught instanceof ReviewApiError && caught.status === 401 && getAccessToken() === token) {
        expireSession(token)
      }
      setFormError(reviewErrorMessage(caught, 'Không xóa được đánh giá.'))
    } finally {
      submitLock.current = false
      setSubmitting(false)
    }
  }

  const ownReview = ownerId === user?.id ? mine : null
  const ownLoaded = customer && ownerId === user?.id
  const showForm = ownLoaded && (!ownReview || editing)

  return (
    <section className="mt-12 border-t border-line pt-8">
      <h2 className="text-2xl font-semibold tracking-tight text-ink">Đánh giá</h2>
      {!publicPage && !publicError ? <p className="mt-3 text-sm text-ash">Đang tải đánh giá...</p> : null}
      {publicPage ? (
        <p className="mt-3 text-sm text-ink">
          {publicPage.reviewCount} đánh giá
          {publicPage.reviewCount > 0 && publicPage.averageRating != null
            ? ` · trung bình ${publicPage.averageRating.toFixed(2)}/5`
            : ''}
        </p>
      ) : null}
      {publicError ? (
        <div className="mt-4 flex flex-wrap items-center gap-3" role="alert">
          <p className="text-sm text-ink">{publicError}</p>
          <button
            type="button"
            className={`inline-flex min-h-11 items-center bg-neon px-4 text-sm font-semibold text-ink ${focusRing}`}
            onClick={() => setReloadTick((current) => current + 1)}
          >
            Thử lại
          </button>
        </div>
      ) : null}
      {publicPage && publicPage.content.length === 0 ? (
        <p className="mt-4 text-sm text-ash">Chưa có đánh giá công khai.</p>
      ) : null}
      {publicPage && publicPage.content.length > 0 ? (
        <ul className="mt-4 border-t border-line">
          {publicPage.content.map((review) => (
            <li key={review.id} className="border-b border-line py-4">
              <p className="flex flex-wrap items-center gap-x-2 text-sm font-semibold text-ink">
                <span>{review.reviewerName}</span>
                <Stars value={review.rating} />
                <span className="font-normal text-ash">{formatDate(review.createdAt)}</span>
              </p>
              {review.comment ? <p className="mt-2 text-sm leading-6 text-ink">{review.comment}</p> : null}
            </li>
          ))}
        </ul>
      ) : null}
      {publicPage && publicPage.totalPages > 1 ? (
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <button
            type="button"
            className={`inline-flex min-h-11 items-center border border-ink px-4 text-sm font-semibold text-ink disabled:text-ash ${focusRing}`}
            disabled={publicPage.first}
            onClick={() => setPage((current) => Math.max(0, current - 1))}
          >
            Trang trước
          </button>
          <button
            type="button"
            className={`inline-flex min-h-11 items-center border border-ink px-4 text-sm font-semibold text-ink disabled:text-ash ${focusRing}`}
            disabled={publicPage.last}
            onClick={() => setPage((current) => current + 1)}
          >
            Trang sau
          </button>
        </div>
      ) : null}

      {!isInitializing && !user ? (
        <p className="mt-6 text-sm text-ink">
          <Link to="/login" state={{ from: `${location.pathname}${location.search}` }} className={`font-semibold underline ${focusRing}`}>
            Đăng nhập
          </Link>{' '}
          để gửi đánh giá.
        </p>
      ) : null}
      {user && !hasCustomerRole(user.roles) ? (
        <p className="mt-6 text-sm text-ash">Gửi đánh giá dành cho tài khoản khách hàng.</p>
      ) : null}
      {customer ? (
        <div className="mt-6 max-w-xl">
          {!ownLoaded && !formError ? <p className="text-sm text-ash">Đang kiểm tra đánh giá của bạn...</p> : null}
          {ownLoaded && !ownReview ? (
            <p className="text-sm leading-6 text-ash">
              Chỉ đánh giá được sản phẩm trong đơn đã giao. Máy chủ kiểm tra điều kiện khi gửi.
            </p>
          ) : null}
          {ownReview && !editing ? (
            <div className="mt-4 border border-line p-4">
              <p className="flex flex-wrap items-center gap-x-2 text-sm font-semibold text-ink">
                <span>Đánh giá của bạn</span>
                <Stars value={ownReview.rating} />
                <span className={ownReview.status === 'HIDDEN' ? 'bg-sun px-2 py-0.5 font-normal' : 'font-normal text-ash'}>
                  {REVIEW_STATUS[ownReview.status] ?? ownReview.status}
                </span>
              </p>
              {ownReview.comment ? <p className="mt-2 text-sm text-ink">{ownReview.comment}</p> : null}
              <div className="mt-4 flex flex-wrap gap-3">
                <button
                  type="button"
                  className={`inline-flex min-h-11 items-center border border-ink px-4 text-sm font-semibold text-ink ${focusRing}`}
                  onClick={() => {
                    setRating(ownReview.rating)
                    setComment(ownReview.comment ?? '')
                    setConfirmDelete(false)
                    setNotice(null)
                    setFormError(null)
                    setEditing(true)
                  }}
                >
                  Sửa
                </button>
                {confirmDelete ? (
                  <button
                    type="button"
                    className={`inline-flex min-h-11 items-center bg-ink px-4 text-sm font-semibold text-white disabled:bg-line ${focusRing}`}
                    disabled={submitting}
                    onClick={() => void remove()}
                  >
                    {submitting ? 'Đang xóa...' : 'Xác nhận xóa'}
                  </button>
                ) : (
                  <button
                    type="button"
                    className={`inline-flex min-h-11 items-center text-sm font-semibold text-ink underline ${focusRing}`}
                    onClick={() => setConfirmDelete(true)}
                  >
                    Xóa
                  </button>
                )}
                {confirmDelete ? (
                  <button
                    type="button"
                    className={`inline-flex min-h-11 items-center px-2 text-sm font-semibold text-ink underline ${focusRing}`}
                    disabled={submitting}
                    onClick={() => setConfirmDelete(false)}
                  >
                    Giữ lại
                  </button>
                ) : null}
              </div>
            </div>
          ) : null}
          {showForm ? (
            <form
              className="mt-4"
              onSubmit={(event) => {
                event.preventDefault()
                void submit()
              }}
            >
              <fieldset>
                <legend className="text-sm text-ink">Số sao</legend>
                <div className="mt-2 flex flex-wrap gap-2">
                  {[1, 2, 3, 4, 5].map((value) => (
                    <label key={value} className="inline-flex min-h-11 items-center gap-2 border border-line px-3 text-sm text-ink">
                      <input
                        type="radio"
                        aria-label={`${value} sao`}
                        name={`rating-${productId}`}
                        checked={rating === value}
                        onChange={() => setRating(value)}
                      />
                      {value}
                    </label>
                  ))}
                </div>
              </fieldset>
              <label className="mt-4 block text-sm text-ink">
                Nội dung
                <textarea
                  value={comment}
                  maxLength={COMMENT_MAX}
                  onChange={(event) => setComment(event.target.value)}
                  className="mt-2 min-h-24 w-full border border-line px-3 py-3 text-sm text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
                />
              </label>
              <p className="mt-1 text-xs text-ash">
                Không bắt buộc · {comment.trim().length}/{COMMENT_MAX} ký tự
              </p>
              {rating < 1 ? <p className="mt-2 text-sm text-ash">Chọn số sao từ 1 đến 5.</p> : null}
              <div className="mt-4 flex flex-wrap gap-3">
                <button
                  type="submit"
                  className={`inline-flex min-h-11 items-center bg-neon px-4 text-sm font-semibold text-ink disabled:bg-line ${focusRing}`}
                  disabled={submitting || rating < 1 || comment.trim().length > COMMENT_MAX}
                >
                  {submitting ? 'Đang gửi...' : ownReview ? 'Lưu đánh giá' : 'Gửi đánh giá'}
                </button>
                {ownReview ? (
                  <button
                    type="button"
                    className={`inline-flex min-h-11 items-center border border-ink px-4 text-sm font-semibold text-ink ${focusRing}`}
                    disabled={submitting}
                    onClick={() => {
                      setRating(ownReview.rating)
                      setComment(ownReview.comment ?? '')
                      setFormError(null)
                      setEditing(false)
                    }}
                  >
                    Hủy sửa
                  </button>
                ) : null}
              </div>
            </form>
          ) : null}
          {formError ? (
            <p className="mt-3 text-sm text-ink" role="alert">
              {formError}
            </p>
          ) : null}
          {notice ? <p className="mt-3 text-sm text-ink">{notice}</p> : null}
        </div>
      ) : null}
    </section>
  )
}

function isAbort(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError'
}

export default ProductReviews
