import { apiClient } from '../../lib/apiClient';
import type {
  Assignment,
  AssignmentRequest,
  AssignmentTargetType,
  ResolvedScheduleDay,
  Template,
  TemplateRequest,
  TemporarySchedule,
  TemporaryScheduleRequest
} from './types';

export const scheduleApi = {
  // Templates
  listTemplates: async (q?: string): Promise<Template[]> =>
    (await apiClient.get<Template[]>('/schedule-templates', { params: { q } })).data,
  getTemplate: async (id: string): Promise<Template> =>
    (await apiClient.get<Template>(`/schedule-templates/${id}`)).data,
  createTemplate: async (body: TemplateRequest): Promise<Template> =>
    (await apiClient.post<Template>('/schedule-templates', body)).data,
  updateTemplate: async (id: string, body: TemplateRequest): Promise<Template> =>
    (await apiClient.put<Template>(`/schedule-templates/${id}`, body)).data,
  deleteTemplate: async (id: string): Promise<void> => {
    await apiClient.delete(`/schedule-templates/${id}`);
  },

  // Assignments
  listAssignments: async (params?: {
    targetType?: AssignmentTargetType;
    targetId?: string;
    templateId?: string;
  }): Promise<Assignment[]> =>
    (await apiClient.get<Assignment[]>('/schedule-assignments', { params })).data,
  getAssignment: async (id: string): Promise<Assignment> =>
    (await apiClient.get<Assignment>(`/schedule-assignments/${id}`)).data,
  createAssignment: async (body: AssignmentRequest): Promise<Assignment> =>
    (await apiClient.post<Assignment>('/schedule-assignments', body)).data,
  updateAssignment: async (id: string, body: AssignmentRequest): Promise<Assignment> =>
    (await apiClient.put<Assignment>(`/schedule-assignments/${id}`, body)).data,
  deleteAssignment: async (id: string): Promise<void> => {
    await apiClient.delete(`/schedule-assignments/${id}`);
  },

  // Temporary schedules
  listTemporary: async (params?: {
    employeeId?: string;
    from?: string;
    to?: string;
  }): Promise<TemporarySchedule[]> =>
    (await apiClient.get<TemporarySchedule[]>('/temporary-schedules', { params })).data,
  getTemporary: async (id: string): Promise<TemporarySchedule> =>
    (await apiClient.get<TemporarySchedule>(`/temporary-schedules/${id}`)).data,
  createTemporary: async (body: TemporaryScheduleRequest): Promise<TemporarySchedule> =>
    (await apiClient.post<TemporarySchedule>('/temporary-schedules', body)).data,
  updateTemporary: async (id: string, body: TemporaryScheduleRequest): Promise<TemporarySchedule> =>
    (await apiClient.put<TemporarySchedule>(`/temporary-schedules/${id}`, body)).data,
  deleteTemporary: async (id: string): Promise<void> => {
    await apiClient.delete(`/temporary-schedules/${id}`);
  },

  // Resolution
  resolveRange: async (params: {
    employeeId: string;
    from: string;
    to: string;
  }): Promise<ResolvedScheduleDay[]> =>
    (await apiClient.get<ResolvedScheduleDay[]>('/schedule-resolution', { params })).data
};
