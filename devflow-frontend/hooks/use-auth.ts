'use client';

import { useEffect, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useRouter, usePathname } from 'next/navigation';
import { authApi } from '@/lib/api';
import { useAppStore } from '@/store/app-store';

const PUBLIC_PATHS = ['/login'];

export function useAuth() {
  const router = useRouter();
  const pathname = usePathname();
  const { user, token, setUser, setToken } = useAppStore();

  const { data, isLoading, error } = useQuery({
    queryKey: ['me'],
    queryFn: authApi.me,
    enabled: !!token && !user,
    retry: false,
  });

  useEffect(() => {
    if (data) setUser(data);
  }, [data, setUser]);

  useEffect(() => {
    if (error) {
      setToken(null);
      setUser(null);
    }
  }, [error, setToken, setUser]);

  useEffect(() => {
    if (!isLoading && !token && !PUBLIC_PATHS.includes(pathname)) {
      router.push('/login');
    }
  }, [isLoading, token, pathname, router]);

  const login = useCallback(async (email: string, code: string) => {
    const result = await authApi.verifyCode(email, code);
    setToken(result.token);
    setUser(result.user);
    router.push('/');
  }, [router, setToken, setUser]);

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
    router.push('/login');
  }, [router, setToken, setUser]);

  return { user, isLoading, login, logout };
}
