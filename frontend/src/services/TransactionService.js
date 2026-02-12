import api from './api';

const AUTH_STATUSES = new Set([401, 403]);

const parseStoredUser = () => {
  try {
    return JSON.parse(localStorage.getItem('user') || '{}');
  } catch {
    return {};
  }
};

const createIdempotencyKey = (prefix = 'req') => {
  const randomPart =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}-${randomPart}`;
};

const toNumber = (value) => {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue : 0;
};

const toList = (payload) => {
  if (Array.isArray(payload)) {
    return payload;
  }

  if (Array.isArray(payload?.data)) {
    return payload.data;
  }

  if (Array.isArray(payload?.transactions)) {
    return payload.transactions;
  }

  if (Array.isArray(payload?.content)) {
    return payload.content;
  }

  return [];
};

const shortId = (id) => {
  if (!id) {
    return 'Unknown';
  }

  const value = String(id);
  if (value.length <= 8) {
    return value;
  }

  return `${value.slice(0, 4)}...${value.slice(-4)}`;
};

export const isAuthError = (error) => AUTH_STATUSES.has(error?.response?.status);

export const getErrorMessage = (error, fallback = 'Something went wrong.') => {
  const payload = error?.response?.data;
  const message =
    payload?.message ||
    payload?.errorMessage ||
    (typeof payload === 'string' ? payload : '') ||
    error?.message;
  return typeof message === 'string' && message.trim() ? message.trim() : fallback;
};

export const normalizeProfile = (payload = {}, fallback = {}) => ({
  name: payload?.name || payload?.username || payload?.fullName || payload?.user?.name || fallback?.name || 'User',
  email: payload?.email || payload?.user?.email || fallback?.email || '',
  mobile: payload?.mobile || payload?.phone || fallback?.mobile || '',
  upiId:
    payload?.upiId ||
    payload?.wallet?.upiId ||
    payload?.user?.upiId ||
    payload?.profile?.upiId ||
    fallback?.upiId ||
    '',
  balance: toNumber(
    payload?.balance ??
      payload?.currentWalletBalance ??
      payload?.walletBalance ??
      payload?.wallet?.balance ??
      payload?.profile?.balance ??
      fallback?.balance ??
      0
  ),
  userId: payload?.id || payload?.userId || payload?.user?.id || fallback?.userId || null,
  walletId: payload?.walletId || payload?.wallet?.id || payload?.userWalletId || fallback?.walletId || null,
  pinConfigured: Boolean(payload?.pinConfigured ?? fallback?.pinConfigured ?? false),
  kycStatus: payload?.kycStatus || fallback?.kycStatus || 'NOT_SUBMITTED',
  kycDocumentName: payload?.kycDocumentName || fallback?.kycDocumentName || '',
  kycSubmittedAt: payload?.kycSubmittedAt || fallback?.kycSubmittedAt || null,
  kycReviewedAt: payload?.kycReviewedAt || fallback?.kycReviewedAt || null,
});

export const normalizeContact = (contact = {}) => ({
  userId: contact?.userId || contact?.id || null,
  name: contact?.name || 'Unknown User',
  mobile: contact?.mobile || '',
  upiId: contact?.upiId || '',
  kycStatus: contact?.kycStatus || 'NOT_SUBMITTED',
});

export const normalizeTransaction = (transaction = {}, identity = {}) => {
  const normalizedType = (transaction?.type || transaction?.transactionType || '').toString().toUpperCase();
  const senderId = transaction?.senderId || transaction?.fromWalletId || null;
  const receiverId = transaction?.receiverId || transaction?.toWalletId || null;
  const identityWalletId = identity?.walletId ? String(identity.walletId) : '';
  const identityUpiId = identity?.upiId || '';

  let type = normalizedType;
  if (!type) {
    if (identityWalletId && receiverId && String(receiverId) === identityWalletId) {
      type = 'CREDIT';
    } else if (identityWalletId && senderId && String(senderId) === identityWalletId) {
      type = 'DEBIT';
    } else if (identityUpiId && transaction?.receiverUpiId === identityUpiId) {
      type = 'CREDIT';
    } else {
      type = 'DEBIT';
    }
  }

  return {
    id: transaction?.id || `${transaction?.timestamp || Date.now()}-${transaction?.amount || 0}`,
    senderId,
    receiverId,
    senderName: transaction?.senderName || transaction?.sender || shortId(senderId),
    receiverName: transaction?.receiverName || transaction?.receiver || shortId(receiverId),
    senderUpiId: transaction?.senderUpiId || '',
    receiverUpiId: transaction?.receiverUpiId || '',
    amount: toNumber(transaction?.amount),
    timestamp: transaction?.timestamp || transaction?.createdAt || new Date().toISOString(),
    type,
    status: transaction?.status || 'SUCCESS',
  };
};

export const normalizeHistoryPage = (payload = {}, identity = {}, fallback = {}) => {
  const transactions = toList(payload).map((item) => normalizeTransaction(item, identity));
  const pageNumber = Number(payload?.number ?? fallback?.page ?? 0);
  const pageSize = Number(payload?.size ?? fallback?.size ?? transactions.length ?? 0);
  const totalElements = Number(payload?.totalElements ?? transactions.length ?? 0);
  const totalPages = Number(payload?.totalPages ?? (pageSize > 0 ? Math.ceil(totalElements / pageSize) : 1));
  const first = Boolean(payload?.first ?? pageNumber <= 0);
  const last = Boolean(payload?.last ?? pageNumber >= Math.max(totalPages - 1, 0));

  return {
    transactions,
    page: Number.isFinite(pageNumber) ? pageNumber : 0,
    size: Number.isFinite(pageSize) ? pageSize : transactions.length,
    totalElements: Number.isFinite(totalElements) ? totalElements : transactions.length,
    totalPages: Number.isFinite(totalPages) ? totalPages : 1,
    first,
    last,
  };
};

export const getProfile = async () => {
  const storedUser = parseStoredUser();
  const fallbackProfile = normalizeProfile(storedUser);

  const candidateEndpoints = ['/users/profile', '/users/me'];

  for (const endpoint of candidateEndpoints) {
    try {
      const response = await api.get(endpoint);
      return normalizeProfile(response.data, fallbackProfile);
    } catch (error) {
      const status = error?.response?.status;

      if (isAuthError(error)) {
        throw error;
      }

      if (status !== 404 && status !== 405) {
        break;
      }
    }
  }

  if (storedUser?.email) {
    try {
      const response = await api.get('/users');
      const match = toList(response.data).find((user) => user?.email === storedUser.email);
      if (match) {
        return normalizeProfile(match, fallbackProfile);
      }
    } catch (error) {
      if (isAuthError(error)) {
        throw error;
      }
    }
  }

  return fallbackProfile;
};

export const getTransactionHistoryPage = async (identity = {}, options = {}) => {
  const page = Number.isFinite(Number(options?.page)) ? Number(options.page) : 0;
  const size = Number.isFinite(Number(options?.size)) ? Number(options.size) : 10;
  const type = (options?.type || 'ALL').toString().toUpperCase();
  const fromDate = options?.fromDate?.trim();
  const toDate = options?.toDate?.trim();
  const params = {
    page,
    size,
    type,
    fromDate: fromDate || undefined,
    toDate: toDate || undefined,
  };

  const fallbackId = identity?.walletId || identity?.userId;
  if (fallbackId) {
    try {
      const response = await api.get(`/transactions/history/${fallbackId}/paged`, { params });
      return normalizeHistoryPage(response.data, identity, { page, size });
    } catch (error) {
      if (isAuthError(error)) {
        throw error;
      }
      const status = error?.response?.status;
      if (status !== 404 && status !== 405) {
        throw error;
      }
    }
  }

  try {
    const response = await api.get('/transactions/history', { params });
    return normalizeHistoryPage(response.data, identity, { page, size });
  } catch (error) {
    if (isAuthError(error)) {
      throw error;
    }

    if (!fallbackId) {
      throw error;
    }

    const response = await api.get(`/transactions/history/${fallbackId}`);
    return normalizeHistoryPage(response.data, identity, { page: 0, size });
  }
};

export const getTransactionHistory = async (identity = {}) => {
  const historyPage = await getTransactionHistoryPage(identity, { page: 0, size: 50, type: 'ALL' });
  return historyPage.transactions;
};

export const transferMoney = async ({
  receiverUpiId,
  receiverMobile,
  amount,
  pin,
  senderId,
  identity,
}) => {
  const cleanReceiverUpiId = receiverUpiId?.trim();
  const cleanReceiverMobile = receiverMobile?.trim();
  const cleanAmount = toNumber(amount);
  const payload = {
    amount: cleanAmount,
  };

  if (cleanReceiverUpiId) {
    payload.receiverUpiId = cleanReceiverUpiId;
  }

  if (cleanReceiverMobile) {
    payload.receiverMobile = cleanReceiverMobile;
  }

  if (pin?.trim()) {
    payload.pin = pin.trim();
  }

  const idempotencyKey = createIdempotencyKey('transfer');

  try {
    const response = await api.post('/transactions/transfer', payload, {
      headers: {
        'Idempotency-Key': idempotencyKey,
      },
    });
    return normalizeTransaction(response.data, identity);
  } catch (error) {
    if (isAuthError(error) || !senderId) {
      throw error;
    }
  }

  const fallbackResponse = await api.post('/transactions/transfer', {
    ...payload,
    senderId,
  }, {
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
  });

  return normalizeTransaction(fallbackResponse.data, identity);
};

export const setPin = async ({ pin, confirmPin }) => {
  const response = await api.post('/users/pin/setup', {
    pin,
    confirmPin,
  });
  return response.data;
};

export const creditWallet = async ({ amount }) => {
  const response = await api.post('/wallet/credit', { amount: toNumber(amount) }, {
    headers: {
      'Idempotency-Key': createIdempotencyKey('credit'),
    },
  });
  return response.data;
};

export const getMyUpiQr = async ({ amount, note } = {}) => {
  const params = {};
  if (amount !== undefined && amount !== null && String(amount).trim() !== '') {
    params.amount = toNumber(amount);
  }
  if (note?.trim()) {
    params.note = note.trim();
  }

  const response = await api.get('/qr/my-upi', { params });
  return response.data;
};

export const getKycStatus = async () => {
  const response = await api.get('/users/kyc/status');
  return response.data;
};

export const submitKyc = async (documentFile) => {
  const formData = new FormData();
  formData.append('document', documentFile);

  const response = await api.post('/users/kyc/submit', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return response.data;
};

export const mockApproveKyc = async () => {
  const response = await api.post('/users/kyc/mock-approve');
  return response.data;
};

export const getContacts = async (query = '') => {
  const response = await api.get('/users/contacts', {
    params: {
      query: query?.trim() || undefined,
    },
  });
  return toList(response.data).map(normalizeContact);
};

export const sendContactMessage = async ({ receiverUpiId, receiverMobile, message }) => {
  const payload = {
    message: message?.trim() || '',
  };

  if (receiverUpiId?.trim()) {
    payload.receiverUpiId = receiverUpiId.trim();
  }

  if (receiverMobile?.trim()) {
    payload.receiverMobile = receiverMobile.trim();
  }

  const response = await api.post('/users/contacts/message', payload);
  return response.data;
};

const parseEventPayload = (rawData) => {
  if (typeof rawData !== 'string') {
    return rawData ?? null;
  }

  try {
    return JSON.parse(rawData);
  } catch {
    return { message: rawData };
  }
};

export const subscribePaymentAlerts = ({ onAlert, onOpen, onError } = {}) => {
  const token = localStorage.getItem('token');
  if (!token) {
    return () => {};
  }

  const streamUrl = `${api.defaults.baseURL}/notifications/stream?token=${encodeURIComponent(token)}`;
  const eventSource = new EventSource(streamUrl);

  eventSource.addEventListener('connected', (event) => {
    if (typeof onOpen === 'function') {
      onOpen(parseEventPayload(event?.data));
    }
  });

  eventSource.addEventListener('payment-alert', (event) => {
    if (typeof onAlert === 'function') {
      onAlert(parseEventPayload(event?.data));
    }
  });

  eventSource.onerror = (event) => {
    if (typeof onError === 'function') {
      onError(event);
    }
  };

  return () => {
    eventSource.close();
  };
};

const TransactionService = {
  getProfile,
  getTransactionHistoryPage,
  getTransactionHistory,
  transferMoney,
  setPin,
  creditWallet,
  getMyUpiQr,
  getKycStatus,
  submitKyc,
  mockApproveKyc,
  getContacts,
  sendContactMessage,
  subscribePaymentAlerts,
  getErrorMessage,
  isAuthError,
  normalizeTransaction,
  normalizeHistoryPage,
  normalizeProfile,
  normalizeContact,
};

export default TransactionService;
