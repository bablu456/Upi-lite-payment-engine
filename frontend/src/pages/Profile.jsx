import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { BookOpen, Loader2, PencilLine, QrCode, Save, UserCircle2 } from 'lucide-react';
import Sidebar from '../components/Sidebar';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import { useAuth } from '../context/AuthContext';
import TransactionService from '../services/TransactionService';

const initialProfile = {
  name: 'User',
  email: '',
  mobile: '',
  upiId: '',
  balance: 0,
};

const upiPattern = /^[a-z0-9._-]{3,40}@[a-z0-9]{2,20}$/;

const Profile = () => {
  const navigate = useNavigate();
  const { logout, updateUserProfile } = useAuth();

  const [profile, setProfile] = useState(initialProfile);
  const [form, setForm] = useState({ name: '', upiId: '' });
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const redirectIfUnauthorized = useCallback(
    (apiError) => {
      if (!TransactionService.isAuthError(apiError)) {
        return false;
      }
      logout();
      navigate('/login', { replace: true });
      return true;
    },
    [logout, navigate]
  );

  const fetchProfile = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const response = await TransactionService.getProfile();
      setProfile(response);
      setForm({
        name: response?.name || '',
        upiId: response?.upiId || '',
      });
    } catch (apiError) {
      if (!redirectIfUnauthorized(apiError)) {
        setError(TransactionService.getErrorMessage(apiError, 'Unable to load profile.'));
      }
    } finally {
      setIsLoading(false);
    }
  }, [redirectIfUnauthorized]);

  useEffect(() => {
    void fetchProfile();
  }, [fetchProfile]);

  const handleSave = async (event) => {
    event.preventDefault();

    const normalizedName = form.name.trim();
    const normalizedUpiId = form.upiId.trim().toLowerCase();

    if (normalizedName.length < 2 || normalizedName.length > 60) {
      setError('Name must be between 2 and 60 characters.');
      return;
    }

    if (!upiPattern.test(normalizedUpiId)) {
      setError('UPI ID is invalid. Use format like username@upi.');
      return;
    }

    setIsSaving(true);
    setError('');
    setSuccess('');

    try {
      const updatedProfile = await TransactionService.updateProfile({
        name: normalizedName,
        upiId: normalizedUpiId,
      });
      setProfile(updatedProfile);
      setForm({
        name: updatedProfile.name || '',
        upiId: updatedProfile.upiId || '',
      });
      updateUserProfile({
        name: updatedProfile.name,
        email: updatedProfile.email,
        mobile: updatedProfile.mobile,
        upiId: updatedProfile.upiId,
      });
      setSuccess('Profile updated successfully.');
      window.setTimeout(() => setSuccess(''), 2200);
    } catch (apiError) {
      if (redirectIfUnauthorized(apiError)) {
        return;
      }
      setError(TransactionService.getErrorMessage(apiError, 'Unable to update profile.'));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="flex h-screen overflow-hidden bg-cyber-dark">
      <Sidebar />
      <main className="flex-1 overflow-y-auto pt-16 pb-24 md:pt-0 md:pb-0">
        <div className="mx-auto max-w-5xl p-5 md:p-8">
          <motion.div
            initial={{ opacity: 0, y: -16 }}
            animate={{ opacity: 1, y: 0 }}
            className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between"
          >
            <div>
              <h1 className="text-3xl font-bold text-white md:text-4xl">Profile</h1>
              <p className="mt-1 text-sm text-gray-300">Manage your name, UPI ID, and quick account actions.</p>
            </div>
            <div className="flex h-14 w-14 items-center justify-center rounded-full border border-white/20 bg-white/10">
              <UserCircle2 className="h-8 w-8 text-cyan-200" />
            </div>
          </motion.div>

          {error ? (
            <div className="mb-4 rounded-xl border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200">
              {error}
            </div>
          ) : null}
          {success ? (
            <div className="mb-4 rounded-xl border border-emerald-500/40 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-200">
              {success}
            </div>
          ) : null}

          <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
            <Card className="p-6">
              <h2 className="text-xl font-semibold text-white">Edit Profile</h2>
              {isLoading ? (
                <div className="mt-5 space-y-3">
                  <div className="shimmer h-12 rounded-xl" />
                  <div className="shimmer h-12 rounded-xl" />
                  <div className="shimmer h-10 rounded-xl" />
                </div>
              ) : (
                <form className="mt-5 space-y-4" onSubmit={handleSave}>
                  <Input
                    label="Full Name"
                    value={form.name}
                    onChange={(event) => setForm((previous) => ({ ...previous, name: event.target.value }))}
                    placeholder="Enter your full name"
                    autoComplete="name"
                  />
                  <Input
                    label="UPI ID"
                    value={form.upiId}
                    onChange={(event) => setForm((previous) => ({ ...previous, upiId: event.target.value }))}
                    placeholder="username@upi"
                    autoComplete="off"
                  />
                  <Button type="submit" variant="primary" className="w-full" disabled={isSaving}>
                    {isSaving ? (
                      <span className="flex items-center justify-center">
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        Saving...
                      </span>
                    ) : (
                      <span className="flex items-center justify-center">
                        <Save className="mr-2 h-4 w-4" />
                        Save Changes
                      </span>
                    )}
                  </Button>
                </form>
              )}
            </Card>

            <Card className="p-6">
              <h2 className="text-xl font-semibold text-white">Account Snapshot</h2>
              {isLoading ? (
                <div className="mt-5 space-y-3">
                  <div className="shimmer h-12 rounded-xl" />
                  <div className="shimmer h-12 rounded-xl" />
                  <div className="shimmer h-12 rounded-xl" />
                </div>
              ) : (
                <div className="mt-5 space-y-3 text-sm">
                  <div className="rounded-xl border border-white/15 bg-white/5 px-4 py-3 text-gray-200">
                    <p className="text-xs uppercase tracking-wide text-gray-400">Name</p>
                    <p className="mt-1 font-semibold text-white">{profile.name || '-'}</p>
                  </div>
                  <div className="rounded-xl border border-white/15 bg-white/5 px-4 py-3 text-gray-200">
                    <p className="text-xs uppercase tracking-wide text-gray-400">Email</p>
                    <p className="mt-1 font-semibold text-white">{profile.email || '-'}</p>
                  </div>
                  <div className="rounded-xl border border-white/15 bg-white/5 px-4 py-3 text-gray-200">
                    <p className="text-xs uppercase tracking-wide text-gray-400">Mobile</p>
                    <p className="mt-1 font-semibold text-white">{profile.mobile || '-'}</p>
                  </div>
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <Button type="button" variant="secondary" onClick={() => navigate('/qr')}>
                      <QrCode className="mr-2 h-4 w-4" />
                      View My QR
                    </Button>
                    <Button type="button" variant="ghost" onClick={() => navigate('/dashboard')}>
                      <PencilLine className="mr-2 h-4 w-4" />
                      Back to Dashboard
                    </Button>
                  </div>
                </div>
              )}
            </Card>
          </div>

          <Card className="mt-5 p-6">
            <h2 className="flex items-center text-xl font-semibold text-white">
              <BookOpen className="mr-2 h-5 w-5 text-cyan-300" />
              About UPI Lite
            </h2>
            <p className="mt-3 text-sm leading-relaxed text-gray-300">
              UPI Lite is designed for fast low-value payments with reduced friction. In this demo, wallet balance is
              capped at Rs 2000, transfers below Rs 500 can go through without PIN, and higher amounts require UPI PIN
              verification for stronger transaction safety.
            </p>
          </Card>
        </div>
      </main>
    </div>
  );
};

export default Profile;
