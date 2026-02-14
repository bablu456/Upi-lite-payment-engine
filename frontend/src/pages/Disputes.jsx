import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { AlertTriangle, BadgeCheck, Loader2, ShieldAlert, Wallet } from 'lucide-react';
import Sidebar from '../components/Sidebar';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import { useAuth } from '../context/AuthContext';
import TransactionService from '../services/TransactionService';

const statusClassMap = {
  OPEN: 'border-amber-500/40 bg-amber-500/15 text-amber-300',
  UNDER_REVIEW: 'border-cyan-500/40 bg-cyan-500/15 text-cyan-200',
  RESOLVED: 'border-emerald-500/40 bg-emerald-500/15 text-emerald-300',
};

const balanceFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const formatDate = (value) => {
  if (!value) {
    return 'Pending';
  }
  const parsedDate = new Date(value);
  if (Number.isNaN(parsedDate.getTime())) {
    return 'Pending';
  }
  return parsedDate.toLocaleString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const Disputes = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { logout } = useAuth();

  const [transactions, setTransactions] = useState([]);
  const [disputes, setDisputes] = useState([]);

  const [transactionId, setTransactionId] = useState('');
  const [reason, setReason] = useState('');
  const [description, setDescription] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  const [isLoading, setIsLoading] = useState(true);
  const [isRaising, setIsRaising] = useState(false);
  const [actionInFlight, setActionInFlight] = useState({ disputeId: null, type: '' });
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

  const refreshData = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const fetchedProfile = await TransactionService.getProfile();
      const [historyPage, disputeList] = await Promise.all([
        TransactionService.getTransactionHistoryPage(fetchedProfile, {
          page: 0,
          size: 30,
          type: 'ALL',
        }),
        TransactionService.getDisputes(),
      ]);
      setTransactions(historyPage.transactions || []);
      setDisputes(disputeList);
      setTransactionId((previous) => previous || historyPage.transactions?.[0]?.id || '');
    } catch (apiError) {
      if (!redirectIfUnauthorized(apiError)) {
        setError(TransactionService.getErrorMessage(apiError, 'Unable to load disputes.'));
      }
    } finally {
      setIsLoading(false);
    }
  }, [redirectIfUnauthorized]);

  useEffect(() => {
    void refreshData();
  }, [refreshData]);

  useEffect(() => {
    const preselectedTransactionId = location.state?.transactionId;
    if (!preselectedTransactionId) {
      return;
    }

    setTransactionId(preselectedTransactionId);
    navigate('/disputes', { replace: true, state: null });
  }, [location.state, navigate]);

  const filteredDisputes = useMemo(() => {
    if (statusFilter === 'ALL') {
      return disputes;
    }
    return disputes.filter((item) => item.status === statusFilter);
  }, [disputes, statusFilter]);

  const replaceDispute = (updatedDispute) => {
    setDisputes((previous) =>
      previous.map((item) => (item.disputeId === updatedDispute.disputeId ? updatedDispute : item))
    );
  };

  const handleRaiseDispute = async (event) => {
    event.preventDefault();

    if (!transactionId) {
      setError('Please select a transaction.');
      return;
    }

    if (!reason.trim()) {
      setError('Reason is required to raise a dispute.');
      return;
    }

    setIsRaising(true);
    setError('');
    setSuccess('');

    try {
      const dispute = await TransactionService.raiseDispute({
        transactionId,
        reason: reason.trim(),
        description: description.trim(),
      });
      setDisputes((previous) => [dispute, ...previous]);
      setReason('');
      setDescription('');
      setSuccess('Dispute raised successfully.');
      window.setTimeout(() => setSuccess(''), 2200);
    } catch (apiError) {
      if (redirectIfUnauthorized(apiError)) {
        return;
      }
      setError(TransactionService.getErrorMessage(apiError, 'Unable to raise dispute.'));
    } finally {
      setIsRaising(false);
    }
  };

  const handleMarkUnderReview = async (disputeId) => {
    setActionInFlight({ disputeId, type: 'review' });
    setError('');
    setSuccess('');
    try {
      const updated = await TransactionService.markDisputeUnderReview(disputeId);
      replaceDispute(updated);
      setSuccess('Dispute moved to UNDER_REVIEW.');
      window.setTimeout(() => setSuccess(''), 2200);
    } catch (apiError) {
      if (!redirectIfUnauthorized(apiError)) {
        setError(TransactionService.getErrorMessage(apiError, 'Unable to update dispute.'));
      }
    } finally {
      setActionInFlight({ disputeId: null, type: '' });
    }
  };

  const handleResolve = async (disputeId, issueRefund) => {
    setActionInFlight({ disputeId, type: issueRefund ? 'refund' : 'resolve' });
    setError('');
    setSuccess('');
    try {
      const updated = await TransactionService.resolveDispute(disputeId, {
        issueRefund,
        resolutionNote: issueRefund
          ? 'Resolved with simulated refund.'
          : 'Resolved without refund by user action.',
      });
      replaceDispute(updated);
      setSuccess(issueRefund ? 'Dispute resolved with refund simulation.' : 'Dispute resolved.');
      window.setTimeout(() => setSuccess(''), 2200);
    } catch (apiError) {
      if (!redirectIfUnauthorized(apiError)) {
        setError(TransactionService.getErrorMessage(apiError, 'Unable to resolve dispute.'));
      }
    } finally {
      setActionInFlight({ disputeId: null, type: '' });
    }
  };

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
              <h1 className="text-3xl font-bold text-white md:text-4xl">Dispute Center</h1>
              <p className="mt-1 text-sm text-gray-300">
                Report suspicious payments and track status timeline: OPEN, UNDER_REVIEW, RESOLVED.
              </p>
            </div>
            <div className="rounded-xl border border-white/20 bg-white/5 px-4 py-3 text-sm text-gray-200">
              {disputes.length} cases raised
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

          <Card className="mb-5 p-5">
            <div className="mb-3 flex items-center gap-2">
              <ShieldAlert className="h-5 w-5 text-amber-300" />
              <h2 className="text-lg font-semibold text-white">Raise Dispute / Report Scam</h2>
            </div>

            {isLoading ? (
              <div className="space-y-3">
                <div className="shimmer h-12 rounded-xl" />
                <div className="shimmer h-12 rounded-xl" />
                <div className="shimmer h-24 rounded-xl" />
              </div>
            ) : (
              <form className="space-y-4" onSubmit={handleRaiseDispute}>
                <div>
                  <label className="mb-2 block text-sm font-medium text-gray-300">Select Transaction</label>
                  <select
                    value={transactionId}
                    onChange={(event) => setTransactionId(event.target.value)}
                    className="w-full rounded-xl border border-white/20 bg-white/10 px-4 py-3 text-white outline-none transition focus:border-cyan-400/40"
                  >
                    {transactions.map((transaction) => (
                      <option key={transaction.id} value={transaction.id} className="bg-slate-900">
                        {transaction.type} | {balanceFormatter.format(Math.abs(transaction.amount))} |{' '}
                        {formatDate(transaction.timestamp)}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="mb-2 block text-sm font-medium text-gray-300">Reason</label>
                  <input
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    className="w-full rounded-xl border border-white/20 bg-white/10 px-4 py-3 text-white placeholder-gray-400 outline-none transition focus:border-cyan-400/40"
                    placeholder="Ex: Unauthorized transfer / Scam suspicion"
                    maxLength={80}
                  />
                </div>

                <div>
                  <label className="mb-2 block text-sm font-medium text-gray-300">Description (optional)</label>
                  <textarea
                    value={description}
                    onChange={(event) => setDescription(event.target.value)}
                    className="min-h-24 w-full rounded-xl border border-white/20 bg-white/10 px-4 py-3 text-white placeholder-gray-400 outline-none transition focus:border-cyan-400/40"
                    placeholder="Share additional details"
                    maxLength={500}
                  />
                </div>

                <Button type="submit" variant="primary" disabled={isRaising}>
                  {isRaising ? (
                    <span className="flex items-center justify-center">
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      Creating...
                    </span>
                  ) : (
                    <span className="flex items-center justify-center">
                      <AlertTriangle className="mr-2 h-4 w-4" />
                      Raise Dispute
                    </span>
                  )}
                </Button>
              </form>
            )}
          </Card>

          <Card className="p-5">
            <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <h2 className="text-xl font-semibold text-white">My Dispute Cases</h2>
              <select
                value={statusFilter}
                onChange={(event) => setStatusFilter(event.target.value)}
                className="rounded-xl border border-white/20 bg-white/10 px-4 py-2 text-sm text-white outline-none transition focus:border-cyan-400/40"
              >
                <option value="ALL" className="bg-slate-900">
                  All
                </option>
                <option value="OPEN" className="bg-slate-900">
                  OPEN
                </option>
                <option value="UNDER_REVIEW" className="bg-slate-900">
                  UNDER_REVIEW
                </option>
                <option value="RESOLVED" className="bg-slate-900">
                  RESOLVED
                </option>
              </select>
            </div>

            {isLoading ? (
              <div className="space-y-3">
                {Array.from({ length: 4 }).map((_, index) => (
                  <div key={index} className="shimmer h-28 rounded-xl" />
                ))}
              </div>
            ) : filteredDisputes.length === 0 ? (
              <div className="rounded-xl border border-white/10 bg-white/5 px-4 py-10 text-center text-gray-300">
                No disputes found.
              </div>
            ) : (
              <div className="space-y-4">
                {filteredDisputes.map((dispute) => {
                  const statusClass = statusClassMap[dispute.status] || statusClassMap.OPEN;
                  const isBusy = actionInFlight.disputeId === dispute.disputeId;

                  return (
                    <div key={dispute.disputeId} className="rounded-xl border border-white/15 bg-white/5 p-4">
                      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                        <div>
                          <p className="text-sm text-gray-400">Transaction: {dispute.transactionId}</p>
                          <p className="mt-1 text-base font-semibold text-white">{dispute.reason}</p>
                          {dispute.description ? <p className="mt-1 text-sm text-gray-300">{dispute.description}</p> : null}
                        </div>
                        <span className={`self-start rounded-full border px-3 py-1 text-xs font-semibold ${statusClass}`}>
                          {dispute.status}
                        </span>
                      </div>

                      <div className="mt-4 space-y-3">
                        {dispute.timeline.map((event) => (
                          <div key={`${dispute.disputeId}-${event.status}`} className="flex items-start gap-3">
                            <span
                              className={`mt-1 h-2.5 w-2.5 rounded-full ${
                                event.completed ? 'bg-cyan-300 shadow-[0_0_14px_rgba(34,211,238,0.8)]' : 'bg-slate-500'
                              }`}
                            />
                            <div>
                              <p className="text-sm font-semibold text-white">{event.title}</p>
                              <p className="text-xs text-gray-300">{event.description}</p>
                              <p className="text-[11px] text-gray-500">{formatDate(event.occurredAt)}</p>
                            </div>
                          </div>
                        ))}
                      </div>

                      {dispute.refundProcessed ? (
                        <div className="mt-3 rounded-lg border border-emerald-500/40 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-200">
                          <BadgeCheck className="mr-2 inline h-4 w-4" />
                          Refund simulated: {balanceFormatter.format(dispute.refundAmount || 0)}
                        </div>
                      ) : null}

                      {dispute.status !== 'RESOLVED' ? (
                        <div className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-3">
                          {dispute.status === 'OPEN' ? (
                            <Button
                              type="button"
                              variant="ghost"
                              disabled={isBusy}
                              onClick={() => {
                                void handleMarkUnderReview(dispute.disputeId);
                              }}
                            >
                              {isBusy && actionInFlight.type === 'review' ? (
                                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                              ) : null}
                              Mark Under Review
                            </Button>
                          ) : (
                            <div />
                          )}
                          <Button
                            type="button"
                            variant="secondary"
                            disabled={isBusy}
                            onClick={() => {
                              void handleResolve(dispute.disputeId, false);
                            }}
                          >
                            {isBusy && actionInFlight.type === 'resolve' ? (
                              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                            ) : null}
                            Resolve
                          </Button>
                          <Button
                            type="button"
                            variant="primary"
                            disabled={isBusy}
                            onClick={() => {
                              void handleResolve(dispute.disputeId, true);
                            }}
                          >
                            {isBusy && actionInFlight.type === 'refund' ? (
                              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                            ) : (
                              <Wallet className="mr-2 h-4 w-4" />
                            )}
                            Resolve + Refund
                          </Button>
                        </div>
                      ) : null}
                    </div>
                  );
                })}
              </div>
            )}
          </Card>
        </div>
      </main>
    </div>
  );
};

export default Disputes;
