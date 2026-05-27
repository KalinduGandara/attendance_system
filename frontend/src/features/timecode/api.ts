import { apiClient } from '../../lib/apiClient';
import type { TimeCode, TimeCodeCategory, TimeCodeRequest } from './types';

export const timeCodeApi = {
  list: async (params?: {
    category?: TimeCodeCategory;
    activeOnly?: boolean;
  }): Promise<TimeCode[]> =>
    (await apiClient.get<TimeCode[]>('/time-codes', { params })).data,

  get: async (id: string): Promise<TimeCode> =>
    (await apiClient.get<TimeCode>(`/time-codes/${id}`)).data,

  create: async (body: TimeCodeRequest): Promise<TimeCode> =>
    (await apiClient.post<TimeCode>('/time-codes', body)).data,

  update: async (id: string, body: TimeCodeRequest): Promise<TimeCode> =>
    (await apiClient.put<TimeCode>(`/time-codes/${id}`, body)).data,

  remove: async (id: string): Promise<void> => {
    await apiClient.delete(`/time-codes/${id}`);
  }
};
