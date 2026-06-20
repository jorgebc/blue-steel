import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { ACCENT_PALETTE } from '../accentPalette'

interface Props {
  value: string
  onChange: (hex: string) => void
}

/**
 * Swatch group for picking an avatar accent color from the bounded {@link ACCENT_PALETTE}. Built on
 * the radio-group primitive so it is keyboard- and screen-reader-operable; each swatch carries the
 * color's name as its accessible label.
 */
export function AccentColorPicker({ value, onChange }: Props) {
  return (
    <RadioGroup
      value={value}
      onValueChange={onChange}
      className="flex flex-wrap gap-3"
      aria-label="Avatar accent color"
    >
      {ACCENT_PALETTE.map((color) => (
        <RadioGroupItem
          key={color.hex}
          value={color.hex}
          aria-label={color.name}
          style={{ backgroundColor: color.hex }}
          className="size-8 rounded-full border-0 shadow-sm data-[state=checked]:ring-2 data-[state=checked]:ring-slate-900 data-[state=checked]:ring-offset-2"
        />
      ))}
    </RadioGroup>
  )
}
