import { useEffect, useMemo, useState } from 'react';
import {
  Badge,
  Button,
  Card,
  Checkbox,
  Group,
  Paper,
  Select,
  SimpleGrid,
  Stack,
  Table,
  Text,
  Textarea,
  Title
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { useSearchParams } from 'react-router-dom';
import { leaveApi } from '../api';
import type { HalfDayPart, LeaveRequest, LeaveRequestStatus } from '../types';
import { orgApi } from '../../organization/api';
import { describeApiError } from '../../../lib/apiError';

const STATUS_COLORS: Record<LeaveRequestStatus, string> = {
  PENDING: 'yellow',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'gray'
};

function toDateInputString(d: Date | null): string | null {
  if (!d) return null;
  const y = d.getFullYear();
  const m = (d.getMonth() + 1).toString().padStart(2, '0');
  const day = d.getDate().toString().padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function fromDateInputString(s: string | null): Date | null {
  if (!s) return null;
  const [y, m, d] = s.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function EmployeeLeavePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const qc = useQueryClient();
  const initialEmpId = searchParams.get('employeeId') ?? '';
  const [employeeId, setEmployeeId] = useState(initialEmpId);
  const [employeeSearch, setEmployeeSearch] = useState('');

  const year = new Date().getFullYear();

  const employeeOptions = useQuery({
    queryKey: ['employees', 'search', employeeSearch],
    queryFn: () => orgApi.searchEmployees({ q: employeeSearch || undefined, size: 30 })
  });

  const balances = useQuery({
    queryKey: ['leave-balances', employeeId, year],
    queryFn: () => leaveApi.listBalances(employeeId, year),
    enabled: !!employeeId
  });

  const requests = useQuery({
    queryKey: ['leave-requests', employeeId],
    queryFn: () =>
      leaveApi.listRequests({ employeeId: employeeId || undefined }),
    enabled: !!employeeId
  });

  const cancel = useMutation({
    mutationFn: (id: string) => leaveApi.cancelRequest(id),
    onSuccess: () => {
      notifications.show({ message: 'Request cancelled', color: 'green' });
      qc.invalidateQueries({ queryKey: ['leave-requests'] });
      qc.invalidateQueries({ queryKey: ['leave-balances'] });
      qc.invalidateQueries({ queryKey: ['timecards'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  return (
    <Stack>
      <Title order={2}>Leave</Title>

      <Paper withBorder p="md">
        <Group align="flex-end">
          <Select
            label="Employee"
            placeholder="Search by name or code"
            value={employeeId || null}
            onChange={(v) => {
              setEmployeeId(v ?? '');
              if (v) {
                searchParams.set('employeeId', v);
                setSearchParams(searchParams, { replace: true });
              }
            }}
            data={
              employeeOptions.data?.items.map((e) => ({
                value: e.id,
                label: `${e.firstName} ${e.lastName} (${e.employeeCode})`
              })) ?? []
            }
            searchable
            searchValue={employeeSearch}
            onSearchChange={setEmployeeSearch}
            style={{ flex: 1, minWidth: 280 }}
            clearable
            nothingFoundMessage={employeeOptions.isFetching ? 'Searching…' : 'No matches'}
          />
        </Group>
      </Paper>

      {employeeId && (
        <>
          <SimpleGrid cols={{ base: 1, sm: 2, md: 3 }}>
            {balances.data?.map((b) => (
              <Card withBorder padding="md" key={b.id}>
                <Text size="xs" c="dimmed" tt="uppercase">
                  {b.leaveTypeName ?? 'Leave'}
                </Text>
                <Text size="xl" fw={700}>
                  {Number(b.balanceDays).toFixed(2)} days
                </Text>
                <Text size="xs" c="dimmed">
                  Balance for {b.year}
                </Text>
              </Card>
            ))}
            {balances.data && balances.data.length === 0 && (
              <Card withBorder>
                <Text c="dimmed" size="sm">
                  No balances yet. Submitting a request will lazy-create one at the type's default.
                </Text>
              </Card>
            )}
          </SimpleGrid>

          <LeaveRequestForm
            employeeId={employeeId}
            prefillStartDate={searchParams.get('date')}
            prefillRetroactive={searchParams.get('retroactive') === '1'}
          />

          <Paper withBorder p="md">
            <Title order={4} mb="sm">
              History
            </Title>
            <Table striped highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Type</Table.Th>
                  <Table.Th>Range</Table.Th>
                  <Table.Th>Days</Table.Th>
                  <Table.Th>Status</Table.Th>
                  <Table.Th>Reason</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {requests.data?.map((r) => (
                  <Table.Tr key={r.id}>
                    <Table.Td>{r.leaveTypeName ?? r.leaveTypeId.slice(0, 8)}</Table.Td>
                    <Table.Td>
                      {r.startDate}
                      {r.startDate !== r.endDate ? ` → ${r.endDate}` : ''}
                      {r.halfDay && (
                        <Badge size="xs" ml={4} variant="outline">
                          ½ {r.halfDayPart}
                        </Badge>
                      )}
                    </Table.Td>
                    <Table.Td>{Number(r.daysRequested).toFixed(2)}</Table.Td>
                    <Table.Td>
                      <Badge color={STATUS_COLORS[r.status]} variant="light">
                        {r.status}
                      </Badge>
                      {r.retroactive && (
                        <Badge size="xs" ml={4} variant="outline">
                          retroactive
                        </Badge>
                      )}
                    </Table.Td>
                    <Table.Td>
                      <Text size="xs">{r.reason ?? '—'}</Text>
                    </Table.Td>
                    <Table.Td>
                      {(r.status === 'PENDING' || r.status === 'APPROVED') && (
                        <Button
                          size="xs"
                          variant="subtle"
                          color="red"
                          onClick={() => cancel.mutate(r.id)}
                        >
                          Cancel
                        </Button>
                      )}
                    </Table.Td>
                  </Table.Tr>
                ))}
                {requests.data && requests.data.length === 0 && (
                  <Table.Tr>
                    <Table.Td colSpan={6}>
                      <Text c="dimmed">No leave requests yet.</Text>
                    </Table.Td>
                  </Table.Tr>
                )}
              </Table.Tbody>
            </Table>
          </Paper>
        </>
      )}
    </Stack>
  );
}

interface FormProps {
  employeeId: string;
  prefillStartDate: string | null;
  prefillRetroactive: boolean;
}

function LeaveRequestForm({ employeeId, prefillStartDate, prefillRetroactive }: FormProps) {
  const qc = useQueryClient();
  const [leaveTypeId, setLeaveTypeId] = useState<string | null>(null);
  const [start, setStart] = useState<Date | null>(fromDateInputString(prefillStartDate));
  const [end, setEnd] = useState<Date | null>(fromDateInputString(prefillStartDate));
  const [halfDay, setHalfDay] = useState(false);
  const [halfDayPart, setHalfDayPart] = useState<HalfDayPart | null>(null);
  const [reason, setReason] = useState('');
  const [retroactive, setRetroactive] = useState(prefillRetroactive);

  const types = useQuery({ queryKey: ['leave-types'], queryFn: leaveApi.listTypes });
  const activeTypes = useMemo(
    () => types.data?.filter((t) => t.active) ?? [],
    [types.data]
  );

  useEffect(() => {
    if (prefillStartDate) {
      const d = fromDateInputString(prefillStartDate);
      setStart(d);
      setEnd(d);
    }
    if (prefillRetroactive) setRetroactive(true);
  }, [prefillStartDate, prefillRetroactive]);

  const mutation = useMutation({
    mutationFn: () => {
      if (!leaveTypeId || !start || !end) throw new Error('Pick a type and date range');
      return leaveApi.createRequest({
        employeeId,
        leaveTypeId,
        startDate: toDateInputString(start) as string,
        endDate: toDateInputString(end) as string,
        halfDay,
        halfDayPart: halfDay ? halfDayPart : null,
        reason: reason || null,
        retroactive
      });
    },
    onSuccess: () => {
      notifications.show({ message: 'Request submitted', color: 'green' });
      qc.invalidateQueries({ queryKey: ['leave-requests'] });
      qc.invalidateQueries({ queryKey: ['leave-balances'] });
      qc.invalidateQueries({ queryKey: ['timecards'] });
      setReason('');
      setHalfDay(false);
      setHalfDayPart(null);
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  return (
    <Paper withBorder p="md">
      <Title order={4} mb="sm">
        Request leave
      </Title>
      <Stack>
        <Select
          label="Leave type"
          data={activeTypes.map((t) => ({ value: t.id, label: t.name }))}
          value={leaveTypeId}
          onChange={setLeaveTypeId}
          required
          searchable
          allowDeselect={false}
        />
        <Group grow>
          <DateInput label="Start" value={start} onChange={setStart} required />
          <DateInput label="End" value={end} onChange={setEnd} required minDate={start ?? undefined} />
        </Group>
        <Group>
          <Checkbox
            label="Half day"
            checked={halfDay}
            onChange={(e) => {
              setHalfDay(e.currentTarget.checked);
              if (e.currentTarget.checked && start) {
                setEnd(start);
              }
            }}
          />
          {halfDay && (
            <Select
              label="Which half"
              data={[
                { value: 'FIRST_HALF', label: 'First half (AM)' },
                { value: 'SECOND_HALF', label: 'Second half (PM)' }
              ]}
              value={halfDayPart}
              onChange={(v) => setHalfDayPart(v as HalfDayPart)}
              required
              style={{ width: 240 }}
            />
          )}
          <Checkbox
            label="Retroactive (for past days)"
            checked={retroactive}
            onChange={(e) => setRetroactive(e.currentTarget.checked)}
          />
        </Group>
        <Textarea
          label="Reason"
          value={reason}
          onChange={(e) => setReason(e.currentTarget.value)}
          autosize
          minRows={2}
        />
        <Group justify="flex-end">
          <Button onClick={() => mutation.mutate()} loading={mutation.isPending}>
            Submit
          </Button>
        </Group>
      </Stack>
    </Paper>
  );
}

// Unused — left as a hook into LeaveRequest details in the history table for future expansion.
export type { LeaveRequest };
