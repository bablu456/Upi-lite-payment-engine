# API Endpoints Reference

This document lists the backend API endpoints and how the frontend integrates with them.

## Authentication Endpoints

### Login
- **Endpoint**: `POST /api/users/login`
- **Request Body**: 
  ```json
  {
    "email": "user@example.com",
    "password": "password123"
  }
  ```
- **Response**: Adjust based on your backend implementation
  - Expected: `{ token: "...", user: {...} }` or similar
- **Note**: You may need to create a login controller if it doesn't exist yet

### Register
- **Endpoint**: `POST /api/users/register`
- **Request Body**:
  ```json
  {
    "username": "johndoe",
    "email": "john@example.com",
    "password": "password123"
  }
  ```
- **Response**: User object (may or may not include token)

## Transaction Endpoints

### Get Transaction History
- **Endpoint**: `GET /api/transactions/history/{userId}`
- **Headers**: `Authorization: Bearer <token>`
- **Response**: Array of transactions
  ```json
  [
    {
      "id": "uuid",
      "senderId": "wallet-uuid",
      "receiverId": "wallet-uuid",
      "amount": 100.00,
      "status": "SUCCESS",
      "timestamp": "2024-01-01T12:00:00"
    }
  ]
  ```

### Transfer Money
- **Endpoint**: `POST /api/transactions/transfer`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
  ```json
  {
    "senderId": "wallet-uuid",
    "receiverUpiId": "username@upi",
    "amount": 100.00
  }
  ```

## Wallet Endpoints

### Get Wallet Balance
- **Status**: ⚠️ **Not yet implemented in backend**
- **Suggestion**: Create endpoint `GET /api/wallet/balance` that returns:
  ```json
  {
    "balance": 1000.00,
    "walletId": "uuid",
    "upiId": "username@upi"
  }
  ```
- **Alternative**: Include wallet balance in user object or create a user profile endpoint

## Notes

1. **Login Endpoint**: The backend security config allows `/api/users/login` but you may need to create a controller for it that:
   - Authenticates user credentials
   - Returns JWT token
   - Returns user object

2. **Wallet Balance**: Currently, the Dashboard uses a default balance. You should either:
   - Create a `/api/wallet/balance` endpoint
   - Include wallet info in the user object after login
   - Create a `/api/users/me` endpoint that returns user with wallet

3. **Transaction Type**: Transactions don't have a `type` field. To determine if a transaction is credit/debit:
   - Compare `transaction.receiverId` with user's `walletId` (credit if match)
   - Compare `transaction.senderId` with user's `walletId` (debit if match)

4. **User Object**: After login, ensure the user object includes:
   - `id`: User UUID
   - `walletId`: Wallet UUID (or access via `user.wallet.id`)
   - `name`, `email`, `upiId`
