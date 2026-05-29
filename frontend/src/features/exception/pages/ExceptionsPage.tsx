import { useState } from 'react';
import {
  Badge,
  Button,
  Checkbox,
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
import { useNavigate } from 'react-router-dom';
import { exceptionApi } from '../api';
import type { ExceptionEvent, ExceptionSeverity, ExceptionStatus } from '../types';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

const STATUS_COLORS: Record<ExceptionStatus, string> = {
  OPEN: 'orange',
  RESOLVED: 'green',
  IGNORED: 'gray'
};

const SEVERITY_COLORS: Record<ExceptionSeverity, string> = {
  INFO: 'blue',
  WARN: 'orange',
  CRITICAL: 'red'
};

function toDateInputString(d: Date | null): string | undefined {
  if (!d) return undefined;
  const y = d.getFullYear();
  const m = (d.getMonth() + 1).toString().padStart(2, '0');
  const day = d.getDate().toString().padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function ExceptionsPage() {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const hasPermission = useAuthStore((s) => s.hasPermission);
  const canResolve = hasPermission('exception.resolve');
  const [status, setStatus] = useState<string | null>('OPEN');
  const [from, setFrom] = useState<Date | null>(null);
  const [to, setTo] = useState<Date | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [resolveTarget, setResolveTarget] = useState<'bulk' | ExceptionEvent | null>(null);
  const [resolutionNote, setResolutionNote] = useState('');
  const [resolveStatus, setResolveStatus] = useState<'RESOLVED' | 'IGNORED'>('RESOLVED');

  const exceptions = useQuery({
    queryKey: ['exceptions', status, from?.toISOString(), to?.toISOString()],
    queryFn: () =>
      exceptionApi.list({
        status: (status as ExceptionStatus) || undefined,
        from: toDateInputString(from),
        to: toDateInputString(to)
      })
  });

  const resolve = useMutation({
    mutationFn: async (ids: string[]) => {
      const results = [];
      for (const id of ids) {
        const res = await exceptionApi.resolve(id, {
          status: resolveStatus,
          resolutionNote: resolutionNote || null
        });
        results.push(res);
      }
      return results;
    },
    onSuccess: (results) => {
      notifications.show({
        message: `${results.length} exception(s) ${resolveStatus.toLowerCase()}`,
        color: 'green'
      });
      qc.invalidateQueries({ queryKey: ['exceptions'] });
      qc.invalidateQueries({ queryKey: ['timecards'] });
      setResolveTarget(null);
      setResolutionNote('');
      setSelected(new Set());
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  function toggle(id: string) {
    const next = new Set(selected);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setSelected(next);
  }

  const visibleIds = exceptions.data?.map((e) => e.id) ?? [];
  const allSelected = visibleIds.length > 0 && visibleIds.every((id) => selected.has(id));

  return (
    <Stack>
      <Title order={2}>Exceptions</Title>

      <Paper withBorder p="md">
        <Group mb="sm" align="flex-end">
          <Select
            label="Status"
            data={['OPEN', 'RESOLVED', 'IGNORED']}
            value={status}
            onChange={setStatus}
            clearable
            style={{ width: 160 }}
          />
          <DateInput label="From" value={from} onChange={setFrom} clearable style={{ width: 160 }} />
          <DateInput label="To" value={to} onChange={setTo} clearable style={{ width: 160 }} />
          {canResolve && selected.size > 0 && (
            <Button
              onClick={() => setResolveTarget('bulk')}
              ml="auto"
              data-testid="bulk-resolve"
            >
              Resolve {selected.size}
            </Button>
          )}
        </Group>

        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              {canResolve && (
                <Table.Th style={{ width: 40 }}>
                  <Checkbox
                    checked={allSelected}
                    indeterminate={selected.size > 0 && !allSelected}
                    onChange={() => {
                      if (allSelected) {
                        setSelected(new Set());
                      } else {
                        setSelected(new Set(visibleIds));
                      }
                    }}
                  />
                </Table.Th>
              )}
              <Table.Th>Date</Table.Th>
              <Table.Th>Employee</Table.Th>
              <Table.Th>Type</Table.Th>
              <Table.Th>Severity</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th>Resolution</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {exceptions.data?.map((e) => (
              <Table.Tr key={e.id}>
                {canResolve && (
                  <Table.Td>
                    {e.status === 'OPEN' && (
                      <Checkbox
                        checked={selected.has(e.id)}
                        onChange={() => toggle(e.id)}
                      />
                    )}
                  </Table.Td>
                )}
                <Table.Td>{e.workDate}</Table.Td>
                <Table.Td>{e.employeeName ?? e.employeeId.slice(0, 8)}</Table.Td>
                <Table.Td>{e.exceptionType.replace(/_/g, ' ')}</Table.Td>
                <Table.Td>
                  <Badge color={SEVERITY_COLORS[e.severity]} variant="light">
                    {e.severity}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  <Badge color={STATUS_COLORS[e.status]} variant="light">
                    {e.status}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  <Text size="xs" c="dimmed">
                    {e.resolutionNote ?? '—'}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Group gap="xs">
                    {e.dailyTimeCardId && (
                      <Button
                        size="xs"
                        variant="subtle"
                        onClick={() => navigate(`/timecards?open=${e.dailyTimeCardId}`)}
                      >
                        Open time card
                      </Button>
                    )}
                    {canResolve && e.status === 'OPEN' && (
                      <Button
                        size="xs"
                        variant="outline"
                        onClick={() => setResolveTarget(e)}
                      >
                        Resolve
                      </Button>
                    )}
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
            {exceptions.data && exceptions.data.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={canResolve ? 8 : 7}>
                  <Text c="dimmed">No exceptions match these filters.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>

      <Modal
        opened={resolveTarget !== null}
        onClose={() => setResolveTarget(null)}
        title={
          resolveTarget === 'bulk'
            ? `Resolve ${selected.size} exceptions`
            : 'Resolve exception'
        }
      >
        <Stack>
          <Select
            label="Outcome"
            data={[
              { value: 'RESOLVED', label: 'Resolved' },
              { value: 'IGNORED', label: 'Ignored' }
            ]}
            value={resolveStatus}
            onChange={(v) => setResolveStatus((v as 'RESOLVED' | 'IGNORED') ?? 'RESOLVED')}
            allowDeselect={false}
          />
          <Textarea
            label="Note"
            description="Optional but recommended — appended to the audit trail."
            value={resolutionNote}
            onChange={(e) => setResolutionNote(e.currentTarget.value)}
            autosize
            minRows={2}
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setResolveTarget(null)}>
              Cancel
            </Button>
            <Button
              loading={resolve.isPending}
              onClick={() => {
                const ids =
                  resolveTarget === 'bulk'
                    ? Array.from(selected)
                    : resolveTarget
                      ? [resolveTarget.id]
                      : [];
                resolve.mutate(ids);
              }}
            >
              Apply
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}
