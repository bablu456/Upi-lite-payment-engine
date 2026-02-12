import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { BadgeCheck, FileCheck2, Loader2, Upload } from 'lucide-react';
import Sidebar from '../components/Sidebar';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import { useAuth } from '../context/AuthContext';
import TransactionService from '../services/TransactionService';

const initialStatus = {
  kycStatus: 'NOT_SUBMITTED',
  kycDocumentName: '',
  kycSubmittedAt: null,
  kycReviewedAt: null,
  message: '',
};

const formatDateTime = (value) => {
  if (!value) {
    return '-';
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return '-';
  }

  return parsed.toLocaleString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const statusStyles = {
  NOT_SUBMITTED: 'bg-slate-500/20 text-slate-200 border-slate-400/30',
  PENDING: 'bg-amber-500/20 text-amber-200 border-amber-400/30',
  APPROVED: 'bg-emerald-500/20 text-emerald-200 border-emerald-400/30',
  REJECTED: 'bg-rose-500/20 text-rose-200 border-rose-400/30',
};

const KycPage = () => {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const fileInputRef = useRef(null);

  const [profile, setProfile] = useState({ name: 'User', upiId: '' });
  const [kycStatus, setKycStatus] = useState(initialStatus);
  const [selectedFile, setSelectedFile] = useState(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isApproving, setIsApproving] = useState(false);

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

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const [profileResponse, statusResponse] = await Promise.all([
        TransactionService.getProfile(),
        TransactionService.getKycStatus(),
      ]);
      setProfile(profileResponse);
      setKycStatus(statusResponse);
    } catch (apiError) {
      if (!redirectIfUnauthorized(apiError)) {
        setError(TransactionService.getErrorMessage(apiError, 'Unable to fetch KYC status.'));
      }
    } finally {
      setIsLoading(false);
    }
  }, [redirectIfUnauthorized]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const validateDocument = (file) => {
    if (!file) {
      return 'Please select a document to upload.';
    }

    const extension = file.name.split('.').pop()?.toLowerCase();
    const allowedExtensions = ['pdf', 'png', 'jpg', 'jpeg'];
    if (!extension || !allowedExtensions.includes(extension)) {
      return 'Only PDF, PNG, JPG, and JPEG files are allowed.';
    }

    if (file.size > 5 * 1024 * 1024) {
      return 'File size must be under 5MB.';
    }

    return '';
  };

  const handleSubmitKyc = async (event) => {
    event.preventDefault();
    const validationError = validateDocument(selectedFile);
    if (validationError) {
      setError(validationError);
      setSuccess('');
      return;
    }

    setIsSubmitting(true);
    setError('');
    setSuccess('');

    try {
      const response = await TransactionService.submitKyc(selectedFile);
      setKycStatus(response);
      setSuccess(response?.message || 'KYC submitted successfully.');
      setSelectedFile(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    } catch (apiError) {
      if (redirectIfUnauthorized(apiError)) {
        return;
      }
      setError(TransactionService.getErrorMessage(apiError, 'Unable to submit KYC.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleMockApprove = async () => {
    setIsApproving(true);
    setError('');
    setSuccess('');

    try {
      const response = await TransactionService.mockApproveKyc();
      setKycStatus(response);
      setSuccess(response?.message || 'KYC approved.');
    } catch (apiError) {
      if (redirectIfUnauthorized(apiError)) {
        return;
      }
      setError(TransactionService.getErrorMessage(apiError, 'Unable to approve KYC.'));
    } finally {
      setIsApproving(false);
    }
  };

  const currentStatus = kycStatus?.kycStatus || 'NOT_SUBMITTED';

  return (
    <div className="flex h-screen overflow-hidden bg-cyber-dark">
      <Sidebar />
      <main className="flex-1 overflow-y-auto">
        <div className="mx-auto max-w-5xl p-5 md:p-8">
          <motion.div initial={{ opacity: 0, y: -16 }} animate={{ opacity: 1, y: 0 }} className="mb-6">
            <h1 className="text-3xl font-bold text-white md:text-4xl">KYC Verification</h1>
            <p className="mt-1 text-sm text-gray-300">
              Submit a mock identity document to enable compliant wallet operations.
            </p>
          </motion.div>

          {error ? (
            <div className="mb-5 rounded-xl border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200">
              {error}
            </div>
          ) : null}

          {success ? (
            <div className="mb-5 rounded-xl border border-emerald-500/40 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-200">
              {success}
            </div>
          ) : null}

          <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
            <Card className="p-6">
              <h2 className="text-xl font-semibold text-white">Current Status</h2>
              {isLoading ? (
                <div className="mt-5 space-y-3">
                  <div className="shimmer h-8 rounded-xl" />
                  <div className="shimmer h-12 rounded-xl" />
                  <div className="shimmer h-12 rounded-xl" />
                </div>
              ) : (
                <div className="mt-5 space-y-3">
                  <div
                    className={`inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold ${statusStyles[currentStatus] || statusStyles.NOT_SUBMITTED}`}
                  >
                    <BadgeCheck className="h-4 w-4" />
                    {currentStatus}
                  </div>
                  <div className="rounded-xl border border-white/10 bg-white/5 p-4 text-sm text-gray-300">
                    <p>User: {profile.name || 'User'}</p>
                    <p className="mt-1">UPI ID: {profile.upiId || '-'}</p>
                    <p className="mt-1">Document: {kycStatus?.kycDocumentName || '-'}</p>
                    <p className="mt-1">Submitted: {formatDateTime(kycStatus?.kycSubmittedAt)}</p>
                    <p className="mt-1">Reviewed: {formatDateTime(kycStatus?.kycReviewedAt)}</p>
                  </div>
                </div>
              )}
            </Card>

            <Card className="p-6">
              <h2 className="text-xl font-semibold text-white">Submit KYC</h2>
              <p className="mt-1 text-sm text-gray-400">Accepted formats: PDF, PNG, JPG, JPEG (max 5MB).</p>

              <form className="mt-5 space-y-4" onSubmit={handleSubmitKyc}>
                <div className="rounded-xl border border-dashed border-white/25 bg-white/5 p-4">
                  <label htmlFor="kyc-document" className="block text-sm font-medium text-gray-200">
                    Choose Document
                  </label>
                  <input
                    ref={fileInputRef}
                    id="kyc-document"
                    type="file"
                    accept=".pdf,.png,.jpg,.jpeg"
                    className="mt-3 block w-full text-sm text-gray-200 file:mr-4 file:rounded-lg file:border-0 file:bg-white/15 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-white hover:file:bg-white/25"
                    onChange={(event) => {
                      setSelectedFile(event.target.files?.[0] || null);
                      setError('');
                      setSuccess('');
                    }}
                  />
                  <p className="mt-2 text-xs text-gray-400">{selectedFile ? selectedFile.name : 'No file selected'}</p>
                </div>

                <Button type="submit" variant="primary" className="w-full" disabled={isSubmitting}>
                  {isSubmitting ? (
                    <span className="flex items-center justify-center">
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      Submitting...
                    </span>
                  ) : (
                    <span className="flex items-center justify-center">
                      <Upload className="mr-2 h-4 w-4" />
                      Submit KYC
                    </span>
                  )}
                </Button>
              </form>

              {currentStatus === 'PENDING' ? (
                <Button
                  type="button"
                  variant="secondary"
                  className="mt-4 w-full"
                  onClick={handleMockApprove}
                  disabled={isApproving}
                >
                  {isApproving ? (
                    <span className="flex items-center justify-center">
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      Approving...
                    </span>
                  ) : (
                    <span className="flex items-center justify-center">
                      <FileCheck2 className="mr-2 h-4 w-4" />
                      Mock Approve KYC
                    </span>
                  )}
                </Button>
              ) : null}
            </Card>
          </div>
        </div>
      </main>
    </div>
  );
};

export default KycPage;
