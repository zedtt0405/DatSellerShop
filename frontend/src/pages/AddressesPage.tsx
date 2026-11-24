import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import {
  AccountApiError,
  accountErrorMessage,
  createAddress,
  deleteAddress,
  listAddresses,
  setDefaultAddress,
  updateAddress,
  validateAddress,
  type AddressInput,
  type AddressResponse,
} from '../api/account.ts'
import { useAuth } from '../auth/auth-context.ts'

const emptyForm = (): AddressInput => ({
  recipientName: '',
  phoneNumber: '',
  province: '',
  district: '',
  ward: '',
  addressLine: '',
  isDefault: false,
})

const fieldClass =
  'mt-2 min-h-11 w-full border border-line bg-white px-3 text-sm text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'
const focusRing = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink'

function AddressesPage() {
  const { user, isInitializing } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (isInitializing || user) {
      return
    }
    const timer = window.setTimeout(() => {
      if (window.location.pathname === '/addresses') {
        navigate('/login', { replace: true, state: { from: '/addresses' } })
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

  return <AddressBook key={user.id} />
}

function AddressBook() {
  const { getAccessToken, expireSession } = useAuth()
  const [addresses, setAddresses] = useState<AddressResponse[] | null>(null)
  const [phase, setPhase] = useState<'loading' | 'ready' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [form, setForm] = useState<AddressInput>(emptyForm)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [pending, setPending] = useState<{ id: number; action: 'default' | 'delete' } | null>(null)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const requestSeq = useRef(0)
  const hasList = useRef(false)

  useEffect(() => {
    const token = getAccessToken()
    if (!token) {
      return
    }
    const seq = ++requestSeq.current
    const controller = new AbortController()
    let active = true
    if (!hasList.current) {
      setPhase('loading')
    }
    void listAddresses(token, controller.signal)
      .then((next) => {
        if (!active || seq !== requestSeq.current || getAccessToken() !== token) {
          return
        }
        hasList.current = true
        setAddresses(next)
        setPhase('ready')
        setError(null)
      })
      .catch((caught: unknown) => {
        if (!active || seq !== requestSeq.current || isAbort(caught)) {
          return
        }
        if (caught instanceof AccountApiError && caught.status === 401 && getAccessToken() === token) {
          expireSession(token)
        }
        setError(accountErrorMessage(caught, 'Không tải được địa chỉ.'))
        if (!hasList.current) {
          setAddresses(null)
          setPhase('error')
        }
      })
    return () => {
      active = false
      controller.abort()
    }
  }, [expireSession, getAccessToken, reloadKey])

  function noteFailure(caught: unknown, token: string) {
    if (caught instanceof AccountApiError && caught.status === 401 && getAccessToken() === token) {
      expireSession(token)
    }
    setError(accountErrorMessage(caught, 'Không tải được địa chỉ.'))
  }

  function currentToken(): string | null {
    return getAccessToken()
  }

  async function refresh(token: string, seq: number): Promise<boolean> {
    try {
      const next = await listAddresses(token)
      if (seq !== requestSeq.current || getAccessToken() !== token) {
        return false
      }
      hasList.current = true
      setAddresses(next)
      setPhase('ready')
      setError(null)
      setNotice(null)
      return true
    } catch (caught) {
      if (seq !== requestSeq.current || getAccessToken() !== token) {
        return false
      }
      if (caught instanceof AccountApiError && caught.status === 401) {
        expireSession(token)
      }
      setNotice('Thay đổi đã được lưu, nhưng chưa tải lại được danh sách.')
      return false
    }
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) {
      return
    }
    const validation = validateAddress(form)
    if (validation) {
      setNotice(null)
      setError(validation)
      return
    }
    const token = currentToken()
    if (!token) {
      return
    }
    const seq = ++requestSeq.current
    const wasEditing = editingId != null
    setSubmitting(true)
    setError(null)
    setNotice(null)
    try {
      const saved = editingId == null ? await createAddress(token, form) : await updateAddress(token, editingId, form)
      if (seq !== requestSeq.current || getAccessToken() !== token) {
        return
      }
      hasList.current = true
      setAddresses((current) => applySaved(current ?? [], saved))
      setPhase('ready')
      setForm(emptyForm())
      setEditingId(null)
      const synced = await refresh(token, seq)
      if (synced) {
        setNotice(wasEditing ? 'Đã cập nhật địa chỉ.' : 'Đã thêm địa chỉ.')
      }
    } catch (caught) {
      if (getAccessToken() === token) {
        noteFailure(caught, token)
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function markDefault(addressId: number) {
    const token = currentToken()
    if (!token || pending != null) {
      return
    }
    const seq = ++requestSeq.current
    setPending({ id: addressId, action: 'default' })
    setError(null)
    setNotice(null)
    try {
      const saved = await setDefaultAddress(token, addressId)
      if (seq !== requestSeq.current || getAccessToken() !== token) {
        return
      }
      hasList.current = true
      setAddresses((current) => (current ? current.map((item) => ({ ...item, isDefault: item.id === saved.id })) : current))
      await refresh(token, seq)
    } catch (caught) {
      if (getAccessToken() === token) {
        noteFailure(caught, token)
      }
    } finally {
      setPending(null)
    }
  }

  async function remove(addressId: number) {
    const token = currentToken()
    if (!token || pending != null) {
      return
    }
    const seq = ++requestSeq.current
    setPending({ id: addressId, action: 'delete' })
    setError(null)
    setNotice(null)
    try {
      await deleteAddress(token, addressId)
      if (seq !== requestSeq.current || getAccessToken() !== token) {
        return
      }
      hasList.current = true
      setAddresses((current) => current?.filter((item) => item.id !== addressId) ?? current)
      if (editingId === addressId) {
        setEditingId(null)
        setForm(emptyForm())
      }
      setConfirmId(null)
      const synced = await refresh(token, seq)
      if (synced) {
        setNotice('Đã xóa địa chỉ.')
      }
    } catch (caught) {
      if (getAccessToken() === token) {
        noteFailure(caught, token)
      }
    } finally {
      setPending(null)
    }
  }

  function beginEdit(address: AddressResponse) {
    setEditingId(address.id)
    setConfirmId(null)
    setError(null)
    setNotice(null)
    setForm({
      recipientName: address.recipientName,
      phoneNumber: address.phoneNumber,
      province: address.province,
      district: address.district,
      ward: address.ward,
      addressLine: address.addressLine,
      isDefault: address.isDefault,
    })
  }

  return (
    <section className="mx-auto max-w-3xl px-4 py-12 md:px-6 md:py-16">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h1 className="text-3xl font-semibold tracking-tight text-ink md:text-4xl">Địa chỉ</h1>
        <Link to="/profile" className={`inline-flex min-h-11 items-center text-sm font-semibold text-ink underline ${focusRing}`}>
          Tài khoản
        </Link>
      </div>

      {phase === 'loading' ? <p className="mt-8 text-sm text-ash">Đang tải địa chỉ...</p> : null}

      {phase === 'error' && !addresses ? (
        <div className="mt-8" role="alert">
          <p className="text-sm text-ink">{error ?? 'Chưa tải được địa chỉ.'}</p>
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

      {phase === 'ready' && addresses && addresses.length === 0 ? (
        <p className="mt-8 text-base text-ink">Bạn chưa có địa chỉ giao hàng.</p>
      ) : null}

      {addresses && addresses.length > 0 ? (
        <ul className="mt-8 space-y-4">
          {addresses.map((address) => (
            <li key={address.id} className="border border-line p-4">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="text-base font-semibold text-ink">{address.recipientName}</h2>
                {address.isDefault ? (
                  <span className="inline-flex min-h-7 items-center bg-neon px-2 text-xs font-semibold text-ink">Mặc định</span>
                ) : null}
              </div>
              <p className="mt-2 text-sm text-ash">{address.phoneNumber}</p>
              <p className="mt-1 text-sm leading-6 text-ink">
                {address.addressLine}, {address.ward}, {address.district}, {address.province}
              </p>
              <div className="mt-4 flex flex-wrap gap-2">
                <button
                  type="button"
                  className={`inline-flex min-h-11 items-center border border-ink px-4 text-sm font-semibold text-ink ${focusRing}`}
                  onClick={() => beginEdit(address)}
                >
                  Sửa
                </button>
                {address.isDefault ? null : (
                  <button
                    type="button"
                    className={`inline-flex min-h-11 items-center px-4 text-sm font-semibold text-ink underline disabled:text-ash ${focusRing}`}
                    disabled={pending != null}
                    onClick={() => void markDefault(address.id)}
                  >
                    {pending?.id === address.id && pending.action === 'default' ? 'Đang đặt...' : 'Đặt mặc định'}
                  </button>
                )}
                {confirmId === address.id ? (
                  <button
                    type="button"
                    className={`inline-flex min-h-11 items-center bg-ink px-4 text-sm font-semibold text-white disabled:opacity-60 ${focusRing}`}
                    disabled={pending != null}
                    onClick={() => void remove(address.id)}
                  >
                    {pending?.id === address.id && pending.action === 'delete' ? 'Đang xóa...' : 'Xác nhận xóa'}
                  </button>
                ) : (
                  <button
                    type="button"
                    className={`inline-flex min-h-11 items-center px-4 text-sm font-semibold text-ink underline ${focusRing}`}
                    onClick={() => setConfirmId(address.id)}
                  >
                    Xóa
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      ) : null}

      {error && addresses ? (
        <p className="mt-6 border border-sun bg-sun/40 px-3 py-3 text-sm text-ink" role="alert">
          {error}
        </p>
      ) : null}
      {notice ? (
        <p className="mt-6 border border-neon bg-neon/40 px-3 py-3 text-sm text-ink" role="status">
          {notice}
        </p>
      ) : null}

      {phase !== 'error' || addresses ? (
        <form className="mt-10 space-y-4 border-t border-line pt-8" onSubmit={onSubmit} noValidate>
          <h2 className="text-xl font-semibold text-ink">{editingId == null ? 'Thêm địa chỉ' : 'Sửa địa chỉ'}</h2>
          <Field label="Người nhận" name="recipientName" value={form.recipientName} autoComplete="name" maxLength={100} onChange={(value) => setForm({ ...form, recipientName: value })} />
          <Field label="Số điện thoại" name="phoneNumber" value={form.phoneNumber} autoComplete="tel" maxLength={20} onChange={(value) => setForm({ ...form, phoneNumber: value })} />
          <div className="grid gap-4 sm:grid-cols-3">
            <Field label="Tỉnh hoặc thành phố" name="province" value={form.province} maxLength={100} onChange={(value) => setForm({ ...form, province: value })} />
            <Field label="Quận hoặc huyện" name="district" value={form.district} maxLength={100} onChange={(value) => setForm({ ...form, district: value })} />
            <Field label="Phường hoặc xã" name="ward" value={form.ward} maxLength={100} onChange={(value) => setForm({ ...form, ward: value })} />
          </div>
          <Field label="Địa chỉ cụ thể" name="addressLine" value={form.addressLine} autoComplete="street-address" maxLength={255} onChange={(value) => setForm({ ...form, addressLine: value })} />
          <label className="flex min-h-11 items-center gap-3 text-sm text-ink">
            <input
              type="checkbox"
              className="size-4 accent-ink"
              checked={form.isDefault}
              onChange={(event) => setForm({ ...form, isDefault: event.target.checked })}
            />
            Đặt làm địa chỉ mặc định
          </label>
          <div className="flex flex-wrap gap-3">
            <button
              type="submit"
              className={`inline-flex min-h-11 items-center bg-neon px-6 text-sm font-semibold text-ink hover:bg-neon/80 disabled:cursor-not-allowed disabled:opacity-60 ${focusRing}`}
              disabled={submitting}
            >
              {submitting ? 'Đang lưu...' : editingId == null ? 'Thêm địa chỉ' : 'Lưu địa chỉ'}
            </button>
            {editingId != null ? (
              <button
                type="button"
                className={`inline-flex min-h-11 items-center px-4 text-sm font-semibold text-ink underline ${focusRing}`}
                onClick={() => {
                  setEditingId(null)
                  setForm(emptyForm())
                  setError(null)
                }}
              >
                Hủy sửa
              </button>
            ) : null}
          </div>
        </form>
      ) : null}
    </section>
  )
}

function Field({
  label,
  name,
  value,
  maxLength,
  autoComplete,
  onChange,
}: {
  label: string
  name: string
  value: string
  maxLength: number
  autoComplete?: string
  onChange: (value: string) => void
}) {
  return (
    <label className="block text-sm text-ash">
      {label}
      <input
        className={fieldClass}
        name={name}
        value={value}
        maxLength={maxLength}
        autoComplete={autoComplete}
        required
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  )
}

function applySaved(current: AddressResponse[], saved: AddressResponse): AddressResponse[] {
  const next = current.some((item) => item.id === saved.id)
    ? current.map((item) => (item.id === saved.id ? saved : item))
    : [...current, saved]
  if (!saved.isDefault) {
    return next
  }
  return next.map((item) => (item.id === saved.id ? saved : { ...item, isDefault: false }))
}

function isAbort(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError'
}

export default AddressesPage
