import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { AlertTriangle, ArrowDownLeft, ArrowUpRight, BellRing, ChevronLeft, ChevronRight, Filter, Loader2 } from 'lucide-react';
import Sidebar from '../components/Sidebar';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import TransactionService from '../services/TransactionService';
import { useAuth } from '../context/AuthContext';

const initialProfile = {
  name: 'User',
  upiId: '',
  userId: null,
  walletId: null,
};

const DEFAULT_FILTERS = {
  type: 'ALL',
  fromDate: '',
  toDate: '',
};

const PAGE_SIZE = 8;

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

const Transactions = () => {
  const navigate = useNavigate();
  const { logout } = useAuth();

  const [profile, setProfile] = useState(initialProfile);
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [appliedFilters, setAppliedFilters] = useState(DEFAULT_FILTERS);
  const [page, setPage] = useState(0);
  const [historyPage, setHistoryPage] = useState({
    transactions: [],
    page: 0,
    size: PAGE_SIZE,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  });
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [liveAlert, setLiveAlert] = useState('');

  const profileRef = useRef(initialProfile);
  const alertTimeoutRef = useRef(null);

  useEffect(() => {
    profileRef.current = profile;
  }, [profile]);

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

  const fetchHistory = useCallback(
    async (identity, { silent = false } = {}) => {
      if (silent) {
        setIsRefreshing(true);
      } else {
        setIsLoading(true);
      }

      try {
        const response = await TransactionService.getTransactionHistoryPage(identity, {
          page,
          size: PAGE_SIZE,
          type: appliedFilters.type,
          fromDate: appliedFilters.fromDate || undefined,
          toDate: appliedFilters.toDate || undefined,
        });
        setHistoryPage(response);
        setError('');
      } catch (apiError) {
        if (!redirectIfUnauthorized(apiError)) {
          setError(TransactionService.getErrorMessage(apiError, 'Unable to fetch transaction history.'));
        }
      } finally {
        if (silent) {
          setIsRefreshing(false);
        } else {
          setIsLoading(false);
        }
      }
    },
    [appliedFilters.fromDate, appliedFilters.toDate, appliedFilters.type, page, redirectIfUnauthorized]
  );

  useEffect(() => {
    let isMounted = true;

    const loadInitialData = async () => {
      setIsLoading(true);
      try {
        const fetchedProfile = await TransactionService.getProfile();
        if (!isMounted) {
          return;
        }
        setProfile(fetchedProfile);
      } catch (apiError) {
        if (!isMounted) {
          return;
        }
        if (!redirectIfUnauthorized(apiError)) {
          setError(TransactionService.getErrorMessage(apiError, 'Unable to load transactions.'));
          setIsLoading(false);
        }
      }
    };

    void loadInitialData();

    return () => {
      isMounted = false;
    };
  }, [redirectIfUnauthorized]);

  useEffect(() => {
    if (!profile.userId && !profile.walletId) {
      return;
    }

    void fetchHistory(profile, { silent: false });
  }, [appliedFilters, page, fetchHistory, profile]);

  useEffect(() => {
    const unsubscribe = TransactionService.subscribePaymentAlerts({
      onAlert: (payload) => {
        const message = payload?.message || 'Payment status updated.';
        setLiveAlert(message);
        if (alertTimeoutRef.current) {
          window.clearTimeout(alertTimeoutRef.current);
        }
        alertTimeoutRef.current = window.setTimeout(() => setLiveAlert(''), 4500);
        void fetchHistory(profileRef.current, { silent: true });
      },
    });

    return () => {
      unsubscribe();
      if (alertTimeoutRef.current) {
        window.clearTimeout(alertTimeoutRef.current);
      }
    };
  }, [fetchHistory]);

  const applyFilters = () => {
    setPage(0);
    setAppliedFilters({ ...filters });
  };

  const resetFilters = () => {
    setFilters(DEFAULT_FILTERS);
    setPage(0);
    setAppliedFilters(DEFAULT_FILTERS);
  };

  const transactions = historyPage.transactions || [];
  const pageLabel = useMemo(() => {
    if (!historyPage.totalElements) {
      return 'No records';
    }
    return `Page ${historyPage.page + 1} of ${Math.max(historyPage.totalPages, 1)}`;
  }, [historyPage.page, historyPage.totalElements, historyPage.totalPages]);

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
              <h1 className="text-3xl font-bold text-white md:text-4xl">Transaction History</h1>
              <p className="mt-1 text-sm text-gray-300">Filter by date and credit/debit with paginated results.</p>
            </div>
            <div className="rounded-xl border border-white/20 bg-white/5 px-4 py-3 text-sm text-gray-200">
              {historyPage.totalElements} total transactions
            </div>
          </motion.div>

          {error ? (
            <div className="mb-4 rounded-xl border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200">
              {error}
            </div>
          ) : null}

          {liveAlert ? (
            <div className="mb-4 rounded-xl border border-cyan-500/40 bg-cyan-500/10 px-4 py-3 text-sm text-cyan-100">
              <BellRing className="mr-2 inline h-4 w-4" />
              {liveAlert}
            </div>
          ) : null}

          <Card className="mb-5 p-5">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">Type</label>
                <select
                  value={filters.type}
                  onChange={(event) => setFilters((prev) => ({ ...prev, type: event.target.value }))}
                  className="w-full rounded-xl border border-white/20 bg-white/10 px-4 py-3 text-white outline-none transition focus:border-cyan-400/40"
                >
                  <option value="ALL" className="bg-slate-900">
                    All
                  </option>
                  <option value="CREDIT" className="bg-slate-900">
                    Credit
                  </option>
                  <option value="DEBIT" className="bg-slate-900">
                    Debit
                  </option>
                </select>
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">From Date</label>
                <input
                  type="date"
                  value={filters.fromDate}
                  onChange={(event) => setFilters((prev) => ({ ...prev, fromDate: event.target.value }))}
                  className="w-full rounded-xl border border-white/20 bg-white/10 px-4 py-3 text-white outline-none transition focus:border-cyan-400/40"
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-300">To Date</label>
                <input
                  type="date"
                  value={filters.toDate}
                  onChange={(event) => setFilters((prev) => ({ ...prev, toDate: event.target.value }))}
                  className="w-full rounded-xl border border-white/20 bg-white/10 px-4 py-3 text-white outline-none transition focus:border-cyan-400/40"
                />
              </div>
              <div className="flex items-end gap-2">
                <Button type="button" variant="secondary" className="flex-1" onClick={applyFilters}>
                  <Filter className="mr-2 h-4 w-4" />
                  Apply
                </Button>
                <Button type="button" variant="ghost" className="flex-1" onClick={resetFilters}>
                  Reset
                </Button>
              </div>
            </div>
          </Card>

          <Card className="p-0">
            <div className="flex flex-col gap-2 border-b border-white/10 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 className="text-xl font-semibold text-white">Transactions</h2>
                <p className="text-sm text-gray-400">{pageLabel}</p>
              </div>
              {isRefreshing ? (
                <p className="text-xs text-cyan-200">
                  <Loader2 className="mr-1 inline h-3 w-3 animate-spin" />
                  Refreshing...
                </p>
              ) : null}
            </div>

            {isLoading ? (
              <div className="space-y-3 p-5">
                {Array.from({ length: PAGE_SIZE }).map((_, index) => (
                  <div key={index} className="shimmer h-16 rounded-xl" />
                ))}
              </div>
            ) : transactions.length === 0 ? (
              <div className="px-5 py-12 text-center text-gray-300">No transactions found for selected filters.</div>
            ) : (
              <div className="divide-y divide-white/10">
                {transactions.map((transaction, index) => {
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
                      transition={{ delay: 0.02 * index }}
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
                        <p className="text-xs uppercase tracking-wide text-gray-500">{transaction.status || 'SUCCESS'}</p>
                        {!isCredit ? (
                          <button
                            type="button"
                            className="mt-1 inline-flex items-center text-[11px] text-amber-300 transition hover:text-amber-200"
                            onClick={() => navigate('/disputes', { state: { transactionId: transaction.id } })}
                          >
                            <AlertTriangle className="mr-1 h-3 w-3" />
                            Raise dispute
                          </button>
                        ) : null}
                      </div>
                    </motion.div>
                  );
                })}
              </div>
            )}
          </Card>

          <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <Button type="button" variant="ghost" disabled={historyPage.first || isLoading} onClick={() => setPage((prev) => Math.max(prev - 1, 0))}>
              <ChevronLeft className="mr-2 h-4 w-4" />
              Previous
            </Button>
            <p className="text-center text-sm text-gray-300">{pageLabel}</p>
            <Button
              type="button"
              variant="ghost"
              disabled={historyPage.last || isLoading || historyPage.totalPages === 0}
              onClick={() => setPage((prev) => prev + 1)}
            >
              Next
              <ChevronRight className="ml-2 h-4 w-4" />
            </Button>
          </div>
        </div>
      </main>
    </div>
  );
};

export default Transactions;
