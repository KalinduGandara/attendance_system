import { useEffect, useMemo, useState } from 'react';
import {
  ActionIcon,
  Badge,
  Button,
  Divider,
  Group,
  MultiSelect,
  Paper,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
  Tooltip
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { IconDownload, IconRefresh, IconTrash } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { reportApi } from '../api';
import type { ReportParameters, ReportStatus, ReportType } from '../types';
import { orgApi } from '../../organization/api';
import { useAuthStore } from '../../../lib/authStore';
import { describeApiError } from '../../../lib/apiError';

interface ReportTypeMeta {
  value: ReportType;
  label: string;
  requiresEmployee: boolean;
  statusOptions?: string[];
}

const REPORT_TYPES: ReportTypeMeta[] = [
  { value: 'DAILY', label: 'Daily (punch-level)', requiresEmployee: false },
  { value: 'DAILY_SUMMARY', label: 'Daily Summary', requiresEmployee: false },
  { value: 'INDIVIDUAL', label: 'Individual (full detail)', requiresEmployee: true },
  { value: 'INDIVIDUAL_SUMMARY', label: 'Individual Summary', requiresEmployee: false },
  {
    value: 'LEAVE',
    label: 'Leave',
    requiresEmployee: false,
    statusOptions: ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED']
  },
  {
    value: 'EXCEPTION',
    label: 'Exception',
    requiresEmployee: false,
    statusOptions: ['OPEN', 'RESOLVED', 'IGNORED']
  },
  { value: 'MODIFIED_PUNCH_LOG', label: 'Modified Punch Log History', requiresEmployee: false }
];

const STATUS_COLORS: Record<ReportStatus, string> = {
  QUEUED: 'gray',
  RUNNING: 'blue',
  DONE: 'green',
  FAILED: 'red',
  CANCELLED: 'gray'
};

function toDateInputString(d: Date | null): string | undefined {
  if (!d) return undefined;
  const y = d.getFullYear();
  const m = (d.getMonth() + 1).toString().padStart(2, '0');
  const day = d.getDate().toString().padStart(2, '0');
  return `${y}-${m}-${day}`;
}

interface Preset {
  name: string;
  reportType: ReportType;
  parameters: ReportParameters;
}

export function ReportsPage() {
  const qc = useQueryClient();
  const userId = useAuthStore((s) => s.user?.id ?? 'anon');
  const presetKey = `report-presets:${userId}`;

  const [reportType, setReportType] = useState<ReportType>('DAILY_SUMMARY');
  const [from, setFrom] = useState<Date | null>(null);
  const [to, setTo] = useState<Date | null>(null);
  const [employeeId, setEmployeeId] = useState<string | null>(null);
  const [employeeSearch, setEmployeeSearch] = useState('');
  const [departmentId, setDepartmentId] = useState<string | null>(null);
  const [groupId, setGroupId] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [customFields, setCustomFields] = useState<string[]>([]);
  const [activeJobId, setActiveJobId] = useState<string | null>(null);
  const [presets, setPresets] = useState<Preset[]>([]);
  const [presetName, setPresetName] = useState('');

  const meta = useMemo(
    () => REPORT_TYPES.find((r) => r.value === reportType) ?? REPORT_TYPES[0],
    [reportType]
  );

  useEffect(() => {
    try {
      setPresets(JSON.parse(localStorage.getItem(presetKey) ?? '[]') as Preset[]);
    } catch {
      setPresets([]);
    }
  }, [presetKey]);

  const employees = useQuery({
    queryKey: ['employees', 'search', employeeSearch],
    queryFn: () => orgApi.searchEmployees({ q: employeeSearch || undefined, size: 30 })
  });
  const departments = useQuery({ queryKey: ['departments'], queryFn: orgApi.listDepartments });
  const groups = useQuery({ queryKey: ['groups'], queryFn: orgApi.listGroups });
  const fields = useQuery({ queryKey: ['custom-fields'], queryFn: orgApi.listCustomFields });

  const recent = useQuery({ queryKey: ['reports'], queryFn: reportApi.listRecent });

  const activeJob = useQuery({
    queryKey: ['report', activeJobId],
    queryFn: () => reportApi.get(activeJobId as string),
    enabled: !!activeJobId,
    refetchInterval: (query) => {
      const s = query.state.data?.status;
      return s === 'QUEUED' || s === 'RUNNING' ? 1500 : false;
    }
  });

  // Refresh the recent list whenever the active job settles.
  useEffect(() => {
    const s = activeJob.data?.status;
    if (s === 'DONE' || s === 'FAILED' || s === 'CANCELLED') {
      qc.invalidateQueries({ queryKey: ['reports'] });
    }
  }, [activeJob.data?.status, qc]);

  const buildParameters = (): ReportParameters => ({
    from: toDateInputString(from) ?? null,
    to: toDateInputString(to) ?? null,
    employeeId: employeeId || null,
    departmentId: departmentId || null,
    groupId: groupId || null,
    status: meta.statusOptions ? status || null : null,
    includeCustomFields: customFields
  });

  const run = useMutation({
    mutationFn: () => reportApi.run({ reportType, parameters: buildParameters() }),
    onSuccess: (job) => {
      setActiveJobId(job.id);
      notifications.show({ message: 'Report queued', color: 'blue' });
      qc.invalidateQueries({ queryKey: ['reports'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  function onRun() {
    if (!from || !to) {
      notifications.show({ message: 'From and To dates are required', color: 'red' });
      return;
    }
    if (meta.requiresEmployee && !employeeId) {
      notifications.show({ message: 'This report requires an employee', color: 'red' });
      return;
    }
    run.mutate();
  }

  function persistPresets(next: Preset[]) {
    setPresets(next);
    localStorage.setItem(presetKey, JSON.stringify(next));
  }

  function savePreset() {
    const name = presetName.trim();
    if (!name) {
      notifications.show({ message: 'Preset name is required', color: 'red' });
      return;
    }
    const next = [
      ...presets.filter((p) => p.name !== name),
      { name, reportType, parameters: buildParameters() }
    ];
    persistPresets(next);
    setPresetName('');
    notifications.show({ message: `Saved preset "${name}"`, color: 'green' });
  }

  function loadPreset(name: string | null) {
    const p = presets.find((x) => x.name === name);
    if (!p) return;
    setReportType(p.reportType);
    setFrom(p.parameters.from ? new Date(p.parameters.from) : null);
    setTo(p.parameters.to ? new Date(p.parameters.to) : null);
    setEmployeeId(p.parameters.employeeId ?? null);
    setDepartmentId(p.parameters.departmentId ?? null);
    setGroupId(p.parameters.groupId ?? null);
    setStatus(p.parameters.status ?? null);
    setCustomFields(p.parameters.includeCustomFields ?? []);
  }

  function deletePreset(name: string) {
    persistPresets(presets.filter((p) => p.name !== name));
  }

  return (
    <Stack>
      <Title order={2}>Reports</Title>

      <Paper withBorder p="md">
        <Stack>
          <Group grow align="flex-end">
            <Select
              label="Report type"
              data={REPORT_TYPES.map((r) => ({ value: r.value, label: r.label }))}
              value={reportType}
              onChange={(v) => v && setReportType(v as ReportType)}
              allowDeselect={false}
            />
            <DateInput label="From" value={from} onChange={setFrom} valueFormat="YYYY-MM-DD" withAsterisk />
            <DateInput label="To" value={to} onChange={setTo} valueFormat="YYYY-MM-DD" withAsterisk />
          </Group>

          <Group grow align="flex-end">
            <Select
              label="Employee"
              placeholder={meta.requiresEmployee ? 'Required' : 'All employees'}
              data={(employees.data?.items ?? []).map((e) => ({
                value: e.id,
                label: `${e.firstName} ${e.lastName} (${e.employeeCode})`
              }))}
              value={employeeId}
              onChange={setEmployeeId}
              searchable
              searchValue={employeeSearch}
              onSearchChange={setEmployeeSearch}
              clearable
              nothingFoundMessage={employees.isFetching ? 'Searching…' : 'No matches'}
              withAsterisk={meta.requiresEmployee}
            />
            <Select
              label="Department"
              placeholder="Any"
              data={(departments.data ?? []).map((d) => ({ value: d.id, label: d.name }))}
              value={departmentId}
              onChange={setDepartmentId}
              clearable
              disabled={!!employeeId}
            />
            <Select
              label="Group"
              placeholder="Any"
              data={(groups.data ?? []).map((g) => ({ value: g.id, label: g.name }))}
              value={groupId}
              onChange={setGroupId}
              clearable
              disabled={!!employeeId}
            />
          </Group>

          <Group grow align="flex-end">
            {meta.statusOptions && (
              <Select
                label="Status filter"
                placeholder="Any"
                data={meta.statusOptions}
                value={status}
                onChange={setStatus}
                clearable
              />
            )}
            <MultiSelect
              label="Include custom fields"
              placeholder="None"
              data={(fields.data ?? []).map((f) => ({ value: f.fieldKey, label: f.displayLabel }))}
              value={customFields}
              onChange={setCustomFields}
              clearable
              searchable
            />
          </Group>

          <Group justify="space-between">
            <Group gap="xs">
              <Select
                placeholder="Load preset…"
                data={presets.map((p) => ({ value: p.name, label: p.name }))}
                onChange={loadPreset}
                value={null}
                clearable
                style={{ width: 180 }}
                nothingFoundMessage="No presets"
              />
              <TextInput
                placeholder="Preset name"
                value={presetName}
                onChange={(e) => setPresetName(e.currentTarget.value)}
                style={{ width: 160 }}
              />
              <Button variant="default" onClick={savePreset}>
                Save preset
              </Button>
            </Group>
            <Button onClick={onRun} loading={run.isPending}>
              Run report
            </Button>
          </Group>

          {presets.length > 0 && (
            <Group gap="xs">
              {presets.map((p) => (
                <Badge
                  key={p.name}
                  variant="outline"
                  rightSection={
                    <ActionIcon size="xs" variant="transparent" onClick={() => deletePreset(p.name)}>
                      <IconTrash size={12} />
                    </ActionIcon>
                  }
                >
                  {p.name}
                </Badge>
              ))}
            </Group>
          )}
        </Stack>
      </Paper>

      {activeJob.data && (
        <Paper withBorder p="md">
          <Group justify="space-between">
            <Group>
              <Text fw={600}>Current job</Text>
              <Badge color={STATUS_COLORS[activeJob.data.status]} variant="light">
                {activeJob.data.status}
              </Badge>
              {activeJob.data.rowCount != null && (
                <Text size="sm" c="dimmed">
                  {activeJob.data.rowCount} rows
                </Text>
              )}
              {activeJob.data.errorMessage && (
                <Text size="sm" c="red">
                  {activeJob.data.errorMessage}
                </Text>
              )}
            </Group>
            {activeJob.data.status === 'DONE' && (
              <Button
                leftSection={<IconDownload size={16} />}
                onClick={() => reportApi.download(activeJob.data!.id)}
              >
                Download CSV
              </Button>
            )}
          </Group>
        </Paper>
      )}

      <Divider label="Recent reports" />

      <Paper withBorder p="md">
        <Group justify="flex-end" mb="sm">
          <Tooltip label="Refresh">
            <ActionIcon variant="default" onClick={() => recent.refetch()}>
              <IconRefresh size={16} />
            </ActionIcon>
          </Tooltip>
        </Group>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Type</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th>Rows</Table.Th>
              <Table.Th>Created</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {recent.data?.map((j) => (
              <Table.Tr key={j.id}>
                <Table.Td>{j.reportType}</Table.Td>
                <Table.Td>
                  <Badge color={STATUS_COLORS[j.status]} variant="light">
                    {j.status}
                  </Badge>
                </Table.Td>
                <Table.Td>{j.rowCount ?? '—'}</Table.Td>
                <Table.Td>{new Date(j.createdAt).toLocaleString()}</Table.Td>
                <Table.Td>
                  {j.status === 'DONE' && (
                    <Button
                      size="xs"
                      variant="light"
                      leftSection={<IconDownload size={14} />}
                      onClick={() => reportApi.download(j.id)}
                    >
                      Download
                    </Button>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
            {recent.data && recent.data.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={5}>
                  <Text c="dimmed">No reports yet.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>
    </Stack>
  );
}
