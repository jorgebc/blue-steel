---
name: ux-skeleton-crafting
description: >
  Use this skill whenever a component fetches data and needs a loading state. Triggers: "loading
  state", "skeleton", "placeholder", "data fetching UI", "TanStack Query isLoading", "suspense
  fallback". Spinners are forbidden in primary content areas (D-086). Skeletons are the mandatory
  replacement. Dimensions must derive from the TypeScript DTO in src/types/ to guarantee
  zero layout shift.
---

# UX Skill — Skeleton Crafting

## Technical Context

**No external library.** Use Tailwind's `animate-pulse` with `bg-slate-200` blocks.

**Derive from DTOs:** Open `src/types/<resource>.ts` before writing a skeleton. Each DTO field maps to a skeleton block. The skeleton must structurally mirror the real component.

**Usage pattern:** Export a `<ComponentSkeleton />` alongside every data-fetching component:
```
src/features/exploration/entities/
  EntityCard.tsx          ← real component
  EntityCardSkeleton.tsx  ← skeleton (same outer dimensions)
```

**UX_CONSTITUTION.md §5** is the authority. This skill implements it.

---

## Golden Rules

**Dimension map (derive from field types in the DTO):**

| DTO field | Skeleton block |
|---|---|
| `string` (short: name, title) | `h-4 w-3/4 rounded bg-slate-200 animate-pulse` |
| `string` (long: description, summary) | Two lines: `h-4 w-full` + `h-4 w-2/3` |
| `UUID` / `id` | Not rendered — skip |
| `Date` / timestamp | `h-3 w-1/3 rounded bg-slate-200 animate-pulse` |
| avatar / image | `h-10 w-10 rounded-full bg-slate-200 animate-pulse` |
| badge / chip | `h-5 w-16 rounded-full bg-slate-200 animate-pulse` |
| button | `h-9 w-24 rounded-lg bg-slate-200 animate-pulse` |

**Do:**
- Match the skeleton's outer container height to the real component's final height within ±2px
- Use the same padding, gap, and border-radius as the real card (`rounded-2xl`, `p-4`, etc.)
- Group skeleton lines with `space-y-2` to mirror real typography spacing
- Render a list of 3–5 skeletons for list views (match expected page size)
- Pass a `count` prop when the skeleton is for a list container

**Don't:**
- Never use `<Spinner />` or `<Loader2 className="animate-spin" />` in primary content areas
- Never use a fixed height that differs from the real rendered component
- Never skip the `rounded-2xl` on card skeletons — border-radius must match the real card
- Never use `opacity-50` on a real component as a loading state — render the skeleton instead
- Spinners are only permitted **inside buttons** (submit loading state), nowhere else

---

## Reference Snippet

```tsx
// Example DTO (src/types/actors.ts):
// interface Actor { id: string; name: string; description: string; role: string; createdAt: string; }

// src/features/exploration/entities/EntityCardSkeleton.tsx
export function EntityCardSkeleton() {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-start gap-3">
        {/* Avatar placeholder */}
        <div className="h-10 w-10 rounded-full bg-slate-200 animate-pulse shrink-0" />
        <div className="flex-1 space-y-2">
          {/* name */}
          <div className="h-4 w-3/4 rounded bg-slate-200 animate-pulse" />
          {/* role badge */}
          <div className="h-5 w-16 rounded-full bg-slate-200 animate-pulse" />
        </div>
      </div>
      {/* description — two lines */}
      <div className="mt-4 space-y-2">
        <div className="h-4 w-full rounded bg-slate-200 animate-pulse" />
        <div className="h-4 w-2/3 rounded bg-slate-200 animate-pulse" />
      </div>
    </div>
  );
}

// List usage — render 4 skeletons while data loads:
function EntityList() {
  const { data, isLoading } = useActors(campaignId, page);

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 4 }).map((_, i) => <EntityCardSkeleton key={i} />)}
      </div>
    );
  }
  return <div className="space-y-3">{data.map(a => <EntityCard key={a.id} actor={a} />)}</div>;
}
```
