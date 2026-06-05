import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { Button } from '@/components/ui/button'

/** Disabled "Propose a change" affordance — cosmetic stub in v1 (D-012). */
export function ProposeChangeButton() {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          {/* span captures pointer events so the tooltip fires over the disabled button */}
          <span>
            <Button type="button" variant="outline" disabled aria-disabled="true">
              Propose a change
            </Button>
          </span>
        </TooltipTrigger>
        <TooltipContent>Proposal system coming in a future update</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
