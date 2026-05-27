import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Loader2 } from 'lucide-react'
import { useLogin } from '@/api/auth'
import { ApiClientError } from '@/api/client'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Button } from '@/components/ui/button'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'

const schema = z.object({
  email: z.string().email('Please enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
})

type FormValues = z.infer<typeof schema>

export function LoginPage() {
  const navigate = useNavigate()
  const { mutate: login, isPending } = useLogin()
  const [banner, setBanner] = useState<string | null>(null)

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '' },
  })

  function onSubmit(values: FormValues) {
    setBanner(null)
    login(values, {
      onSuccess(data) {
        if (data.forcePasswordChange) {
          navigate('/change-password', { replace: true })
        } else {
          navigate('/', { replace: true })
        }
      },
      onError(err) {
        if (err instanceof ApiClientError) {
          let hasFieldError = false
          for (const e of err.errors) {
            if (e.field) {
              form.setError(e.field as keyof FormValues, { message: e.message })
              hasFieldError = true
            }
          }
          if (!hasFieldError) {
            setBanner(err.errors[0]?.message ?? 'Invalid email or password.')
          }
        } else {
          setBanner('An unexpected error occurred. Please try again.')
        }
      },
    })
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-8">
      <div className="w-full max-w-sm">
        <div className="rounded-2xl bg-white p-8 shadow-sm">
          <h1 className="mb-6 text-2xl font-semibold text-slate-900">Sign in</h1>
          {banner && (
            <div className="mb-4">
              <InlineBanner variant="error" message={banner} onDismiss={() => setBanner(null)} />
            </div>
          )}
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4" noValidate>
              <FormField
                control={form.control}
                name="email"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Email</FormLabel>
                    <FormControl>
                      <Input
                        type="email"
                        placeholder="you@example.com"
                        autoComplete="email"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="password"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Password</FormLabel>
                    <FormControl>
                      <Input
                        type="password"
                        placeholder="••••••••"
                        autoComplete="current-password"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <Button type="submit" className="w-full" disabled={isPending} aria-disabled={isPending}>
                {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
                Sign in
              </Button>
            </form>
          </Form>
        </div>
      </div>
    </div>
  )
}
