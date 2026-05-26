import { apiClient } from './apiClient';
import type { TokenResponse, UserInfo } from './types';

export const authApi = {
  async login(username: string, password: string): Promise<TokenResponse> {
    const { data } = await apiClient.post<TokenResponse>('/auth/login', { username, password });
    return data;
  },
  async logout(): Promise<void> {
    await apiClient.post('/auth/logout');
  },
  async me(): Promise<UserInfo> {
    const { data } = await apiClient.get<UserInfo>('/auth/me');
    return data;
  },
  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await apiClient.post('/auth/change-password', { currentPassword, newPassword });
  }
};
