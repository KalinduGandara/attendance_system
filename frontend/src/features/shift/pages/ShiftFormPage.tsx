import { useMemo, useState } from 'react';
import {
  ActionIcon,
  Anchor,
  Box,
  Button,
  ColorInput,
  Divider,
  Group,
  MultiSelect,
  NumberInput,
  Paper,
  Select,
  Stack,
  Switch,
  Table,
  Tabs,
  Text,
  Textarea,
  TextInput,
  Title
} from '@mantine/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { notifications } from '@mantine/notifications';
import { IconArrowLeft, IconPlus, IconTrash } from '@tabler/icons-react';
import { shiftApi } from '../api';
import { timeCodeApi } from '../../timecode/api';
import { SchedulePreview } from '../components/SchedulePreview';
import type {
  BreakKind,
  BreakRuleRequest,
  GraceKind,
  GraceRuleRequest,
  OvertimeRuleRequest,
  RoundingKind,
  RoundingMode,
  RoundingRuleRequest,
  SegmentRequest,
  Shift,
  ShiftRequest,
  ShiftType
} from '../types';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

interface FormState {
  name: string;
  shiftType: ShiftType;
  color: string;
  timezone: string;
  description: string;
  active: boolean;
  attendanceTimeCodeId: string;
  segments: SegmentRequest[];
  roundingRules: RoundingRuleRequest[];
  graceRules: GraceRuleRequest[];
  breakRules: BreakRuleRequest[];
  overtimeRules: OvertimeRuleRequest[];
  candidateShiftIds: string[];
}

const TYPES: { value: ShiftType; label: string }[] = [
  { value: 'FIXED', label: 'Fixed' },
  { value: 'FLEXIBLE', label: 'Flexible' },
  { value: 'FLOATING', label: 'Floating' }
];

const ROUNDING_KINDS: { value: RoundingKind; label: string }[] = [
  { value: 'PUNCH_IN', label: 'Punch in' },
  { value: 'PUNCH_OUT', label: 'Punch out' },
  { value: 'SHIFT', label: 'Shift total' }
];
const ROUNDING_MODES: { value: RoundingMode; label: string }[] = [
  { value: 'NEAREST', label: 'Nearest' },
  { value: 'UP', label: 'Up' },
  { value: 'DOWN', label: 'Down' }
];
const GRACE_KINDS: { value: GraceKind; label: string }[] = [
  { value: 'LATE_IN', label: 'Late in' },
  { value: 'EARLY_OUT', label: 'Early out' }
];
const BREAK_KINDS: { value: BreakKind; label: string }[] = [
  { value: 'AUTO_DEDUCT', label: 'Auto-deduct' },
  { value: 'PUNCH_TRACKED', label: 'Punch tracked' }
];

function emptyState(): FormState {
  return {
    name: '',
    shiftType: 'FIXED',
    color: '#3b82f6',
    timezone: '',
    description: '',
    active: true,
    attendanceTimeCodeId: '',
    segments: [{ segmentOrder: 0, startMinuteOfDay: 540, endMinuteOfDay: 1020, requiredMinutes: null }],
    roundingRules: [],
    graceRules: [],
    breakRules: [],
    overtimeRules: [],
    candidateShiftIds: []
  };
}

function fromShift(s: Shift): FormState {
  return {
    name: s.name,
    shiftType: s.shiftType,
    color: s.color,
    timezone: s.timezone ?? '',
    description: s.description ?? '',
    active: s.active,
    attendanceTimeCodeId: s.attendanceTimeCodeId,
    segments: s.segments.map((seg) => ({
      segmentOrder: seg.segmentOrder,
      startMinuteOfDay: seg.startMinuteOfDay,
      endMinuteOfDay: seg.endMinuteOfDay,
      requiredMinutes: seg.requiredMinutes
    })),
    roundingRules: s.roundingRules.map((r) => ({
      kind: r.kind,
      unitMinutes: r.unitMinutes,
      mode: r.mode
    })),
    graceRules: s.graceRules.map((g) => ({ kind: g.kind, minutes: g.minutes })),
    breakRules: s.breakRules.map((b) => ({
      name: b.name,
      kind: b.kind,
      durationMinutes: b.durationMinutes,
      earliestStartMinute: b.earliestStartMinute,
      afterHoursWorked: b.afterHoursWorked,
      paid: b.paid,
      timeCodeId: b.timeCodeId
    })),
    overtimeRules: s.overtimeRules.map((o) => ({
      sequenceOrder: o.sequenceOrder,
      afterMinutesWorked: o.afterMinutesWorked,
      timeCodeId: o.timeCodeId,
      maxMinutes: o.maxMinutes
    })),
    candidateShiftIds: s.candidateShiftIds
  };
}

function toRequest(state: FormState, expectedVersion: number | null): ShiftRequest {
  return {
    name: state.name.trim(),
    shiftType: state.shiftType,
    color: state.color,
    timezone: state.timezone.trim() || null,
    description: state.description.trim() || null,
    active: state.active,
    attendanceTimeCodeId: state.attendanceTimeCodeId,
    segments: state.segments,
    roundingRules: state.roundingRules,
    graceRules: state.graceRules,
    breakRules: state.breakRules,
    overtimeRules: state.overtimeRules,
    candidateShiftIds: state.candidateShiftIds,
    expectedVersion
  };
}

export function ShiftFormPage() {
  const { id } = useParams<{ id: string }>();
  const isNew = !id || id === 'new';
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const canWrite = useAuthStore((s) => s.hasPermission('shift.write'));

  const existing = useQuery({
    queryKey: ['shifts', id],
    queryFn: () => shiftApi.get(id as string),
    enabled: !isNew
  });

  const timeCodes = useQuery({
    queryKey: ['time-codes', { all: true }],
    queryFn: () => timeCodeApi.list({ activeOnly: true })
  });

  const allShifts = useQuery({
    queryKey: ['shifts', { all: true }],
    queryFn: () => shiftApi.list()
  });

  const [state, setState] = useState<FormState>(emptyState);
  const [loaded, setLoaded] = useState(isNew);

  if (existing.data && !loaded) {
    setState(fromShift(existing.data));
    setLoaded(true);
  }

  const saveMutation = useMutation({
    mutationFn: async (body: ShiftRequest) => {
      if (isNew) return shiftApi.create(body);
      return shiftApi.update(id as string, body);
    },
    onSuccess: (saved) => {
      notifications.show({ message: isNew ? 'Shift created' : 'Shift updated', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['shifts'] });
      navigate(`/shifts/${saved.id}`);
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  const attendanceOptions = useMemo(
    () =>
      (timeCodes.data ?? [])
        .filter((t) => t.category !== 'LEAVE')
        .map((t) => ({ value: t.id, label: `${t.code} — ${t.name}` })),
    [timeCodes.data]
  );
  const overtimeOptions = useMemo(
    () =>
      (timeCodes.data ?? [])
        .filter((t) => t.category === 'OVERTIME')
        .map((t) => ({ value: t.id, label: `${t.code} — ${t.name}` })),
    [timeCodes.data]
  );
  const anyTimeCodeOptions = useMemo(
    () => (timeCodes.data ?? []).map((t) => ({ value: t.id, label: `${t.code} — ${t.name}` })),
    [timeCodes.data]
  );
  const candidateOptions = useMemo(
    () =>
      (allShifts.data ?? [])
        .filter((s) => s.id !== id && s.shiftType !== 'FLOATING')
        .map((s) => ({ value: s.id, label: `${s.name} (${s.shiftType})` })),
    [allShifts.data, id]
  );

  function patch(p: Partial<FormState>) {
    setState((prev) => ({ ...prev, ...p }));
  }

  function onSubmit() {
    if (!state.name.trim()) {
      notifications.show({ message: 'Name is required', color: 'red' });
      return;
    }
    if (!state.attendanceTimeCodeId) {
      notifications.show({ message: 'Attendance time code is required', color: 'red' });
      return;
    }
    saveMutation.mutate(toRequest(state, existing.data?.version ?? null));
  }

  return (
    <Stack>
      <Group justify="space-between">
        <Group>
          <ActionIcon variant="subtle" component={Link} to="/shifts">
            <IconArrowLeft size={18} />
          </ActionIcon>
          <Title order={2}>{isNew ? 'New shift' : `Edit shift — ${state.name || ''}`}</Title>
        </Group>
        <Group>
          <Anchor component={Link} to="/shifts">
            Cancel
          </Anchor>
          <Button onClick={onSubmit} loading={saveMutation.isPending} disabled={!canWrite}>
            Save
          </Button>
        </Group>
      </Group>

      <Paper withBorder p="md">
        <Stack>
          <Group grow>
            <TextInput
              label="Name"
              required
              value={state.name}
              onChange={(e) => patch({ name: e.currentTarget.value })}
            />
            <Select
              label="Type"
              required
              data={TYPES}
              value={state.shiftType}
              onChange={(v) => v && patch({ shiftType: v as ShiftType })}
            />
          </Group>
          <Group grow>
            <ColorInput
              label="Color"
              format="hex"
              value={state.color}
              onChange={(v) => patch({ color: v })}
            />
            <TextInput
              label="Timezone (IANA, optional)"
              placeholder="e.g. Asia/Colombo"
              value={state.timezone}
              onChange={(e) => patch({ timezone: e.currentTarget.value })}
            />
            <Select
              label="Attendance time code"
              required
              searchable
              data={attendanceOptions}
              value={state.attendanceTimeCodeId || null}
              onChange={(v) => patch({ attendanceTimeCodeId: v ?? '' })}
            />
          </Group>
          <Textarea
            label="Description"
            autosize
            minRows={2}
            value={state.description}
            onChange={(e) => patch({ description: e.currentTarget.value })}
          />
          <Switch
            label="Active"
            checked={state.active}
            onChange={(e) => patch({ active: e.currentTarget.checked })}
          />

          <SchedulePreview
            segments={state.segments}
            breakRules={state.breakRules}
            color={state.color}
          />
        </Stack>
      </Paper>

      <Tabs defaultValue="segments" keepMounted={false}>
        <Tabs.List>
          <Tabs.Tab value="segments">Segments</Tabs.Tab>
          <Tabs.Tab value="rounding">Rounding & grace</Tabs.Tab>
          <Tabs.Tab value="breaks">Breaks</Tabs.Tab>
          <Tabs.Tab value="overtime">Overtime tiers</Tabs.Tab>
          {state.shiftType === 'FLOATING' && (
            <Tabs.Tab value="candidates">Floating candidates</Tabs.Tab>
          )}
        </Tabs.List>

        <Tabs.Panel value="segments" pt="md">
          <SegmentsEditor state={state} onChange={patch} />
        </Tabs.Panel>
        <Tabs.Panel value="rounding" pt="md">
          <RoundingAndGraceEditor state={state} onChange={patch} />
        </Tabs.Panel>
        <Tabs.Panel value="breaks" pt="md">
          <BreaksEditor state={state} onChange={patch} timeCodeOptions={anyTimeCodeOptions} />
        </Tabs.Panel>
        <Tabs.Panel value="overtime" pt="md">
          <OvertimeEditor state={state} onChange={patch} overtimeOptions={overtimeOptions} />
        </Tabs.Panel>
        {state.shiftType === 'FLOATING' && (
          <Tabs.Panel value="candidates" pt="md">
            <Paper withBorder p="md">
              <Stack>
                <Text fw={500}>Candidate shifts</Text>
                <Text size="sm" c="dimmed">
                  The engine picks the closest matching candidate based on the first check-in time.
                  Only Fixed and Flexible shifts can be candidates.
                </Text>
                <MultiSelect
                  searchable
                  data={candidateOptions}
                  value={state.candidateShiftIds}
                  onChange={(v) => patch({ candidateShiftIds: v })}
                  placeholder="Select candidate shifts"
                />
              </Stack>
            </Paper>
          </Tabs.Panel>
        )}
      </Tabs>
    </Stack>
  );
}

// ----- subforms -----

function SegmentsEditor({
  state,
  onChange
}: {
  state: FormState;
  onChange: (p: Partial<FormState>) => void;
}) {
  function addSegment() {
    const next = [...state.segments];
    const order = next.length === 0 ? 0 : Math.max(...next.map((s) => s.segmentOrder)) + 1;
    next.push({ segmentOrder: order, startMinuteOfDay: 540, endMinuteOfDay: 1020, requiredMinutes: null });
    onChange({ segments: next });
  }

  function updateSegment(i: number, patch: Partial<SegmentRequest>) {
    const next = state.segments.map((s, idx) => (idx === i ? { ...s, ...patch } : s));
    onChange({ segments: next });
  }

  function removeSegment(i: number) {
    onChange({ segments: state.segments.filter((_, idx) => idx !== i) });
  }

  return (
    <Paper withBorder p="md">
      <Stack>
        <Group justify="space-between">
          <Text fw={500}>Segments</Text>
          <Button leftSection={<IconPlus size={14} />} variant="light" onClick={addSegment}>
            Add segment
          </Button>
        </Group>
        <Text size="sm" c="dimmed">
          Times are minutes-of-day in the shift's timezone. Values above 1440 cross midnight.
          {state.shiftType === 'FLEXIBLE' &&
            ' For FLEXIBLE shifts, required minutes is the work expected within each window.'}
        </Text>
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Order</Table.Th>
              <Table.Th>Start (min)</Table.Th>
              <Table.Th>End (min)</Table.Th>
              <Table.Th>Required (min)</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {state.segments.map((seg, i) => (
              <Table.Tr key={i}>
                <Table.Td>
                  <NumberInput
                    value={seg.segmentOrder}
                    onChange={(v) => updateSegment(i, { segmentOrder: Number(v) || 0 })}
                    min={0}
                    w={80}
                  />
                </Table.Td>
                <Table.Td>
                  <NumberInput
                    value={seg.startMinuteOfDay}
                    onChange={(v) =>
                      updateSegment(i, { startMinuteOfDay: Number(v) || 0 })
                    }
                    min={0}
                    max={2880}
                    w={120}
                  />
                </Table.Td>
                <Table.Td>
                  <NumberInput
                    value={seg.endMinuteOfDay}
                    onChange={(v) => updateSegment(i, { endMinuteOfDay: Number(v) || 0 })}
                    min={1}
                    max={2880}
                    w={120}
                  />
                </Table.Td>
                <Table.Td>
                  <NumberInput
                    value={seg.requiredMinutes ?? ''}
                    onChange={(v) =>
                      updateSegment(i, {
                        requiredMinutes: v === '' ? null : Number(v) || 0
                      })
                    }
                    min={0}
                    placeholder={state.shiftType === 'FLEXIBLE' ? 'required' : 'n/a'}
                    w={120}
                  />
                </Table.Td>
                <Table.Td>
                  <ActionIcon color="red" variant="subtle" onClick={() => removeSegment(i)}>
                    <IconTrash size={14} />
                  </ActionIcon>
                </Table.Td>
              </Table.Tr>
            ))}
            {state.segments.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={5}>
                  <Text c="dimmed">No segments yet.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Stack>
    </Paper>
  );
}

function RoundingAndGraceEditor({
  state,
  onChange
}: {
  state: FormState;
  onChange: (p: Partial<FormState>) => void;
}) {
  function addRounding() {
    onChange({
      roundingRules: [
        ...state.roundingRules,
        { kind: 'PUNCH_IN', unitMinutes: 15, mode: 'NEAREST' }
      ]
    });
  }
  function addGrace() {
    onChange({
      graceRules: [...state.graceRules, { kind: 'LATE_IN', minutes: 5 }]
    });
  }
  function updateRounding(i: number, p: Partial<RoundingRuleRequest>) {
    onChange({
      roundingRules: state.roundingRules.map((r, idx) => (idx === i ? { ...r, ...p } : r))
    });
  }
  function updateGrace(i: number, p: Partial<GraceRuleRequest>) {
    onChange({
      graceRules: state.graceRules.map((g, idx) => (idx === i ? { ...g, ...p } : g))
    });
  }

  return (
    <Stack>
      <Paper withBorder p="md">
        <Stack>
          <Group justify="space-between">
            <Text fw={500}>Rounding rules</Text>
            <Button leftSection={<IconPlus size={14} />} variant="light" onClick={addRounding}>
              Add rounding rule
            </Button>
          </Group>
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Kind</Table.Th>
                <Table.Th>Unit (min)</Table.Th>
                <Table.Th>Mode</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {state.roundingRules.map((r, i) => (
                <Table.Tr key={i}>
                  <Table.Td>
                    <Select
                      data={ROUNDING_KINDS}
                      value={r.kind}
                      onChange={(v) => v && updateRounding(i, { kind: v as RoundingKind })}
                      w={160}
                    />
                  </Table.Td>
                  <Table.Td>
                    <NumberInput
                      value={r.unitMinutes}
                      onChange={(v) => updateRounding(i, { unitMinutes: Number(v) || 1 })}
                      min={1}
                      max={120}
                      w={100}
                    />
                  </Table.Td>
                  <Table.Td>
                    <Select
                      data={ROUNDING_MODES}
                      value={r.mode}
                      onChange={(v) => v && updateRounding(i, { mode: v as RoundingMode })}
                      w={140}
                    />
                  </Table.Td>
                  <Table.Td>
                    <ActionIcon
                      color="red"
                      variant="subtle"
                      onClick={() =>
                        onChange({
                          roundingRules: state.roundingRules.filter((_, idx) => idx !== i)
                        })
                      }
                    >
                      <IconTrash size={14} />
                    </ActionIcon>
                  </Table.Td>
                </Table.Tr>
              ))}
              {state.roundingRules.length === 0 && (
                <Table.Tr>
                  <Table.Td colSpan={4}>
                    <Text c="dimmed">No rounding rules.</Text>
                  </Table.Td>
                </Table.Tr>
              )}
            </Table.Tbody>
          </Table>
        </Stack>
      </Paper>

      <Paper withBorder p="md">
        <Stack>
          <Group justify="space-between">
            <Text fw={500}>Grace rules</Text>
            <Button leftSection={<IconPlus size={14} />} variant="light" onClick={addGrace}>
              Add grace rule
            </Button>
          </Group>
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Kind</Table.Th>
                <Table.Th>Minutes</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {state.graceRules.map((g, i) => (
                <Table.Tr key={i}>
                  <Table.Td>
                    <Select
                      data={GRACE_KINDS}
                      value={g.kind}
                      onChange={(v) => v && updateGrace(i, { kind: v as GraceKind })}
                      w={160}
                    />
                  </Table.Td>
                  <Table.Td>
                    <NumberInput
                      value={g.minutes}
                      onChange={(v) => updateGrace(i, { minutes: Number(v) || 0 })}
                      min={0}
                      max={240}
                      w={100}
                    />
                  </Table.Td>
                  <Table.Td>
                    <ActionIcon
                      color="red"
                      variant="subtle"
                      onClick={() =>
                        onChange({
                          graceRules: state.graceRules.filter((_, idx) => idx !== i)
                        })
                      }
                    >
                      <IconTrash size={14} />
                    </ActionIcon>
                  </Table.Td>
                </Table.Tr>
              ))}
              {state.graceRules.length === 0 && (
                <Table.Tr>
                  <Table.Td colSpan={3}>
                    <Text c="dimmed">No grace rules.</Text>
                  </Table.Td>
                </Table.Tr>
              )}
            </Table.Tbody>
          </Table>
        </Stack>
      </Paper>
    </Stack>
  );
}

function BreaksEditor({
  state,
  onChange,
  timeCodeOptions
}: {
  state: FormState;
  onChange: (p: Partial<FormState>) => void;
  timeCodeOptions: { value: string; label: string }[];
}) {
  function addBreak() {
    onChange({
      breakRules: [
        ...state.breakRules,
        {
          name: 'Lunch',
          kind: 'AUTO_DEDUCT',
          durationMinutes: 30,
          earliestStartMinute: 720,
          afterHoursWorked: null,
          paid: false,
          timeCodeId: null
        }
      ]
    });
  }
  function update(i: number, p: Partial<BreakRuleRequest>) {
    onChange({
      breakRules: state.breakRules.map((b, idx) => (idx === i ? { ...b, ...p } : b))
    });
  }
  return (
    <Paper withBorder p="md">
      <Stack>
        <Group justify="space-between">
          <Text fw={500}>Break rules</Text>
          <Button leftSection={<IconPlus size={14} />} variant="light" onClick={addBreak}>
            Add break
          </Button>
        </Group>
        {state.breakRules.length === 0 && <Text c="dimmed">No break rules.</Text>}
        {state.breakRules.map((b, i) => (
          <Box key={i}>
            <Group align="end" grow>
              <TextInput
                label="Name"
                value={b.name}
                onChange={(e) => update(i, { name: e.currentTarget.value })}
              />
              <Select
                label="Kind"
                data={BREAK_KINDS}
                value={b.kind}
                onChange={(v) => v && update(i, { kind: v as BreakKind })}
              />
              <NumberInput
                label="Duration (min)"
                value={b.durationMinutes}
                onChange={(v) => update(i, { durationMinutes: Number(v) || 0 })}
                min={0}
                max={480}
              />
              <NumberInput
                label="Earliest start (min)"
                value={b.earliestStartMinute ?? ''}
                onChange={(v) =>
                  update(i, { earliestStartMinute: v === '' ? null : Number(v) || 0 })
                }
                min={0}
                max={2880}
                placeholder="optional"
              />
              <NumberInput
                label="After minutes worked"
                value={b.afterHoursWorked ?? ''}
                onChange={(v) =>
                  update(i, { afterHoursWorked: v === '' ? null : Number(v) || 0 })
                }
                min={0}
                placeholder="optional"
              />
            </Group>
            <Group mt="xs" grow>
              <Switch
                label="Paid"
                checked={b.paid}
                onChange={(e) => update(i, { paid: e.currentTarget.checked })}
              />
              <Select
                label="Time code (required if paid)"
                searchable
                clearable
                data={timeCodeOptions}
                value={b.timeCodeId}
                onChange={(v) => update(i, { timeCodeId: v })}
              />
              <ActionIcon
                color="red"
                variant="subtle"
                onClick={() =>
                  onChange({
                    breakRules: state.breakRules.filter((_, idx) => idx !== i)
                  })
                }
              >
                <IconTrash size={14} />
              </ActionIcon>
            </Group>
            {i < state.breakRules.length - 1 && <Divider my="md" />}
          </Box>
        ))}
      </Stack>
    </Paper>
  );
}

function OvertimeEditor({
  state,
  onChange,
  overtimeOptions
}: {
  state: FormState;
  onChange: (p: Partial<FormState>) => void;
  overtimeOptions: { value: string; label: string }[];
}) {
  function addTier() {
    const order =
      state.overtimeRules.length === 0
        ? 1
        : Math.max(...state.overtimeRules.map((o) => o.sequenceOrder)) + 1;
    const minAfter =
      state.overtimeRules.length === 0
        ? 480
        : Math.max(...state.overtimeRules.map((o) => o.afterMinutesWorked)) + 60;
    onChange({
      overtimeRules: [
        ...state.overtimeRules,
        {
          sequenceOrder: order,
          afterMinutesWorked: minAfter,
          timeCodeId: overtimeOptions[0]?.value ?? '',
          maxMinutes: null
        }
      ]
    });
  }
  function update(i: number, p: Partial<OvertimeRuleRequest>) {
    onChange({
      overtimeRules: state.overtimeRules.map((o, idx) => (idx === i ? { ...o, ...p } : o))
    });
  }
  return (
    <Paper withBorder p="md">
      <Stack>
        <Group justify="space-between">
          <Text fw={500}>Overtime tiers</Text>
          <Button leftSection={<IconPlus size={14} />} variant="light" onClick={addTier}>
            Add tier
          </Button>
        </Group>
        <Text size="sm" c="dimmed">
          Tiers are applied in <code>sequence_order</code>. <code>after_minutes_worked</code> must
          be strictly increasing.
        </Text>
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Order</Table.Th>
              <Table.Th>After minutes worked</Table.Th>
              <Table.Th>Time code</Table.Th>
              <Table.Th>Max minutes</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {state.overtimeRules.map((o, i) => (
              <Table.Tr key={i}>
                <Table.Td>
                  <NumberInput
                    value={o.sequenceOrder}
                    onChange={(v) => update(i, { sequenceOrder: Number(v) || 0 })}
                    min={0}
                    w={80}
                  />
                </Table.Td>
                <Table.Td>
                  <NumberInput
                    value={o.afterMinutesWorked}
                    onChange={(v) => update(i, { afterMinutesWorked: Number(v) || 0 })}
                    min={0}
                    w={140}
                  />
                </Table.Td>
                <Table.Td>
                  <Select
                    searchable
                    data={overtimeOptions}
                    value={o.timeCodeId}
                    onChange={(v) => v && update(i, { timeCodeId: v })}
                    w={220}
                  />
                </Table.Td>
                <Table.Td>
                  <NumberInput
                    value={o.maxMinutes ?? ''}
                    onChange={(v) =>
                      update(i, { maxMinutes: v === '' ? null : Number(v) || 0 })
                    }
                    min={0}
                    placeholder="no cap"
                    w={140}
                  />
                </Table.Td>
                <Table.Td>
                  <ActionIcon
                    color="red"
                    variant="subtle"
                    onClick={() =>
                      onChange({
                        overtimeRules: state.overtimeRules.filter((_, idx) => idx !== i)
                      })
                    }
                  >
                    <IconTrash size={14} />
                  </ActionIcon>
                </Table.Td>
              </Table.Tr>
            ))}
            {state.overtimeRules.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={5}>
                  <Text c="dimmed">No overtime tiers.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Stack>
    </Paper>
  );
}
