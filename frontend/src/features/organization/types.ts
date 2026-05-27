export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  sort?: string | null;
}

export interface Department {
  id: string;
  name: string;
  parentId: string | null;
  timezone: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface DepartmentNode {
  id: string;
  name: string;
  timezone: string | null;
  children: DepartmentNode[];
}

export interface DepartmentRequest {
  name: string;
  parentId: string | null;
  timezone: string | null;
}

export interface UserGroup {
  id: string;
  name: string;
  parentId: string | null;
  description: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface UserGroupNode {
  id: string;
  name: string;
  description: string | null;
  children: UserGroupNode[];
}

export interface UserGroupRequest {
  name: string;
  parentId: string | null;
  description: string | null;
}

export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'TEMP';
export type EmployeeStatus = 'ACTIVE' | 'INACTIVE' | 'TERMINATED';

export interface GroupRef {
  id: string;
  name: string;
}

export interface EmployeeSummary {
  id: string;
  employeeCode: string;
  firstName: string;
  lastName: string;
  email: string | null;
  departmentId: string | null;
  departmentName: string | null;
  status: EmployeeStatus;
  employmentType: EmploymentType;
  hireDate: string;
}

export interface CustomFieldValue {
  definitionId: string;
  fieldKey: string;
  displayLabel: string;
  fieldType: 'STRING' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'ENUM';
  stringValue: string | null;
  numberValue: number | null;
  dateValue: string | null;
  booleanValue: boolean | null;
}

export interface EmployeeDetail {
  id: string;
  employeeCode: string;
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
  departmentId: string | null;
  departmentName: string | null;
  managerId: string | null;
  managerName: string | null;
  userId: string | null;
  username: string | null;
  employmentType: EmploymentType;
  hireDate: string;
  terminationDate: string | null;
  timezone: string | null;
  status: EmployeeStatus;
  groups: GroupRef[];
  customFields: CustomFieldValue[];
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface EmployeeRequest {
  employeeCode: string;
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
  departmentId: string | null;
  managerId: string | null;
  userId: string | null;
  employmentType: EmploymentType;
  hireDate: string;
  terminationDate: string | null;
  timezone: string | null;
  status: EmployeeStatus;
  groupIds: string[];
  customFields: Record<string, unknown>;
}

export interface CustomFieldDefinition {
  id: string;
  entityType: 'EMPLOYEE';
  fieldKey: string;
  displayLabel: string;
  fieldType: 'STRING' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'ENUM';
  required: boolean;
  options: string[];
  displayOrder: number;
}

export interface CustomFieldDefinitionRequest {
  entityType: 'EMPLOYEE';
  fieldKey: string;
  displayLabel: string;
  fieldType: 'STRING' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'ENUM';
  required: boolean;
  options: string[];
  displayOrder: number;
}

export interface Holiday {
  id: string;
  name: string;
  holidayDate: string;
  recurringYearly: boolean;
  paid: boolean;
  description: string | null;
  groups: GroupRef[];
}

export interface HolidayRequest {
  name: string;
  holidayDate: string;
  recurringYearly: boolean;
  paid: boolean;
  description: string | null;
  groupIds: string[];
}

export interface EmployeeImportJob {
  id: string;
  status: 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED';
  fileName: string | null;
  totalRows: number;
  processedRows: number;
  createdCount: number;
  updatedCount: number;
  errorCount: number;
  errorReport: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
}
