import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { authErrorMessage, validateLogin } from '../api/auth.ts'
import { useAuth } from '../auth/auth-context.ts'

const fieldClass =
  'mt-2 min-h-11 w-full border border-line bg-white px-3 text-sm text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'
const focusRing = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'

type LoginLocationState = {
  from?: string
  registered?: boolean
}

function safeNext(from: unknown): string {
  if (typeof from !== 'string' || !from.startsWith('/') || from.startsWith('//')) {
    return '/'
  }
  if (from.startsWith('/login') || from.startsWith('/register')) {
    return '/'
  }
  return from
}

function LoginPage() {
  const { login, user, isInitializing } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = (location.state ?? null) as LoginLocationState | null
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) {
      return
    }
    const validation = validateLogin(username, password)
    if (validation) {
      setError(validation)
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await login(username, password)
      navigate(safeNext(state?.from), { replace: true })
    } catch (caught) {
      setError(authErrorMessage(caught, 'Không đăng nhập được.'))
    } finally {
      setSubmitting(false)
    }
  }

  if (isInitializing) {
    return (
      <section className="mx-auto max-w-md px-4 py-12 md:px-6 md:py-16">
        <p className="text-sm text-ash">Đang kiểm tra phiên...</p>
      </section>
    )
  }

  if (user) {
    return (
      <section className="mx-auto max-w-md px-4 py-12 md:px-6 md:py-16">
        <h1 className="text-3xl font-semibold tracking-tight text-ink">Đăng nhập</h1>
        <p className="mt-4 text-sm leading-6 text-ash">Bạn đã đăng nhập với tên {user.fullName}.</p>
        <Link to="/" className={`mt-6 inline-flex min-h-11 items-center text-sm font-semibold text-ink underline ${focusRing}`}>
          Về trang chủ
        </Link>
      </section>
    )
  }

  return (
    <section className="mx-auto max-w-md px-4 py-12 md:px-6 md:py-16">
      <h1 className="text-3xl font-semibold tracking-tight text-ink md:text-4xl">Đăng nhập</h1>
      <p className="mt-3 text-sm leading-6 text-ash">Dùng tên đăng nhập và mật khẩu của tài khoản DatSellerShop.</p>

      {state?.registered ? (
        <p className="mt-6 border border-neon bg-neon/40 px-3 py-3 text-sm text-ink" role="status">
          Đăng ký thành công. Hãy đăng nhập để tiếp tục.
        </p>
      ) : null}

      <form className="mt-8 space-y-4" onSubmit={onSubmit} noValidate>
        <label className="block text-sm text-ash">
          Tên đăng nhập
          <input
            className={fieldClass}
            name="username"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            maxLength={50}
            required
          />
        </label>
        <label className="block text-sm text-ash">
          Mật khẩu
          <input
            className={fieldClass}
            name="password"
            type={showPassword ? 'text' : 'password'}
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            maxLength={72}
            required
          />
        </label>
        <button
          type="button"
          className={`min-h-11 text-sm font-medium text-ink underline ${focusRing}`}
          onClick={() => setShowPassword((current) => !current)}
        >
          {showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
        </button>
        {error ? (
          <p className="border border-sun bg-sun/40 px-3 py-3 text-sm text-ink" role="alert">
            {error}
          </p>
        ) : null}
        <button
          type="submit"
          className={`inline-flex min-h-11 w-full items-center justify-center bg-neon px-4 text-sm font-semibold text-ink hover:bg-neon/80 disabled:cursor-not-allowed disabled:opacity-60 ${focusRing}`}
          disabled={submitting}
        >
          {submitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
        </button>
      </form>
      <p className="mt-6 text-sm text-ash">
        Chưa có tài khoản?{' '}
        <Link to="/register" className={`font-semibold text-ink underline ${focusRing}`}>
          Đăng ký
        </Link>
      </p>
    </section>
  )
}

export default LoginPage
