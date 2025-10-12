# OAuth2 Integration with Google & GitHub

## 📘 Overview
A Spring Boot application demonstrating OAuth2 login with Google and GitHub.  
This project supports:
- Automatic user provisioning on first login
- Linking subsequent logins to the same user
- Viewing and editing user profiles

## 🧩 Features
- OAuth2 login with Google and GitHub
- Auto-registration on first OAuth2 login
- Session-based authentication (no JWT)
- Profile management: view and update display name and bio
- CSRF protection enabled on all forms

## 🗂️ Domain Model
### User
- id
- email
- displayName
- avatarUrl
- bio
- createdAt
- updatedAt

### AuthProvider
- id
- userId → User
- provider (GOOGLE | GITHUB)
- providerUserId
- providerEmail

## 📁 Project Structure


oauth2integration/
├─ src/
│ └─ main/
│ ├─ java/
│ │ └─ edu/
│ │ └─ cit/
│ │ └─ johnjosephlaborada/
│ │ └─ oauth2integration/
│ │ ├─ controller/
│ │ ├─ model/
│ │ ├─ repository/
│ │ └─ service/
│ └─ resources/
│ ├─ static/
│ ├─ templates/
│ └─ application.properties
└─ pom.xml (or build.gradle)


## ⚙️ How to Run
### 1️⃣ Configure Environment Variables
Set the following in your IntelliJ Run Configuration or `.env` file:

GOOGLE_CLIENT_ID=#YOUR_CLIENT_ID_GOOGLE
GOOGLE_CLIENT_SECRET=#YOUR_CLIENT_SECRET_GOOGLE
GITHUB_CLIENT_ID=#YOUR_CLIENT_ID_GITHUB
GITHUB_CLIENT_SECRET=#YOUR_CLIENT_SECRET_GITHUB


Google: https://console.cloud.google.com/  
GitHub: https://github.com/settings/developers

### 2️⃣ Run Application
Run the main class in Spring Boot.

### 3️⃣ Access Application
Open [http://localhost:8080](http://localhost:8080)

## 🌐 Endpoints
| Method | Endpoint    | Description                  | Auth Required |
|--------|------------|------------------------------|---------------|
| GET    | `/`        | Home with login buttons      | ❌            |
| GET    | `/profile` | View own profile             | ✅            |
| POST   | `/profile` | Update displayName, bio      | ✅            |
| GET    | `/logout`  | Logout and redirect to home  | ✅            |

## 📝 Notes
- H2 used for development (in-memory). Use MySQL or PostgreSQL for persistent storage.
- GitHub may not return email by default; the app fetches emails from `/user/emails`.
- CSRF protection is enabled; all forms include a CSRF token.
- Session-based authentication only (JWT not used).

## 🏆 Milestones Achieved
- Milestone 1: OAuth2 login works with Google and GitHub
- Milestone 2: Both providers work, user data persisted, profile page protected
- Final: Profile editing, CSRF protection, error handling included

## 🖼️ Screenshots
*(Add screenshots of login pages and profile page here)*

## Author
John Joseph Laborada  
CIT-U | IT342 – System Integration & Architecture  
October 2025

