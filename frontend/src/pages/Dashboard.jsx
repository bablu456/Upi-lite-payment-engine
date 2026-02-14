import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowDownLeft,
  ArrowUpRight,
  Clock3,
  CreditCard,
  KeyRound,
  PlusCircle,
  QrCode,
  ScanLine,
  Send,
  ShieldCheck,
  Users,
  UserCircle2,
  Wallet,
  BellRing,
} from 'lucide-react';
import Sidebar from '../components/Sidebar';
import SendMoneyModal from '../components/SendMoneyModal';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import { useAuth } from '../context/AuthContext';
import TransactionService from '../services/TransactionService';

const initialProfile = {
  name: 'Bablu',
  email: '',
  mobile: '',
  upiId: '',
  balance: 0,
  userId: null,
  walletId: null,
  pinConfigured: false,
  kycStatus: 'NOT_SUBMITTED',
};

const balanceFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const formatDate = (value) => {
  const parsedDate = new Date(value);
  if (Number.isNaN(parsedDate.getTime())) {
    return '-';
  }

  return parsedDate.toLocaleString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const Dashboard = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { logout } = useAuth();

  const [profile, setProfile] = useState(initialProfile);
  const [transactions, setTransactions] = useState([]);
  const [isProfileLoading, setIsProfileLoading] = useState(true);
  const [isHistoryLoading, setIsHistoryLoading] = useState(true);
  const [isSyncing, setIsSyncing] = useState(false);

  const [dashboardError, setDashboardError] = useState('');
  const [realtimeMessage, setRealtimeMessage] = useState('');
  const [isSendModalOpen, setIsSendModalOpen] = useState(false);
  const [sendPreset, setSendPreset] = useState({
    receiverMode: '',
    receiverValue: '',
    amount: '',
  });
  const [isLoadMoneyOpen, setIsLoadMoneyOpen] = useState(false);
  const [isPinSetupOpen, setIsPinSetupOpen] = useState(false);

  const [creditAmount, setCreditAmount] = useState('');
  const [creditError, setCreditError] = useState('');
  const [isCrediting, setIsCrediting] = useState(false);

  const [pinForm, setPinForm] = useState({ pin: '', confirmPin: '' });
  const [pinError, setPinError] = useState('');
  const [isSavingPin, setIsSavingPin] = useState(false);

  const clearSendPreset = useCallback(() => {
    setSendPreset({
      receiverMode: '',
      receiverValue: '',
      amount: '',
    });
  }, []);

  const profileRef = useRef(initialProfile);
  const realtimeTimeoutRef = useRef(null);

  useEffect(() => {
    profileRef.current = profile;
  }, [profile]);

  const sortByLatest = useCallback(
    (items) =>
      [...items].sort((a, b) => {
        const left = new Date(a.timestamp).getTime();
        const right = new Date(b.timestamp).getTime();
        return right - left;
      }),
    []
  );

  const redirectIfUnauthorized = useCallback(
    (error) => {
      if (!TransactionService.isAuthError(error)) {
        return false;
      }

      logout();
      navigate('/login', { replace: true });
      return true;
    },
    [logout, navigate]
  );

  const fetchDashboardData = useCallback(
    async ({ silent = false } = {}) => {
      if (!silent) {
        setIsProfileLoading(true);
        setIsHistoryLoading(true);
      }

      setDashboardError('');

      let identity = profileRef.current;
      try {
        const fetchedProfile = await TransactionService.getProfile();
        identity = { ...identity, ...fetchedProfile };
        setProfile(identity);
      } catch (error) {
        if (redirectIfUnauthorized(error)) {
          return;
        }

        setDashboardError((currentValue) =>
          currentValue || TransactionService.getErrorMessage(error, 'Unable to load your profile.')
        );
      } finally {
        if (!silent) {
          setIsProfileLoading(false);
        }
      }

      try {
        const history = await TransactionService.getTransactionHistory(identity);
        setTransactions(sortByLatest(history));
      } catch (error) {
        if (redirectIfUnauthorized(error)) {
          return;
        }

        setDashboardError((currentValue) =>
          currentValue || TransactionService.getErrorMessage(error, 'Unable to load transactions.')
        );
      } finally {
        if (!silent) {
          setIsHistoryLoading(false);
        }
        setIsSyncing(false);
      }
    },
    [redirectIfUnauthorized, sortByLatest]
  );

  useEffect(() => {
    fetchDashboardData();
  }, [fetchDashboardData]);

  useEffect(() => {
    const scanPrefill = location.state?.scanPrefill;
    if (!scanPrefill?.receiverValue) {
      return;
    }

    setSendPreset({
      receiverMode: scanPrefill.receiverMode === 'mobile' ? 'mobile' : 'upi',
      receiverValue: String(scanPrefill.receiverValue),
      amount: String(scanPrefill.amount ?? ''),
    });
    setIsSendModalOpen(true);
    navigate('/dashboard', { replace: true, state: null });
  }, [location.state, navigate]);

  useEffect(() => {
    const unsubscribe = TransactionService.subscribePaymentAlerts({
      onAlert: (payload) => {
        const message = payload?.message || 'Payment status updated.';
        setRealtimeMessage(message);

        if (realtimeTimeoutRef.current) {
          window.clearTimeout(realtimeTimeoutRef.current);
        }
        realtimeTimeoutRef.current = window.setTimeout(() => setRealtimeMessage(''), 4500);

        setIsSyncing(true);
        void fetchDashboardData({ silent: true });
      },
    });

    return () => {
      unsubscribe();
      if (realtimeTimeoutRef.current) {
        window.clearTimeout(realtimeTimeoutRef.current);
      }
    };
  }, [fetchDashboardData]);

  const onTransferSuccess = async ({ receiverUpiId, receiverMobile, amount, pin, riskAcknowledged }) => {
    const value = Number(amount);
    const counterparty = receiverUpiId || receiverMobile || 'Receiver';

    const optimisticTransaction = {
      id: `optimistic-${Date.now()}`,
      senderName: profile.name || 'You',
      senderUpiId: profile.upiId,
      receiverName: counterparty,
      receiverUpiId,
      amount: value,
      timestamp: new Date().toISOString(),
      status: 'PENDING',
      type: 'DEBIT',
    };

    setTransactions((previous) => sortByLatest([optimisticTransaction, ...previous]));
    setProfile((previous) => ({
      ...previous,
      balance: Math.max(0, Number(previous.balance) - value),
    }));

    try {
      const savedTransaction = await TransactionService.transferMoney({
        receiverUpiId,
        receiverMobile,
        amount: value,
        pin,
        riskAcknowledged,
        senderId: profile.walletId || profile.userId,
        identity: profile,
      });

      setTransactions((previous) =>
        sortByLatest([savedTransaction, ...previous.filter((item) => item.id !== optimisticTransaction.id)])
      );

      setIsSyncing(true);
      void fetchDashboardData({ silent: true });
    } catch (error) {
      setTransactions((previous) => previous.filter((item) => item.id !== optimisticTransaction.id));
      setProfile((previous) => ({
        ...previous,
        balance: Number(previous.balance) + value,
      }));

      if (redirectIfUnauthorized(error)) {
        throw error;
      }

      if (error?.scamRisk) {
        throw error;
      }

      throw new Error(TransactionService.getErrorMessage(error, 'Transfer failed. Please try again.'));
    }
  };

  const handleCreditWallet = async (event) => {
    event.preventDefault();
    const value = Number(creditAmount);

    if (!Number.isFinite(value) || value <= 0) {
      setCreditError('Amount must be greater than 0.');
      return;
    }

    setCreditError('');
    setIsCrediting(true);

    try {
      const response = await TransactionService.creditWallet({ amount: value });
      setProfile((previous) => ({
        ...previous,
        balance: response?.balance ?? previous.balance,
      }));

      setCreditAmount('');
      setIsLoadMoneyOpen(false);
      setIsSyncing(true);
      void fetchDashboardData({ silent: true });
    } catch (error) {
      if (redirectIfUnauthorized(error)) {
        return;
      }

      setCreditError(TransactionService.getErrorMessage(error, 'Unable to load money right now.'));
    } finally {
      setIsCrediting(false);
    }
  };

  const handlePinSetup = async (event) => {
    event.preventDefault();

    if (!/^\d{4}$/.test(pinForm.pin)) {
      setPinError('UPI PIN must be exactly 4 digits.');
      return;
    }

    if (pinForm.pin !== pinForm.confirmPin) {
      setPinError('PIN and confirm PIN do not match.');
      return;
    }

    setPinError('');
    setIsSavingPin(true);

    try {
      await TransactionService.setPin({ pin: pinForm.pin, confirmPin: pinForm.confirmPin });
      setProfile((previous) => ({
        ...previous,
        pinConfigured: true,
      }));
      setPinForm({ pin: '', confirmPin: '' });
      setIsPinSetupOpen(false);
    } catch (error) {
      if (redirectIfUnauthorized(error)) {
        return;
      }

      setPinError(TransactionService.getErrorMessage(error, 'Unable to set UPI PIN.'));
    } finally {
      setIsSavingPin(false);
    }
  };

  const recentTransactions = useMemo(() => transactions.slice(0, 5), [transactions]);

  return (
    <div className="flex h-screen overflow-hidden bg-cyber-dark">
      <Sidebar />
      <main className="flex-1 overflow-y-auto pt-16 pb-24 md:pt-0 md:pb-0">
        <div className="mx-auto max-w-6xl p-5 md:p-8">
          <motion.div
            initial={{ opacity: 0, y: -16 }}
            animate={{ opacity: 1, y: 0 }}
            className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between"
          >
            <div>
              <h1 className="text-3xl font-bold text-white md:text-4xl">Hello, {profile.name || 'Bablu'}</h1>
              <p className="mt-1 text-sm text-gray-300">{profile.upiId || 'UPI ID unavailable'}</p>
              <p className="text-xs text-gray-400">{profile.mobile ? `Mobile: ${profile.mobile}` : ''}</p>
            </div>
            <button
              type="button"
              onClick={() => navigate('/profile')}
              className="flex h-12 w-12 items-center justify-center rounded-full border border-white/30 bg-white/10 transition hover:bg-white/20"
              aria-label="Open profile"
            >
              <UserCircle2 className="h-7 w-7 text-cyan-200" />
            </button>
          </motion.div>

          {dashboardError ? (
            <div className="mb-5 rounded-xl border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200">
              {dashboardError}
            </div>
          ) : null}

          {realtimeMessage ? (
            <div className="mb-5 rounded-xl border border-cyan-500/40 bg-cyan-500/10 px-4 py-3 text-sm text-cyan-100">
              <BellRing className="mr-2 inline h-4 w-4" />
              {realtimeMessage}
            </div>
          ) : null}

          {!profile.pinConfigured ? (
            <div className="mb-5 rounded-xl border border-amber-500/30 bg-amber-500/10 px-4 py-3">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <p className="text-sm text-amber-200">
                  Set your UPI PIN to authorize transactions of Rs 500 and above.
                </p>
                <Button variant="secondary" size="sm" onClick={() => setIsPinSetupOpen(true)}>
                  <KeyRound className="mr-2 h-4 w-4" />
                  Set UPI PIN
                </Button>
              </div>
            </div>
          ) : null}

          {profile.kycStatus !== 'APPROVED' ? (
            <div className="mb-5 rounded-xl border border-cyan-500/30 bg-cyan-500/10 px-4 py-3">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <p className="text-sm text-cyan-100">
                  KYC status: {profile.kycStatus}. Complete KYC to unlock full UPI Lite trust flow.
                </p>
                <Button variant="secondary" size="sm" onClick={() => navigate('/kyc')}>
                  <ShieldCheck className="mr-2 h-4 w-4" />
                  Open KYC
                </Button>
              </div>
            </div>
          ) : null}

          <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
            <Card className="relative mb-6 overflow-hidden border-cyan-200/20 bg-white/10 p-6 md:p-8">
              <motion.div
                className="pointer-events-none absolute inset-0 opacity-70"
                style={{
                  background:
                    'linear-gradient(120deg, rgba(56,189,248,0.18), rgba(14,165,233,0.1), rgba(16,185,129,0.16))',
                  backgroundSize: '220% 220%',
                }}
                animate={{ backgroundPosition: ['0% 50%', '100% 50%', '0% 50%'] }}
                transition={{ duration: 9, ease: 'easeInOut', repeat: Number.POSITIVE_INFINITY }}
              />

              <div className="relative flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
                <div>
                  <p className="text-sm uppercase tracking-[0.22em] text-cyan-100/90">Total Balance</p>
                  {isProfileLoading ? (
                    <div className="shimmer mt-3 h-11 w-56 rounded-xl" />
                  ) : (
                    <h2 className="mt-2 text-4xl font-bold text-white md:text-5xl">
                      {balanceFormatter.format(profile.balance)}
                    </h2>
                  )}
                  {isSyncing ? <p className="mt-2 text-xs text-cyan-100">Syncing latest balance...</p> : null}
                </div>
                <div className="flex h-14 w-14 items-center justify-center rounded-2xl border border-cyan-100/30 bg-white/10">
                  <Wallet className="h-7 w-7 text-cyan-100" />
                </div>
              </div>
            </Card>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.18 }}
            className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-7"
          >
            <Button
              variant="secondary"
              size="md"
              className="flex w-full items-center justify-center"
              onClick={() => {
                clearSendPreset();
                setIsSendModalOpen(true);
              }}
            >
              <Send className="mr-2 h-5 w-5" />
              Send Money
            </Button>
            <Button
              variant="secondary"
              size="md"
              className="flex w-full items-center justify-center"
              onClick={() => navigate('/scan-pay')}
            >
              <ScanLine className="mr-2 h-5 w-5" />
              Scan & Pay
            </Button>
            <Button
              variant="secondary"
              size="md"
              className="flex w-full items-center justify-center"
              onClick={() => {
                setCreditError('');
                setIsLoadMoneyOpen(true);
              }}
            >
              <PlusCircle className="mr-2 h-5 w-5" />
              Load Money
            </Button>
            <Button
              variant="secondary"
              size="md"
              className="flex w-full items-center justify-center"
              onClick={() => {
                setPinError('');
                setIsPinSetupOpen(true);
              }}
            >
              <KeyRound className="mr-2 h-5 w-5" />
              Set UPI PIN
            </Button>
            <Button
              variant="secondary"
              size="md"
              className="flex w-full items-center justify-center"
              onClick={() => navigate('/qr')}
            >
              <QrCode className="mr-2 h-5 w-5" />
              My QR
            </Button>
            <Button
              variant="secondary"
              size="md"
              className="flex w-full items-center justify-center"
              onClick={() => navigate('/transactions')}
            >
              <Clock3 className="mr-2 h-5 w-5" />
              History
            </Button>
            <Button
              variant="secondary"
              size="md"
              className="flex w-full items-center justify-center"
              onClick={() => navigate('/contacts')}
            >
              <Users className="mr-2 h-5 w-5" />
              Contacts
            </Button>
          </motion.div>

          <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.26 }}>
            <Card className="p-0">
              <div className="border-b border-white/10 px-5 py-4">
                <h3 className="text-xl font-semibold text-white">Recent Transactions</h3>
                <p className="text-sm text-gray-400">Showing last 5 transfers</p>
              </div>

              {isHistoryLoading ? (
                <div className="space-y-3 p-5">
                  {Array.from({ length: 5 }).map((_, index) => (
                    <div key={index} className="shimmer h-16 rounded-xl" />
                  ))}
                </div>
              ) : recentTransactions.length === 0 ? (
                <div className="px-5 py-12 text-center text-gray-300">No transactions yet.</div>
              ) : (
                <div className="divide-y divide-white/10">
                  {recentTransactions.map((transaction, index) => {
                    const isCredit = transaction.type === 'CREDIT';
                    const Icon = isCredit ? ArrowDownLeft : ArrowUpRight;
                    const accent = isCredit ? 'text-emerald-400' : 'text-rose-400';
                    const badgeBackground = isCredit ? 'bg-emerald-500/15' : 'bg-rose-500/15';
                    const counterparty = isCredit
                      ? transaction.senderUpiId || transaction.senderName
                      : transaction.receiverUpiId || transaction.receiverName;

                    return (
                      <motion.div
                        key={transaction.id || `${transaction.timestamp}-${index}`}
                        initial={{ opacity: 0, x: -12 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: 0.03 * index }}
                        className="flex items-center justify-between gap-3 px-5 py-4"
                      >
                        <div className="flex min-w-0 items-center gap-3">
                          <div
                            className={`flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-xl ${badgeBackground}`}
                          >
                            <Icon className={`h-5 w-5 ${accent}`} />
                          </div>
                          <div className="min-w-0">
                            <p className="truncate text-sm font-semibold text-white">
                              {isCredit ? 'Money Received' : 'Money Sent'}
                            </p>
                            <p className="truncate text-xs text-gray-400">
                              {isCredit ? 'From' : 'To'}: {counterparty || '-'}
                            </p>
                            <p className="text-xs text-gray-500">{formatDate(transaction.timestamp)}</p>
                          </div>
                        </div>
                        <div className="text-right">
                          <p className={`text-sm font-semibold ${accent}`}>
                            {isCredit ? '+' : '-'}
                            {balanceFormatter.format(Math.abs(transaction.amount))}
                          </p>
                          <p className="text-xs uppercase tracking-wide text-gray-500">
                            {transaction.status || 'SUCCESS'}
                          </p>
                        </div>
                      </motion.div>
                    );
                  })}
                </div>
              )}
            </Card>
          </motion.div>
        </div>
      </main>

      <SendMoneyModal
        isOpen={isSendModalOpen}
        onClose={() => {
          setIsSendModalOpen(false);
          clearSendPreset();
        }}
        onSend={onTransferSuccess}
        senderUpiId={profile.upiId}
        presetReceiverMode={sendPreset.receiverMode}
        presetReceiverValue={sendPreset.receiverValue}
        presetAmount={sendPreset.amount}
      />

      {isLoadMoneyOpen ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-2xl border border-white/20 bg-slate-900/90 p-6 shadow-2xl">
            <h3 className="text-xl font-semibold text-white">Load Money</h3>
            <p className="mt-1 text-sm text-gray-300">Add funds to your UPI Lite wallet (max balance Rs 2000).</p>

            <form className="mt-5 space-y-4" onSubmit={handleCreditWallet}>
              <Input
                label="Amount"
                value={creditAmount}
                onChange={(event) => setCreditAmount(event.target.value)}
                placeholder="0.00"
                type="number"
                min="0"
                step="0.01"
                autoFocus
              />

              {creditError ? (
                <p className="rounded-lg border border-red-500/40 bg-red-500/10 px-3 py-2 text-sm text-red-300">
                  {creditError}
                </p>
              ) : null}

              <div className="flex gap-3">
                <Button
                  type="button"
                  variant="ghost"
                  className="flex-1"
                  disabled={isCrediting}
                  onClick={() => setIsLoadMoneyOpen(false)}
                >
                  Cancel
                </Button>
                <Button type="submit" variant="primary" className="flex-1" disabled={isCrediting}>
                  {isCrediting ? (
                    <span className="flex items-center justify-center">
                      <CreditCard className="mr-2 h-4 w-4 animate-pulse" />
                      Loading...
                    </span>
                  ) : (
                    'Load'
                  )}
                </Button>
              </div>
            </form>
          </div>
        </div>
      ) : null}

      {isPinSetupOpen ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-2xl border border-white/20 bg-slate-900/90 p-6 shadow-2xl">
            <h3 className="text-xl font-semibold text-white">Set UPI PIN</h3>
            <p className="mt-1 text-sm text-gray-300">Required for transactions of Rs 500 and above.</p>

            <form className="mt-5 space-y-4" onSubmit={handlePinSetup}>
              <Input
                label="UPI PIN"
                type="password"
                maxLength={4}
                value={pinForm.pin}
                onChange={(event) => setPinForm((previous) => ({ ...previous, pin: event.target.value }))}
                placeholder="4-digit PIN"
                autoFocus
              />
              <Input
                label="Confirm UPI PIN"
                type="password"
                maxLength={4}
                value={pinForm.confirmPin}
                onChange={(event) => setPinForm((previous) => ({ ...previous, confirmPin: event.target.value }))}
                placeholder="Re-enter PIN"
              />

              {pinError ? (
                <p className="rounded-lg border border-red-500/40 bg-red-500/10 px-3 py-2 text-sm text-red-300">
                  {pinError}
                </p>
              ) : null}

              <div className="flex gap-3">
                <Button
                  type="button"
                  variant="ghost"
                  className="flex-1"
                  disabled={isSavingPin}
                  onClick={() => setIsPinSetupOpen(false)}
                >
                  Cancel
                </Button>
                <Button type="submit" variant="primary" className="flex-1" disabled={isSavingPin}>
                  {isSavingPin ? 'Saving...' : 'Save PIN'}
                </Button>
              </div>
            </form>
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default Dashboard;
