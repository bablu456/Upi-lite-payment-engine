# UPI-Lite Frontend

A premium Fintech application frontend built with React + Vite, featuring a modern Cyber-Fintech design aesthetic.

## 🚀 Quick Start

### Prerequisites
- Node.js 18+ and npm/yarn

### Installation

```bash
cd frontend
npm install
```

### Development

```bash
npm run dev
```

The app will be available at `http://localhost:3000`

### Build for Production

```bash
npm run build
```

## 🎨 Design System

- **Theme**: Cyber-Fintech (Dark Mode)
- **Colors**: 
  - Deep Charcoal: `#0f172a`
  - Electric Purple: `#8b5cf6`
  - Neon Blue: `#3b82f6`
- **Style**: Glassmorphism with translucent backgrounds and blur effects
- **Typography**: Inter/Poppins

## 📁 Project Structure

```
frontend/
├── src/
│   ├── components/
│   │   ├── ui/          # Reusable UI components (Button, Input, Card)
│   │   ├── Sidebar.jsx
│   │   └── ProtectedRoute.jsx
│   ├── pages/           # Page components (Login, Register, Dashboard)
│   ├── services/        # API service with Axios
│   ├── context/         # React Context (AuthContext)
│   ├── App.jsx          # Main app component with routing
│   └── main.jsx         # Entry point
├── package.json
├── vite.config.js
└── tailwind.config.js
```

## 🔐 Authentication

The app uses JWT authentication. Tokens are stored in localStorage and automatically attached to API requests via Axios interceptors.

**Important**: Ensure your backend has a login endpoint at `/api/users/login` that returns a JWT token. See `API_ENDPOINTS.md` for details.

## 🌐 API Configuration

The API base URL is configured in `src/services/api.js`. Default: `http://localhost:8080/api`

**Note**: You may need to adjust the API endpoints in:
- `src/context/AuthContext.jsx` - Login/Register endpoints
- `src/pages/Dashboard.jsx` - Transaction and balance endpoints

See `API_ENDPOINTS.md` for complete endpoint documentation.

## 📝 Features

- ✅ Modern glassmorphism UI design
- ✅ JWT-based authentication
- ✅ Protected routes
- ✅ Responsive design
- ✅ Smooth animations with Framer Motion
- ✅ Dashboard with balance and transactions
- ✅ Sidebar navigation

## 🛠️ Tech Stack

- React 18
- Vite
- Tailwind CSS
- Framer Motion
- Axios
- React Router DOM
- Lucide React (Icons)
