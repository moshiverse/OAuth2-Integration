# OAuth2 Integration with Google & GitHub

## Overview
Spring Boot app demonstrating OAuth2 login with Google and GitHub.
- Auto-provision user on first login
- Map subsequent logins to same user
- Profile view & edit

## How to run
1. Set environment variables in IntelliJ Run Configuration:
    - GOOGLE_CLIENT_ID
    - GOOGLE_CLIENT_SECRET
    - GITHUB_CLIENT_ID
    - GITHUB_CLIENT_SECRET

2. Run the main class `Oauth2IntegrationApplication`.

3. Visit http://localhost:8080

## Notes
- H2 used for dev (in-memory). Switch to MySQL/Postgres for persistent DB.
- GitHub may not return email; application fetches `/user/emails` using token.
- CSRF enabled; forms include CSRF token.

## Endpoints
- GET `/` - home (login)
- GET `/profile` - view profile (authenticated)
- POST `/profile` - update profile (authenticated)
- GET `/logout` - logout and redirect to home
