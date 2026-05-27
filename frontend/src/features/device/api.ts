import { apiClient } from '../../lib/apiClient';
import type {
  Credential,
  CredentialRequest,
  Device,
  DevicePage,
  DeviceRequest,
  DeviceStatus,
  IngestionSource,
  IngestionSourceRequest,
  IngestionSourceWithKey
} from './types';

export const deviceApi = {
  // Devices
  searchDevices: async (params: {
    q?: string;
    status?: DeviceStatus;
    page?: number;
    size?: number;
    sort?: string;
    direction?: 'asc' | 'desc';
  }): Promise<DevicePage> => (await apiClient.get<DevicePage>('/devices', { params })).data,

  getDevice: async (id: string): Promise<Device> =>
    (await apiClient.get<Device>(`/devices/${id}`)).data,
  createDevice: async (body: DeviceRequest): Promise<Device> =>
    (await apiClient.post<Device>('/devices', body)).data,
  updateDevice: async (id: string, body: DeviceRequest): Promise<Device> =>
    (await apiClient.put<Device>(`/devices/${id}`, body)).data,
  deleteDevice: async (id: string): Promise<void> => {
    await apiClient.delete(`/devices/${id}`);
  },

  // Ingestion sources
  listSources: async (): Promise<IngestionSource[]> =>
    (await apiClient.get<IngestionSource[]>('/ingestion-sources')).data,
  createSource: async (body: IngestionSourceRequest): Promise<IngestionSourceWithKey> =>
    (await apiClient.post<IngestionSourceWithKey>('/ingestion-sources', body)).data,
  updateSource: async (id: string, body: IngestionSourceRequest): Promise<IngestionSource> =>
    (await apiClient.put<IngestionSource>(`/ingestion-sources/${id}`, body)).data,
  rotateSourceKey: async (id: string): Promise<IngestionSourceWithKey> =>
    (await apiClient.post<IngestionSourceWithKey>(`/ingestion-sources/${id}/rotate-key`)).data,
  deleteSource: async (id: string): Promise<void> => {
    await apiClient.delete(`/ingestion-sources/${id}`);
  },

  // Credentials
  listCredentials: async (employeeId: string): Promise<Credential[]> =>
    (await apiClient.get<Credential[]>(`/employees/${employeeId}/credentials`)).data,
  createCredential: async (employeeId: string, body: CredentialRequest): Promise<Credential> =>
    (await apiClient.post<Credential>(`/employees/${employeeId}/credentials`, body)).data,
  updateCredential: async (
    employeeId: string,
    credentialId: string,
    body: CredentialRequest
  ): Promise<Credential> =>
    (await apiClient.put<Credential>(`/employees/${employeeId}/credentials/${credentialId}`, body))
      .data,
  revokeCredential: async (employeeId: string, credentialId: string): Promise<Credential> =>
    (
      await apiClient.post<Credential>(
        `/employees/${employeeId}/credentials/${credentialId}/revoke`
      )
    ).data,
  deleteCredential: async (employeeId: string, credentialId: string): Promise<void> => {
    await apiClient.delete(`/employees/${employeeId}/credentials/${credentialId}`);
  }
};
