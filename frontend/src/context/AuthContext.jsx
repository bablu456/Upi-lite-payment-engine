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
    // Check if user is logged in on mount
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

  const login = async (email, password) => {
    try {
      // Note: Adjust endpoint based on your backend
      // Backend has /api/users/login as public endpoint
      // Response format may vary - adjust based on your backend implementation
      const response = await api.post('/users/login', { email, password });
      
      // Adjust based on your backend response structure
      // Expected: { token: "...", user: {...} } or similar
      const token = response.data.token || response.data.jwt || response.headers.authorization?.replace('Bearer ', '');
      const userData = response.data.user || response.data;
      
      if (!token) {
        throw new Error('No token received from server');
      }
      
      localStorage.setItem('token', token);
      localStorage.setItem('user', JSON.stringify(userData));
      setUser(userData);
      
      return { success: true };
    } catch (error) {
      return {
        success: false,
        error: error.response?.data?.message || error.message || 'Login failed. Please try again.',
      };
    }
  };

  const register = async (userData) => {
    try {
      // Backend endpoint: /api/users/register
      // Expected DTO: { username, email, password }
      const response = await api.post('/users/register', {
        username: userData.name,
        email: userData.email,
        password: userData.password,
      });
      
      const newUser = response.data;
      
      // Note: Registration might not return a token - you may need to login after registration
      // If token is returned, store it; otherwise, redirect to login
      const token = response.data.token || response.data.jwt;
      
      if (token) {
        localStorage.setItem('token', token);
        localStorage.setItem('user', JSON.stringify(newUser));
        setUser(newUser);
      } else {
        // If no token, just store user and let them login
        localStorage.setItem('user', JSON.stringify(newUser));
        setUser(newUser);
      }
      
      return { success: true, needsLogin: !token };
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

  const value = {
    user,
    login,
    register,
    logout,
    loading,
    isAuthenticated: !!user,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
