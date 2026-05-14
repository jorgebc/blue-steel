---
name: ux-navigation-logic
description: >
  Use this skill whenever you are building or modifying the application sidebar, route-based
  active state, campaign switcher, or mode navigation. Triggers: "sidebar", "navigation",
  "active route", "mode switch", "campaign switcher", "nav item", "collapsed sidebar",
  "expanded sidebar". The sidebar is the primary navigation surface — all mode switching
  flows through it.
---

# UX Skill — Navigation Logic (Sidebar)

## Technical Context

**Files to create/use:**
- `src/components/domain/Sidebar.tsx` — sidebar component
- `src/store/uiStore.ts` — Zustand store for `sidebarExpanded` (persisted to `localStorage`)
- React Router `useLocation()` — derive active route for highlight logic

**Dependencies:** `lucide-react` for icons, `@xyflow/react` icons not used here.

**Sidebar dimensions:** `w-16` collapsed (64px) | `w-64` expanded (256px)

**UX_CONSTITUTION.md §6** is the authority. This skill implements it.

---

## Golden Rules

**Do:**
- Sidebar state in `uiStore.sidebarExpanded` — persisted to `localStorage` key `blue-steel-sidebar`
- Transition: `transition-all duration-200 ease-in-out` on the sidebar `<nav>` element
- Active route styles: `bg-blue-50 text-blue-600 border-r-2 border-blue-500 font-medium`
- Inactive route styles: `text-slate-600 hover:bg-slate-100 hover:text-slate-900`
- In collapsed state, render icon only — never truncate text (remove it entirely via conditional)
- Campaign switcher at top; Settings + User at bottom (flex column with `justify-between`)
- Role gating: hide Input Mode nav item (not just disable) when `currentUserRole === 'player'`
- Use `<NavLink>` from React Router for automatic active detection, or `useLocation().pathname`

**Don't:**
- Never derive sidebar state from local `useState` — only from `uiStore`
- Never read `localStorage` directly in components — let the Zustand store do it
- Never use `router.push()` to switch modes if already on the same campaign — use the nav link directly
- Never show the accent border (`border-r-2 border-blue-500`) on inactive or hover items
- Never collapse sub-items without also collapsing their parent section

---

## Reference Snippet

```tsx
// src/store/uiStore.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface UiStore {
  sidebarExpanded: boolean;
  toggleSidebar: () => void;
  setSidebarExpanded: (value: boolean) => void;
}

export const useUiStore = create<UiStore>()(
  persist(
    (set) => ({
      sidebarExpanded: true,
      toggleSidebar: () => set((s) => ({ sidebarExpanded: !s.sidebarExpanded })),
      setSidebarExpanded: (value) => set({ sidebarExpanded: value }),
    }),
    { name: 'blue-steel-sidebar' }
  )
);
```

```tsx
// src/components/domain/Sidebar.tsx
import { useLocation, Link } from 'react-router-dom';
import { Upload, Search, Compass, Settings, User, ChevronLeft } from 'lucide-react';
import { useUiStore } from '@/store/uiStore';
import { useCampaignStore } from '@/store/campaignStore';

const NAV_ITEMS = [
  { label: 'Input', icon: Upload, href: (id: string) => `/campaigns/${id}/input`, roles: ['gm', 'editor'] },
  { label: 'Query', icon: Search, href: (id: string) => `/campaigns/${id}/query`, roles: ['gm', 'editor', 'player'] },
  { label: 'Explore', icon: Compass, href: (id: string) => `/campaigns/${id}/exploration`, roles: ['gm', 'editor', 'player'] },
];

export function Sidebar() {
  const { pathname } = useLocation();
  const { sidebarExpanded, toggleSidebar } = useUiStore();
  const { currentCampaignId, currentUserRole } = useCampaignStore();

  const w = sidebarExpanded ? 'w-64' : 'w-16';

  return (
    <nav className={`${w} h-screen flex flex-col bg-white border-r border-slate-200 shadow-xl transition-all duration-200 ease-in-out`}>
      {/* Campaign switcher */}
      <div className="p-4 border-b border-slate-200">
        {sidebarExpanded ? <CampaignSwitcher /> : <CampaignIcon />}
      </div>

      {/* Mode nav */}
      <div className="flex-1 py-2">
        {NAV_ITEMS.filter(item => currentUserRole && item.roles.includes(currentUserRole)).map(({ label, icon: Icon, href }) => {
          const to = currentCampaignId ? href(currentCampaignId) : '#';
          const active = pathname.startsWith(to) && to !== '#';
          return (
            <Link
              key={label}
              to={to}
              className={[
                'flex items-center gap-3 px-4 py-3 text-sm transition-colors',
                active
                  ? 'bg-blue-50 text-blue-600 border-r-2 border-blue-500 font-medium'
                  : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
              ].join(' ')}
            >
              <Icon className="h-5 w-5 shrink-0" />
              {sidebarExpanded && <span>{label}</span>}
            </Link>
          );
        })}
      </div>

      {/* Bottom controls */}
      <div className="border-t border-slate-200 p-2">
        <button onClick={toggleSidebar} className="w-full flex items-center justify-center p-2 rounded-lg text-slate-500 hover:bg-slate-100">
          <ChevronLeft className={`h-4 w-4 transition-transform duration-200 ${sidebarExpanded ? '' : 'rotate-180'}`} />
        </button>
      </div>
    </nav>
  );
}
```
