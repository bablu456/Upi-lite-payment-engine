import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Loader2, MessageCircle, Search, Send, ShieldCheck, Smartphone, UserRound } from 'lucide-react';
import Sidebar from '../components/Sidebar';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import SendMoneyModal from '../components/SendMoneyModal';
import { useAuth } from '../context/AuthContext';
import TransactionService from '../services/TransactionService';

const initialProfile = {
  name: 'User',
  upiId: '',
  userId: null,
  walletId: null,
};

const statusClasses = {
  APPROVED: 'border-emerald-500/40 bg-emerald-500/15 text-emerald-300',
  PENDING: 'border-amber-500/40 bg-amber-500/15 text-amber-300',
  REJECTED: 'border-rose-500/40 bg-rose-500/15 text-rose-300',
  NOT_SUBMITTED: 'border-slate-500/40 bg-slate-500/15 text-slate-300',
};

const Contacts = () => {
  const navigate = useNavigate();
  const { logout } = useAuth();

  const [profile, setProfile] = useState(initialProfile);
  const [contacts, setContacts] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const [isSendModalOpen, setIsSendModalOpen] = useState(false);
  const [selectedContact, setSelectedContact] = useState(null);

  const [messageContact, setMessageContact] = useState(null);
  const [messageText, setMessageText] = useState('');
  const [isSendingMessage, setIsSendingMessage] = useState(false);

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
    try {
      const fetchedProfile = await TransactionService.getProfile();
      setProfile(fetchedProfile);
      return fetchedProfile;
    } catch (apiError) {
      if (!redirectIfUnauthorized(apiError)) {
        setError(TransactionService.getErrorMessage(apiError, 'Unable to load user profile.'));
      }
      return null;
    }
  }, [redirectIfUnauthorized]);

  const fetchContacts = useCallback(
    async (query = '', silent = false) => {
      if (!silent) {
        setIsLoading(true);
      } else {
        setIsRefreshing(true);
      }

      setError('');
      try {
        const response = await TransactionService.getContacts(query);
        setContacts(response);
      } catch (apiError) {
        if (!redirectIfUnauthorized(apiError)) {
          setError(TransactionService.getErrorMessage(apiError, 'Unable to load contacts.'));
        }
      } finally {
        if (!silent) {
          setIsLoading(false);
        } else {
          setIsRefreshing(false);
        }
      }
    },
    [redirectIfUnauthorized]
  );

  useEffect(() => {
    void fetchProfile();
    void fetchContacts('');
  }, [fetchContacts, fetchProfile]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void fetchContacts(searchQuery, true);
    }, 250);

    return () => window.clearTimeout(timeoutId);
  }, [fetchContacts, searchQuery]);

  const onTransferSuccess = async ({ receiverUpiId, receiverMobile, amount, pin, riskAcknowledged }) => {
    try {
      await TransactionService.transferMoney({
        receiverUpiId,
        receiverMobile,
        amount,
        pin,
        riskAcknowledged,
        senderId: profile.walletId || profile.userId,
        identity: profile,
      });

      setSuccessMessage('Money sent successfully.');
      setTimeout(() => setSuccessMessage(''), 2200);
      void fetchProfile();
    } catch (apiError) {
      if (redirectIfUnauthorized(apiError)) {
        throw apiError;
      }

      if (apiError?.scamRisk) {
        throw apiError;
      }

      throw new Error(TransactionService.getErrorMessage(apiError, 'Transfer failed.'));
    }
  };

  const openSendModal = (contact) => {
    setSelectedContact(contact);
    setIsSendModalOpen(true);
  };

  const openMessageComposer = (contact) => {
    setMessageContact(contact);
    setMessageText('');
    setSuccessMessage('');
    setError('');
  };

  const handleSendMessage = async (event) => {
    event.preventDefault();

    if (!messageContact) {
      return;
    }

    if (!messageText.trim()) {
      setError('Message cannot be empty.');
      return;
    }

    setIsSendingMessage(true);
    setError('');
    setSuccessMessage('');

    try {
      await TransactionService.sendContactMessage({
        receiverUpiId: messageContact.upiId,
        receiverMobile: messageContact.mobile,
        message: messageText,
      });
      setSuccessMessage(`Message sent to ${messageContact.name}.`);
      setMessageText('');
      setMessageContact(null);
      setTimeout(() => setSuccessMessage(''), 2200);
    } catch (apiError) {
      if (redirectIfUnauthorized(apiError)) {
        return;
      }
      setError(TransactionService.getErrorMessage(apiError, 'Unable to send message.'));
    } finally {
      setIsSendingMessage(false);
    }
  };

  const totalContacts = useMemo(() => contacts.length, [contacts]);

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
              <h1 className="text-3xl font-bold text-white md:text-4xl">Contacts</h1>
              <p className="mt-1 text-sm text-gray-300">
                All registered UPI users from your database. Select any contact to message or send money.
              </p>
            </div>
            <div className="rounded-xl border border-white/20 bg-white/5 px-4 py-3 text-sm text-gray-200">
              {totalContacts} users available
            </div>
          </motion.div>

          {error ? (
            <div className="mb-4 rounded-xl border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200">
              {error}
            </div>
          ) : null}

          {successMessage ? (
            <div className="mb-4 rounded-xl border border-emerald-500/40 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-200">
              {successMessage}
            </div>
          ) : null}

          {messageContact ? (
            <Card className="mb-5 p-5">
              <h2 className="text-lg font-semibold text-white">Message {messageContact.name}</h2>
              <p className="mt-1 text-sm text-gray-400">{messageContact.upiId}</p>
              <form className="mt-4 space-y-3" onSubmit={handleSendMessage}>
                <textarea
                  className="min-h-24 w-full rounded-xl border border-white/20 bg-white/10 p-3 text-sm text-white placeholder-gray-400 outline-none transition focus:border-cyan-400/50 focus:ring-2 focus:ring-cyan-500/30"
                  placeholder="Type your message..."
                  value={messageText}
                  onChange={(event) => setMessageText(event.target.value)}
                  maxLength={500}
                />
                <div className="flex gap-3">
                  <Button
                    type="button"
                    variant="ghost"
                    className="flex-1"
                    onClick={() => {
                      setMessageContact(null);
                      setMessageText('');
                    }}
                    disabled={isSendingMessage}
                  >
                    Cancel
                  </Button>
                  <Button type="submit" variant="primary" className="flex-1" disabled={isSendingMessage}>
                    {isSendingMessage ? (
                      <span className="flex items-center justify-center">
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        Sending...
                      </span>
                    ) : (
                      <span className="flex items-center justify-center">
                        <Send className="mr-2 h-4 w-4" />
                        Send Message
                      </span>
                    )}
                  </Button>
                </div>
              </form>
            </Card>
          ) : null}

          <Card className="mb-5 p-5">
            <Input
              label="Search contacts"
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder="Search by name, mobile, or UPI ID"
            />
            {isRefreshing ? (
              <p className="mt-2 flex items-center text-xs text-cyan-200">
                <Loader2 className="mr-1 h-3 w-3 animate-spin" />
                Refreshing results...
              </p>
            ) : null}
          </Card>

          {isLoading ? (
            <div className="space-y-3">
              {Array.from({ length: 6 }).map((_, index) => (
                <div key={index} className="shimmer h-24 rounded-xl" />
              ))}
            </div>
          ) : contacts.length === 0 ? (
            <Card className="p-10 text-center">
              <Search className="mx-auto h-8 w-8 text-gray-400" />
              <p className="mt-3 text-gray-300">No contacts found for your search.</p>
            </Card>
          ) : (
            <div className="space-y-3">
              {contacts.map((contact, index) => (
                <motion.div
                  key={contact.userId || `${contact.upiId}-${index}`}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: Math.min(index, 6) * 0.03 }}
                >
                  <Card className="p-4">
                    <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <UserRound className="h-4 w-4 text-cyan-300" />
                          <h3 className="truncate text-base font-semibold text-white">{contact.name}</h3>
                          <span
                            className={`rounded-full border px-2 py-0.5 text-[11px] font-semibold ${
                              statusClasses[contact.kycStatus] || statusClasses.NOT_SUBMITTED
                            }`}
                          >
                            <ShieldCheck className="mr-1 inline h-3 w-3" />
                            {contact.kycStatus}
                          </span>
                        </div>
                        <p className="mt-1 truncate text-sm text-gray-300">{contact.upiId || '-'}</p>
                        <p className="mt-1 flex items-center text-xs text-gray-400">
                          <Smartphone className="mr-1 h-3 w-3" />
                          {contact.mobile || '-'}
                        </p>
                      </div>
                      <div className="flex gap-2">
                        <Button type="button" variant="ghost" onClick={() => openMessageComposer(contact)}>
                          <MessageCircle className="mr-2 h-4 w-4" />
                          Message
                        </Button>
                        <Button type="button" variant="secondary" onClick={() => openSendModal(contact)}>
                          <Send className="mr-2 h-4 w-4" />
                          Send Money
                        </Button>
                      </div>
                    </div>
                  </Card>
                </motion.div>
              ))}
            </div>
          )}
        </div>
      </main>

      <SendMoneyModal
        isOpen={isSendModalOpen}
        onClose={() => setIsSendModalOpen(false)}
        onSend={onTransferSuccess}
        senderUpiId={profile.upiId}
        presetReceiverMode="upi"
        presetReceiverValue={selectedContact?.upiId || ''}
      />
    </div>
  );
};

export default Contacts;
