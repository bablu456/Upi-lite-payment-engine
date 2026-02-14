import { useEffect, useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { CheckCircle2, Loader2, Send, Smartphone, UserRound, X } from 'lucide-react';
import Button from './ui/Button';
import Input from './ui/Input';

const emptyErrors = {
  receiver: '',
  amount: '',
  pin: '',
  form: '',
};

const LARGE_TXN_THRESHOLD = 500;

const SendMoneyModal = ({
  isOpen,
  onClose,
  onSend,
  senderUpiId,
  presetReceiverMode = '',
  presetReceiverValue = '',
  presetAmount = '',
}) => {
  const [receiverMode, setReceiverMode] = useState('upi');
  const [receiverValue, setReceiverValue] = useState('');
  const [amount, setAmount] = useState('');
  const [pin, setPin] = useState('');
  const [errors, setErrors] = useState(emptyErrors);
  const [riskChallenge, setRiskChallenge] = useState(null);
  const [isSending, setIsSending] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);

  const numericAmount = useMemo(() => Number(amount), [amount]);
  const requiresPin = Number.isFinite(numericAmount) && numericAmount >= LARGE_TXN_THRESHOLD;

  useEffect(() => {
    if (!isOpen) {
      setReceiverMode('upi');
      setReceiverValue('');
      setAmount('');
      setPin('');
      setErrors(emptyErrors);
      setRiskChallenge(null);
      setIsSending(false);
      setIsSuccess(false);
      return;
    }

    const normalizedMode = presetReceiverMode === 'mobile' ? 'mobile' : 'upi';
    const normalizedValue = presetReceiverValue?.trim() || '';
    const normalizedAmount = String(presetAmount ?? '').trim();
    if (normalizedValue) {
      setReceiverMode(normalizedMode);
      setReceiverValue(normalizedValue);
    }

    if (normalizedAmount) {
      setAmount(normalizedAmount);
    }
  }, [isOpen, presetReceiverMode, presetReceiverValue, presetAmount]);

  const validate = () => {
    const nextErrors = { ...emptyErrors };
    const trimmedReceiver = receiverValue.trim();

    if (!trimmedReceiver) {
      nextErrors.receiver = receiverMode === 'upi' ? 'Receiver UPI ID is required.' : 'Receiver mobile is required.';
    } else if (receiverMode === 'mobile' && !/^\d{10,15}$/.test(trimmedReceiver)) {
      nextErrors.receiver = 'Enter a valid mobile number.';
    }

    if (!Number.isFinite(numericAmount) || numericAmount <= 0) {
      nextErrors.amount = 'Amount must be greater than 0.';
    }

    if (requiresPin && !/^\d{4}$/.test(pin.trim())) {
      nextErrors.pin = '4-digit UPI PIN is required for amounts >= Rs 500.';
    }

    setErrors(nextErrors);
    return !nextErrors.receiver && !nextErrors.amount && !nextErrors.pin;
  };

  const executeTransfer = async (riskAcknowledged = false) => {
    if (!riskAcknowledged && !validate()) {
      return;
    }

    setIsSending(true);
    setErrors(emptyErrors);
    setRiskChallenge(null);

    try {
      await onSend({
        receiverUpiId: receiverMode === 'upi' ? receiverValue.trim() : '',
        receiverMobile: receiverMode === 'mobile' ? receiverValue.trim() : '',
        amount: numericAmount,
        pin: requiresPin ? pin.trim() : '',
        riskAcknowledged,
      });

      setIsSuccess(true);
      window.setTimeout(() => {
        onClose();
      }, 900);
    } catch (error) {
      const riskPayload = error?.scamRisk;
      if (riskPayload?.action === 'CHALLENGE') {
        setRiskChallenge(riskPayload);
        setErrors({
          ...emptyErrors,
          form: riskPayload.message || 'Suspicious transfer detected. Confirm to continue.',
        });
        return;
      }

      const message = error?.response?.data?.message || error?.response?.data?.errorMessage || error?.response?.data || error?.message;
      setErrors({
        ...emptyErrors,
        form: typeof message === 'string' ? message : 'Transfer failed. Please try again.',
      });
    } finally {
      setIsSending(false);
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    await executeTransfer(false);
  };

  return (
    <AnimatePresence>
      {isOpen ? (
        <motion.div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4 backdrop-blur-sm"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
        >
          <motion.div
            initial={{ y: 24, opacity: 0, scale: 0.97 }}
            animate={{ y: 0, opacity: 1, scale: 1 }}
            exit={{ y: 24, opacity: 0, scale: 0.97 }}
            transition={{ duration: 0.25 }}
            className="relative w-full max-w-md overflow-hidden rounded-2xl border border-white/20 bg-slate-900/85 p-6 shadow-2xl"
          >
            <button
              onClick={onClose}
              className="absolute right-4 top-4 rounded-full p-1 text-gray-300 transition-colors hover:bg-white/10 hover:text-white"
              type="button"
              aria-label="Close send money modal"
            >
              <X className="h-5 w-5" />
            </button>

            {isSuccess ? (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                className="py-8 text-center"
              >
                <motion.div
                  className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-emerald-500/20"
                  animate={{ scale: [1, 1.12, 1] }}
                  transition={{ duration: 0.6 }}
                >
                  <CheckCircle2 className="h-10 w-10 text-emerald-400" />
                </motion.div>
                <h3 className="mb-1 text-2xl font-semibold text-white">Transfer Successful</h3>
                <p className="text-sm text-gray-300">Your balance is being refreshed.</p>
              </motion.div>
            ) : (
              <>
                <h2 className="text-2xl font-bold text-white">Send Money</h2>
                <p className="mt-1 text-sm text-gray-300">Send instantly from {senderUpiId || 'your wallet'}.</p>

                <div className="mt-5 grid grid-cols-2 gap-2 rounded-xl border border-white/10 bg-white/5 p-1">
                  <button
                    type="button"
                    onClick={() => {
                      setReceiverMode('upi');
                      setReceiverValue('');
                      setErrors(emptyErrors);
                      setRiskChallenge(null);
                    }}
                    className={`flex items-center justify-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition ${
                      receiverMode === 'upi' ? 'bg-cyan-500/20 text-cyan-100' : 'text-gray-300 hover:bg-white/5'
                    }`}
                  >
                    <UserRound className="h-4 w-4" /> UPI ID
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setReceiverMode('mobile');
                      setReceiverValue('');
                      setErrors(emptyErrors);
                      setRiskChallenge(null);
                    }}
                    className={`flex items-center justify-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition ${
                      receiverMode === 'mobile' ? 'bg-cyan-500/20 text-cyan-100' : 'text-gray-300 hover:bg-white/5'
                    }`}
                  >
                    <Smartphone className="h-4 w-4" /> Mobile
                  </button>
                </div>

                <form className="mt-5 space-y-4" onSubmit={handleSubmit}>
                  <Input
                    label={receiverMode === 'upi' ? 'Receiver UPI ID' : 'Receiver Mobile'}
                    value={receiverValue}
                    onChange={(event) => {
                      setReceiverValue(event.target.value);
                      setRiskChallenge(null);
                    }}
                    placeholder={receiverMode === 'upi' ? 'receiver@bank' : '9876543210'}
                    autoComplete="off"
                    error={errors.receiver}
                  />

                  <Input
                    label="Amount"
                    value={amount}
                    onChange={(event) => {
                      setAmount(event.target.value);
                      setRiskChallenge(null);
                    }}
                    placeholder="0.00"
                    type="number"
                    min="0"
                    step="0.01"
                    error={errors.amount}
                  />

                  {requiresPin ? (
                    <Input
                      label="UPI PIN"
                      value={pin}
                      onChange={(event) => {
                        setPin(event.target.value);
                        setRiskChallenge(null);
                      }}
                      placeholder="4-digit PIN"
                      type="password"
                      maxLength={4}
                      error={errors.pin}
                    />
                  ) : (
                    <p className="text-xs text-gray-400">PIN not required for amounts below Rs 500.</p>
                  )}

                  {errors.form ? (
                    <p className="rounded-lg border border-red-500/40 bg-red-500/10 px-3 py-2 text-sm text-red-300">
                      {errors.form}
                    </p>
                  ) : null}

                  {riskChallenge ? (
                    <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-3 text-sm text-amber-200">
                      <p className="font-semibold">Scam Shield Warning</p>
                      {riskChallenge.riskScore !== null ? (
                        <p className="mt-1 text-xs text-amber-300">Risk score: {riskChallenge.riskScore}</p>
                      ) : null}
                      {Array.isArray(riskChallenge.reasons) && riskChallenge.reasons.length > 0 ? (
                        <ul className="mt-2 list-disc space-y-1 pl-4 text-xs text-amber-100">
                          {riskChallenge.reasons.map((reason, index) => (
                            <li key={`${reason}-${index}`}>{reason}</li>
                          ))}
                        </ul>
                      ) : null}
                      <Button
                        type="button"
                        variant="secondary"
                        className="mt-3 w-full"
                        disabled={isSending}
                        onClick={() => {
                          void executeTransfer(true);
                        }}
                      >
                        Proceed Anyway
                      </Button>
                    </div>
                  ) : null}

                  <div className="flex gap-3 pt-2">
                    <Button
                      type="button"
                      variant="ghost"
                      className="flex-1"
                      onClick={onClose}
                      disabled={isSending}
                    >
                      Cancel
                    </Button>
                    <Button type="submit" variant="primary" className="flex-1" disabled={isSending}>
                      {isSending ? (
                        <span className="flex items-center justify-center">
                          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                          Sending...
                        </span>
                      ) : (
                        <span className="flex items-center justify-center">
                          <Send className="mr-2 h-4 w-4" />
                          Send
                        </span>
                      )}
                    </Button>
                  </div>
                </form>
              </>
            )}
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
};

export default SendMoneyModal;
