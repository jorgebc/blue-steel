import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import i18n from '@/i18n'
import { CampaignExportButton } from './CampaignExportButton'
import { useExportCampaign } from '@/api/campaigns'
import { ApiClientError } from '@/api/client'
import { downloadBlob } from '@/lib/downloadBlob'

vi.mock('@/api/campaigns', () => ({ useExportCampaign: vi.fn() }))
vi.mock('@/lib/downloadBlob', () => ({ downloadBlob: vi.fn() }))

const mockedUseExportCampaign = vi.mocked(useExportCampaign)

type MutateOptions = {
  onSuccess?: (payload: { blob: Blob; filename: string }) => void
  onError?: (error: unknown) => void
}

// Build a mock hook return whose mutate immediately invokes the success or error
// callback, mimicking a resolved/rejected mutation.
function mockHook(opts: {
  isPending?: boolean
  resolveWith?: { blob: Blob; filename: string }
  reject?: boolean
  rejectWith?: unknown
}) {
  const mutate = vi.fn((_id: string, options?: MutateOptions) => {
    if (opts.rejectWith !== undefined) options?.onError?.(opts.rejectWith)
    else if (opts.reject) options?.onError?.(new Error('failed'))
    else if (opts.resolveWith) options?.onSuccess?.(opts.resolveWith)
  })
  mockedUseExportCampaign.mockReturnValue({
    mutate,
    isPending: opts.isPending ?? false,
  } as unknown as ReturnType<typeof useExportCampaign>)
  return mutate
}

describe('CampaignExportButton', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    await i18n.changeLanguage('en')
  })

  it('downloads the blob and shows a success banner on a successful export', async () => {
    const payload = { blob: new Blob(['{}']), filename: 'curse-of-strahd-export.json' }
    mockHook({ resolveWith: payload })

    render(<CampaignExportButton campaignId="c1" />)
    await userEvent.click(screen.getByRole('button', { name: 'Export campaign' }))

    expect(downloadBlob).toHaveBeenCalledWith(payload.blob, payload.filename)
    expect(screen.getByText('Export downloaded.')).toBeInTheDocument()
  })

  it('shows an error banner when the export fails', async () => {
    mockHook({ reject: true })

    render(<CampaignExportButton campaignId="c1" />)
    await userEvent.click(screen.getByRole('button', { name: 'Export campaign' }))

    expect(downloadBlob).not.toHaveBeenCalled()
    expect(screen.getByText('Failed to export the campaign. Please try again.')).toBeInTheDocument()
  })

  it('shows a specific banner when the campaign exceeds the export size cap', async () => {
    mockHook({
      rejectWith: new ApiClientError('too large', 422, [
        { code: 'EXPORT_TOO_LARGE', message: 'too large', field: null },
      ]),
    })

    render(<CampaignExportButton campaignId="c1" />)
    await userEvent.click(screen.getByRole('button', { name: 'Export campaign' }))

    expect(downloadBlob).not.toHaveBeenCalled()
    expect(
      screen.getByText(
        'This campaign is too large to export. Ask an admin to raise the export limit.'
      )
    ).toBeInTheDocument()
  })

  it('disables the button and shows the pending label while exporting', () => {
    mockHook({ isPending: true })

    render(<CampaignExportButton campaignId="c1" />)

    const button = screen.getByRole('button', { name: 'Exporting…' })
    expect(button).toBeDisabled()
  })

  it('has no accessibility violations', async () => {
    mockHook({})
    const { container } = render(<CampaignExportButton campaignId="c1" />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
