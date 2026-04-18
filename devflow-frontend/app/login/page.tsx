'use client';

import { useAuth } from '@/hooks/use-auth';
import { LoginForm } from '@/components/auth/login-form';

export default function LoginPage() {
  const { login } = useAuth();
  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <LoginForm onLogin={login} />
    </div>
  );
}
