import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Copy, QrCode, RefreshCw } from 'lucide-react';
import Sidebar from '../components/Sidebar';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import { useAuth } from '../context/AuthContext';
import TransactionService from '../services/TransactionService';

const balanceFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const initialProfile = {
  name: 'User',
  upiId: '',
  balance: 0,
};

const QrCodePage = () => {
  const navigate = useNavigate();
  const { logout } = useAuth();

  const [profile, setProfile] = useState(initialProfile);
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [qrData, setQrData] = useState(null);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isGenerating, setIsGenerating] = useState(false);
  const [copied, setCopied] = useState(false);

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

  const fetchInitialData = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const [profileResponse, qrResponse] = await Promise.all([
        TransactionService.getProfile(),
        TransactionService.getMyUpiQr(),
      ]);
      setProfile(profileResponse);
      setQrData(qrResponse);
    } catch (apiError) {
      if (!redirectIfUnauthorized(apiError)) {
        setError(TransactionService.getErrorMessage(apiError, 'Unable to load your QR details.'));
      }
    } finally {
      setIsLoading(false);
    }
  }, [redirectIfUnauthorized]);

  useEffect(() => {
    fetchInitialData();
  }, [fetchInitialData]);

  const handleGenerate = async (event) => {
    event.preventDefault();

    if (amount.trim()) {
      const parsedAmount = Number(amount);
      if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) {
        setError('Amount must be greater than 0.');
        return;
      }
    }

    setIsGenerating(true);
    setError('');
    setCopied(false);

    try {
      const response = await TransactionService.getMyUpiQr({
        amount: amount.trim() ? Number(amount) : undefined,
        note,
      });
      setQrData(response);
    } catch (apiError) {
      if (redirectIfUnauthorized(apiError)) {
        return;
      }
      setError(TransactionService.getErrorMessage(apiError, 'Unable to generate QR code.'));
    } finally {
      setIsGenerating(false);
    }
  };

  const handleCopyPayload = async () => {
    if (!qrData?.qrPayload) {
      return;
    }

    try {
      await navigator.clipboard.writeText(qrData.qrPayload);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1400);
    } catch {
      setError('Could not copy QR payload on this device.');
    }
  };

  return (
    <div className="flex h-screen overflow-hidden bg-cyber-dark">
      <Sidebar />
      <main className="flex-1 overflow-y-auto pt-16 pb-24 md:pt-0 md:pb-0">
        <div className="mx-auto max-w-5xl p-5 md:p-8">
          <motion.div initial={{ opacity: 0, y: -16 }} animate={{ opacity: 1, y: 0 }} className="mb-6">
            <h1 className="text-3xl font-bold text-white md:text-4xl">My UPI QR</h1>
            <p className="mt-1 text-sm text-gray-300">
              Create a scannable QR to receive money directly into your wallet.
            </p>
          </motion.div>

          {error ? (
            <div className="mb-5 rounded-xl border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200">
              {error}
            </div>
          ) : null}

          <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
            <Card className="p-6">
              <h2 className="text-xl font-semibold text-white">Generate QR</h2>
              <p className="mt-1 text-sm text-gray-400">
                Leave amount blank to create a generic receive QR.
              </p>

              <form onSubmit={handleGenerate} className="mt-5 space-y-4">
                <Input
                  label="Amount (optional)"
                  value={amount}
                  onChange={(event) => setAmount(event.target.value)}
                  placeholder="0.00"
                  type="number"
                  min="0"
                  step="0.01"
                />

                <Input
                  label="Note (optional)"
                  value={note}
                  onChange={(event) => setNote(event.target.value)}
                  placeholder="Add a payment note"
                  maxLength={80}
                />

                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                  {['100', '250', '499'].map((preset) => (
                    <Button
                      key={preset}
                      type="button"
                      variant="ghost"
                      className="w-full"
                      onClick={() => setAmount(preset)}
                    >
                      Rs {preset}
                    </Button>
                  ))}
                </div>

                <Button type="submit" variant="primary" className="w-full" disabled={isGenerating}>
                  {isGenerating ? (
                    <span className="flex items-center justify-center">
                      <RefreshCw className="mr-2 h-4 w-4 animate-spin" />
                      Generating...
                    </span>
                  ) : (
                    <span className="flex items-center justify-center">
                      <QrCode className="mr-2 h-4 w-4" />
                      Generate QR
                    </span>
                  )}
                </Button>
              </form>
            </Card>

            <Card className="p-6">
              <h2 className="text-xl font-semibold text-white">Preview</h2>
              {isLoading ? (
                <div className="mt-5 space-y-4">
                  <div className="shimmer h-72 rounded-2xl" />
                  <div className="shimmer h-10 rounded-xl" />
                </div>
              ) : qrData?.qrImageDataUri ? (
                <div className="mt-5">
                  <div className="mx-auto max-w-xs rounded-2xl border border-white/20 bg-white p-4 shadow-2xl">
                    <img src={qrData.qrImageDataUri} alt="UPI QR code" className="w-full rounded-xl" />
                  </div>

                  <div className="mt-4 rounded-xl border border-white/10 bg-white/5 p-4">
                    <p className="text-sm text-gray-300">UPI ID: {profile.upiId || qrData.upiId}</p>
                    <p className="mt-1 text-sm text-gray-300">Balance: {balanceFormatter.format(profile.balance || 0)}</p>
                  </div>

                  <Button type="button" variant="secondary" className="mt-4 w-full" onClick={handleCopyPayload}>
                    <Copy className="mr-2 h-4 w-4" />
                    {copied ? 'Payload Copied' : 'Copy QR Payload'}
                  </Button>
                </div>
              ) : (
                <p className="mt-5 text-sm text-gray-400">No QR generated yet.</p>
              )}
            </Card>
          </div>
        </div>
      </main>
    </div>
  );
};

export default QrCodePage;
