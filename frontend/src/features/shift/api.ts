import { apiClient } from '../../lib/apiClient';
import type { Shift, ShiftRequest, ShiftType } from './types';

export const shiftApi = {
  list: async (params?: { q?: string; type?: ShiftType; active?: boolean }): Promise<Shift[]> =>
    (await apiClient.get<Shift[]>('/shifts', { params })).data,

  get: async (id: string): Promise<Shift> => (await apiClient.get<Shift>(`/shifts/${id}`)).data,

  create: async (body: ShiftRequest): Promise<Shift> =>
    (await apiClient.post<Shift>('/shifts', body)).data,

  update: async (id: string, body: ShiftRequest): Promise<Shift> =>
    (await apiClient.put<Shift>(`/shifts/${id}`, body)).data,

  remove: async (id: string): Promise<void> => {
    await apiClient.delete(`/shifts/${id}`);
  }
};
