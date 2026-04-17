---
name: react-hook-form
description: >
  Use this skill whenever you are building or modifying a form in `apps/web`. Triggers include:
  "form", "React Hook Form", "shadcn Form", "FormField", "form validation", "form errors",
  "useForm", "session submission form", "login form", "annotation input", "password change form",
  "field error", "API validation error", "setError", or any task that requires a user-input form
  with submission, validation, or error display. This skill covers React Hook Form v7 + shadcn/ui
  Form primitives, client-side validation, API error mapping, and loading state management.
---

# Frontend — Forms with React Hook Form + shadcn/ui

All forms in Blue Steel use React Hook Form v7 with shadcn/ui's `Form`, `FormField`, `FormItem`,
`FormLabel`, `FormControl`, and `FormMessage` primitives. This stack composes directly — do not
reach for a different form approach (D-067).

## Core Pattern

```tsx
import { useForm } from 'react-hook-form';
import {
  Form, FormControl, FormField, FormItem, FormLabel, FormMessage,
} from '../../components/ui/form';
import { Input } from '../../components/ui/input';
import { Button } from '../../components/ui/button';

interface FormValues {
  summaryText: string;
}

export function SessionSubmissionForm({ onSubmit }: { onSubmit: (text: string) => void }) {
  const form = useForm<FormValues>({
    defaultValues: { summaryText: '' },
  });

  function handleSubmit(values: FormValues) {
    onSubmit(values.summaryText);
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(handleSubmit)}>
        <FormField
          control={form.control}
          name="summaryText"
          rules={{
            required: 'Session summary is required',
            minLength: { value: 50, message: 'Summary must be at least 50 characters' },
          }}
          render={({ field }) => (
            <FormItem>
              <FormLabel>Session Summary</FormLabel>
              <FormControl>
                <Textarea {...field} placeholder="Describe what happened this session..." rows={8} />
              </FormControl>
              <FormMessage />  {/* Renders validation error for this field */}
            </FormItem>
          )}
        />
        <Button type="submit" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting ? 'Submitting…' : 'Submit Session'}
        </Button>
      </form>
    </Form>
  );
}
```

---

## Mapping API Validation Errors to Form Fields

The backend returns `400` responses with field-level error codes (ERR-01). Map them to form
fields using `form.setError()`:

```typescript
// In the mutation's onError handler
const { mutate } = useMutation({
  mutationFn: submitSession,
  onError: (err) => {
    if (err instanceof ApiError && err.status === 400) {
      // Map each error to its form field
      for (const e of err.errors) {
        if (e.field) {
          form.setError(e.field as keyof FormValues, {
            type: 'server',
            message: e.message,
          });
        } else {
          // Non-field error — set on a root-level error key
          form.setError('root', { type: 'server', message: e.message });
        }
      }
    }
  },
});
```

To display root-level (non-field) server errors:
```tsx
{form.formState.errors.root && (
  <p role="alert" className="text-destructive text-sm">
    {form.formState.errors.root.message}
  </p>
)}
```

---

## Special Case: `SUMMARY_TOO_LARGE`

When the API returns `400` with `code: 'SUMMARY_TOO_LARGE'`, the response includes a `max_tokens`
field. Surface this as a specific user message:

```typescript
onError: (err) => {
  if (err instanceof ApiError && err.status === 400) {
    const summaryTooLarge = err.errors.find(e => e.code === 'SUMMARY_TOO_LARGE');
    if (summaryTooLarge) {
      form.setError('summaryText', {
        type: 'server',
        message: `Summary is too long (limit: ${err.maxTokens} tokens). Try splitting it into multiple sessions.`,
      });
      return;
    }
    // ... handle other errors
  }
}
```

---

## Forms in This Project

| Form | Location | Submit endpoint |
|---|---|---|
| Session submission | `features/input/SessionIngestionPage.tsx` | `POST /campaigns/{id}/sessions` |
| Login | `features/auth/LoginPage.tsx` | `POST /auth/login` |
| Password change | `features/auth/ChangePasswordPage.tsx` | `PATCH /users/me/password` |
| Annotation input | `components/domain/AnnotationThread.tsx` | `POST /campaigns/{id}/annotations` |

---

## Loading States

Always disable the submit button while a mutation is in-flight:

```tsx
const { mutate, isPending } = useMutation({ mutationFn: ... });

<Button type="submit" disabled={isPending || !form.formState.isValid}>
  {isPending ? 'Saving…' : 'Save'}
</Button>
```

After successful submission, reset the form to clear values:
```typescript
onSuccess: () => {
  form.reset();
  // invalidate relevant TanStack Query cache keys
}
```

---

## Controlled vs Uncontrolled

React Hook Form is uncontrolled by default for performance. shadcn/ui's `FormControl` wraps the
`field` props — spread them directly:

```tsx
// ✅ Correct — spread field props onto the input
<FormControl>
  <Input {...field} />
</FormControl>

// ❌ Wrong — managing controlled state manually defeats the purpose
<FormControl>
  <Input
    value={watchedValue}
    onChange={(e) => setValue('field', e.target.value)}
  />
</FormControl>
```

For `Checkbox`, `Select`, and other non-native inputs, use `field.onChange` and `field.value`:
```tsx
<FormControl>
  <Checkbox
    checked={field.value}
    onCheckedChange={field.onChange}
  />
</FormControl>
```

---

## Accessibility Requirements

- Every form input must have a `<FormLabel>` — never use placeholder as the label.
- `<FormMessage>` auto-associates with the field via `id` attrs — keep the shadcn structure intact.
- Submit buttons must have `aria-disabled={isPending}` in addition to the HTML `disabled` prop.
- Write an axe-core assertion on every form component (frontend-testing skill).

---

## Common Mistakes to Avoid

- **Not spreading `form` into `<Form {...form}>`.** Without this, `FormField` cannot access the
  form context and will throw a React context error.

- **Forgetting `form.handleSubmit()` wrapper.** Direct `onSubmit` on `<form>` bypasses React Hook
  Form's validation and `isSubmitting` state management.

- **Resetting the form in `onSuccess` before navigating away.** If the form is unmounted
  immediately after success (e.g., redirect), calling `form.reset()` will error. Only call
  `reset()` if the component stays mounted after success.

- **Showing API errors as `role="alert"` on field-level `<FormMessage>`.** `<FormMessage>` renders
  inline — use `role="alert"` only on non-field, page-level errors.

- **Using `watch()` extensively for display-only derived values.** `watch()` triggers re-renders
  on every keystroke for the watched fields. Prefer `getValues()` in event handlers when the
  value is only needed on submit.

## References

- `apps/web/CLAUDE.md` §4 (workflow: adding a new form)
- `DECISIONS.md` D-067 (React Hook Form is the mandated form library)
- `frontend-api-resource` skill (mutation hooks, API error types)
- `frontend-testing` skill (axe-core on form components)
