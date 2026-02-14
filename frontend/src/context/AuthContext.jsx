import { createContext, useContext, useState, useEffect } from 'react';
import api from '../services/api';

const AuthContext = createContext(null);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('token');
    const storedUser = localStorage.getItem('user');
    
    if (token && storedUser) {
      try {
        setUser(JSON.parse(storedUser));
      } catch (error) {
        console.error('Error parsing user data:', error);
        localStorage.removeItem('token');
        localStorage.removeItem('user');
      }
    }
    setLoading(false);
  }, []);

  const applyAuthenticatedSession = (responseData = {}) => {
    const token =
      responseData.token ||
      responseData.jwt ||
      responseData.accessToken;

    if (!token) {
      throw new Error('No token received from server');
    }

    const userData = {
      name: responseData.name || responseData.username || 'User',
      email: responseData.email || '',
      mobile: responseData.mobile || '',
      upiId: responseData.upiId || '',
    };

    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(userData));
    setUser(userData);
  };

  const loginWithPassword = async (email, password) => {
    try {
      const response = await api.post('/users/login', { email, password });
      applyAuthenticatedSession(response.data);
      return { success: true };
    } catch (error) {
      return {
        success: false,
        error: error.response?.data?.message || error.message || 'Login failed. Please try again.',
      };
    }
  };

  const requestLoginOtp = async (identifier) => {
    try {
      const response = await api.post('/users/login/otp/request', { identifier });
      return {
        success: true,
        message: response.data?.message || 'OTP sent successfully.',
      };
    } catch (error) {
      return {
        success: false,
        error: error.response?.data?.message || error.message || 'Failed to send OTP.',
      };
    }
  };

  const verifyLoginOtp = async (identifier, otp) => {
    try {
      const response = await api.post('/users/login/otp/verify', { identifier, otp });
      applyAuthenticatedSession(response.data);
      return { success: true };
    } catch (error) {
      return {
        success: false,
        error: error.response?.data?.message || error.message || 'OTP verification failed.',
      };
    }
  };

  const requestForgotPasswordOtp = async (identifier) => {
    try {
      const response = await api.post('/users/password/forgot/request', { identifier });
      return {
        success: true,
        message: response.data?.message || 'OTP sent successfully.',
      };
    } catch (error) {
      return {
        success: false,
        error: error.response?.data?.message || error.message || 'Failed to send OTP.',
      };
    }
  };

  const resetPasswordWithOtp = async ({ identifier, otp, newPassword, confirmPassword }) => {
    try {
      const response = await api.post('/users/password/forgot/reset', {
        identifier,
        otp,
        newPassword,
        confirmPassword,
      });
      return {
        success: true,
        message: response.data?.message || 'Password reset successful.',
      };
    } catch (error) {
      return {
        success: false,
        error: error.response?.data?.message || error.message || 'Password reset failed.',
      };
    }
  };

  const register = async (userData) => {
    try {
      const response = await api.post('/users/register', {
        username: userData.name,
        email: userData.email,
        mobile: userData.mobile,
        password: userData.password,
      });
      applyAuthenticatedSession(response.data);
      return { success: true, needsLogin: false };
    } catch (error) {
      return {
        success: false,
        error: error.response?.data?.message || error.message || 'Registration failed. Please try again.',
      };
    }
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setUser(null);
  };

  const updateUserProfile = (profileUpdates = {}) => {
    setUser((previousUser) => {
      const nextUser = {
        ...(previousUser || {}),
        ...profileUpdates,
      };
      localStorage.setItem('user', JSON.stringify(nextUser));
      return nextUser;
    });
  };

  const value = {
    user,
    loginWithPassword,
    requestLoginOtp,
    verifyLoginOtp,
    requestForgotPasswordOtp,
    resetPasswordWithOtp,
    register,
    logout,
    updateUserProfile,
    loading,
    isAuthenticated: !!user,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
