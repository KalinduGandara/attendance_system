import { apiClient } from '../../lib/apiClient';
import type {
  BalanceAdjustment,
  LeaveBalance,
  LeaveDecision,
  LeaveRequest,
  LeaveRequestPayload,
  LeaveRequestStatus,
  LeaveType,
  LeaveTypeRequest
} from './types';

export const leaveApi = {
  listTypes: async (): Promise<LeaveType[]> =>
    (await apiClient.get<LeaveType[]>('/leave-types')).data,
  createType: async (body: LeaveTypeRequest): Promise<LeaveType> =>
    (await apiClient.post<LeaveType>('/leave-types', body)).data,
  updateType: async (id: string, body: LeaveTypeRequest): Promise<LeaveType> =>
    (await apiClient.put<LeaveType>(`/leave-types/${id}`, body)).data,
  deleteType: async (id: string): Promise<void> => {
    await apiClient.delete(`/leave-types/${id}`);
  },

  listBalances: async (employeeId: string, year?: number): Promise<LeaveBalance[]> =>
    (await apiClient.get<LeaveBalance[]>(`/employees/${employeeId}/leave-balances`, {
      params: { year }
    })).data,
  adjustBalance: async (employeeId: string, body: BalanceAdjustment): Promise<LeaveBalance> =>
    (await apiClient.post<LeaveBalance>(`/employees/${employeeId}/leave-balances/adjust`, body))
      .data,

  listRequests: async (params: {
    employeeId?: string;
    status?: LeaveRequestStatus;
    from?: string;
    to?: string;
  }): Promise<LeaveRequest[]> =>
    (await apiClient.get<LeaveRequest[]>('/leave-requests', { params })).data,
  createRequest: async (body: LeaveRequestPayload): Promise<LeaveRequest> =>
    (await apiClient.post<LeaveRequest>('/leave-requests', body)).data,
  approveRequest: async (id: string, decision?: LeaveDecision): Promise<LeaveRequest> =>
    (await apiClient.post<LeaveRequest>(`/leave-requests/${id}/approve`, decision ?? {})).data,
  rejectRequest: async (id: string, decision?: LeaveDecision): Promise<LeaveRequest> =>
    (await apiClient.post<LeaveRequest>(`/leave-requests/${id}/reject`, decision ?? {})).data,
  cancelRequest: async (id: string): Promise<LeaveRequest> =>
    (await apiClient.post<LeaveRequest>(`/leave-requests/${id}/cancel`)).data
};
