import React, { useEffect } from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './index.css'
import { initAuth } from '@/api/auth'
import { LoginPage } from '@/features/auth/LoginPage'
import { ChangePasswordPage } from '@/features/auth/ChangePasswordPage'
import { StatusPage } from '@/features/status/StatusPage'
import { CampaignListPage } from '@/features/campaigns/CampaignListPage'
import { CampaignHomePage } from '@/features/campaigns/CampaignHomePage'
import { CreateCampaignPage } from '@/features/campaigns/CreateCampaignPage'
import { CampaignContextGuard } from '@/components/domain/CampaignContextGuard'
import { AppShell } from '@/components/domain/AppShell'
import { AuthenticatedLayout } from '@/components/domain/AuthenticatedLayout'
import { RequireAuth } from '@/components/domain/RequireAuth'
import { useAuthStore } from '@/store/authStore'

const queryClient = new QueryClient()

function AppInitializer({ children }: { children: React.ReactNode }) {
  useEffect(() => {
    initAuth().finally(() => useAuthStore.getState().setInitialized())
  }, [])
  return <>{children}</>
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppInitializer>
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
              element={
                <RequireAuth>
                  <AuthenticatedLayout />
                </RequireAuth>
              }
            >
              <Route path="/" element={<CampaignListPage />} />
              <Route path="/campaigns/new" element={<CreateCampaignPage />} />
              <Route path="/campaigns/:campaignId" element={<CampaignContextGuard />}>
                <Route element={<AppShell />}>
                  <Route index element={<CampaignHomePage />} />
                </Route>
              </Route>
            </Route>
          </Routes>
        </AppInitializer>
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>
)
