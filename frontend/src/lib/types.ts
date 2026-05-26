export interface UserInfo {
  id: string;
  username: string;
  displayName: string | null;
  roles: string[];
  permissions: string[];
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserInfo;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  requestId?: string;
  errors?: Array<{ field: string; code: string; message: string }>;
}
