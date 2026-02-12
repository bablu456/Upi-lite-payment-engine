import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowRight, KeyRound, Mail, Wallet } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import Input from '../components/ui/Input';
import Button from '../components/ui/Button';

const initialResetState = {
  identifier: '',
  otp: '',
  newPassword: '',
  confirmPassword: '',
};

const Login = () => {
  const navigate = useNavigate();
  const {
    requestLoginOtp,
    verifyLoginOtp,
    requestForgotPasswordOtp,
    resetPasswordWithOtp,
  } = useAuth();

  const [identifier, setIdentifier] = useState('');
  const [otp, setOtp] = useState('');
  const [otpRequested, setOtpRequested] = useState(false);
  const [authMessage, setAuthMessage] = useState('');
  const [authError, setAuthError] = useState('');
  const [authLoading, setAuthLoading] = useState(false);

  const [showForgotPassword, setShowForgotPassword] = useState(false);
  const [resetForm, setResetForm] = useState(initialResetState);
  const [resetOtpRequested, setResetOtpRequested] = useState(false);
  const [resetMessage, setResetMessage] = useState('');
  const [resetError, setResetError] = useState('');
  const [resetLoading, setResetLoading] = useState(false);

  const handleRequestOtp = async (event) => {
    event.preventDefault();
    setAuthError('');
    setAuthMessage('');
    setAuthLoading(true);

    const response = await requestLoginOtp(identifier);
    if (response.success) {
      setOtpRequested(true);
      setAuthMessage(response.message || 'OTP sent.');
    } else {
      setAuthError(response.error);
    }
    setAuthLoading(false);
  };

  const handleVerifyOtp = async (event) => {
    event.preventDefault();
    setAuthError('');
    setAuthLoading(true);

    const response = await verifyLoginOtp(identifier, otp);
    if (response.success) {
      navigate('/dashboard');
    } else {
      setAuthError(response.error);
    }
    setAuthLoading(false);
  };

  const handleForgotRequestOtp = async (event) => {
    event.preventDefault();
    setResetError('');
    setResetMessage('');
    setResetLoading(true);

    const response = await requestForgotPasswordOtp(resetForm.identifier);
    if (response.success) {
      setResetOtpRequested(true);
      setResetMessage(response.message || 'OTP sent.');
    } else {
      setResetError(response.error);
    }
    setResetLoading(false);
  };

  const handleForgotResetPassword = async (event) => {
    event.preventDefault();
    setResetError('');
    setResetMessage('');
    setResetLoading(true);

    const response = await resetPasswordWithOtp(resetForm);
    if (response.success) {
      setResetMessage(response.message || 'Password reset successful.');
      setResetForm(initialResetState);
      setResetOtpRequested(false);
    } else {
      setResetError(response.error);
    }
    setResetLoading(false);
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-6">
      <motion.div
        className="w-full max-w-xl"
        initial={{ y: 20, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.4 }}
      >
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 inline-flex h-16 w-16 items-center justify-center rounded-2xl border border-white/20 bg-white/10">
            <Wallet className="h-8 w-8 text-cyan-100" />
          </div>
          <h1 className="text-4xl font-bold gradient-text">UPI-Lite Login</h1>
          <p className="mt-2 text-sm text-gray-300">
            PhonePe/GPay style OTP sign-in using your registered email or mobile.
          </p>
        </div>

        <div className="glass-card p-8">
          <h2 className="text-2xl font-semibold text-white">Login With OTP</h2>
          <p className="mt-1 text-sm text-gray-400">Step 1: Request OTP. Step 2: Verify OTP.</p>

          <form className="mt-5 space-y-4" onSubmit={otpRequested ? handleVerifyOtp : handleRequestOtp}>
            <Input
              label="Email or Mobile"
              placeholder="you@example.com or 9876543210"
              value={identifier}
              onChange={(event) => setIdentifier(event.target.value)}
              required
            />

            {otpRequested ? (
              <Input
                label="OTP"
                placeholder="6-digit OTP"
                value={otp}
                onChange={(event) => setOtp(event.target.value)}
                maxLength={6}
                required
              />
            ) : null}

            {authMessage ? (
              <p className="rounded-xl border border-emerald-500/40 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-200">
                {authMessage}
              </p>
            ) : null}

            {authError ? (
              <p className="rounded-xl border border-red-500/40 bg-red-500/10 px-3 py-2 text-sm text-red-200">
                {authError}
              </p>
            ) : null}

            <div className="flex gap-3">
              {otpRequested ? (
                <Button
                  type="button"
                  variant="ghost"
                  className="flex-1"
                  onClick={() => {
                    setOtpRequested(false);
                    setOtp('');
                    setAuthError('');
                    setAuthMessage('');
                  }}
                  disabled={authLoading}
                >
                  Change Identifier
                </Button>
              ) : null}

              <Button type="submit" variant="primary" className="flex-1" disabled={authLoading}>
                {authLoading ? (
                  'Please wait...'
                ) : otpRequested ? (
                  <span className="flex items-center justify-center">
                    <KeyRound className="mr-2 h-4 w-4" />
                    Verify OTP
                  </span>
                ) : (
                  <span className="flex items-center justify-center">
                    <Mail className="mr-2 h-4 w-4" />
                    Send OTP
                  </span>
                )}
              </Button>
            </div>
          </form>

          <div className="mt-6 text-center">
            <button
              type="button"
              className="text-sm font-semibold text-cyan-200 hover:text-cyan-100"
              onClick={() => {
                setShowForgotPassword((previous) => !previous);
                setResetError('');
                setResetMessage('');
              }}
            >
              {showForgotPassword ? 'Hide Forgot Password' : 'Forgot Password?'}
            </button>
          </div>

          {showForgotPassword ? (
            <div className="mt-6 rounded-2xl border border-white/10 bg-white/5 p-5">
              <h3 className="text-lg font-semibold text-white">Reset Password With OTP</h3>
              <p className="mt-1 text-xs text-gray-400">Request OTP and set a new password.</p>

              <form
                className="mt-4 space-y-3"
                onSubmit={resetOtpRequested ? handleForgotResetPassword : handleForgotRequestOtp}
              >
                <Input
                  label="Email or Mobile"
                  value={resetForm.identifier}
                  onChange={(event) =>
                    setResetForm((previous) => ({ ...previous, identifier: event.target.value }))
                  }
                  placeholder="you@example.com or 9876543210"
                  required
                />

                {resetOtpRequested ? (
                  <>
                    <Input
                      label="OTP"
                      value={resetForm.otp}
                      onChange={(event) =>
                        setResetForm((previous) => ({ ...previous, otp: event.target.value }))
                      }
                      placeholder="6-digit OTP"
                      maxLength={6}
                      required
                    />
                    <Input
                      label="New Password"
                      type="password"
                      value={resetForm.newPassword}
                      onChange={(event) =>
                        setResetForm((previous) => ({ ...previous, newPassword: event.target.value }))
                      }
                      placeholder="New password"
                      required
                    />
                    <Input
                      label="Confirm Password"
                      type="password"
                      value={resetForm.confirmPassword}
                      onChange={(event) =>
                        setResetForm((previous) => ({ ...previous, confirmPassword: event.target.value }))
                      }
                      placeholder="Confirm new password"
                      required
                    />
                  </>
                ) : null}

                {resetMessage ? (
                  <p className="rounded-xl border border-emerald-500/40 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-200">
                    {resetMessage}
                  </p>
                ) : null}

                {resetError ? (
                  <p className="rounded-xl border border-red-500/40 bg-red-500/10 px-3 py-2 text-sm text-red-200">
                    {resetError}
                  </p>
                ) : null}

                <div className="flex gap-3">
                  {resetOtpRequested ? (
                    <Button
                      type="button"
                      variant="ghost"
                      className="flex-1"
                      onClick={() => {
                        setResetOtpRequested(false);
                        setResetForm(initialResetState);
                        setResetError('');
                        setResetMessage('');
                      }}
                      disabled={resetLoading}
                    >
                      Restart
                    </Button>
                  ) : null}
                  <Button type="submit" variant="secondary" className="flex-1" disabled={resetLoading}>
                    {resetLoading ? 'Please wait...' : resetOtpRequested ? 'Reset Password' : 'Send Reset OTP'}
                  </Button>
                </div>
              </form>
            </div>
          ) : null}

          <p className="mt-8 text-center text-sm text-gray-400">
            New here?{' '}
            <Link className="font-semibold text-cyan-200 hover:text-cyan-100" to="/register">
              Create account
            </Link>
            <ArrowRight className="ml-1 inline h-4 w-4" />
          </p>
        </div>
      </motion.div>
    </div>
  );
};

export default Login;
