import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { AlertTriangle, ArrowRight, Camera, ScanLine, StopCircle, Upload } from 'lucide-react';
import { Html5Qrcode } from 'html5-qrcode';
import Sidebar from '../components/Sidebar';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';

const SCANNER_REGION_ID = 'upi-qr-scanner-region';

const parseUpiPaymentPayload = (rawPayload) => {
  const value = String(rawPayload || '').trim();
  if (!value) {
    throw new Error('Empty QR payload.');
  }

  if (!/^upi:\/\/pay(\?|$)/i.test(value)) {
    throw new Error('This QR is not a valid UPI payment QR.');
  }

  let queryParams;
  try {
    const parsedUrl = new URL(value);
    queryParams = parsedUrl.searchParams;
  } catch {
    const query = value.split('?')[1] || '';
    queryParams = new URLSearchParams(query);
  }

  const receiverUpiId = (queryParams.get('pa') || '').trim();
  const payeeName = (queryParams.get('pn') || '').trim();
  const note = (queryParams.get('tn') || '').trim();
  const amountRaw = (queryParams.get('am') || '').trim();

  if (!receiverUpiId) {
    throw new Error('UPI ID is missing in this QR.');
  }

  let amount = '';
  if (amountRaw) {
    const parsedAmount = Number(amountRaw);
    if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) {
      throw new Error('Amount in QR is invalid.');
    }
    amount = parsedAmount.toString();
  }

  return {
    receiverUpiId,
    payeeName,
    note,
    amount,
    rawPayload: value,
  };
};

const ScanPayPage = () => {
  const navigate = useNavigate();
  const scannerRef = useRef(null);
  const fileInputRef = useRef(null);

  const [isStartingCamera, setIsStartingCamera] = useState(false);
  const [isScanning, setIsScanning] = useState(false);
  const [error, setError] = useState('');
  const [scanResult, setScanResult] = useState(null);

  const clearScanner = useCallback(async () => {
    const scanner = scannerRef.current;
    if (!scanner) {
      return;
    }

    try {
      await scanner.stop();
    } catch {
      // Scanner might already be stopped.
    }

    try {
      await scanner.clear();
    } catch {
      // Ignore cleanup errors.
    }

    scannerRef.current = null;
    setIsScanning(false);
  }, []);

  useEffect(() => () => {
    void clearScanner();
  }, [clearScanner]);

  const handleDecodedPayload = useCallback(
    async (decodedText, stopAfterDecode) => {
      try {
        const parsedPayload = parseUpiPaymentPayload(decodedText);
        setScanResult(parsedPayload);
        setError('');

        if (stopAfterDecode) {
          await clearScanner();
        }
      } catch (payloadError) {
        setScanResult(null);
        setError(payloadError?.message || 'Invalid QR payload.');
      }
    },
    [clearScanner]
  );

  const startCameraScanner = useCallback(async () => {
    if (isStartingCamera || isScanning) {
      return;
    }

    setError('');
    setScanResult(null);
    setIsStartingCamera(true);

    try {
      const scanner = scannerRef.current || new Html5Qrcode(SCANNER_REGION_ID);
      scannerRef.current = scanner;

      const cameras = await Html5Qrcode.getCameras();
      if (!cameras.length) {
        throw new Error('No camera found on this device.');
      }

      const preferredCamera =
        cameras.find((camera) => /back|rear|environment/i.test(camera.label || '')) || cameras[0];

      await scanner.start(
        preferredCamera.id,
        {
          fps: 10,
          qrbox: { width: 260, height: 260 },
          aspectRatio: 1,
          disableFlip: false,
        },
        (decodedText) => {
          void handleDecodedPayload(decodedText, true);
        },
        () => {}
      );

      setIsScanning(true);
    } catch (scannerError) {
      setError(scannerError?.message || 'Unable to start scanner. Check camera permission.');
      await clearScanner();
    } finally {
      setIsStartingCamera(false);
    }
  }, [clearScanner, handleDecodedPayload, isScanning, isStartingCamera]);

  const stopCameraScanner = useCallback(async () => {
    await clearScanner();
  }, [clearScanner]);

  const handleImageScan = useCallback(
    async (event) => {
      const file = event.target.files?.[0];
      event.target.value = '';
      if (!file) {
        return;
      }

      setError('');
      setScanResult(null);

      try {
        const scanner = scannerRef.current || new Html5Qrcode(SCANNER_REGION_ID);
        scannerRef.current = scanner;

        if (isScanning) {
          await stopCameraScanner();
        }

        const decodedText = await scanner.scanFile(file, true);
        await handleDecodedPayload(decodedText, false);
      } catch (scanError) {
        setError(scanError?.message || 'Unable to read QR from the selected image.');
      }
    },
    [handleDecodedPayload, isScanning, stopCameraScanner]
  );

  const continueToPay = useCallback(async () => {
    if (!scanResult?.receiverUpiId) {
      return;
    }

    await clearScanner();
    navigate('/dashboard', {
      state: {
        scanPrefill: {
          receiverMode: 'upi',
          receiverValue: scanResult.receiverUpiId,
          amount: scanResult.amount || '',
        },
      },
    });
  }, [clearScanner, navigate, scanResult]);

  return (
    <div className="flex h-screen overflow-hidden bg-cyber-dark">
      <Sidebar />
      <main className="flex-1 overflow-y-auto pt-16 pb-24 md:pt-0 md:pb-0">
        <div className="mx-auto max-w-5xl p-5 md:p-8">
          <motion.div initial={{ opacity: 0, y: -16 }} animate={{ opacity: 1, y: 0 }} className="mb-6">
            <h1 className="text-3xl font-bold text-white md:text-4xl">Scan & Pay</h1>
            <p className="mt-1 text-sm text-gray-300">
              Scan any UPI QR from camera or gallery and continue payment instantly.
            </p>
          </motion.div>

          {error ? (
            <div className="mb-5 rounded-xl border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200">
              <AlertTriangle className="mr-2 inline h-4 w-4" />
              {error}
            </div>
          ) : null}

          <div className="grid grid-cols-1 gap-5 xl:grid-cols-[1.4fr_1fr]">
            <Card className="p-6">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="text-xl font-semibold text-white">Scanner</h2>
                {isScanning ? (
                  <span className="rounded-full border border-emerald-500/40 bg-emerald-500/20 px-2.5 py-1 text-xs font-semibold text-emerald-300">
                    Live
                  </span>
                ) : (
                  <span className="rounded-full border border-slate-500/40 bg-slate-500/20 px-2.5 py-1 text-xs font-semibold text-slate-300">
                    Idle
                  </span>
                )}
              </div>

              <div className="overflow-hidden rounded-2xl border border-white/20 bg-black/40">
                <div id={SCANNER_REGION_ID} className="min-h-[320px] w-full" />
              </div>

              <div className="mt-4 flex flex-wrap gap-3">
                <Button type="button" variant="primary" disabled={isStartingCamera || isScanning} onClick={startCameraScanner}>
                  <Camera className="mr-2 h-4 w-4" />
                  {isStartingCamera ? 'Starting...' : 'Start Camera'}
                </Button>

                <Button type="button" variant="ghost" disabled={!isScanning} onClick={stopCameraScanner}>
                  <StopCircle className="mr-2 h-4 w-4" />
                  Stop Camera
                </Button>

                <Button type="button" variant="secondary" onClick={() => fileInputRef.current?.click()}>
                  <Upload className="mr-2 h-4 w-4" />
                  Scan from Image
                </Button>
                <input ref={fileInputRef} type="file" accept="image/*" className="hidden" onChange={handleImageScan} />
              </div>
            </Card>

            <Card className="p-6">
              <h2 className="text-xl font-semibold text-white">Scanned Result</h2>

              {!scanResult ? (
                <div className="mt-4 rounded-xl border border-white/10 bg-white/5 p-4 text-sm text-gray-300">
                  <ScanLine className="mb-2 h-5 w-5 text-cyan-300" />
                  Scan a valid UPI QR. We will prefill receiver and amount in your Send Money flow.
                </div>
              ) : (
                <div className="mt-4 space-y-3">
                  <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                    <p className="text-xs uppercase tracking-wide text-gray-400">Receiver UPI ID</p>
                    <p className="mt-1 break-all text-sm font-semibold text-white">{scanResult.receiverUpiId}</p>
                  </div>

                  {scanResult.payeeName ? (
                    <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                      <p className="text-xs uppercase tracking-wide text-gray-400">Payee Name</p>
                      <p className="mt-1 text-sm text-white">{scanResult.payeeName}</p>
                    </div>
                  ) : null}

                  <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                    <p className="text-xs uppercase tracking-wide text-gray-400">Amount</p>
                    <p className="mt-1 text-sm text-white">{scanResult.amount ? `Rs ${scanResult.amount}` : 'Not specified'}</p>
                  </div>

                  {scanResult.note ? (
                    <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                      <p className="text-xs uppercase tracking-wide text-gray-400">Note</p>
                      <p className="mt-1 text-sm text-white">{scanResult.note}</p>
                    </div>
                  ) : null}

                  <Button type="button" variant="primary" className="mt-2 w-full" onClick={continueToPay}>
                    Continue to Pay
                    <ArrowRight className="ml-2 h-4 w-4" />
                  </Button>
                </div>
              )}
            </Card>
          </div>
        </div>
      </main>
    </div>
  );
};

export default ScanPayPage;
