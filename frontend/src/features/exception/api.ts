import { apiClient } from '../../lib/apiClient';
import type { ExceptionEvent, ExceptionStatus, ResolutionRequest } from './types';

export const exceptionApi = {
  list: async (params: {
    employeeId?: string;
    status?: ExceptionStatus;
    from?: string;
    to?: string;
  }): Promise<ExceptionEvent[]> =>
    (await apiClient.get<ExceptionEvent[]>('/exceptions', { params })).data,

  resolve: async (id: string, body: ResolutionRequest): Promise<ExceptionEvent> =>
    (await apiClient.patch<ExceptionEvent>(`/exceptions/${id}/resolve`, body)).data
};
