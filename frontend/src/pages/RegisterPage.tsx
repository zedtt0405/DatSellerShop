import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { authErrorMessage, validateRegister } from '../api/auth.ts'
import { useAuth } from '../auth/auth-context.ts'

const fieldClass =
  'mt-2 min-h-11 w-full border border-line bg-white px-3 text-sm text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'
const focusRing = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'

function RegisterPage() {
  const { register, user, isInitializing } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fullName, setFullName] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) {
      return
    }
    const input = { username, email, password, fullName, phoneNumber }
    const validation = validateRegister(input)
    if (validation) {
      setError(validation)
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await register(input)
      navigate('/login', { replace: true, state: { registered: true } })
    } catch (caught) {
      setError(authErrorMessage(caught, 'Không đăng ký được.'))
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
        <h1 className="text-3xl font-semibold tracking-tight text-ink">Đăng ký</h1>
        <p className="mt-4 text-sm leading-6 text-ash">Bạn đã đăng nhập với tên {user.fullName}.</p>
        <Link to="/" className={`mt-6 inline-flex min-h-11 items-center text-sm font-semibold text-ink underline ${focusRing}`}>
          Về trang chủ
        </Link>
      </section>
    )
  }

  return (
    <section className="mx-auto max-w-md px-4 py-12 md:px-6 md:py-16">
      <h1 className="text-3xl font-semibold tracking-tight text-ink md:text-4xl">Đăng ký</h1>
      <p className="mt-3 text-sm leading-6 text-ash">Tạo tài khoản khách hàng. Số điện thoại có thể bỏ trống.</p>
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
          Email
          <input
            className={fieldClass}
            name="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            maxLength={100}
            required
          />
        </label>
        <label className="block text-sm text-ash">
          Mật khẩu
          <input
            className={fieldClass}
            name="password"
            type={showPassword ? 'text' : 'password'}
            autoComplete="new-password"
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
        <label className="block text-sm text-ash">
          Họ tên
          <input
            className={fieldClass}
            name="fullName"
            autoComplete="name"
            value={fullName}
            onChange={(event) => setFullName(event.target.value)}
            maxLength={100}
            required
          />
        </label>
        <label className="block text-sm text-ash">
          Số điện thoại
          <input
            className={fieldClass}
            name="phoneNumber"
            autoComplete="tel"
            value={phoneNumber}
            onChange={(event) => setPhoneNumber(event.target.value)}
            maxLength={20}
          />
        </label>
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
          {submitting ? 'Đang đăng ký...' : 'Đăng ký'}
        </button>
      </form>
      <p className="mt-6 text-sm text-ash">
        Đã có tài khoản?{' '}
        <Link to="/login" className={`font-semibold text-ink underline ${focusRing}`}>
          Đăng nhập
        </Link>
      </p>
    </section>
  )
}

export default RegisterPage
