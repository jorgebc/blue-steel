import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { CreateCampaignPage } from './CreateCampaignPage'
import { useCreateCampaign } from '@/api/campaigns'
import { useUserSearch } from '@/api/users'
import { ApiClientError } from '@/api/client'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/api/campaigns')
vi.mock('@/api/users')

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

const mockUseCreateCampaign = vi.mocked(useCreateCampaign)
const mockUseUserSearch = vi.mocked(useUserSearch)

function setAdmin(isAdmin: boolean) {
  useAuthStore.setState({
    currentUser: { id: 'u0', email: 'admin@example.com', isAdmin, forcePasswordChange: false },
  })
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/campaigns/new']}>
      <Routes>
        <Route path="/campaigns/new" element={<CreateCampaignPage />} />
        <Route path="/" element={<div>Campaign list</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('CreateCampaignPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setAdmin(true)
    mockUseCreateCampaign.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useCreateCampaign>)
    mockUseUserSearch.mockReturnValue({
      data: [{ id: 'gm1', email: 'gm@example.com' }],
    } as unknown as ReturnType<typeof useUserSearch>)
  })

  it('redirects non-admins to the campaign list', () => {
    setAdmin(false)
    renderPage()
    expect(screen.getByText('Campaign list')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /new campaign/i })).not.toBeInTheDocument()
  })

  it('lets the admin pick a GM from the search results', async () => {
    renderPage()
    await userEvent.type(screen.getByLabelText(/game master/i), 'gm')
    await userEvent.click(screen.getByRole('button', { name: 'gm@example.com' }))
    expect(screen.getByText('gm@example.com')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /change/i })).toBeInTheDocument()
  })

  it('shows an empty state when a search returns no users', async () => {
    mockUseUserSearch.mockReturnValue({
      data: [],
      isFetching: false,
    } as unknown as ReturnType<typeof useUserSearch>)

    renderPage()
    await userEvent.type(screen.getByLabelText(/game master/i), 'zzz')

    await waitFor(() => expect(screen.getByText(/no users found/i)).toBeInTheDocument())
  })

  it('creates the campaign and navigates to its home on success', async () => {
    const mutate = vi.fn((_vars: unknown, opts?: { onSuccess?: (c: { id: string }) => void }) => {
      opts?.onSuccess?.({ id: 'new-campaign' })
    })
    mockUseCreateCampaign.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<
      typeof useCreateCampaign
    >)

    renderPage()
    await userEvent.type(screen.getByLabelText(/campaign name/i), 'Curse of Strahd')
    await userEvent.type(screen.getByLabelText(/game master/i), 'gm')
    await userEvent.click(screen.getByRole('button', { name: 'gm@example.com' }))
    await userEvent.click(screen.getByRole('button', { name: /create campaign/i }))

    await waitFor(() =>
      expect(mutate).toHaveBeenCalledWith(
        { name: 'Curse of Strahd', gmUserId: 'gm1' },
        expect.anything()
      )
    )
    expect(mockNavigate).toHaveBeenCalledWith('/campaigns/new-campaign')
  })

  it('maps a 400 field error to the corresponding form field', async () => {
    const mutate = vi.fn((_vars: unknown, opts?: { onError?: (err: unknown) => void }) => {
      opts?.onError?.(
        new ApiClientError('Validation failed', 400, [
          { code: 'INVALID', message: 'Name already taken', field: 'name' },
        ])
      )
    })
    mockUseCreateCampaign.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<
      typeof useCreateCampaign
    >)

    renderPage()
    await userEvent.type(screen.getByLabelText(/campaign name/i), 'Curse of Strahd')
    await userEvent.type(screen.getByLabelText(/game master/i), 'gm')
    await userEvent.click(screen.getByRole('button', { name: 'gm@example.com' }))
    await userEvent.click(screen.getByRole('button', { name: /create campaign/i }))

    await waitFor(() => expect(screen.getByText('Name already taken')).toBeInTheDocument())
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('disables the submit button with a spinner while pending', () => {
    mockUseCreateCampaign.mockReturnValue({
      mutate: vi.fn(),
      isPending: true,
    } as unknown as ReturnType<typeof useCreateCampaign>)

    renderPage()
    const button = screen.getByRole('button', { name: /create campaign/i })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-disabled', 'true')
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPage()
    expect(await axe(container)).toHaveNoViolations()
  })
})
