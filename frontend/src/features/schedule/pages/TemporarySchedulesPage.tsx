import { useMemo, useState } from 'react';
import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Modal,
  Paper,
  Select,
  Stack,
  Table,
  Text,
  Textarea,
  Title
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { IconPlus, IconTrash } from '@tabler/icons-react';
import { scheduleApi } from '../api';
import { shiftApi } from '../../shift/api';
import { orgApi } from '../../organization/api';
import type { TemporarySchedule, TemporaryScheduleRequest } from '../types';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

interface Draft {
  employeeId: string | null;
  startDate: Date | null;
  endDate: Date | null;
  shiftId: string | null;
  reason: string;
}

function emptyDraft(): Draft {
  return {
    employeeId: null,
    startDate: new Date(),
    endDate: new Date(),
    shiftId: null,
    reason: ''
  };
}

function toIso(d: Date) {
  return d.toISOString().slice(0, 10);
}

export function TemporarySchedulesPage() {
  const queryClient = useQueryClient();
  const canWrite = useAuthStore((s) => s.hasPermission('schedule.write'));
  const [filterEmployeeId, setFilterEmployeeId] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [draft, setDraft] = useState<Draft>(emptyDraft());

  const items = useQuery({
    queryKey: ['temporary-schedules', { filterEmployeeId }],
    queryFn: () =>
      scheduleApi.listTemporary({ employeeId: filterEmployeeId ?? undefined })
  });

  const shifts = useQuery({
    queryKey: ['shifts', { activeOnly: true }],
    queryFn: () => shiftApi.list({ active: true })
  });

  const employees = useQuery({
    queryKey: ['employees-all'],
    queryFn: () => orgApi.searchEmployees({ size: 200 })
  });

  const employeeMap = useMemo(
    () =>
      new Map(
        (employees.data?.items ?? []).map((e) => [
          e.id,
          `${e.firstName} ${e.lastName} (${e.employeeCode})`
        ])
      ),
    [employees.data]
  );
  const shiftMap = useMemo(
    () => new Map((shifts.data ?? []).map((s) => [s.id, s.name])),
    [shifts.data]
  );

  const create = useMutation({
    mutationFn: (req: TemporaryScheduleRequest) => scheduleApi.createTemporary(req),
    onSuccess: () => {
      notifications.show({ message: 'Override created', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['temporary-schedules'] });
      setModalOpen(false);
      setDraft(emptyDraft());
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  const remove = useMutation({
    mutationFn: scheduleApi.deleteTemporary,
    onSuccess: () => {
      notifications.show({ message: 'Override deleted', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['temporary-schedules'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  function onSave() {
    if (!draft.employeeId || !draft.startDate || !draft.endDate) {
      notifications.show({ message: 'Employee and dates are required', color: 'red' });
      return;
    }
    create.mutate({
      employeeId: draft.employeeId,
      startDate: toIso(draft.startDate),
      endDate: toIso(draft.endDate),
      shiftId: draft.shiftId,
      reason: draft.reason.trim() || null,
      expectedVersion: null
    });
  }

  const employeeOptions = (employees.data?.items ?? []).map((e) => ({
    value: e.id,
    label: `${e.firstName} ${e.lastName} (${e.employeeCode})`
  }));
  const shiftOptions = (shifts.data ?? []).map((s) => ({ value: s.id, label: s.name }));

  function shiftLabel(t: TemporarySchedule) {
    if (!t.shiftId) return <Badge variant="light" color="gray">Day off</Badge>;
    return shiftMap.get(t.shiftId) ?? t.shiftId;
  }

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Temporary schedules</Title>
        {canWrite && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setModalOpen(true)}>
            New override
          </Button>
        )}
      </Group>

      <Paper withBorder p="md">
        <Group mb="sm">
          <Select
            placeholder="Filter by employee"
            clearable
            searchable
            data={employeeOptions}
            value={filterEmployeeId}
            onChange={(v) => setFilterEmployeeId(v)}
            style={{ flex: 1 }}
          />
        </Group>

        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Employee</Table.Th>
              <Table.Th>Start</Table.Th>
              <Table.Th>End</Table.Th>
              <Table.Th>Shift</Table.Th>
              <Table.Th>Reason</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {items.data?.map((t) => (
              <Table.Tr key={t.id}>
                <Table.Td>{employeeMap.get(t.employeeId) ?? t.employeeId}</Table.Td>
                <Table.Td>{t.startDate}</Table.Td>
                <Table.Td>{t.endDate}</Table.Td>
                <Table.Td>{shiftLabel(t)}</Table.Td>
                <Table.Td>
                  <Text size="sm" c="dimmed" lineClamp={1}>
                    {t.reason || '—'}
                  </Text>
                </Table.Td>
                <Table.Td>
                  {canWrite && (
                    <ActionIcon
                      variant="subtle"
                      color="red"
                      onClick={() => {
                        if (confirm('Delete this override?')) {
                          remove.mutate(t.id);
                        }
                      }}
                    >
                      <IconTrash size={14} />
                    </ActionIcon>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
            {items.data && items.data.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={6}>
                  <Text c="dimmed">No overrides found.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>

      <Modal opened={modalOpen} onClose={() => setModalOpen(false)} title="New override" size="lg">
        <Stack>
          <Select
            label="Employee"
            required
            searchable
            data={employeeOptions}
            value={draft.employeeId}
            onChange={(v) => setDraft((d) => ({ ...d, employeeId: v }))}
          />
          <Group grow>
            <DateInput
              label="Start date"
              required
              value={draft.startDate}
              onChange={(v) => setDraft((d) => ({ ...d, startDate: v }))}
            />
            <DateInput
              label="End date"
              required
              value={draft.endDate}
              onChange={(v) => setDraft((d) => ({ ...d, endDate: v }))}
            />
          </Group>
          <Select
            label="Shift"
            clearable
            searchable
            placeholder="Leave empty for an explicit day off"
            data={shiftOptions}
            value={draft.shiftId}
            onChange={(v) => setDraft((d) => ({ ...d, shiftId: v }))}
          />
          <Textarea
            label="Reason"
            value={draft.reason}
            onChange={(e) => setDraft((d) => ({ ...d, reason: e.currentTarget.value }))}
            minRows={2}
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setModalOpen(false)}>
              Cancel
            </Button>
            <Button onClick={onSave} loading={create.isPending}>
              Create
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}
