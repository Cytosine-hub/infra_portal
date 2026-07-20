# Frontend/Backend Split

This project now has a Spring Boot backend API and a separate Vue frontend.

## Backend

Run from the `backend/` directory:

```powershell
cd backend
mvn spring-boot:run
```

Important API groups:

- `GET /api/public/releases`: public release list
- `GET /api/public/releases/{token}`: public release detail
- `GET /files/{token}`: public file download
- `GET /api/auth/login`: validates admin credentials with HTTP Basic auth
- `GET /api/admin/releases`: admin release list
- `POST /api/admin/releases`: create release with multipart form data
- `PUT /api/admin/releases/{id}`: update release with multipart form data
- `POST /api/admin/releases/{id}/publish`: publish a release
- `POST /api/admin/releases/{id}/unpublish`: unpublish a release
- `DELETE /api/admin/releases/{id}`: delete a release
- `POST /api/admin/releases/import`: batch import from a server directory
- `POST /api/admin/account/password`: change the admin password

Admin APIs use the existing Spring Security admin account. The Vue app stores Basic auth credentials in `sessionStorage` for the current browser session.

## Frontend

Run from `frontend`:

```powershell
npm.cmd install
npm.cmd run dev
```

The Vite dev server listens on `http://localhost:5173` and proxies `/api` and `/files` to `http://localhost:8080`.

Build for production:

```powershell
npm.cmd run build
```

Production files are emitted to `frontend/dist`.
