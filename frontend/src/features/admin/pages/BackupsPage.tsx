import {
  Badge,
  Button,
  Group,
  Paper,
  Stack,
  Table,
  Text,
  Title
} from '@mantine/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { IconDatabaseExport, IconDownload } from '@tabler/icons-react';
import { adminApi } from '../api';
import type { BackupJob, BackupStatus } from '../types';
import { describeApiError } from '../../../lib/apiError';

const STATUS_COLORS: Record<BackupStatus, string> = {
  RUNNING: 'blue',
  DONE: 'green',
  FAILED: 'red'
};

function formatBytes(bytes: number | null): string {
  if (bytes == null) return '—';
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let value = bytes / 1024;
  let i = 0;
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024;
    i += 1;
  }
  return `${value.toFixed(1)} ${units[i]}`;
}

export function BackupsPage() {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ['backups'],
    queryFn: adminApi.listBackups,
    // Poll while any backup is still running so the table updates live.
    refetchInterval: (q) =>
      (q.state.data ?? []).some((j: BackupJob) => j.status === 'RUNNING') ? 2000 : false
  });

  const run = useMutation({
    mutationFn: adminApi.runBackup,
    onSuccess: () => {
      notifications.show({ message: 'Backup started', color: 'blue' });
      queryClient.invalidateQueries({ queryKey: ['backups'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  const download = useMutation({
    mutationFn: adminApi.downloadBackup,
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Backups</Title>
        <Button
          leftSection={<IconDatabaseExport size={16} />}
          onClick={() => run.mutate()}
          loading={run.isPending}
        >
          Run now
        </Button>
      </Group>

      <Text size="sm" c="dimmed">
        Backups run on the schedule configured by <b>backup_cron</b> when <b>backup_enabled</b> is
        on (see System settings), plus manual runs here.
      </Text>

      <Paper withBorder p="md">
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Started</Table.Th>
              <Table.Th>Trigger</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th>Size</Table.Th>
              <Table.Th>Detail</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {query.data?.map((j) => (
              <Table.Tr key={j.id}>
                <Table.Td>{j.startedAt ? new Date(j.startedAt).toLocaleString() : '—'}</Table.Td>
                <Table.Td>
                  <Badge variant="light" color={j.triggerType === 'MANUAL' ? 'grape' : 'gray'}>
                    {j.triggerType}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  <Badge color={STATUS_COLORS[j.status]} variant="light">
                    {j.status}
                  </Badge>
                </Table.Td>
                <Table.Td>{formatBytes(j.sizeBytes)}</Table.Td>
                <Table.Td>
                  {j.status === 'FAILED' ? (
                    <Text size="sm" c="red">
                      {j.errorMessage ?? 'Failed'}
                    </Text>
                  ) : (
                    <Text size="sm" c="dimmed">
                      {j.completedAt ? `done ${new Date(j.completedAt).toLocaleTimeString()}` : '—'}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>
                  {j.status === 'DONE' && (
                    <Button
                      size="xs"
                      variant="subtle"
                      leftSection={<IconDownload size={14} />}
                      onClick={() => download.mutate(j.id)}
                    >
                      Download
                    </Button>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
            {query.data && query.data.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={6}>
                  <Text c="dimmed">No backups yet.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>
    </Stack>
  );
}
