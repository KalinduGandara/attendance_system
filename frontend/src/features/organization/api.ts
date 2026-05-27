import { apiClient } from '../../lib/apiClient';
import type {
  CustomFieldDefinition,
  CustomFieldDefinitionRequest,
  Department,
  DepartmentNode,
  DepartmentRequest,
  EmployeeDetail,
  EmployeeImportJob,
  EmployeeRequest,
  EmployeeStatus,
  EmployeeSummary,
  Holiday,
  HolidayRequest,
  PageResponse,
  UserGroup,
  UserGroupNode,
  UserGroupRequest
} from './types';

export const orgApi = {
  // Departments
  listDepartments: async (): Promise<Department[]> =>
    (await apiClient.get<Department[]>('/departments')).data,
  departmentTree: async (): Promise<DepartmentNode[]> =>
    (await apiClient.get<DepartmentNode[]>('/departments/tree')).data,
  createDepartment: async (body: DepartmentRequest): Promise<Department> =>
    (await apiClient.post<Department>('/departments', body)).data,
  updateDepartment: async (id: string, body: DepartmentRequest): Promise<Department> =>
    (await apiClient.put<Department>(`/departments/${id}`, body)).data,
  deleteDepartment: async (id: string): Promise<void> => {
    await apiClient.delete(`/departments/${id}`);
  },

  // Groups
  listGroups: async (): Promise<UserGroup[]> =>
    (await apiClient.get<UserGroup[]>('/groups')).data,
  groupTree: async (): Promise<UserGroupNode[]> =>
    (await apiClient.get<UserGroupNode[]>('/groups/tree')).data,
  createGroup: async (body: UserGroupRequest): Promise<UserGroup> =>
    (await apiClient.post<UserGroup>('/groups', body)).data,
  updateGroup: async (id: string, body: UserGroupRequest): Promise<UserGroup> =>
    (await apiClient.put<UserGroup>(`/groups/${id}`, body)).data,
  deleteGroup: async (id: string): Promise<void> => {
    await apiClient.delete(`/groups/${id}`);
  },

  // Employees
  searchEmployees: async (params: {
    q?: string;
    departmentId?: string;
    status?: EmployeeStatus;
    page?: number;
    size?: number;
    sort?: string;
    direction?: 'asc' | 'desc';
  }): Promise<PageResponse<EmployeeSummary>> =>
    (await apiClient.get<PageResponse<EmployeeSummary>>('/employees', { params })).data,
  getEmployee: async (id: string): Promise<EmployeeDetail> =>
    (await apiClient.get<EmployeeDetail>(`/employees/${id}`)).data,
  createEmployee: async (body: EmployeeRequest): Promise<EmployeeDetail> =>
    (await apiClient.post<EmployeeDetail>('/employees', body)).data,
  updateEmployee: async (id: string, body: EmployeeRequest): Promise<EmployeeDetail> =>
    (await apiClient.put<EmployeeDetail>(`/employees/${id}`, body)).data,
  deleteEmployee: async (id: string): Promise<void> => {
    await apiClient.delete(`/employees/${id}`);
  },
  importEmployees: async (file: File): Promise<EmployeeImportJob> => {
    const form = new FormData();
    form.append('file', file);
    const res = await apiClient.post<EmployeeImportJob>('/employees/imports', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    return res.data;
  },
  getImportJob: async (id: string): Promise<EmployeeImportJob> =>
    (await apiClient.get<EmployeeImportJob>(`/employees/imports/${id}`)).data,

  // Custom fields
  listCustomFields: async (): Promise<CustomFieldDefinition[]> =>
    (await apiClient.get<CustomFieldDefinition[]>('/custom-fields', {
      params: { entityType: 'EMPLOYEE' }
    })).data,
  createCustomField: async (body: CustomFieldDefinitionRequest): Promise<CustomFieldDefinition> =>
    (await apiClient.post<CustomFieldDefinition>('/custom-fields', body)).data,
  updateCustomField: async (id: string, body: CustomFieldDefinitionRequest): Promise<CustomFieldDefinition> =>
    (await apiClient.put<CustomFieldDefinition>(`/custom-fields/${id}`, body)).data,
  deleteCustomField: async (id: string): Promise<void> => {
    await apiClient.delete(`/custom-fields/${id}`);
  },

  // Holidays
  listHolidays: async (from?: string, to?: string): Promise<Holiday[]> =>
    (await apiClient.get<Holiday[]>('/holidays', { params: { from, to } })).data,
  createHoliday: async (body: HolidayRequest): Promise<Holiday> =>
    (await apiClient.post<Holiday>('/holidays', body)).data,
  updateHoliday: async (id: string, body: HolidayRequest): Promise<Holiday> =>
    (await apiClient.put<Holiday>(`/holidays/${id}`, body)).data,
  deleteHoliday: async (id: string): Promise<void> => {
    await apiClient.delete(`/holidays/${id}`);
  }
};
