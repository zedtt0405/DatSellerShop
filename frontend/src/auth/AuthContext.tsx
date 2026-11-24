import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  AuthApiError,
  fetchCurrentUser,
  loginAccount,
  registerAccount,
  type RegisterInput,
  type SessionUser,
} from '../api/auth.ts'
import { AuthContext, type AuthContextValue } from './auth-context.ts'

const TOKEN_KEY = 'datsellershop.accessToken'

function readToken(): string | null {
  try {
    const token = sessionStorage.getItem(TOKEN_KEY)
    return token && token.length > 0 ? token : null
  } catch {
    return null
  }
}

function writeToken(token: string | null) {
  try {
    if (token) {
      sessionStorage.setItem(TOKEN_KEY, token)
      return
    }
    sessionStorage.removeItem(TOKEN_KEY)
  } catch {
    // Storage can be blocked. The in-memory user state still updates.
  }
}

function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(null)
  const [isInitializing, setInitializing] = useState(() => readToken() !== null)
  const [sessionError, setSessionError] = useState<string | null>(null)

  const clearTokenIfCurrent = useCallback((token: string) => {
    if (readToken() === token) {
      writeToken(null)
      setUser(null)
      setSessionError(null)
    }
  }, [])

  const restore = useCallback(
    async (token: string, signal?: AbortSignal) => {
      try {
        const current = await fetchCurrentUser(token, signal)
        if (signal?.aborted || readToken() !== token) {
          return
        }
        setUser(current)
        setSessionError(null)
      } catch (error) {
        if (signal?.aborted || (error instanceof Error && error.name === 'AbortError')) {
          return
        }
        if (readToken() !== token) {
          return
        }
        if (error instanceof AuthApiError && error.status === 401) {
          clearTokenIfCurrent(token)
          return
        }
        setUser(null)
        setSessionError('Không khôi phục được phiên. Kiểm tra kết nối rồi thử lại.')
      }
    },
    [clearTokenIfCurrent],
  )

  useEffect(() => {
    const token = readToken()
    if (!token) {
      return
    }
    const controller = new AbortController()
    let active = true
    queueMicrotask(() => {
      if (!active) {
        return
      }
      void restore(token, controller.signal).finally(() => {
        if (active && !controller.signal.aborted) {
          setInitializing(false)
        }
      })
    })
    return () => {
      active = false
      controller.abort()
    }
  }, [restore])

  const login = useCallback(async (username: string, password: string) => {
    const session = await loginAccount(username, password)
    writeToken(session.accessToken)
    setUser(session.user)
    setSessionError(null)
    setInitializing(false)
  }, [])

  const register = useCallback(async (input: RegisterInput) => {
    await registerAccount(input)
  }, [])

  const logout = useCallback(() => {
    writeToken(null)
    setUser(null)
    setSessionError(null)
  }, [])

  const retrySession = useCallback(async () => {
    const token = readToken()
    if (!token) {
      setUser(null)
      setSessionError(null)
      return
    }
    setSessionError(null)
    await restore(token)
  }, [restore])

  const getAccessToken = useCallback(() => readToken(), [])

  const expireSession = useCallback(
    (token: string) => {
      clearTokenIfCurrent(token)
    },
    [clearTokenIfCurrent],
  )

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      isInitializing,
      sessionError,
      login,
      register,
      logout,
      retrySession,
      getAccessToken,
      expireSession,
    }),
    [user, isInitializing, sessionError, login, register, logout, retrySession, getAccessToken, expireSession],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export default AuthProvider
