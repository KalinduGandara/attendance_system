import { create } from 'zustand';
import type { UserInfo } from './types';

interface AuthState {
  accessToken: string | null;
  user: UserInfo | null;
  bootstrapped: boolean;
  setSession: (accessToken: string, user: UserInfo) => void;
  setAccessToken: (token: string | null) => void;
  setUser: (user: UserInfo | null) => void;
  clear: () => void;
  markBootstrapped: () => void;
  hasPermission: (code: string) => boolean;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  user: null,
  bootstrapped: false,
  setSession: (accessToken, user) => set({ accessToken, user }),
  setAccessToken: (accessToken) => set({ accessToken }),
  setUser: (user) => set({ user }),
  clear: () => set({ accessToken: null, user: null }),
  markBootstrapped: () => set({ bootstrapped: true }),
  hasPermission: (code) => get().user?.permissions.includes(code) ?? false
}));
