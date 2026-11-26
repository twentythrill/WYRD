import React, { useState } from 'react'
import { supabase } from '@/lib/supabase'
import { Lock, Mail, ArrowRight, Loader2 } from 'lucide-react'

interface AuthModalProps {
  onSuccess: () => void
}

export function AuthModal({ onSuccess }: AuthModalProps) {
  const [isSignUp, setIsSignUp] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [infoMsg, setInfoMsg] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!email || !password) return

    setLoading(true)
    setErrorMsg(null)
    setInfoMsg(null)

    try {
      if (isSignUp) {
        const { error, data } = await supabase.auth.signUp({
          email: email.trim(),
          password,
        })
        if (error) throw error

        if (data.user && !data.session) {
          setInfoMsg('Verification email sent! Check your inbox or confirm in Supabase.')
        } else {
          onSuccess()
        }
      } else {
        const { error } = await supabase.auth.signInWithPassword({
          email: email.trim(),
          password,
        })
        if (error) throw error
        onSuccess()
      }
    } catch (err: any) {
      let msg = err.message || 'Authentication failed'
      if (msg.includes('Invalid login credentials')) {
        msg = "Invalid email or password. If you haven't created an account yet, switch to 'Sign Up' below."
      } else if (msg.includes('Email not confirmed')) {
        msg = "Email not confirmed. Check your inbox or disable 'Confirm email' in Supabase to log in immediately."
      } else if (msg.toLowerCase().includes('rate limit')) {
        msg = "Supabase email rate limit exceeded. Disable 'Confirm email' in your Supabase project settings to bypass this."
      }
      setErrorMsg(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#000000]/90 backdrop-blur-md p-4">
      <div className="w-full max-w-sm bg-[#000000] border border-[#21211F] rounded-3xl p-8 shadow-2xl relative text-center select-none">
        {/* Exact Brand Logo */}
        <div className="w-16 h-16 flex items-center justify-center mx-auto mb-4">
          <img
            src="/app_logo.png"
            alt="WYRD Logo"
            className="w-12 h-12 object-contain"
          />
        </div>

        <h1 className="text-xl font-bold tracking-[2px] uppercase font-sans text-[#CECDC4] mb-1">
          WYRD
        </h1>
        <p className="text-xs text-[#84837D] mb-6 font-sans">
          {isSignUp ? 'Create an account to synchronize' : 'Sign in to access your conversations'}
        </p>

        {errorMsg && (
          <div className="mb-4 p-3 rounded-xl bg-[#100F0F] border border-[#CECDC4]/40 text-xs text-[#CECDC4] text-left font-sans">
            {errorMsg}
          </div>
        )}

        {infoMsg && (
          <div className="mb-4 p-3 rounded-xl bg-[#100F0F] border border-[#CECDC4]/40 text-xs text-[#CECDC4] text-left font-sans">
            {infoMsg}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-3.5 text-left">
          <div>
            <label className="block text-[11px] font-sans font-medium text-[#84837D] mb-1">
              Email
            </label>
            <div className="relative flex items-center">
              <Mail className="w-4 h-4 absolute left-3.5 text-[#84837D] pointer-events-none" />
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="name@domain.com"
                className="w-full pl-10 pr-3 py-2.5 bg-[#100F0F] border border-[#21211F] rounded-xl text-sm text-[#CECDC4] placeholder-[#84837D] focus:outline-none focus:border-[#CECDC4] transition-colors font-sans"
              />
            </div>
          </div>

          <div>
            <label className="block text-[11px] font-sans font-medium text-[#84837D] mb-1">
              Password
            </label>
            <div className="relative flex items-center">
              <Lock className="w-4 h-4 absolute left-3.5 text-[#84837D] pointer-events-none" />
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full pl-10 pr-3 py-2.5 bg-[#100F0F] border border-[#21211F] rounded-xl text-sm text-[#CECDC4] placeholder-[#84837D] focus:outline-none focus:border-[#CECDC4] transition-colors font-sans"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full mt-3 py-2.5 px-4 rounded-xl bg-[#CECDC4] hover:opacity-90 text-[#100F0F] font-semibold text-xs transition-all flex items-center justify-center gap-2 shadow-lg disabled:opacity-50"
          >
            {loading ? (
              <Loader2 className="w-4 h-4 animate-spin text-[#100F0F]" />
            ) : (
              <>
                <span>{isSignUp ? 'Create Account' : 'Sign In'}</span>
                <ArrowRight className="w-4 h-4" />
              </>
            )}
          </button>
        </form>

        <div className="mt-6 pt-5 border-t border-[#21211F] flex items-center justify-center text-xs text-[#84837D] font-sans">
          <span>{isSignUp ? 'Already have an account?' : "Don't have an account yet?"}</span>
          <button
            onClick={() => {
              setIsSignUp(!isSignUp)
              setErrorMsg(null)
              setInfoMsg(null)
            }}
            className="ml-1.5 text-[#CECDC4] font-medium hover:underline"
          >
            {isSignUp ? 'Sign In' : 'Sign Up'}
          </button>
        </div>
      </div>
    </div>
  )
}
