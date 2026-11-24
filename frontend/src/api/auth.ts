import { apiUrl } from './catalog.ts'

export class AuthApiError extends Error {
  readonly status: number
  readonly serverMessage: string | null

  constructor(message: string, status: number, serverMessage: string | null = null) {
    super(message)
    this.name = 'AuthApiError'
    this.status = status
    this.serverMessage = serverMessage
  }
}

export type SessionUser = {
  id: number
  username: string
  email: string
  fullName: string
  roles: string[]
  phoneNumber: string | null
  status: string | null
}

export type RegisterInput = {
  username: string
  email: string
  password: string
  fullName: string
  phoneNumber?: string
}

export type LoginResult = {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: SessionUser
}

const USERNAME_PATTERN = /^[a-zA-Z0-9._-]+$/
const PHONE_PATTERN = /^[0-9+\-\s]{8,20}$/

export function validateLogin(username: string, password: string): string | null {
  if (username.trim().length === 0 || password.length === 0) {
    return 'Nhập tên đăng nhập và mật khẩu.'
  }
  if (username.trim().length > 50 || password.length > 72) {
    return 'Tên đăng nhập hoặc mật khẩu vượt quá độ dài cho phép.'
  }
  return null
}

export function validateRegister(input: RegisterInput): string | null {
  const username = input.username.trim()
  const email = input.email.trim()
  const fullName = input.fullName.trim()
  const phone = input.phoneNumber?.trim() ?? ''

  if (username.length < 3 || username.length > 50 || !USERNAME_PATTERN.test(username)) {
    return 'Tên đăng nhập cần 3–50 ký tự và chỉ gồm chữ, số, dấu chấm, gạch dưới hoặc gạch ngang.'
  }
  if (email.length === 0 || email.length > 100 || !email.includes('@')) {
    return 'Email không hợp lệ.'
  }
  if (input.password.length < 8 || input.password.length > 72) {
    return 'Mật khẩu cần từ 8 đến 72 ký tự.'
  }
  if (fullName.length === 0 || fullName.length > 100) {
    return 'Họ tên là bắt buộc và không quá 100 ký tự.'
  }
  if (phone.length > 0 && !PHONE_PATTERN.test(phone)) {
    return 'Số điện thoại cần 8–20 ký tự, chỉ gồm số, dấu cộng, gạch ngang hoặc khoảng trắng.'
  }
  return null
}

export function authErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof AuthApiError)) {
    return fallback
  }
  if (error.status === 0) {
    return 'Không kết nối được máy chủ.'
  }
  if (error.status === 401) {
    return 'Tên đăng nhập hoặc mật khẩu không đúng.'
  }
  if (error.status === 403) {
    return 'Bạn không có quyền thực hiện thao tác này.'
  }
  if (error.status === 409) {
    if (error.serverMessage === 'Username already exists') {
      return 'Tên đăng nhập đã tồn tại.'
    }
    if (error.serverMessage === 'Email already exists') {
      return 'Email đã tồn tại.'
    }
    return 'Tài khoản đã tồn tại.'
  }
  if (error.status === 400) {
    return error.serverMessage
      ? `Dữ liệu không hợp lệ. ${error.serverMessage}`
      : 'Dữ liệu không hợp lệ.'
  }
  if (error.status >= 500) {
    return 'Máy chủ gặp sự cố. Thử lại sau.'
  }
  return fallback
}

export async function registerAccount(input: RegisterInput, signal?: AbortSignal): Promise<SessionUser> {
  const body: Record<string, string> = {
    username: input.username.trim(),
    email: input.email.trim(),
    password: input.password,
    fullName: input.fullName.trim(),
  }
  const phone = input.phoneNumber?.trim() ?? ''
  if (phone.length > 0) {
    body.phoneNumber = phone
  }
  const payload = await requestJson('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(body),
    signal,
  })
  const user = readSessionUser(payload)
  if (!user) {
    throw new AuthApiError('Dữ liệu tài khoản không đúng định dạng.', 201)
  }
  return user
}

export async function loginAccount(
  username: string,
  password: string,
  signal?: AbortSignal,
): Promise<LoginResult> {
  const payload = await requestJson('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: username.trim(), password }),
    signal,
  })
  if (!isRecord(payload) || typeof payload.accessToken !== 'string' || payload.accessToken.length === 0) {
    throw new AuthApiError('Dữ liệu đăng nhập không đúng định dạng.', 200)
  }
  if (typeof payload.tokenType !== 'string' || typeof payload.expiresIn !== 'number') {
    throw new AuthApiError('Dữ liệu đăng nhập không đúng định dạng.', 200)
  }
  const user = readSessionUser(payload.user)
  if (!user) {
    throw new AuthApiError('Dữ liệu đăng nhập không đúng định dạng.', 200)
  }
  return {
    accessToken: payload.accessToken,
    tokenType: payload.tokenType,
    expiresIn: payload.expiresIn,
    user,
  }
}

export async function fetchCurrentUser(token: string, signal?: AbortSignal): Promise<SessionUser> {
  const payload = await requestJson('/api/auth/me', {
    method: 'GET',
    token,
    signal,
  })
  const user = readSessionUser(payload)
  if (!user) {
    throw new AuthApiError('Dữ liệu tài khoản không đúng định dạng.', 200)
  }
  return user
}

type RequestOptions = {
  method: 'GET' | 'POST'
  body?: string
  token?: string
  signal?: AbortSignal
}

async function requestJson(path: string, options: RequestOptions): Promise<unknown> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body) {
    headers['Content-Type'] = 'application/json'
  }
  if (options.token) {
    headers.Authorization = `Bearer ${options.token}`
  }

  let response: Response
  try {
    response = await fetch(apiUrl(path), {
      method: options.method,
      headers,
      body: options.body,
      signal: options.signal,
    })
  } catch (error) {
    if (isAbortError(error)) {
      throw error
    }
    throw new AuthApiError('Không kết nối được máy chủ.', 0)
  }

  if (!response.ok) {
    const serverMessage = await readServerMessage(response)
    throw new AuthApiError(serverMessage ?? `HTTP ${response.status}`, response.status, serverMessage)
  }

  try {
    return (await response.json()) as unknown
  } catch {
    throw new AuthApiError('Máy chủ trả về dữ liệu không đọc được.', response.status)
  }
}

async function readServerMessage(response: Response): Promise<string | null> {
  try {
    const body: unknown = await response.json()
    if (isRecord(body) && typeof body.message === 'string' && body.message.length > 0) {
      return body.message
    }
  } catch {
    // The error body is not JSON.
  }
  return null
}

function readSessionUser(value: unknown): SessionUser | null {
  if (!isRecord(value)) {
    return null
  }
  if (typeof value.id !== 'number' || !Number.isFinite(value.id)) {
    return null
  }
  if (
    typeof value.username !== 'string' ||
    typeof value.email !== 'string' ||
    typeof value.fullName !== 'string' ||
    !Array.isArray(value.roles) ||
    value.roles.some((role) => typeof role !== 'string')
  ) {
    return null
  }
  const phoneNumber = typeof value.phoneNumber === 'string' ? value.phoneNumber : null
  const status = typeof value.status === 'string' ? value.status : null
  return {
    id: value.id,
    username: value.username,
    email: value.email,
    fullName: value.fullName,
    roles: value.roles as string[],
    phoneNumber,
    status,
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError'
}
