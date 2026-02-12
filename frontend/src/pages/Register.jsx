import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowRight, Smartphone, Wallet } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import Input from '../components/ui/Input';
import Button from '../components/ui/Button';

const Register = () => {
  const navigate = useNavigate();
  const { register } = useAuth();

  const [formData, setFormData] = useState({
    name: '',
    email: '',
    mobile: '',
    password: '',
    confirmPassword: '',
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleChange = (event) => {
    setFormData((previous) => ({
      ...previous,
      [event.target.name]: event.target.value,
    }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');

    if (!/^\d{10,15}$/.test(formData.mobile.trim())) {
      setError('Please enter a valid mobile number (10 to 15 digits).');
      return;
    }

    if (formData.password.length < 6) {
      setError('Password must be at least 6 characters.');
      return;
    }

    if (formData.password !== formData.confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    setLoading(true);
    const { confirmPassword, ...payload } = formData;
    const result = await register(payload);
    setLoading(false);

    if (result.success) {
      navigate('/dashboard');
      return;
    }

    setError(result.error || 'Registration failed.');
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-6">
      <motion.div
        className="w-full max-w-lg"
        initial={{ y: 20, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.4 }}
      >
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 inline-flex h-16 w-16 items-center justify-center rounded-2xl border border-white/20 bg-white/10">
            <Wallet className="h-8 w-8 text-cyan-100" />
          </div>
          <h1 className="text-4xl font-bold gradient-text">Create UPI Lite Account</h1>
          <p className="mt-2 text-sm text-gray-300">Register with mobile and email for OTP-based access.</p>
        </div>

        <div className="glass-card p-8">
          <form className="space-y-5" onSubmit={handleSubmit}>
            <Input
              label="Full Name"
              name="name"
              value={formData.name}
              onChange={handleChange}
              placeholder="Bablu Kumar"
              required
            />
            <Input
              label="Email Address"
              type="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              placeholder="you@example.com"
              required
            />
            <Input
              label="Mobile Number"
              type="tel"
              name="mobile"
              value={formData.mobile}
              onChange={handleChange}
              placeholder="9876543210"
              required
            />
            <Input
              label="Password"
              type="password"
              name="password"
              value={formData.password}
              onChange={handleChange}
              placeholder="At least 6 characters"
              required
            />
            <Input
              label="Confirm Password"
              type="password"
              name="confirmPassword"
              value={formData.confirmPassword}
              onChange={handleChange}
              placeholder="Re-enter password"
              required
            />

            {error ? (
              <p className="rounded-xl border border-red-500/40 bg-red-500/10 px-3 py-2 text-sm text-red-200">
                {error}
              </p>
            ) : null}

            <Button type="submit" variant="primary" className="w-full" disabled={loading}>
              {loading ? (
                'Creating account...'
              ) : (
                <span className="flex items-center justify-center">
                  <Smartphone className="mr-2 h-4 w-4" />
                  Register
                </span>
              )}
            </Button>
          </form>

          <p className="mt-7 text-center text-sm text-gray-400">
            Already have an account?{' '}
            <Link to="/login" className="font-semibold text-cyan-200 hover:text-cyan-100">
              Login with OTP
            </Link>
            <ArrowRight className="ml-1 inline h-4 w-4" />
          </p>
        </div>
      </motion.div>
    </div>
  );
};

export default Register;
