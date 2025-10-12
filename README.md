# OAuth2 Integration with Google & GitHub

## 📘 Overview
A Spring Boot application demonstrating OAuth2 login with **Google** and **GitHub**.  
This project supports:
- Automatic user provisioning on first login
- Linking subsequent logins to the same user
- Viewing and editing of user profiles

---

## 🧩 Features
- **OAuth2 Login** with Google and GitHub
- **Auto-registration** on first OAuth2 login
- **Session-based authentication** (no JWT)
- **Profile Management** – view and update user display name and bio
- **CSRF Protection** enabled on all forms

---

## 🗂️ Domain Model
**User**
- `id`
- `email`
- `displayName`
- `avatarUrl`
- `bio`
- `createdAt`
- `updatedAt`

**AuthProvider**
- `id`
- `userId` → User
- `provider` (`GOOGLE` | `GITHUB`)
- `providerUserId`
- `providerEmail`

---

## ⚙️ How to Run

### 1️⃣ Configure Environment Variables
Set the following in your **IntelliJ Run Configuration** or `.env` file:

GOOGLE_CLIENT_ID=#YOUR_CLIENT_ID_GOOGLE
GOOGLE_CLIENT_SECRET=#YOUR_CLIENT_SECRET_GOOGLE
GITHUB_CLIENT_ID=#YOUR_CLIENT_ID_GITHUB
GITHUB_CLIENT_SECRET=#YOUR_CLIENT_SECRET_GITHUB


*(You can obtain these credentials from Google Cloud Console and GitHub Developer Settings.)*

Google: https://console.cloud.google.com/

GitHub: https://github.com/settings/developers

### 2️⃣ Run Application
Start the application by running the main class:

### 3️⃣ Access Application
Open [http://localhost:8080](http://localhost:8080)

---

## 🌐 Endpoints

| Method | Endpoint     | Description                     | Auth Required |
|--------|---------------|----------------------------------|---------------|
| GET    | `/`           | Home with Login buttons          | ❌ |
| GET    | `/profile`    | View own profile                 | ✅ |
| POST   | `/profile`    | Update displayName, bio          | ✅ |
| GET    | `/logout`     | Logout and redirect to home      | ✅ |

---

## 🗒️ Notes
- H2 used for development (in-memory).  
  Use MySQL or PostgreSQL for persistent storage.
- GitHub may not return an email by default.  
  The app retrieves user emails from `/user/emails` endpoint using the access token.
- CSRF protection is enabled; all forms include a CSRF token.
- Session-based authentication only (JWT not used).

---

## 🧠 Author
**John Joseph Laborada**  
CIT-U | IT342 – System Integration & Architecture  
October 2025
