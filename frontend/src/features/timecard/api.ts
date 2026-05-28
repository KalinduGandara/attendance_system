import { apiClient } from '../../lib/apiClient';
import type {
  IngestionResponse,
  PunchBatchRequest,
  PunchEvent,
  PunchEventStatus,
  RecomputeRequest,
  RecomputeResponse,
  TimeCard,
  TimeCardDetail,
  TimeCardEditRequest
} from './types';

export const timecardApi = {
  listTimeCards: async (params?: {
    employeeId?: string;
    status?: string;
    from?: string;
    to?: string;
  }): Promise<TimeCard[]> =>
    (await apiClient.get<TimeCard[]>('/timecards', { params })).data,

  getTimeCard: async (id: string): Promise<TimeCardDetail> =>
    (await apiClient.get<TimeCardDetail>(`/timecards/${id}`)).data,

  editTimeCard: async (id: string, body: TimeCardEditRequest): Promise<TimeCardDetail> =>
    (await apiClient.post<TimeCardDetail>(`/timecards/${id}/edits`, body)).data,

  assignPunch: async (punchId: string, employeeId: string): Promise<PunchEvent> =>
    (await apiClient.post<PunchEvent>(`/punch-events/${punchId}/assign`, { employeeId })).data,

  listPunches: async (params?: {
    employeeId?: string;
    status?: PunchEventStatus;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  }): Promise<PunchEvent[]> =>
    (await apiClient.get<PunchEvent[]>('/punch-events', { params })).data,

  ingest: async (body: PunchBatchRequest, idempotencyKey?: string): Promise<IngestionResponse> => {
    const headers: Record<string, string> = {};
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    return (await apiClient.post<IngestionResponse>('/ingestion/punches', body, { headers })).data;
  },

  recompute: async (body: RecomputeRequest): Promise<RecomputeResponse> =>
    (await apiClient.post<RecomputeResponse>('/admin/recompute', body)).data
};
