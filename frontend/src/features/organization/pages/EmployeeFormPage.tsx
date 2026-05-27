import {
  Anchor,
  Box,
  Breadcrumbs,
  Button,
  Checkbox,
  Group,
  MultiSelect,
  NumberInput,
  Paper,
  Select,
  Stack,
  Text,
  TextInput,
  Title
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { useNavigate, useParams, Link } from 'react-router-dom';
import dayjs from 'dayjs';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { orgApi } from '../api';
import type {
  CustomFieldDefinition,
  EmployeeDetail,
  EmployeeRequest,
  EmployeeStatus,
  EmploymentType
} from '../types';
import { describeApiError } from '../../../lib/apiError';

const schema = z.object({
  employeeCode: z.string().min(1).max(64),
  firstName: z.string().min(1).max(64),
  lastName: z.string().min(1).max(64),
  email: z
    .string()
    .nullable()
    .transform((v) => (v && v.trim().length > 0 ? v : null))
    .refine((v) => v === null || /\S+@\S+\.\S+/.test(v), 'must be a valid email'),
  phone: z
    .string()
    .nullable()
    .transform((v) => (v && v.trim().length > 0 ? v : null)),
  departmentId: z.string().nullable(),
  managerId: z.string().nullable(),
  userId: z.string().nullable(),
  employmentType: z.enum(['FULL_TIME', 'PART_TIME', 'CONTRACT', 'TEMP']),
  hireDate: z.string().min(1, 'Hire date is required'),
  terminationDate: z.string().nullable(),
  timezone: z
    .string()
    .nullable()
    .transform((v) => (v && v.trim().length > 0 ? v : null)),
  status: z.enum(['ACTIVE', 'INACTIVE', 'TERMINATED']),
  groupIds: z.array(z.string()).default([]),
  customFields: z.record(z.string(), z.unknown()).default({})
});

type FormValues = z.infer<typeof schema>;

export function EmployeeFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isNew = !id;

  const employeeQuery = useQuery({
    queryKey: ['employee', id],
    queryFn: () => orgApi.getEmployee(id!),
    enabled: !!id
  });

  const departments = useQuery({ queryKey: ['departments'], queryFn: orgApi.listDepartments });
  const groups = useQuery({ queryKey: ['groups'], queryFn: orgApi.listGroups });
  const customFields = useQuery({ queryKey: ['custom-fields'], queryFn: orgApi.listCustomFields });
  const employeesList = useQuery({
    queryKey: ['employees', 'all-for-manager-picker'],
    queryFn: () => orgApi.searchEmployees({ page: 0, size: 200 })
  });

  const ready = (!id || employeeQuery.data) && departments.data && groups.data && customFields.data;

  if (!ready) {
    return (
      <Stack>
        <Title order={2}>{isNew ? 'New employee' : 'Edit employee'}</Title>
        <Text>Loading…</Text>
      </Stack>
    );
  }

  const employee = employeeQuery.data;

  return (
    <Stack>
      <Breadcrumbs>
        <Anchor component={Link} to="/employees">
          Employees
        </Anchor>
        <Text>{isNew ? 'New' : employee?.employeeCode}</Text>
      </Breadcrumbs>
      <EmployeeForm
        employee={employee}
        departments={departments.data!}
        groups={groups.data!}
        customFields={customFields.data!}
        employeesForPicker={(employeesList.data?.items ?? []).filter((e) => !employee || e.id !== employee.id)}
        onCancel={() => navigate('/employees')}
        onSubmit={async (values) => {
          try {
            if (employee) {
              await orgApi.updateEmployee(employee.id, valuesToRequest(values));
              notifications.show({ message: 'Employee updated', color: 'green' });
            } else {
              const created = await orgApi.createEmployee(valuesToRequest(values));
              notifications.show({ message: 'Employee created', color: 'green' });
              queryClient.invalidateQueries({ queryKey: ['employees'] });
              navigate(`/employees/${created.id}`);
              return;
            }
            queryClient.invalidateQueries({ queryKey: ['employees'] });
            queryClient.invalidateQueries({ queryKey: ['employee', employee?.id] });
          } catch (err) {
            notifications.show({ message: describeApiError(err), color: 'red' });
          }
        }}
        onDelete={
          employee
            ? async () => {
                if (!confirm(`Delete employee ${employee.employeeCode}?`)) return;
                try {
                  await orgApi.deleteEmployee(employee.id);
                  notifications.show({ message: 'Employee deleted', color: 'green' });
                  queryClient.invalidateQueries({ queryKey: ['employees'] });
                  navigate('/employees');
                } catch (err) {
                  notifications.show({ message: describeApiError(err), color: 'red' });
                }
              }
            : undefined
        }
      />
    </Stack>
  );
}

interface EmployeeFormProps {
  employee?: EmployeeDetail;
  departments: { id: string; name: string }[];
  groups: { id: string; name: string }[];
  customFields: CustomFieldDefinition[];
  employeesForPicker: { id: string; firstName: string; lastName: string; employeeCode: string }[];
  onCancel: () => void;
  onSubmit: (v: FormValues) => Promise<void>;
  onDelete?: () => Promise<void>;
}

function EmployeeForm({
  employee,
  departments,
  groups,
  customFields,
  employeesForPicker,
  onCancel,
  onSubmit,
  onDelete
}: EmployeeFormProps) {
  const initialCustomFields: Record<string, unknown> = {};
  for (const def of customFields) {
    const found = employee?.customFields.find((c) => c.fieldKey === def.fieldKey);
    if (found) {
      initialCustomFields[def.fieldKey] =
        def.fieldType === 'NUMBER'
          ? found.numberValue
          : def.fieldType === 'BOOLEAN'
          ? found.booleanValue
          : def.fieldType === 'DATE'
          ? found.dateValue
          : found.stringValue;
    }
  }

  const { register, handleSubmit, setValue, watch, formState: { errors, isSubmitting } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      employeeCode: employee?.employeeCode ?? '',
      firstName: employee?.firstName ?? '',
      lastName: employee?.lastName ?? '',
      email: employee?.email ?? null,
      phone: employee?.phone ?? null,
      departmentId: employee?.departmentId ?? null,
      managerId: employee?.managerId ?? null,
      userId: employee?.userId ?? null,
      employmentType: employee?.employmentType ?? 'FULL_TIME',
      hireDate: employee?.hireDate ?? dayjs().format('YYYY-MM-DD'),
      terminationDate: employee?.terminationDate ?? null,
      timezone: employee?.timezone ?? null,
      status: employee?.status ?? 'ACTIVE',
      groupIds: employee?.groups.map((g) => g.id) ?? [],
      customFields: initialCustomFields
    }
  });

  return (
    <Paper withBorder p="lg">
      <form onSubmit={handleSubmit(onSubmit)}>
        <Stack>
          <Group grow>
            <TextInput
              label="Employee code"
              required
              error={errors.employeeCode?.message}
              {...register('employeeCode')}
            />
            <Select
              label="Status"
              required
              data={[
                { value: 'ACTIVE', label: 'Active' },
                { value: 'INACTIVE', label: 'Inactive' },
                { value: 'TERMINATED', label: 'Terminated' }
              ]}
              value={watch('status')}
              onChange={(v) => v && setValue('status', v as EmployeeStatus)}
            />
          </Group>
          <Group grow>
            <TextInput label="First name" required error={errors.firstName?.message} {...register('firstName')} />
            <TextInput label="Last name" required error={errors.lastName?.message} {...register('lastName')} />
          </Group>
          <Group grow>
            <TextInput
              label="Email"
              type="email"
              error={errors.email?.message}
              {...register('email', { setValueAs: (v) => (v === '' ? null : v) })}
            />
            <TextInput
              label="Phone"
              error={errors.phone?.message}
              {...register('phone', { setValueAs: (v) => (v === '' ? null : v) })}
            />
          </Group>
          <Group grow>
            <Select
              label="Department"
              clearable
              data={departments.map((d) => ({ value: d.id, label: d.name }))}
              value={watch('departmentId')}
              onChange={(v) => setValue('departmentId', v)}
            />
            <Select
              label="Manager"
              clearable
              searchable
              data={employeesForPicker.map((e) => ({
                value: e.id,
                label: `${e.firstName} ${e.lastName} (${e.employeeCode})`
              }))}
              value={watch('managerId')}
              onChange={(v) => setValue('managerId', v)}
            />
          </Group>
          <Group grow>
            <Select
              label="Employment type"
              required
              data={[
                { value: 'FULL_TIME', label: 'Full time' },
                { value: 'PART_TIME', label: 'Part time' },
                { value: 'CONTRACT', label: 'Contract' },
                { value: 'TEMP', label: 'Temporary' }
              ]}
              value={watch('employmentType')}
              onChange={(v) => v && setValue('employmentType', v as EmploymentType)}
            />
            <TextInput
              label="Timezone (IANA)"
              placeholder="e.g. Asia/Colombo"
              {...register('timezone', { setValueAs: (v) => (v === '' ? null : v) })}
            />
          </Group>
          <Group grow>
            <DateInput
              label="Hire date"
              required
              valueFormat="YYYY-MM-DD"
              error={errors.hireDate?.message}
              value={watch('hireDate') ? new Date(watch('hireDate')) : null}
              onChange={(d) => setValue('hireDate', d ? dayjs(d).format('YYYY-MM-DD') : '')}
            />
            <DateInput
              label="Termination date"
              valueFormat="YYYY-MM-DD"
              value={watch('terminationDate') ? new Date(watch('terminationDate')!) : null}
              onChange={(d) => setValue('terminationDate', d ? dayjs(d).format('YYYY-MM-DD') : null)}
            />
          </Group>
          <MultiSelect
            label="Groups"
            data={groups.map((g) => ({ value: g.id, label: g.name }))}
            value={watch('groupIds')}
            onChange={(v) => setValue('groupIds', v)}
            searchable
          />

          {customFields.length > 0 && (
            <Box>
              <Title order={4} mt="md" mb="sm">
                Custom fields
              </Title>
              <Stack>
                {customFields.map((def) => (
                  <CustomFieldInput
                    key={def.id}
                    def={def}
                    value={watch(`customFields.${def.fieldKey}` as const)}
                    onChange={(v) =>
                      setValue(
                        `customFields.${def.fieldKey}` as const,
                        v,
                        { shouldDirty: true }
                      )
                    }
                  />
                ))}
              </Stack>
            </Box>
          )}

          <Group justify="space-between" mt="md">
            <Box>
              {onDelete && (
                <Button variant="outline" color="red" onClick={onDelete} type="button">
                  Delete
                </Button>
              )}
            </Box>
            <Group>
              <Button variant="default" onClick={onCancel} type="button">
                Cancel
              </Button>
              <Button type="submit" loading={isSubmitting}>
                Save
              </Button>
            </Group>
          </Group>
        </Stack>
      </form>
    </Paper>
  );
}

interface CustomFieldInputProps {
  def: CustomFieldDefinition;
  value: unknown;
  onChange: (v: unknown) => void;
}

function CustomFieldInput({ def, value, onChange }: CustomFieldInputProps) {
  switch (def.fieldType) {
    case 'STRING':
      return (
        <TextInput
          label={def.displayLabel + (def.required ? ' *' : '')}
          value={(value as string) ?? ''}
          onChange={(e) => onChange(e.currentTarget.value || null)}
        />
      );
    case 'NUMBER':
      return (
        <NumberInput
          label={def.displayLabel + (def.required ? ' *' : '')}
          value={value === null || value === undefined ? '' : (value as number)}
          onChange={(v) => onChange(v === '' ? null : v)}
        />
      );
    case 'BOOLEAN':
      return (
        <Checkbox
          label={def.displayLabel}
          checked={Boolean(value)}
          onChange={(e) => onChange(e.currentTarget.checked)}
        />
      );
    case 'DATE':
      return (
        <DateInput
          label={def.displayLabel + (def.required ? ' *' : '')}
          valueFormat="YYYY-MM-DD"
          value={value ? new Date(value as string) : null}
          onChange={(d) => onChange(d ? dayjs(d).format('YYYY-MM-DD') : null)}
        />
      );
    case 'ENUM':
      return (
        <Select
          label={def.displayLabel + (def.required ? ' *' : '')}
          data={def.options.map((o) => ({ value: o, label: o }))}
          value={(value as string) ?? null}
          onChange={(v) => onChange(v)}
          clearable={!def.required}
        />
      );
  }
}

function valuesToRequest(v: FormValues): EmployeeRequest {
  const customFields: Record<string, unknown> = {};
  for (const [k, val] of Object.entries(v.customFields)) {
    if (val !== null && val !== undefined && val !== '') {
      customFields[k] = val;
    }
  }
  return {
    employeeCode: v.employeeCode,
    firstName: v.firstName,
    lastName: v.lastName,
    email: v.email,
    phone: v.phone,
    departmentId: v.departmentId,
    managerId: v.managerId,
    userId: v.userId,
    employmentType: v.employmentType,
    hireDate: v.hireDate,
    terminationDate: v.terminationDate,
    timezone: v.timezone,
    status: v.status,
    groupIds: v.groupIds,
    customFields
  };
}
