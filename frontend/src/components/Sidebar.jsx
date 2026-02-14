import { Link, useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  AlertTriangle,
  Clock3,
  LayoutDashboard,
  QrCode,
  ScanLine,
  ShieldCheck,
  Users,
  LogOut,
  Wallet,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

const Sidebar = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const menuItems = [
    { icon: LayoutDashboard, label: 'Dashboard', mobileLabel: 'Home', path: '/dashboard' },
    { icon: ScanLine, label: 'Scan & Pay', mobileLabel: 'Scan', path: '/scan-pay' },
    { icon: QrCode, label: 'My QR', mobileLabel: 'My QR', path: '/qr' },
    { icon: Clock3, label: 'History', mobileLabel: 'History', path: '/transactions' },
    { icon: AlertTriangle, label: 'Disputes', mobileLabel: 'Dispute', path: '/disputes' },
    { icon: Users, label: 'Contacts', mobileLabel: 'Contacts', path: '/contacts' },
    { icon: ShieldCheck, label: 'KYC', mobileLabel: 'KYC', path: '/kyc' },
  ];

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <>
      <motion.aside
        initial={{ x: -100, opacity: 0 }}
        animate={{ x: 0, opacity: 1 }}
        className="hidden h-screen w-64 flex-col p-6 md:flex glass-card"
      >
        <div className="mb-8 flex items-center gap-3">
          <div className="relative">
            <div className="absolute inset-0 rounded-lg bg-gradient-cyber opacity-50 blur-xl" />
            <Wallet className="relative z-10 h-8 w-8 text-white" />
          </div>
          <h1 className="text-2xl font-bold gradient-text">UPI-Lite</h1>
        </div>

        <button
          type="button"
          onClick={() => navigate('/profile')}
          className="mb-8 w-full rounded-xl p-4 text-left glass-card transition hover:bg-white/15"
        >
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-gradient-cyber font-bold text-white">
              {user?.name?.charAt(0).toUpperCase() || 'U'}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold text-white">{user?.name || 'User'}</p>
              <p className="truncate text-xs text-gray-400">{user?.email || ''}</p>
            </div>
          </div>
        </button>

        <nav className="flex-1 space-y-2">
          {menuItems.map((item) => {
            const Icon = item.icon;
            const isActive = location.pathname === item.path;

            return (
              <Link key={item.path} to={item.path}>
                <motion.div
                  whileHover={{ x: 4 }}
                  className={`flex items-center gap-3 rounded-xl px-4 py-3 transition-all duration-200 ${
                    isActive
                      ? 'bg-gradient-cyber text-white shadow-lg shadow-cyber-purple/50'
                      : 'text-gray-300 hover:bg-white/10 hover:text-white'
                  }`}
                >
                  <Icon className="h-5 w-5" />
                  <span className="font-medium">{item.label}</span>
                </motion.div>
              </Link>
            );
          })}
        </nav>

        <motion.button
          onClick={handleLogout}
          whileHover={{ x: 4 }}
          className="mt-auto flex items-center gap-3 rounded-xl px-4 py-3 text-gray-300 transition-all duration-200 hover:bg-red-500/20 hover:text-red-400"
        >
          <LogOut className="h-5 w-5" />
          <span className="font-medium">Logout</span>
        </motion.button>
      </motion.aside>

      <div className="md:hidden">
        <header className="fixed inset-x-0 top-0 z-40 border-b border-white/15 bg-slate-950/85 px-4 py-3 backdrop-blur">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Wallet className="h-5 w-5 text-cyan-200" />
              <span className="text-sm font-semibold text-white">UPI-Lite</span>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => navigate('/profile')}
                className="flex h-8 w-8 items-center justify-center rounded-full border border-white/25 bg-white/10 text-xs font-bold text-white"
                aria-label="Open profile"
              >
                {user?.name?.charAt(0).toUpperCase() || 'U'}
              </button>
              <button
                type="button"
                onClick={handleLogout}
                className="rounded-lg p-2 text-gray-200 transition-colors hover:bg-red-500/20 hover:text-red-300"
                aria-label="Logout"
              >
                <LogOut className="h-4 w-4" />
              </button>
            </div>
          </div>
        </header>

        <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-white/15 bg-slate-950/90 backdrop-blur">
          <div className="grid grid-cols-7">
            {menuItems.map((item) => {
              const Icon = item.icon;
              const isActive = location.pathname === item.path;
              return (
                <Link key={item.path} to={item.path}>
                  <div
                    className={`flex flex-col items-center justify-center gap-1 px-1 py-2 transition-colors ${
                      isActive ? 'text-cyan-200' : 'text-gray-400'
                    }`}
                  >
                    <Icon className="h-4 w-4" />
                    <span className="text-[10px] font-medium leading-none">{item.mobileLabel}</span>
                  </div>
                </Link>
              );
            })}
          </div>
        </nav>
      </div>
    </>
  );
};

export default Sidebar;
