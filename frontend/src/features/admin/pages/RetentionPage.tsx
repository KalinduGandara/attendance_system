import { useEffect, useState } from 'react';
import {
  Button,
  NumberInput,
  Paper,
  Stack,
  Switch,
  Table,
  Text,
  Title
} from '@mantine/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { adminApi } from '../api';
import type { RetentionPolicy } from '../types';
import { describeApiError } from '../../../lib/apiError';

interface Draft {
  retainDays: number;
  enabled: boolean;
}

export function RetentionPage() {
  const queryClient = useQueryClient();
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});

  const query = useQuery({ queryKey: ['retention-policies'], queryFn: adminApi.listRetentionPolicies });

  useEffect(() => {
    if (query.data) {
      setDrafts(
        Object.fromEntries(
          query.data.map((p) => [p.entityType, { retainDays: p.retainDays, enabled: p.enabled }])
        )
      );
    }
  }, [query.data]);

  const save = useMutation({
    mutationFn: ({ entityType, body }: { entityType: string; body: Draft }) =>
      adminApi.updateRetentionPolicy(entityType, body),
    onSuccess: () => {
      notifications.show({ message: 'Retention policy updated', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['retention-policies'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  const policies = query.data ?? [];

  function patch(entityType: string, partial: Partial<Draft>) {
    setDrafts((d) => ({ ...d, [entityType]: { ...d[entityType], ...partial } }));
  }

  function isDirty(p: RetentionPolicy): boolean {
    const d = drafts[p.entityType];
    return !!d && (d.retainDays !== p.retainDays || d.enabled !== p.enabled);
  }

  return (
    <Stack>
      <Title order={2}>Retention policies</Title>
      <Text size="sm" c="dimmed">
        Enabled policies purge rows older than the retention window on the schedule set by{' '}
        <b>retention_cron</b> (when <b>retention_enabled</b> is on). Deleting historical data cannot
        be undone — the audit log is disabled by default for this reason.
      </Text>

      <Paper withBorder p="md">
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Entity</Table.Th>
              <Table.Th style={{ width: 180 }}>Retain days</Table.Th>
              <Table.Th style={{ width: 140 }}>Enabled</Table.Th>
              <Table.Th>Last run</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {policies.map((p) => {
              const d = drafts[p.entityType] ?? { retainDays: p.retainDays, enabled: p.enabled };
              return (
                <Table.Tr key={p.entityType}>
                  <Table.Td>
                    <Text ff="monospace" size="sm">
                      {p.entityType}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <NumberInput
                      min={1}
                      value={d.retainDays}
                      onChange={(v) => patch(p.entityType, { retainDays: Number(v) || 1 })}
                      w={140}
                    />
                  </Table.Td>
                  <Table.Td>
                    <Switch
                      checked={d.enabled}
                      onChange={(e) => patch(p.entityType, { enabled: e.currentTarget.checked })}
                    />
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm" c="dimmed">
                      {p.lastRunAt
                        ? `${new Date(p.lastRunAt).toLocaleString()} · ${p.lastRunDeleted ?? 0} deleted`
                        : 'never'}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Button
                      size="xs"
                      disabled={!isDirty(p)}
                      loading={save.isPending && save.variables?.entityType === p.entityType}
                      onClick={() => save.mutate({ entityType: p.entityType, body: d })}
                    >
                      Save
                    </Button>
                  </Table.Td>
                </Table.Tr>
              );
            })}
            {policies.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={5}>
                  <Text c="dimmed">No retention policies configured.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>
    </Stack>
  );
}
