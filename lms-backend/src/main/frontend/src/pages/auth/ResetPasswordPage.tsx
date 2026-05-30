import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  Eye,
  EyeOff,
  GraduationCap,
  Lock,
} from 'lucide-react';
import api from '@/lib/api';
import Button from '@/components/ui/Button';
import type { PasswordResetConfirmBody } from '@/types/api';

const schema = z
  .object({
    newPassword: z
      .string()
      .min(8, 'Password must be at least 8 characters')
      .max(128, 'Password must be 128 characters or fewer'),
    confirmPassword: z.string(),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Passwords don't match",
    path: ['confirmPassword'],
  });

type FormValues = z.infer<typeof schema>;

function getErrorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const response = (error as { response?: { status?: number } }).response;
    if (response?.status === 400) {
      return 'This reset link is invalid or has expired. Please request a new one.';
    }
  }
  return 'Something went wrong. Please try again.';
}

export default function ResetPasswordPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [showNew, setShowNew]         = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  const token = searchParams.get('token') ?? '';

  // Redirect to forgot-password if no token in URL
  useEffect(() => {
    if (!token) {
      navigate('/forgot-password', { replace: true });
    }
  }, [token, navigate]);

  const { register, handleSubmit, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  const mutation = useMutation({
    mutationFn: (data: PasswordResetConfirmBody) =>
      api.post('/auth/password-reset/confirm', data),
    onSuccess: () => {
      navigate('/login?reset=success', { replace: true });
    },
  });

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm animate-slide-up">
        {/* Logo */}
        <div className="mb-8 flex flex-col items-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary-600">
            <GraduationCap size={28} className="text-white" />
          </div>
        </div>

        <div className="mb-6">
          <h1 className="text-2xl font-bold text-gray-900">Set new password</h1>
          <p className="mt-1 text-sm text-gray-500">
            Choose a strong password — at least 8 characters.
          </p>
        </div>

        {/* Error */}
        {mutation.isError && (
          <div className="mb-5 flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 animate-slide-down">
            <AlertCircle size={18} className="mt-0.5 shrink-0 text-red-500" />
            <div className="text-sm text-red-700">
              <p>{getErrorMessage(mutation.error)}</p>
              <Link
                to="/forgot-password"
                className="mt-1 inline-block font-medium underline underline-offset-2 hover:text-red-800"
              >
                Request a new link
              </Link>
            </div>
          </div>
        )}

        {/* Success (before navigate fires) */}
        {mutation.isSuccess && (
          <div className="mb-5 flex items-start gap-3 rounded-lg border border-green-200 bg-green-50 px-4 py-3">
            <CheckCircle2 size={18} className="mt-0.5 shrink-0 text-green-600" />
            <p className="text-sm text-green-800">Password changed! Redirecting…</p>
          </div>
        )}

        <form
          onSubmit={handleSubmit(({ newPassword }) =>
            mutation.mutate({ token, newPassword }),
          )}
          className="space-y-4 rounded-xl border border-gray-200 bg-white p-6 shadow-sm"
          noValidate
        >
          {/* New password */}
          <div className="space-y-1">
            <label htmlFor="newPassword" className="block text-sm font-medium text-gray-700">
              New password
            </label>
            <div className="relative">
              <Lock
                size={16}
                className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"
              />
              <input
                id="newPassword"
                type={showNew ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="Min. 8 characters"
                className={[
                  'h-10 w-full rounded-lg border pl-9 pr-10 text-sm shadow-sm placeholder:text-gray-400',
                  'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-primary-500',
                  errors.newPassword
                    ? 'border-red-400 focus:ring-red-400 focus:border-red-400'
                    : 'border-gray-300',
                ].join(' ')}
                {...register('newPassword')}
              />
              <button
                type="button"
                onClick={() => setShowNew((v) => !v)}
                aria-label={showNew ? 'Hide password' : 'Show password'}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              >
                {showNew ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            {errors.newPassword && (
              <p className="text-xs text-red-600">{errors.newPassword.message}</p>
            )}
          </div>

          {/* Confirm password */}
          <div className="space-y-1">
            <label htmlFor="confirmPassword" className="block text-sm font-medium text-gray-700">
              Confirm new password
            </label>
            <div className="relative">
              <Lock
                size={16}
                className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"
              />
              <input
                id="confirmPassword"
                type={showConfirm ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="Repeat password"
                className={[
                  'h-10 w-full rounded-lg border pl-9 pr-10 text-sm shadow-sm placeholder:text-gray-400',
                  'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-primary-500',
                  errors.confirmPassword
                    ? 'border-red-400 focus:ring-red-400 focus:border-red-400'
                    : 'border-gray-300',
                ].join(' ')}
                {...register('confirmPassword')}
              />
              <button
                type="button"
                onClick={() => setShowConfirm((v) => !v)}
                aria-label={showConfirm ? 'Hide password' : 'Show password'}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
              >
                {showConfirm ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            {errors.confirmPassword && (
              <p className="text-xs text-red-600">{errors.confirmPassword.message}</p>
            )}
          </div>

          <Button
            type="submit"
            className="mt-2 w-full"
            loading={mutation.isPending}
            disabled={mutation.isSuccess}
          >
            Set new password
          </Button>
        </form>

        <div className="mt-4 text-center">
          <Link
            to="/login"
            className="inline-flex items-center gap-1.5 text-sm font-medium text-primary-600 hover:text-primary-700"
          >
            <ArrowLeft size={15} />
            Back to sign in
          </Link>
        </div>
      </div>
    </div>
  );
}
