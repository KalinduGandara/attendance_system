import { useEffect, useMemo, useState } from 'react';
import {
  Anchor,
  Button,
  Group,
  NumberInput,
  Paper,
  Select,
  Stack,
  Table,
  Text,
  Textarea,
  TextInput,
  Title
} from '@mantine/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { notifications } from '@mantine/notifications';
import { IconArrowLeft } from '@tabler/icons-react';
import { scheduleApi } from '../api';
import { shiftApi } from '../../shift/api';
import type {
  CycleType,
  Template,
  TemplateDayRequest,
  TemplateRequest
} from '../types';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

interface FormState {
  name: string;
  cycleType: CycleType;
  cycleLengthDays: number;
  description: string;
  days: TemplateDayRequest[];
}

const CYCLE_TYPES: { value: CycleType; label: string }[] = [
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'DAILY', label: 'Daily / custom-length' }
];

const WEEKDAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

function emptyState(): FormState {
  return {
    name: '',
    cycleType: 'WEEKLY',
    cycleLengthDays: 7,
    description: '',
    days: Array.from({ length: 7 }, (_, i) => ({ dayIndex: i, shiftId: null }))
  };
}

function toState(t: Template): FormState {
  const days = Array.from({ length: t.cycleLengthDays }, (_, i) => {
    const existing = t.days.find((d) => d.dayIndex === i);
    return { dayIndex: i, shiftId: existing?.shiftId ?? null };
  });
  return {
    name: t.name,
    cycleType: t.cycleType,
    cycleLengthDays: t.cycleLengthDays,
    description: t.description ?? '',
    days
  };
}

function toRequest(s: FormState, version: number | null): TemplateRequest {
  return {
    name: s.name.trim(),
    cycleType: s.cycleType,
    cycleLengthDays: s.cycleLengthDays,
    description: s.description.trim() || null,
    days: s.days.filter((d) => d.shiftId !== null || true),
    expectedVersion: version
  };
}

function dayLabel(cycleType: CycleType, index: number) {
  if (cycleType === 'WEEKLY' && index < 7) {
    return WEEKDAY_LABELS[index];
  }
  return `Day ${index + 1}`;
}

export function ScheduleTemplateFormPage() {
  const { id } = useParams<{ id: string }>();
  const isNew = !id;
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const canWrite = useAuthStore((s) => s.hasPermission('schedule.write'));

  const existing = useQuery({
    queryKey: ['schedule-template', id],
    queryFn: () => scheduleApi.getTemplate(id!),
    enabled: !isNew
  });

  const shifts = useQuery({
    queryKey: ['shifts', { activeOnly: true }],
    queryFn: () => shiftApi.list({ active: true })
  });

  const [state, setState] = useState<FormState>(emptyState());

  useEffect(() => {
    if (existing.data) {
      setState(toState(existing.data));
    }
  }, [existing.data]);

  const shiftOptions = useMemo(
    () => (shifts.data ?? []).map((s) => ({ value: s.id, label: s.name })),
    [shifts.data]
  );

  // Keep `days` aligned with `cycleLengthDays`.
  function setCycleLength(n: number) {
    setState((cur) => {
      const days = Array.from({ length: n }, (_, i) => {
        const existing = cur.days.find((d) => d.dayIndex === i);
        return existing ?? { dayIndex: i, shiftId: null };
      });
      return { ...cur, cycleLengthDays: n, days };
    });
  }

  function setCycleType(t: CycleType) {
    setState((cur) => {
      const length = t === 'WEEKLY' ? 7 : cur.cycleLengthDays;
      const days = Array.from({ length }, (_, i) => {
        const existing = cur.days.find((d) => d.dayIndex === i);
        return existing ?? { dayIndex: i, shiftId: null };
      });
      return { ...cur, cycleType: t, cycleLengthDays: length, days };
    });
  }

  const save = useMutation({
    mutationFn: async (req: TemplateRequest) => {
      if (isNew) return scheduleApi.createTemplate(req);
      return scheduleApi.updateTemplate(id!, req);
    },
    onSuccess: (t) => {
      notifications.show({ message: isNew ? 'Template created' : 'Template updated', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['schedule-templates'] });
      queryClient.invalidateQueries({ queryKey: ['schedule-template', t.id] });
      navigate('/schedule-templates');
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  function onSubmit() {
    if (!state.name.trim()) {
      notifications.show({ message: 'Name is required', color: 'red' });
      return;
    }
    save.mutate(toRequest(state, existing.data?.version ?? null));
  }

  return (
    <Stack>
      <Group justify="space-between">
        <Group gap="xs">
          <Anchor component={Link} to="/schedule-templates">
            <Group gap={4}>
              <IconArrowLeft size={14} />
              Templates
            </Group>
          </Anchor>
          <Title order={2}>{isNew ? 'New template' : 'Edit template'}</Title>
        </Group>
        {canWrite && (
          <Button onClick={onSubmit} loading={save.isPending}>
            {isNew ? 'Create' : 'Save changes'}
          </Button>
        )}
      </Group>

      <Paper withBorder p="md">
        <Stack>
          <Group grow align="flex-start">
            <TextInput
              required
              label="Name"
              value={state.name}
              onChange={(e) => setState((s) => ({ ...s, name: e.currentTarget.value }))}
            />
            <Select
              label="Cycle type"
              data={CYCLE_TYPES}
              value={state.cycleType}
              onChange={(v) => v && setCycleType(v as CycleType)}
            />
            <NumberInput
              label="Cycle length (days)"
              min={1}
              max={366}
              value={state.cycleLengthDays}
              onChange={(v) => typeof v === 'number' && setCycleLength(v)}
              disabled={state.cycleType === 'WEEKLY'}
            />
          </Group>
          <Textarea
            label="Description"
            value={state.description}
            onChange={(e) => setState((s) => ({ ...s, description: e.currentTarget.value }))}
            minRows={2}
          />
        </Stack>
      </Paper>

      <Paper withBorder p="md">
        <Stack>
          <Title order={4}>Days</Title>
          <Text c="dimmed" size="sm">
            Assign a shift to each day in the cycle. Leave blank for "off". The
            cycle repeats from the assignment's start date.
          </Text>
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th style={{ width: 120 }}>Day</Table.Th>
                <Table.Th>Shift</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {state.days.map((d) => (
                <Table.Tr key={d.dayIndex}>
                  <Table.Td>
                    <Text fw={500}>{dayLabel(state.cycleType, d.dayIndex)}</Text>
                  </Table.Td>
                  <Table.Td>
                    <Select
                      placeholder="Off"
                      clearable
                      searchable
                      data={shiftOptions}
                      value={d.shiftId}
                      onChange={(v) =>
                        setState((s) => ({
                          ...s,
                          days: s.days.map((row) =>
                            row.dayIndex === d.dayIndex ? { ...row, shiftId: v } : row
                          )
                        }))
                      }
                    />
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Stack>
      </Paper>
    </Stack>
  );
}
