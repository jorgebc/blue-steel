import React, { useEffect } from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './index.css'
import { initAuth } from '@/api/auth'
import { LoginPage } from '@/features/auth/LoginPage'
import { ChangePasswordPage } from '@/features/auth/ChangePasswordPage'
import { StatusPage } from '@/features/status/StatusPage'
import { CampaignListPage } from '@/features/campaigns/CampaignListPage'
import { CampaignHomePage } from '@/features/campaigns/CampaignHomePage'
import { CreateCampaignPage } from '@/features/campaigns/CreateCampaignPage'
import { UserSettingsPage } from '@/features/settings/UserSettingsPage'
import { InvitePlatformUserPage } from '@/features/admin/InvitePlatformUserPage'
import { SubmitSessionPage } from '@/features/input/SubmitSessionPage'
import { DiffReviewPage } from '@/features/input/DiffReviewPage'
import { SessionsListPage } from '@/features/input/SessionsListPage'
import { SessionDetailPage } from '@/features/input/SessionDetailPage'
import { QueryPage } from '@/features/query/QueryPage'
import { ProposalReviewQueuePage } from '@/features/proposals/ProposalReviewQueuePage'
import { ExplorationLayout } from '@/features/exploration/ExplorationLayout'
import { EntitiesPage } from '@/features/exploration/entities/EntitiesPage'
import { EntityProfilePage } from '@/features/exploration/entities/EntityProfilePage'
import { SpacesPage } from '@/features/exploration/spaces/SpacesPage'
import { SpaceProfilePage } from '@/features/exploration/spaces/SpaceProfilePage'
import { TimelinePage } from '@/features/exploration/timeline/TimelinePage'
import { EventDetailPage } from '@/features/exploration/timeline/EventDetailPage'
import { RelationsPage } from '@/features/exploration/relations/RelationsPage'
import { RelationDetailPage } from '@/features/exploration/relations/RelationDetailPage'
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
              <Route path="/settings" element={<UserSettingsPage />} />
              <Route path="/invite" element={<InvitePlatformUserPage />} />
              <Route path="/campaigns/new" element={<CreateCampaignPage />} />
              <Route path="/campaigns/:campaignId" element={<CampaignContextGuard />}>
                <Route element={<AppShell />}>
                  <Route index element={<CampaignHomePage />} />
                  <Route path="sessions" element={<SessionsListPage />} />
                  <Route path="sessions/new" element={<SubmitSessionPage />} />
                  <Route path="sessions/:sessionId" element={<SessionDetailPage />} />
                  <Route path="sessions/:sessionId/diff" element={<DiffReviewPage />} />
                  <Route path="query" element={<QueryPage />} />
                  <Route path="proposals" element={<ProposalReviewQueuePage />} />
                  <Route path="explore" element={<ExplorationLayout />}>
                    <Route index element={<Navigate to="timeline" replace />} />
                    <Route path="timeline" element={<TimelinePage />} />
                    <Route path="events/:eventId" element={<EventDetailPage />} />
                    <Route path="entities" element={<EntitiesPage />} />
                    <Route path="entities/:entityId" element={<EntityProfilePage />} />
                    <Route path="spaces" element={<SpacesPage />} />
                    <Route path="spaces/:spaceId" element={<SpaceProfilePage />} />
                    <Route path="relations" element={<RelationsPage />} />
                    <Route path="relations/:relationId" element={<RelationDetailPage />} />
                  </Route>
                </Route>
              </Route>
            </Route>
          </Routes>
        </AppInitializer>
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>
)
