import { createContext, useContext } from 'react'
import type { RegisterInput, SessionUser } from '../api/auth.ts'

export type AuthContextValue = {
  user: SessionUser | null
  isAuthenticated: boolean
  isInitializing: boolean
  sessionError: string | null
  login: (username: string, password: string) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  logout: () => void
  retrySession: () => Promise<void>
  getAccessToken: () => string | null
  expireSession: (token: string) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth phải nằm trong AuthProvider.')
  }
  return value
}
