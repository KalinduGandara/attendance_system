import type { PageResponse } from '../organization/types';

export type DeviceType = 'SIMULATED' | 'REST_VIRTUAL' | 'EXTERNAL';
export type DeviceStatus = 'ACTIVE' | 'INACTIVE';

export interface Device {
  id: string;
  name: string;
  deviceType: DeviceType;
  location: string | null;
  status: DeviceStatus;
  capabilities: Record<string, unknown>;
  lastSeenAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface DeviceRequest {
  name: string;
  deviceType: DeviceType;
  location: string | null;
  status: DeviceStatus;
  capabilities: Record<string, unknown>;
}

export type IngestionSourceType = 'REST' | 'DEVICE_SDK' | 'EXTERNAL_DB' | 'CSV';

export interface IngestionSource {
  id: string;
  name: string;
  sourceType: IngestionSourceType;
  enabled: boolean;
  config: Record<string, unknown>;
  apiKeyConfigured: boolean;
  lastEventAt: string | null;
  eventsTotal: number;
  eventsRejected: number;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface IngestionSourceRequest {
  name: string;
  sourceType: IngestionSourceType;
  enabled: boolean;
  config: Record<string, unknown>;
}

export interface IngestionSourceWithKey {
  source: IngestionSource;
  apiKey: string;
}

export type CredentialType = 'RFID' | 'QR' | 'MOBILE' | 'FACE' | 'FINGER' | 'PIN';
export type CredentialStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED';

export interface Credential {
  id: string;
  employeeId: string;
  credentialType: CredentialType;
  valueMasked: string;
  validFrom: string;
  validTo: string | null;
  status: CredentialStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CredentialRequest {
  credentialType: CredentialType;
  value: string;
  validFrom: string;
  validTo: string | null;
  status: CredentialStatus;
}

export type DevicePage = PageResponse<Device>;
