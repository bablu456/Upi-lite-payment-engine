import { Link, useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Clock3,
  LayoutDashboard,
  QrCode,
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
    { icon: LayoutDashboard, label: 'Dashboard', path: '/dashboard' },
    { icon: Clock3, label: 'History', path: '/transactions' },
    { icon: Users, label: 'Contacts', path: '/contacts' },
    { icon: QrCode, label: 'My QR', path: '/qr' },
    { icon: ShieldCheck, label: 'KYC', path: '/kyc' },
  ];

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <motion.div
      initial={{ x: -100, opacity: 0 }}
      animate={{ x: 0, opacity: 1 }}
      className="w-64 h-screen glass-card p-6 flex flex-col"
    >
      {/* Logo */}
      <div className="flex items-center gap-3 mb-8">
        <div className="relative">
          <div className="absolute inset-0 bg-gradient-cyber blur-xl opacity-50 rounded-lg"></div>
          <Wallet className="w-8 h-8 text-white relative z-10" />
        </div>
        <h1 className="text-2xl font-bold gradient-text">UPI-Lite</h1>
      </div>

      {/* User Info */}
      <div className="mb-8 p-4 glass-card rounded-xl">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-full bg-gradient-cyber flex items-center justify-center text-white font-bold">
            {user?.name?.charAt(0).toUpperCase() || 'U'}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-semibold text-white truncate">
              {user?.name || 'User'}
            </p>
            <p className="text-xs text-gray-400 truncate">
              {user?.email || ''}
            </p>
          </div>
        </div>
      </div>

      {/* Menu Items */}
      <nav className="flex-1 space-y-2">
        {menuItems.map((item) => {
          const Icon = item.icon;
          const isActive = location.pathname === item.path;
          
          return (
            <Link key={item.path} to={item.path}>
              <motion.div
                whileHover={{ x: 4 }}
                className={`
                  flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-200
                  ${isActive
                    ? 'bg-gradient-cyber text-white shadow-lg shadow-cyber-purple/50'
                    : 'text-gray-300 hover:bg-white/10 hover:text-white'
                  }
                `}
              >
                <Icon className="w-5 h-5" />
                <span className="font-medium">{item.label}</span>
              </motion.div>
            </Link>
          );
        })}
      </nav>

      {/* Logout Button */}
      <motion.button
        onClick={handleLogout}
        whileHover={{ x: 4 }}
        className="flex items-center gap-3 px-4 py-3 rounded-xl text-gray-300 hover:bg-red-500/20 hover:text-red-400 transition-all duration-200 mt-auto"
      >
        <LogOut className="w-5 h-5" />
        <span className="font-medium">Logout</span>
      </motion.button>
    </motion.div>
  );
};

export default Sidebar;
