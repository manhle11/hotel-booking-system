import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import { refreshAccessToken } from '../refresh-token';
import { API_CLIENT_EVENTS } from '../../../lib/axios';
import { authStorage, tokenStorage, type StoredAuthSession } from '../../../lib/storage';
import { AuthContext, type AuthContextValue } from './auth-context';

export function AuthProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient();
  const [session, setSession] = useState<StoredAuthSession | null>(() => authStorage.getSession());
  const [isInitializing, setIsInitializing] = useState(
    () => !session && authStorage.hasRefreshToken(),
  );
  const refreshStarted = useRef(false);

  const logout = useCallback(() => {
    // Clearing also cancels pending queries so an old response cannot restore cached data.
    client.clear();
    authStorage.clearSession();
    setSession(null);
  }, [client]);

  const updateUser = useCallback((user: StoredAuthSession['user']) => {
    if (!user) return;
    authStorage.updateUser(user);
    setSession((currentSession) => (currentSession ? { ...currentSession, user } : currentSession));
  }, []);

  useEffect(() => {
    window.addEventListener(API_CLIENT_EVENTS.authExpired, logout);
    return () => window.removeEventListener(API_CLIENT_EVENTS.authExpired, logout);
  }, [logout]);

  useEffect(() => {
    if (!isInitializing || refreshStarted.current) return;
    refreshStarted.current = true;
    const refreshToken = tokenStorage.getRefreshToken()!;
    void refreshAccessToken(refreshToken)
      .then((tokens) => {
        authStorage.updateTokens(tokens.accessToken, tokens.refreshToken, tokens.expiresIn);
        setSession(authStorage.getSession());
      })
      .catch(logout)
      .finally(() => setIsInitializing(false));
  }, [isInitializing, logout]);

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      isAuthenticated: session !== null,
      isInitializing,
      roles: session?.roles ?? [],
      startSession: (accessToken, refreshToken, roles, expiresInSeconds, remember, user) => {
        client.clear();
        authStorage.setSession(accessToken, roles, expiresInSeconds, remember, refreshToken, user);
        setSession(authStorage.getSession());
      },
      logout,
      updateUser,
    }),
    [client, isInitializing, logout, session, updateUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
