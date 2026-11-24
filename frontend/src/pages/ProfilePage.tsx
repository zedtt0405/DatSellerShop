import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import {
  AccountApiError,
  accountErrorMessage,
  getProfile,
  updateProfile,
  validateProfile,
  type ProfileResponse,
} from '../api/account.ts'
import { useAuth } from '../auth/auth-context.ts'

const fieldClass =
  'mt-2 min-h-11 w-full border border-line bg-white px-3 text-sm text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'
const focusRing = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'

const statusLabel: Record<string, string> = {
  ACTIVE: 'Đang hoạt động',
  INACTIVE: 'Ngừng hoạt động',
  BLOCKED: 'Bị khóa',
}

function ProfilePage() {
  const { user, isInitializing } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (isInitializing || user) {
      return
    }
    const timer = window.setTimeout(() => {
      if (window.location.pathname === '/profile') {
        navigate('/login', { replace: true, state: { from: '/profile' } })
      }
    }, 0)
    return () => window.clearTimeout(timer)
  }, [isInitializing, user, navigate])

  if (isInitializing) {
    return (
      <section className="mx-auto max-w-3xl px-4 py-12 md:px-6">
        <p className="text-sm text-ash">Đang kiểm tra phiên...</p>
      </section>
    )
  }

  if (!user) {
    return (
      <section className="mx-auto max-w-3xl px-4 py-12 md:px-6">
        <p className="text-sm text-ash">Đang chuyển đến đăng nhập...</p>
      </section>
    )
  }

  return <ProfileEditor key={user.id} />
}

function ProfileEditor() {
  const { getAccessToken, expireSession, syncAccount } = useAuth()
  const [profile, setProfile] = useState<ProfileResponse | null>(null)
  const [fullName, setFullName] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [phase, setPhase] = useState<'loading' | 'ready' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const requestSeq = useRef(0)
  const hasProfile = useRef(false)

  useEffect(() => {
    const token = getAccessToken()
    if (!token) {
      return
    }
    const seq = ++requestSeq.current
    const controller = new AbortController()
    let active = true
    if (!hasProfile.current) {
      setPhase('loading')
    }
    void getProfile(token, controller.signal)
      .then((next) => {
        if (!active || seq !== requestSeq.current || getAccessToken() !== token) {
          return
        }
        hasProfile.current = true
        setProfile(next)
        setFullName(next.fullName)
        setPhoneNumber(next.phoneNumber ?? '')
        setPhase('ready')
        setError(null)
        syncAccount({ fullName: next.fullName, phoneNumber: next.phoneNumber })
      })
      .catch((caught: unknown) => {
        if (!active || seq !== requestSeq.current || isAbort(caught)) {
          return
        }
        if (caught instanceof AccountApiError && caught.status === 401 && getAccessToken() === token) {
          expireSession(token)
        }
        setError(accountErrorMessage(caught, 'Không tải được thông tin tài khoản.'))
        if (!hasProfile.current) {
          setProfile(null)
          setPhase('error')
        }
      })
    return () => {
      active = false
      controller.abort()
    }
  }, [expireSession, getAccessToken, reloadKey, syncAccount])

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting || !profile) {
      return
    }
    const validation = validateProfile({ fullName, phoneNumber })
    if (validation) {
      setSuccess(null)
      setError(validation)
      return
    }
    const token = getAccessToken()
    if (!token) {
      return
    }
    const seq = ++requestSeq.current
    setSubmitting(true)
    setError(null)
    setSuccess(null)
    try {
      const next = await updateProfile(token, { fullName, phoneNumber })
      if (seq !== requestSeq.current || getAccessToken() !== token) {
        return
      }
      setProfile(next)
      setFullName(next.fullName)
      setPhoneNumber(next.phoneNumber ?? '')
      syncAccount({ fullName: next.fullName, phoneNumber: next.phoneNumber })
      setSuccess('Đã cập nhật thông tin tài khoản.')
    } catch (caught) {
      if (caught instanceof AccountApiError && caught.status === 401 && getAccessToken() === token) {
        expireSession(token)
      }
      setError(accountErrorMessage(caught, 'Không cập nhật được thông tin tài khoản.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="mx-auto max-w-3xl px-4 py-12 md:px-6 md:py-16">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h1 className="text-3xl font-semibold tracking-tight text-ink md:text-4xl">Tài khoản</h1>
        <Link to="/addresses" className={`inline-flex min-h-11 items-center text-sm font-semibold text-ink underline ${focusRing}`}>
          Địa chỉ giao hàng
        </Link>
      </div>

      {phase === 'loading' ? <p className="mt-8 text-sm text-ash">Đang tải thông tin tài khoản...</p> : null}

      {phase === 'error' ? (
        <div className="mt-8" role="alert">
          <p className="text-sm text-ink">{error ?? 'Chưa tải được thông tin tài khoản.'}</p>
          <button
            type="button"
            className={`mt-4 inline-flex min-h-11 items-center bg-neon px-4 text-sm font-semibold text-ink hover:bg-neon/80 ${focusRing}`}
            onClick={() => {
              setPhase('loading')
              setError(null)
              setReloadKey((current) => current + 1)
            }}
          >
            Thử lại
          </button>
        </div>
      ) : null}

      {phase === 'ready' && profile ? (
        <>
          <dl className="mt-8 grid gap-4 border border-line p-4 sm:grid-cols-2">
            <Info label="Tên đăng nhập" value={profile.username} />
            <Info label="Email" value={profile.email} />
            <Info label="Trạng thái" value={statusLabel[profile.status] ?? profile.status} />
            <Info label="Vai trò" value={profile.roles.join(', ')} />
          </dl>

          <form className="mt-8 space-y-4" onSubmit={onSubmit} noValidate>
            <label className="block text-sm text-ash">
              Họ tên
              <input
                className={fieldClass}
                name="fullName"
                autoComplete="name"
                value={fullName}
                maxLength={100}
                required
                onChange={(event) => {
                  setFullName(event.target.value)
                  setSuccess(null)
                }}
              />
            </label>
            <label className="block text-sm text-ash">
              Số điện thoại
              <input
                className={fieldClass}
                name="phoneNumber"
                autoComplete="tel"
                inputMode="tel"
                value={phoneNumber}
                maxLength={20}
                onChange={(event) => {
                  setPhoneNumber(event.target.value)
                  setSuccess(null)
                }}
              />
            </label>
            <p className="text-xs leading-5 text-ash">Có thể để trống. Nếu nhập, dùng 8–20 ký tự gồm số, dấu cộng, gạch ngang hoặc khoảng trắng.</p>
            {error ? (
              <p className="border border-sun bg-sun/40 px-3 py-3 text-sm text-ink" role="alert">
                {error}
              </p>
            ) : null}
            {success ? (
              <p className="border border-neon bg-neon/40 px-3 py-3 text-sm text-ink" role="status">
                {success}
              </p>
            ) : null}
            <button
              type="submit"
              className={`inline-flex min-h-11 items-center bg-neon px-6 text-sm font-semibold text-ink hover:bg-neon/80 disabled:cursor-not-allowed disabled:opacity-60 ${focusRing}`}
              disabled={submitting}
            >
              {submitting ? 'Đang lưu...' : 'Lưu thay đổi'}
            </button>
          </form>
        </>
      ) : null}
    </section>
  )
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs font-medium text-ash">{label}</dt>
      <dd className="mt-1 break-words text-sm font-semibold text-ink">{value}</dd>
    </div>
  )
}

function isAbort(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError'
}

export default ProfilePage
