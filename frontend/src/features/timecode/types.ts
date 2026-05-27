export type TimeCodeCategory = 'ATTENDANCE' | 'OVERTIME' | 'LEAVE';

export interface TimeCode {
  id: string;
  code: string;
  name: string;
  category: TimeCodeCategory;
  rate: string;
  color: string;
  paid: boolean;
  countsForAttendance: boolean;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface TimeCodeRequest {
  code: string;
  name: string;
  category: TimeCodeCategory;
  rate: string;
  color: string;
  paid: boolean;
  countsForAttendance: boolean;
  description: string | null;
  active: boolean;
}
