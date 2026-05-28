import { useState } from 'react';
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
  Title,
  Tooltip
} from '@mantine/core';
import { IconLink } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { timecardApi } from '../api';
import { PUNCH_STATUS_COLORS, PUNCH_TYPE_LABELS, fmtInstantLocal } from '../format';
import type { PunchEvent } from '../types';
import { orgApi } from '../../organization/api';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

export function UnresolvedPunchesPage() {
  const queryClient = useQueryClient();
  const hasPermission = useAuthStore((s) => s.hasPermission);
  const canEdit = hasPermission('timecard.edit');
  const [assignTarget, setAssignTarget] = useState<PunchEvent | null>(null);

  const punches = useQuery({
    queryKey: ['punch-events', 'unresolved'],
    queryFn: () =>
      timecardApi.listPunches({ status: 'UNRESOLVED', size: 100 })
  });

  return (
    <Stack>
      <Title order={2}>Unresolved punches</Title>
      <Text c="dimmed" size="sm">
        Punches that arrived without a matching credential. Assign each to the right employee — a
        recompute fires automatically once you do.
      </Text>

      <Paper withBorder p="md">
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>When</Table.Th>
              <Table.Th>Type</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th>External ID</Table.Th>
              <Table.Th>Credential hash</Table.Th>
              {canEdit && <Table.Th />}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {punches.data?.map((p) => (
              <Table.Tr key={p.id}>
                <Table.Td>{fmtInstantLocal(p.eventTimeUtc)}</Table.Td>
                <Table.Td>
                  <Badge variant="light">{PUNCH_TYPE_LABELS[p.eventType]}</Badge>
                </Table.Td>
                <Table.Td>
                  <Badge color={PUNCH_STATUS_COLORS[p.status]} variant="light">
                    {p.status}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  <Text size="xs" ff="monospace">
                    {p.externalEventId}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="xs" c="dimmed" ff="monospace">
                    {p.id.slice(0, 8)}…
                  </Text>
                </Table.Td>
                {canEdit && (
                  <Table.Td>
                    <Tooltip label="Assign to employee">
                      <ActionIcon variant="subtle" onClick={() => setAssignTarget(p)}>
                        <IconLink size={14} />
                      </ActionIcon>
                    </Tooltip>
                  </Table.Td>
                )}
              </Table.Tr>
            ))}
            {punches.data && punches.data.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={canEdit ? 6 : 5}>
                  <Text c="dimmed">No unresolved punches. 🎉</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>

      <AssignToEmployeeModal
        punch={assignTarget}
        onClose={() => setAssignTarget(null)}
        onSuccess={() => {
          queryClient.invalidateQueries({ queryKey: ['punch-events'] });
          queryClient.invalidateQueries({ queryKey: ['timecards'] });
        }}
      />
    </Stack>
  );
}

interface AssignProps {
  punch: PunchEvent | null;
  onClose: () => void;
  onSuccess: () => void;
}

function AssignToEmployeeModal({ punch, onClose, onSuccess }: AssignProps) {
  const [query, setQuery] = useState('');
  const [employeeId, setEmployeeId] = useState<string | null>(null);

  const search = useQuery({
    queryKey: ['employees', 'search', query],
    queryFn: () => orgApi.searchEmployees({ q: query || undefined, size: 20 }),
    enabled: punch !== null
  });

  const mutation = useMutation({
    mutationFn: async () => {
      if (!punch || !employeeId) return null;
      return timecardApi.assignPunch(punch.id, employeeId);
    },
    onSuccess: () => {
      notifications.show({ message: 'Punch assigned and recompute queued', color: 'green' });
      onSuccess();
      onClose();
      setEmployeeId(null);
      setQuery('');
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  const options =
    search.data?.items.map((e) => ({
      value: e.id,
      label: `${e.firstName} ${e.lastName} (${e.employeeCode})`
    })) ?? [];

  return (
    <Modal opened={punch !== null} onClose={onClose} title="Assign unresolved punch">
      <Stack>
        {punch && (
          <Text size="sm" c="dimmed">
            {PUNCH_TYPE_LABELS[punch.eventType]} at {fmtInstantLocal(punch.eventTimeUtc)}
          </Text>
        )}
        <Select
          label="Employee"
          placeholder="Search by name or code"
          data={options}
          value={employeeId}
          onChange={setEmployeeId}
          searchable
          searchValue={query}
          onSearchChange={setQuery}
          required
          withAsterisk
          nothingFoundMessage={search.isFetching ? 'Searching…' : 'No employees found'}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button
            onClick={() => mutation.mutate()}
            disabled={!employeeId}
            loading={mutation.isPending}
          >
            Assign
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
