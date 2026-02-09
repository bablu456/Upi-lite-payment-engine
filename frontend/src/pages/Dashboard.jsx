import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Send, PlusCircle, ArrowUpRight, ArrowDownLeft, TrendingUp } from 'lucide-react';
import Sidebar from '../components/Sidebar';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';

const Dashboard = () => {
  const { user } = useAuth();
  const [balance, setBalance] = useState(0);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (user?.id) {
      fetchDashboardData();
    }
  }, [user]);

  const fetchDashboardData = async () => {
    try {
      // Note: The backend endpoint is /api/transactions/history/{userId}
      // You may need to create a /wallet/balance endpoint or get balance from user object
      if (user?.id) {
        try {
          const transactionsResponse = await api.get(`/transactions/history/${user.id}`);
          const txns = transactionsResponse.data || [];
          setTransactions(txns);
          
          // Determine if transaction is credit (received) or debit (sent)
          // Credit: current user is receiver, Debit: current user is sender
          // Note: You'll need to get walletId from user or create a user-wallet endpoint
          // For now, we'll use a default balance
          setBalance(1000); // Default - replace with actual wallet balance endpoint
        } catch (txnError) {
          console.error('Error fetching transactions:', txnError);
          setBalance(1000);
        }
      }
    } catch (error) {
      console.error('Error fetching dashboard data:', error);
      setBalance(1000);
    } finally {
      setLoading(false);
    }
  };

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      minimumFractionDigits: 0,
    }).format(amount);
  };

  const formatDate = (dateString) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <div className="flex h-screen overflow-hidden bg-cyber-dark">
      <Sidebar />
      
      <div className="flex-1 overflow-y-auto">
        <div className="p-8">
          {/* Header */}
          <motion.div
            initial={{ y: -20, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            className="mb-8"
          >
            <h1 className="text-4xl font-bold mb-2">Dashboard</h1>
            <p className="text-gray-400">Welcome back! Here's your financial overview.</p>
          </motion.div>

          {/* Balance Card */}
          <motion.div
            initial={{ y: 20, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ delay: 0.1 }}
            className="mb-8"
          >
            <Card className="bg-gradient-cyber border-none p-8">
              <div className="flex items-center justify-between mb-4">
                <div>
                  <p className="text-gray-300 text-sm mb-2">Total Balance</p>
                  {loading ? (
                    <div className="h-12 w-48 bg-white/20 rounded-lg animate-pulse"></div>
                  ) : (
                    <h2 className="text-5xl font-bold text-white">
                      {formatCurrency(balance)}
                    </h2>
                  )}
                </div>
                <div className="w-16 h-16 rounded-full bg-white/20 flex items-center justify-center">
                  <TrendingUp className="w-8 h-8 text-white" />
                </div>
              </div>
              <div className="flex gap-4 mt-6">
                <Button variant="secondary" size="md" className="flex-1">
                  <Send className="w-5 h-5 mr-2" />
                  Send Money
                </Button>
                <Button variant="secondary" size="md" className="flex-1">
                  <PlusCircle className="w-5 h-5 mr-2" />
                  Add Money
                </Button>
              </div>
            </Card>
          </motion.div>

          {/* Quick Actions */}
          <motion.div
            initial={{ y: 20, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ delay: 0.2 }}
            className="mb-8"
          >
            <h2 className="text-2xl font-bold mb-4">Quick Actions</h2>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <Card hover className="text-center">
                <div className="w-12 h-12 mx-auto mb-4 rounded-full bg-gradient-cyber flex items-center justify-center">
                  <Send className="w-6 h-6 text-white" />
                </div>
                <h3 className="font-semibold mb-2">Send Money</h3>
                <p className="text-sm text-gray-400">Transfer funds instantly</p>
              </Card>
              
              <Card hover className="text-center">
                <div className="w-12 h-12 mx-auto mb-4 rounded-full bg-gradient-cyber flex items-center justify-center">
                  <PlusCircle className="w-6 h-6 text-white" />
                </div>
                <h3 className="font-semibold mb-2">Add Money</h3>
                <p className="text-sm text-gray-400">Top up your wallet</p>
              </Card>
              
              <Card hover className="text-center">
                <div className="w-12 h-12 mx-auto mb-4 rounded-full bg-gradient-cyber flex items-center justify-center">
                  <TrendingUp className="w-6 h-6 text-white" />
                </div>
                <h3 className="font-semibold mb-2">View History</h3>
                <p className="text-sm text-gray-400">Check all transactions</p>
              </Card>
            </div>
          </motion.div>

          {/* Recent Transactions */}
          <motion.div
            initial={{ y: 20, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ delay: 0.3 }}
          >
            <h2 className="text-2xl font-bold mb-4">Recent Transactions</h2>
            <Card>
              {loading ? (
                <div className="space-y-4">
                  {[...Array(3)].map((_, i) => (
                    <div key={i} className="h-16 bg-white/5 rounded-lg animate-pulse"></div>
                  ))}
                </div>
              ) : transactions.length === 0 ? (
                <div className="text-center py-12">
                  <p className="text-gray-400 mb-4">No transactions yet</p>
                  <Button variant="primary">Make Your First Transaction</Button>
                </div>
              ) : (
                <div className="space-y-4">
                  {transactions.map((transaction, index) => {
                    // Determine if this is a credit (received) or debit (sent) transaction
                    // Credit: current user's wallet is receiver, Debit: current user's wallet is sender
                    // Note: You'll need to compare with user's walletId - adjust based on your backend response
                    const isCredit = transaction.receiverId === user?.walletId || 
                                   transaction.receiverId === user?.id;
                    const Icon = isCredit ? ArrowDownLeft : ArrowUpRight;
                    
                    return (
                      <motion.div
                        key={transaction.id || index}
                        initial={{ x: -20, opacity: 0 }}
                        animate={{ x: 0, opacity: 1 }}
                        transition={{ delay: index * 0.1 }}
                        className="flex items-center justify-between p-4 glass-card rounded-xl hover:bg-white/10 transition-all"
                      >
                        <div className="flex items-center gap-4">
                          <div className={`
                            w-12 h-12 rounded-full flex items-center justify-center
                            ${isCredit 
                              ? 'bg-green-500/20 text-green-400' 
                              : 'bg-red-500/20 text-red-400'
                            }
                          `}>
                            <Icon className="w-6 h-6" />
                          </div>
                          <div>
                            <p className="font-semibold">
                              {isCredit ? 'Money Received' : 'Money Sent'}
                            </p>
                            <p className="text-sm text-gray-400">
                              {formatDate(transaction.timestamp)}
                            </p>
                          </div>
                        </div>
                        <div className="text-right">
                          <p className={`
                            font-bold text-lg
                            ${isCredit ? 'text-green-400' : 'text-red-400'}
                          `}>
                            {isCredit ? '+' : '-'}{formatCurrency(Math.abs(transaction.amount || 0))}
                          </p>
                          <p className="text-xs text-gray-400">
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
      </div>
    </div>
  );
};

export default Dashboard;
