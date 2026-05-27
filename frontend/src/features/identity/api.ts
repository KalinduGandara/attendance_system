import { apiClient } from '../../lib/apiClient';
import type {
  CreateUserRequest,
  PageResponse,
  PermissionResponse,
  RoleResponse,
  UpdateUserRequest,
  UserSummary
} from './types';

export const identityApi = {
  listUsers: async (params: { page?: number; size?: number; sort?: string }): Promise<PageResponse<UserSummary>> =>
    (await apiClient.get<PageResponse<UserSummary>>('/users', { params })).data,
  getUser: async (id: string): Promise<UserSummary> =>
    (await apiClient.get<UserSummary>(`/users/${id}`)).data,
  createUser: async (body: CreateUserRequest): Promise<UserSummary> =>
    (await apiClient.post<UserSummary>('/users', body)).data,
  updateUser: async (id: string, body: UpdateUserRequest): Promise<UserSummary> =>
    (await apiClient.put<UserSummary>(`/users/${id}`, body)).data,
  resetPassword: async (id: string, newPassword: string): Promise<void> => {
    await apiClient.post(`/users/${id}/reset-password`, { newPassword });
  },
  deleteUser: async (id: string): Promise<void> => {
    await apiClient.delete(`/users/${id}`);
  },
  listRoles: async (): Promise<RoleResponse[]> =>
    (await apiClient.get<RoleResponse[]>('/roles')).data,
  listPermissions: async (): Promise<PermissionResponse[]> =>
    (await apiClient.get<PermissionResponse[]>('/permissions')).data
};
