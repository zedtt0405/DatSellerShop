import { apiUrl } from './catalog.ts'

export class AccountApiError extends Error {
  readonly status: number
  readonly serverMessage: string | null

  constructor(message: string, status: number, serverMessage: string | null = null) {
    super(message)
    this.name = 'AccountApiError'
    this.status = status
    this.serverMessage = serverMessage
  }
}

export type ProfileResponse = {
  id: number
  username: string
  email: string
  fullName: string
  phoneNumber: string | null
  status: string
  roles: string[]
  createdAt: string
  updatedAt: string
}

export type ProfileUpdateInput = {
  fullName: string
  phoneNumber: string
}

export type AddressResponse = {
  id: number
  recipientName: string
  phoneNumber: string
  province: string
  district: string
  ward: string
  addressLine: string
  isDefault: boolean
  createdAt: string
  updatedAt: string
}

export type AddressInput = {
  recipientName: string
  phoneNumber: string
  province: string
  district: string
  ward: string
  addressLine: string
  isDefault: boolean
}

const PHONE_PATTERN = /^[0-9+\-\s]{8,20}$/

export function validateProfile(input: ProfileUpdateInput): string | null {
  const fullName = input.fullName.trim()
  const phone = input.phoneNumber.trim()
  if (fullName.length === 0 || fullName.length > 100) {
    return 'Họ tên là bắt buộc và không quá 100 ký tự.'
  }
  if (phone.length > 0 && (phone.length > 20 || !PHONE_PATTERN.test(phone))) {
    return 'Số điện thoại cần 8–20 ký tự, chỉ gồm số, dấu cộng, gạch ngang hoặc khoảng trắng.'
  }
  return null
}

export function validateAddress(input: AddressInput): string | null {
  const fields: Array<[string, string, number]> = [
    ['Người nhận', input.recipientName, 100],
    ['Tỉnh hoặc thành phố', input.province, 100],
    ['Quận hoặc huyện', input.district, 100],
    ['Phường hoặc xã', input.ward, 100],
    ['Địa chỉ cụ thể', input.addressLine, 255],
  ]
  for (const [label, value, max] of fields) {
    const trimmed = value.trim()
    if (trimmed.length === 0 || trimmed.length > max) {
      return `${label} là bắt buộc và không quá ${max} ký tự.`
    }
  }
  const phone = input.phoneNumber.trim()
  if (phone.length === 0 || phone.length > 20 || !PHONE_PATTERN.test(phone)) {
    return 'Số điện thoại cần 8–20 ký tự, chỉ gồm số, dấu cộng, gạch ngang hoặc khoảng trắng.'
  }
  return null
}

export function accountErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof AccountApiError)) {
    return fallback
  }
  if (error.status === 0) {
    return 'Không kết nối được máy chủ.'
  }
  if (error.status === 401) {
    return 'Phiên đăng nhập đã hết hiệu lực.'
  }
  if (error.status === 403) {
    return 'Bạn không có quyền thực hiện thao tác này.'
  }
  if (error.status === 404) {
    return 'Không tìm thấy dữ liệu.'
  }
  if (error.status === 400) {
    return error.serverMessage ? `Dữ liệu không hợp lệ. ${error.serverMessage}` : 'Dữ liệu không hợp lệ.'
  }
  if (error.status >= 500) {
    return 'Máy chủ gặp sự cố. Thử lại sau.'
  }
  return fallback
}

export async function getProfile(token: string, signal?: AbortSignal): Promise<ProfileResponse> {
  return readProfile(await requestJson('/api/profile', { method: 'GET', token, signal }))
}

export async function updateProfile(
  token: string,
  input: ProfileUpdateInput,
  signal?: AbortSignal,
): Promise<ProfileResponse> {
  const phone = input.phoneNumber.trim()
  return readProfile(
    await requestJson('/api/profile', {
      method: 'PUT',
      token,
      signal,
      body: JSON.stringify({
        fullName: input.fullName.trim(),
        phoneNumber: phone.length > 0 ? phone : null,
      }),
    }),
  )
}

export async function listAddresses(token: string, signal?: AbortSignal): Promise<AddressResponse[]> {
  const payload = await requestJson('/api/addresses', { method: 'GET', token, signal })
  if (!Array.isArray(payload)) {
    throw new AccountApiError('Dữ liệu địa chỉ không đúng định dạng.', 200)
  }
  return payload.map(readAddress)
}

export async function createAddress(
  token: string,
  input: AddressInput,
  signal?: AbortSignal,
): Promise<AddressResponse> {
  return readAddress(
    await requestJson('/api/addresses', {
      method: 'POST',
      token,
      signal,
      body: JSON.stringify(addressBody(input)),
    }),
  )
}

export async function updateAddress(
  token: string,
  addressId: number,
  input: AddressInput,
  signal?: AbortSignal,
): Promise<AddressResponse> {
  return readAddress(
    await requestJson(`/api/addresses/${addressId}`, {
      method: 'PUT',
      token,
      signal,
      body: JSON.stringify(addressBody(input)),
    }),
  )
}

export async function deleteAddress(token: string, addressId: number, signal?: AbortSignal): Promise<void> {
  await requestEmpty(`/api/addresses/${addressId}`, { method: 'DELETE', token, signal })
}

export async function setDefaultAddress(
  token: string,
  addressId: number,
  signal?: AbortSignal,
): Promise<AddressResponse> {
  return readAddress(
    await requestJson(`/api/addresses/${addressId}/default`, {
      method: 'PATCH',
      token,
      signal,
    }),
  )
}

function addressBody(input: AddressInput) {
  return {
    recipientName: input.recipientName.trim(),
    phoneNumber: input.phoneNumber.trim(),
    province: input.province.trim(),
    district: input.district.trim(),
    ward: input.ward.trim(),
    addressLine: input.addressLine.trim(),
    isDefault: input.isDefault,
  }
}

type JsonMethod = 'GET' | 'POST' | 'PUT' | 'PATCH'

async function requestJson(
  path: string,
  options: { method: JsonMethod; token: string; body?: string; signal?: AbortSignal },
): Promise<unknown> {
  const response = await send(path, options)
  if (!response.ok) {
    throw await errorFrom(response)
  }
  try {
    return (await response.json()) as unknown
  } catch {
    throw new AccountApiError('Máy chủ trả về dữ liệu không đọc được.', response.status)
  }
}

async function requestEmpty(
  path: string,
  options: { method: 'DELETE'; token: string; signal?: AbortSignal },
): Promise<void> {
  const response = await send(path, options)
  if (response.status === 204) {
    return
  }
  if (!response.ok) {
    throw await errorFrom(response)
  }
  throw new AccountApiError('Máy chủ trả về dữ liệu không đọc được.', response.status)
}

async function send(
  path: string,
  options: { method: JsonMethod | 'DELETE'; token: string; body?: string; signal?: AbortSignal },
): Promise<Response> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${options.token}`,
  }
  if (options.body) {
    headers['Content-Type'] = 'application/json'
  }
  try {
    return await fetch(apiUrl(path), {
      method: options.method,
      headers,
      body: options.body,
      signal: options.signal,
    })
  } catch (error) {
    if (isAbortError(error)) {
      throw error
    }
    throw new AccountApiError('Không kết nối được máy chủ.', 0)
  }
}

async function errorFrom(response: Response): Promise<AccountApiError> {
  const serverMessage = await readServerMessage(response)
  return new AccountApiError(serverMessage ?? `HTTP ${response.status}`, response.status, serverMessage)
}

async function readServerMessage(response: Response): Promise<string | null> {
  try {
    const body: unknown = await response.json()
    if (!isRecord(body) || typeof body.message !== 'string' || body.message.length === 0) {
      return null
    }
    return body.message
  } catch {
    return null
  }
}

function readProfile(payload: unknown): ProfileResponse {
  if (!isRecord(payload) || !Array.isArray(payload.roles) || payload.roles.some((role) => typeof role !== 'string')) {
    throw new AccountApiError('Dữ liệu tài khoản không đúng định dạng.', 200)
  }
  const id = readId(payload.id)
  const phoneNumber = payload.phoneNumber == null ? null : readText(payload.phoneNumber)
  if (
    id == null ||
    typeof payload.username !== 'string' ||
    typeof payload.email !== 'string' ||
    typeof payload.fullName !== 'string' ||
    (payload.phoneNumber != null && phoneNumber == null) ||
    typeof payload.status !== 'string' ||
    typeof payload.createdAt !== 'string' ||
    typeof payload.updatedAt !== 'string'
  ) {
    throw new AccountApiError('Dữ liệu tài khoản không đúng định dạng.', 200)
  }
  return {
    id,
    username: payload.username,
    email: payload.email,
    fullName: payload.fullName,
    phoneNumber,
    status: payload.status,
    roles: payload.roles as string[],
    createdAt: payload.createdAt,
    updatedAt: payload.updatedAt,
  }
}

function readAddress(payload: unknown): AddressResponse {
  if (!isRecord(payload)) {
    throw new AccountApiError('Dữ liệu địa chỉ không đúng định dạng.', 200)
  }
  const id = readId(payload.id)
  if (
    id == null ||
    typeof payload.recipientName !== 'string' ||
    typeof payload.phoneNumber !== 'string' ||
    typeof payload.province !== 'string' ||
    typeof payload.district !== 'string' ||
    typeof payload.ward !== 'string' ||
    typeof payload.addressLine !== 'string' ||
    typeof payload.isDefault !== 'boolean' ||
    typeof payload.createdAt !== 'string' ||
    typeof payload.updatedAt !== 'string'
  ) {
    throw new AccountApiError('Dữ liệu địa chỉ không đúng định dạng.', 200)
  }
  return {
    id,
    recipientName: payload.recipientName,
    phoneNumber: payload.phoneNumber,
    province: payload.province,
    district: payload.district,
    ward: payload.ward,
    addressLine: payload.addressLine,
    isDefault: payload.isDefault,
    createdAt: payload.createdAt,
    updatedAt: payload.updatedAt,
  }
}

function readText(value: unknown): string | null {
  return typeof value === 'string' ? value : null
}

function readId(value: unknown): number | null {
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : null
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError'
}
