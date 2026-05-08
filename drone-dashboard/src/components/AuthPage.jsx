import { useState } from 'react'
import { login, register } from '../api/authApi'

/**
 * Combined login + register page with tab switching.
 * Calls onSuccess(user) when authentication succeeds.
 */
export default function AuthPage({ onSuccess }) {
  const [tab,      setTab]      = useState('login')   // 'login' | 'register'
  const [email,    setEmail]    = useState('')
  const [password, setPassword] = useState('')
  const [name,     setName]     = useState('')
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState(null)

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      let result
      if (tab === 'login') {
        result = await login(email, password)
      } else {
        result = await register(email, password, name)
      }
      onSuccess(result)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  function switchTab(t) {
    setTab(t)
    setError(null)
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-logo">🚁</div>
        <h1 className="auth-title">Drone Flight Analytics</h1>

        {/* Tabs */}
        <div className="auth-tabs">
          <button
            className={`auth-tab ${tab === 'login' ? 'active' : ''}`}
            onClick={() => switchTab('login')}
          >Sign in</button>
          <button
            className={`auth-tab ${tab === 'register' ? 'active' : ''}`}
            onClick={() => switchTab('register')}
          >Create account</button>
        </div>

        <form onSubmit={handleSubmit} className="auth-form">
          {tab === 'register' && (
            <div className="field">
              <label htmlFor="name">Display name</label>
              <input
                id="name"
                type="text"
                placeholder="e.g. Ben Smith"
                value={name}
                onChange={e => setName(e.target.value)}
                required
                autoComplete="name"
              />
            </div>
          )}

          <div className="field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              placeholder="you@example.com"
              value={email}
              onChange={e => setEmail(e.target.value)}
              required
              autoComplete="email"
            />
          </div>

          <div className="field">
            <label htmlFor="password">
              Password
              {tab === 'register' && <span className="field-hint"> (min 6 chars)</span>}
            </label>
            <input
              id="password"
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={e => setPassword(e.target.value)}
              required
              minLength={tab === 'register' ? 6 : undefined}
              autoComplete={tab === 'login' ? 'current-password' : 'new-password'}
            />
          </div>

          {error && <div className="auth-error">{error}</div>}

          <button type="submit" className="btn-auth" disabled={loading}>
            {loading
              ? <><span className="spinner" /> {tab === 'login' ? 'Signing in…' : 'Creating account…'}</>
              : tab === 'login' ? 'Sign in' : 'Create account'
            }
          </button>
        </form>
      </div>
    </div>
  )
}
