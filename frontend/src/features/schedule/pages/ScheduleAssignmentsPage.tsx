import { useMemo, useState } from 'react';
import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Modal,
  NumberInput,
  Paper,
  Select,
  Stack,
  Table,
  Text,
  Title
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { IconPlus, IconTrash } from '@tabler/icons-react';
import { scheduleApi } from '../api';
import { orgApi } from '../../organization/api';
import type {
  Assignment,
  AssignmentRequest,
  AssignmentTargetType
} from '../types';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

const TARGET_TYPES: { value: AssignmentTargetType; label: string }[] = [
  { value: 'EMPLOYEE', label: 'Employee' },
  { value: 'GROUP', label: 'Group' }
];

interface DraftAssignment {
  targetType: AssignmentTargetType;
  targetId: string | null;
  templateId: string | null;
  startDate: Date | null;
  endDate: Date | null;
  priority: number;
}

function emptyDraft(): DraftAssignment {
  return {
    targetType: 'EMPLOYEE',
    targetId: null,
    templateId: null,
    startDate: new Date(),
    endDate: null,
    priority: 0
  };
}

function toIso(d: Date) {
  return d.toISOString().slice(0, 10);
}

export function ScheduleAssignmentsPage() {
  const queryClient = useQueryClient();
  const canWrite = useAuthStore((s) => s.hasPermission('schedule.write'));
  const [filterType, setFilterType] = useState<AssignmentTargetType | null>(null);
  const [filterTemplateId, setFilterTemplateId] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [draft, setDraft] = useState<DraftAssignment>(emptyDraft());

  const assignments = useQuery({
    queryKey: ['schedule-assignments', { filterType, filterTemplateId }],
    queryFn: () =>
      scheduleApi.listAssignments({
        targetType: filterType ?? undefined,
        templateId: filterTemplateId ?? undefined
      })
  });

  const templates = useQuery({
    queryKey: ['schedule-templates'],
    queryFn: () => scheduleApi.listTemplates()
  });

  const groups = useQuery({
    queryKey: ['groups'],
    queryFn: () => orgApi.listGroups()
  });

  const employees = useQuery({
    queryKey: ['employees-all'],
    queryFn: () => orgApi.searchEmployees({ size: 200 })
  });

  const templateMap = useMemo(
    () => new Map((templates.data ?? []).map((t) => [t.id, t.name])),
    [templates.data]
  );
  const groupMap = useMemo(
    () => new Map((groups.data ?? []).map((g) => [g.id, g.name])),
    [groups.data]
  );
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

  function targetLabel(a: Assignment) {
    if (a.targetType === 'GROUP') return groupMap.get(a.targetId) ?? a.targetId;
    return employeeMap.get(a.targetId) ?? a.targetId;
  }

  const create = useMutation({
    mutationFn: (req: AssignmentRequest) => scheduleApi.createAssignment(req),
    onSuccess: () => {
      notifications.show({ message: 'Assignment created', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['schedule-assignments'] });
      setModalOpen(false);
      setDraft(emptyDraft());
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  const remove = useMutation({
    mutationFn: scheduleApi.deleteAssignment,
    onSuccess: () => {
      notifications.show({ message: 'Assignment deleted', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['schedule-assignments'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  function onSave() {
    if (!draft.targetId || !draft.templateId || !draft.startDate) {
      notifications.show({ message: 'Target, template, and start date are required', color: 'red' });
      return;
    }
    create.mutate({
      targetType: draft.targetType,
      targetId: draft.targetId,
      templateId: draft.templateId,
      startDate: toIso(draft.startDate),
      endDate: draft.endDate ? toIso(draft.endDate) : null,
      priority: draft.priority,
      expectedVersion: null
    });
  }

  const templateOptions = (templates.data ?? []).map((t) => ({ value: t.id, label: t.name }));
  const targetOptions =
    draft.targetType === 'GROUP'
      ? (groups.data ?? []).map((g) => ({ value: g.id, label: g.name }))
      : (employees.data?.items ?? []).map((e) => ({
          value: e.id,
          label: `${e.firstName} ${e.lastName} (${e.employeeCode})`
        }));

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Schedule assignments</Title>
        {canWrite && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setModalOpen(true)}>
            New assignment
          </Button>
        )}
      </Group>

      <Paper withBorder p="md">
        <Group mb="sm">
          <Select
            placeholder="Target type"
            clearable
            data={TARGET_TYPES}
            value={filterType}
            onChange={(v) => setFilterType(v as AssignmentTargetType | null)}
          />
          <Select
            placeholder="Template"
            clearable
            searchable
            data={templateOptions}
            value={filterTemplateId}
            onChange={(v) => setFilterTemplateId(v)}
            style={{ flex: 1 }}
          />
        </Group>

        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Target</Table.Th>
              <Table.Th>Template</Table.Th>
              <Table.Th>Start</Table.Th>
              <Table.Th>End</Table.Th>
              <Table.Th>Priority</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {assignments.data?.map((a) => (
              <Table.Tr key={a.id}>
                <Table.Td>
                  <Group gap="xs">
                    <Badge variant="light" color={a.targetType === 'GROUP' ? 'blue' : 'teal'}>
                      {a.targetType}
                    </Badge>
                    <Text size="sm">{targetLabel(a)}</Text>
                  </Group>
                </Table.Td>
                <Table.Td>{templateMap.get(a.templateId) ?? a.templateId}</Table.Td>
                <Table.Td>{a.startDate}</Table.Td>
                <Table.Td>{a.endDate ?? '—'}</Table.Td>
                <Table.Td>{a.priority}</Table.Td>
                <Table.Td>
                  {canWrite && (
                    <ActionIcon
                      variant="subtle"
                      color="red"
                      onClick={() => {
                        if (confirm('Delete this assignment?')) {
                          remove.mutate(a.id);
                        }
                      }}
                    >
                      <IconTrash size={14} />
                    </ActionIcon>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
            {assignments.data && assignments.data.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={6}>
                  <Text c="dimmed">No assignments match the current filters.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>

      <Modal opened={modalOpen} onClose={() => setModalOpen(false)} title="New assignment" size="lg">
        <Stack>
          <Group grow>
            <Select
              label="Target type"
              data={TARGET_TYPES}
              value={draft.targetType}
              onChange={(v) =>
                setDraft((d) => ({ ...d, targetType: (v as AssignmentTargetType) ?? 'EMPLOYEE', targetId: null }))
              }
            />
            <Select
              label={draft.targetType === 'GROUP' ? 'Group' : 'Employee'}
              required
              searchable
              data={targetOptions}
              value={draft.targetId}
              onChange={(v) => setDraft((d) => ({ ...d, targetId: v }))}
            />
          </Group>
          <Select
            label="Template"
            required
            searchable
            data={templateOptions}
            value={draft.templateId}
            onChange={(v) => setDraft((d) => ({ ...d, templateId: v }))}
          />
          <Group grow>
            <DateInput
              label="Start date"
              required
              value={draft.startDate}
              onChange={(v) => setDraft((d) => ({ ...d, startDate: v }))}
            />
            <DateInput
              label="End date (optional)"
              clearable
              value={draft.endDate}
              onChange={(v) => setDraft((d) => ({ ...d, endDate: v }))}
            />
          </Group>
          <NumberInput
            label="Priority"
            description="Higher wins when multiple assignments overlap"
            min={0}
            value={draft.priority}
            onChange={(v) => setDraft((d) => ({ ...d, priority: typeof v === 'number' ? v : 0 }))}
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
