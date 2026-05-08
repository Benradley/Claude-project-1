/**
 * Auth API — register, login, profile, JWT storage.
 */

const BASE = '/api/auth'
const TOKEN_KEY = 'drone_jwt'
const USER_KEY  = 'drone_user'

// ── Token storage ─────────────────────────────────────────────────────────────

export function saveAuth(authResponse) {
  localStorage.setItem(TOKEN_KEY, authResponse.token)
  localStorage.setItem(USER_KEY, JSON.stringify({
    email:       authResponse.email,
    displayName: authResponse.displayName,
  }))
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getUser() {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? JSON.parse(raw) : null
}

export function isLoggedIn() {
  const token = getToken()
  if (!token) return false
  // Decode the JWT payload (no verification — just check exp field client-side)
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.exp * 1000 > Date.now()
  } catch {
    return false
  }
}

// ── Auth requests ─────────────────────────────────────────────────────────────

export async function register(email, password, displayName) {
  const res = await fetch(`${BASE}/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, displayName }),
  })
  const data = await res.json()
  if (!res.ok) throw new Error(data.message || `Registration failed (${res.status})`)
  saveAuth(data)
  return data
}

export async function login(email, password) {
  const res = await fetch(`${BASE}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  const data = await res.json()
  if (!res.ok) throw new Error(data.message || 'Invalid email or password')
  saveAuth(data)
  return data
}

export async function fetchProfile() {
  const res = await fetch(`${BASE}/me`, {
    headers: { Authorization: `Bearer ${getToken()}` },
  })
  if (!res.ok) throw new Error('Session expired — please log in again')
  return res.json()
}
