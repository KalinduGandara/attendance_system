import { useState } from 'react';
import {
  Badge,
  Button,
  FileButton,
  Group,
  Pagination,
  Paper,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Title
} from '@mantine/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { IconPlus, IconUpload } from '@tabler/icons-react';
import { Link } from 'react-router-dom';
import { useDebouncedValue } from '@mantine/hooks';
import { orgApi } from '../api';
import type { EmployeeStatus } from '../types';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

export function EmployeesListPage() {
  const canWrite = useAuthStore((s) => s.hasPermission('employee.write'));
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [debounced] = useDebouncedValue(search, 300);
  const [departmentId, setDepartmentId] = useState<string | null>(null);
  const [status, setStatus] = useState<EmployeeStatus | null>(null);

  const departments = useQuery({ queryKey: ['departments'], queryFn: orgApi.listDepartments });
  const employees = useQuery({
    queryKey: ['employees', { page, debounced, departmentId, status }],
    queryFn: () =>
      orgApi.searchEmployees({
        q: debounced || undefined,
        departmentId: departmentId ?? undefined,
        status: status ?? undefined,
        page,
        size: 25,
        sort: 'lastName',
        direction: 'asc'
      })
  });

  const importMutation = useMutation({
    mutationFn: (file: File) => orgApi.importEmployees(file),
    onSuccess: (job) => {
      const summary = `${job.createdCount} created, ${job.updatedCount} updated, ${job.errorCount} errors.`;
      notifications.show({
        message: `Import ${job.status === 'DONE' ? 'completed' : job.status.toLowerCase()}: ${summary}`,
        color: job.errorCount === 0 ? 'green' : 'yellow',
        autoClose: 8000
      });
      queryClient.invalidateQueries({ queryKey: ['employees'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Employees</Title>
        {canWrite && (
          <Group>
            <FileButton onChange={(f) => f && importMutation.mutate(f)} accept=".csv,text/csv">
              {(props) => (
                <Button {...props} leftSection={<IconUpload size={16} />} variant="default" loading={importMutation.isPending}>
                  Import CSV
                </Button>
              )}
            </FileButton>
            <Button component={Link} to="/employees/new" leftSection={<IconPlus size={16} />}>
              New employee
            </Button>
          </Group>
        )}
      </Group>

      <Paper withBorder p="md">
        <Group mb="sm">
          <TextInput
            placeholder="Search by name or code"
            value={search}
            onChange={(e) => {
              setSearch(e.currentTarget.value);
              setPage(0);
            }}
            style={{ flex: 1 }}
          />
          <Select
            placeholder="Department"
            clearable
            data={(departments.data ?? []).map((d) => ({ value: d.id, label: d.name }))}
            value={departmentId}
            onChange={(v) => {
              setDepartmentId(v);
              setPage(0);
            }}
          />
          <Select
            placeholder="Status"
            clearable
            data={[
              { value: 'ACTIVE', label: 'Active' },
              { value: 'INACTIVE', label: 'Inactive' },
              { value: 'TERMINATED', label: 'Terminated' }
            ]}
            value={status}
            onChange={(v) => {
              setStatus(v as EmployeeStatus | null);
              setPage(0);
            }}
          />
        </Group>

        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Code</Table.Th>
              <Table.Th>Name</Table.Th>
              <Table.Th>Email</Table.Th>
              <Table.Th>Department</Table.Th>
              <Table.Th>Type</Table.Th>
              <Table.Th>Hired</Table.Th>
              <Table.Th>Status</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {employees.data?.items.map((e) => (
              <Table.Tr key={e.id} style={{ cursor: 'pointer' }}>
                <Table.Td>
                  <Link to={`/employees/${e.id}`}>{e.employeeCode}</Link>
                </Table.Td>
                <Table.Td>
                  <Link to={`/employees/${e.id}`}>
                    {e.firstName} {e.lastName}
                  </Link>
                </Table.Td>
                <Table.Td>{e.email ?? '—'}</Table.Td>
                <Table.Td>{e.departmentName ?? '—'}</Table.Td>
                <Table.Td>
                  <Badge variant="light">{e.employmentType}</Badge>
                </Table.Td>
                <Table.Td>{e.hireDate}</Table.Td>
                <Table.Td>
                  <Badge color={e.status === 'ACTIVE' ? 'green' : 'gray'} variant="light">
                    {e.status}
                  </Badge>
                </Table.Td>
              </Table.Tr>
            ))}
            {employees.data && employees.data.items.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={7}>
                  <Text c="dimmed">No employees match the filters.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
        {employees.data && employees.data.totalPages > 1 && (
          <Group justify="flex-end" mt="md">
            <Pagination total={employees.data.totalPages} value={page + 1} onChange={(v) => setPage(v - 1)} />
          </Group>
        )}
      </Paper>
    </Stack>
  );
}
