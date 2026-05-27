import type { PageResponse } from '../organization/types';

export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'LOCKED';

export interface RoleRef {
  id: string;
  name: string;
}

export interface UserSummary {
  id: string;
  username: string;
  email: string;
  displayName: string | null;
  status: UserStatus;
  roles: RoleRef[];
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreateUserRequest {
  username: string;
  email: string;
  displayName: string | null;
  password: string;
  status: UserStatus;
  roleIds: string[];
}

export interface UpdateUserRequest {
  username: string;
  email: string;
  displayName: string | null;
  status: UserStatus;
  roleIds: string[];
}

export interface RoleResponse {
  id: string;
  name: string;
  description: string | null;
  system: boolean;
  permissions: string[];
}

export interface PermissionResponse {
  id: string;
  code: string;
  description: string | null;
}

export type { PageResponse };
