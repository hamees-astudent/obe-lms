import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { AlertCircle, CheckCircle2, Eye, EyeOff, GraduationCap, Lock, Mail } from 'lucide-react';
import { useState } from 'react';
import api from '@/lib/api';
import { useAuthStore } from '@/store/authStore';
import Button from '@/components/ui/Button';
import type { LoginResponse } from '@/types/api';

const schema = z.object({
  email:    z.string().email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});

type FormValues = z.infer<typeof schema>;

function getErrorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const response = (error as { response?: { status?: number } }).response;
    if (response?.status === 401 || response?.status === 403) {
      return 'Invalid email or password. Please try again.';
    }
    if (response?.status === 429) {
      return 'Too many failed attempts. Please wait a moment before trying again.';
    }
  }
  return 'Something went wrong. Please try again.';
}

export default function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const login = useAuthStore((s) => s.login);
  const [showPassword, setShowPassword] = useState(false);

  // Show success banner if redirected after password reset
  const resetSuccess = searchParams.get('reset') === 'success';

  const { register, handleSubmit, formState: { errors }, setFocus } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  useEffect(() => {
    setFocus('email');
  }, [setFocus]);

  const mutation = useMutation({
    mutationFn: (data: FormValues) =>
      api.post<LoginResponse>('/auth/login', data).then((r) => r.data),
    onSuccess: (response) => {
      login(response);
      navigate('/dashboard', { replace: true });
    },
  });

  return (
    <div className="flex min-h-screen">
      {/* Left panel — branding */}
      <div className="hidden lg:flex lg:w-1/2 flex-col items-center justify-center bg-primary-700 px-12 text-white animate-slide-in-left">
        <div className="max-w-sm text-center">
          <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-2xl bg-white/10 ring-1 ring-white/20">
            <GraduationCap size={36} className="text-white" />
          </div>
          <h2 className="text-3xl font-bold">Learning Management System</h2>
          <p className="mt-4 text-base leading-relaxed text-primary-200">
            A unified platform for students, teachers, and administrators to
            manage courses, attendance, assessments, and transcripts.
          </p>
        </div>
      </div>

      {/* Right panel — login form */}
      <div className="flex flex-1 flex-col items-center justify-center px-4 py-12 bg-gray-50 animate-slide-up">
        <div className="w-full max-w-sm">
          {/* Mobile logo */}
          <div className="mb-8 flex flex-col items-center lg:hidden">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary-600">
              <GraduationCap size={28} className="text-white" />
            </div>
            <p className="mt-2 text-sm font-medium text-gray-500">Learning Management System</p>
          </div>

          <div className="mb-6">
            <h1 className="text-2xl font-bold text-gray-900">Sign in</h1>
            <p className="mt-1 text-sm text-gray-500">Enter your credentials to continue</p>
          </div>

          {/* Password-reset success banner */}
          {resetSuccess && (
            <div className="mb-5 flex items-start gap-3 rounded-lg border border-green-200 bg-green-50 px-4 py-3 animate-slide-down">
              <CheckCircle2 size={18} className="mt-0.5 shrink-0 text-green-600" />
              <p className="text-sm text-green-800">
                Your password has been reset. Sign in with your new password.
              </p>
            </div>
          )}

          {/* Error banner */}
          {mutation.isError && (
            <div className="mb-5 flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 animate-slide-down">
              <AlertCircle size={18} className="mt-0.5 shrink-0 text-red-500" />
              <p className="text-sm text-red-700">{getErrorMessage(mutation.error)}</p>
            </div>
          )}

          <form
            onSubmit={handleSubmit((data) => mutation.mutate(data))}
            className="space-y-4 rounded-xl border border-gray-200 bg-white p-6 shadow-sm"
            noValidate
          >
            {/* Email */}
            <div className="space-y-1">
              <label htmlFor="email" className="block text-sm font-medium text-gray-700">
                Email address
              </label>
              <div className="relative">
                <Mail
                  size={16}
                  className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"
                />
                <input
                  id="email"
                  type="email"
                  autoComplete="email"
                  placeholder="you@example.com"
                  className={[
                    'h-10 w-full rounded-lg border pl-9 pr-3 text-sm shadow-sm placeholder:text-gray-400',
                    'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-primary-500',
                    errors.email
                      ? 'border-red-400 focus:ring-red-400 focus:border-red-400'
                      : 'border-gray-300',
                  ].join(' ')}
                  {...register('email')}
                />
              </div>
              {errors.email && (
                <p className="text-xs text-red-600">{errors.email.message}</p>
              )}
            </div>

            {/* Password */}
            <div className="space-y-1">
              <div className="flex items-center justify-between">
                <label htmlFor="password" className="block text-sm font-medium text-gray-700">
                  Password
                </label>
                <Link
                  to="/forgot-password"
                  tabIndex={-1}
                  className="text-xs font-medium text-primary-600 hover:text-primary-700 hover:underline"
                >
                  Forgot password?
                </Link>
              </div>
              <div className="relative">
                <Lock
                  size={16}
                  className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"
                />
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="••••••••"
                  className={[
                    'h-10 w-full rounded-lg border pl-9 pr-10 text-sm shadow-sm placeholder:text-gray-400',
                    'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-primary-500',
                    errors.password
                      ? 'border-red-400 focus:ring-red-400 focus:border-red-400'
                      : 'border-gray-300',
                  ].join(' ')}
                  {...register('password')}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {errors.password && (
                <p className="text-xs text-red-600">{errors.password.message}</p>
              )}
            </div>

            <Button
              type="submit"
              className="mt-2 w-full"
              loading={mutation.isPending}
            >
              Sign in
            </Button>
          </form>
        </div>
      </div>
    </div>
  );
}

