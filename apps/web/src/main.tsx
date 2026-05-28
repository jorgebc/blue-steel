import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './index.css'
import { LoginPage } from '@/features/auth/LoginPage'
import { ChangePasswordPage } from '@/features/auth/ChangePasswordPage'
import { StatusPage } from '@/features/status/StatusPage'
import { RequireAuth } from '@/components/domain/RequireAuth'

const queryClient = new QueryClient()

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/status" element={<StatusPage />} />
          <Route
            path="/change-password"
            element={
              <RequireAuth>
                <ChangePasswordPage />
              </RequireAuth>
            }
          />
          <Route
            path="/"
            element={
              <RequireAuth>
                <div className="flex min-h-screen items-center justify-center bg-slate-50">
                  <p className="text-slate-500">Campaign list coming soon.</p>
                </div>
              </RequireAuth>
            }
          />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
)
