import { useState } from 'react';
import {
  Badge,
  Button,
  Code,
  Group,
  Modal,
  Pagination,
  Paper,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Title
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useQuery } from '@tanstack/react-query';
import { IconSearch } from '@tabler/icons-react';
import { adminApi } from '../api';
import type { AuditEvent } from '../types';

const ACTIONS = ['CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT', 'EXPORT'];

function toDateInputString(d: Date | null): string | undefined {
  if (!d) return undefined;
  const y = d.getFullYear();
  const m = (d.getMonth() + 1).toString().padStart(2, '0');
  const day = d.getDate().toString().padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function actionColor(action: string): string {
  switch (action) {
    case 'CREATE':
      return 'green';
    case 'UPDATE':
      return 'blue';
    case 'DELETE':
      return 'red';
    default:
      return 'gray';
  }
}

function prettyJson(raw: string | null): string {
  if (!raw) return '—';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

export function AuditLogPage() {
  const [page, setPage] = useState(0);
  const [action, setAction] = useState<string | null>(null);
  const [entityType, setEntityType] = useState('');
  const [actorUserId, setActorUserId] = useState('');
  const [from, setFrom] = useState<Date | null>(null);
  const [to, setTo] = useState<Date | null>(null);
  const [selected, setSelected] = useState<AuditEvent | null>(null);

  const query = useQuery({
    queryKey: [
      'audit-events',
      { page, action, entityType, actorUserId, from: from?.toISOString(), to: to?.toISOString() }
    ],
    queryFn: () =>
      adminApi.searchAudit({
        page,
        size: 25,
        action: action ?? undefined,
        entityType: entityType.trim() || undefined,
        actorUserId: actorUserId.trim() || undefined,
        from: toDateInputString(from),
        to: toDateInputString(to)
      })
  });

  function resetToFirstPage<T>(setter: (v: T) => void) {
    return (v: T) => {
      setter(v);
      setPage(0);
    };
  }

  return (
    <Stack>
      <Title order={2}>Audit log</Title>

      <Paper withBorder p="md">
        <Group mb="sm" align="flex-end">
          <Select
            label="Action"
            placeholder="Any"
            clearable
            data={ACTIONS}
            value={action}
            onChange={resetToFirstPage(setAction)}
            w={140}
          />
          <TextInput
            label="Entity type"
            placeholder="e.g. Employee"
            value={entityType}
            onChange={(e) => resetToFirstPage(setEntityType)(e.currentTarget.value)}
            w={180}
          />
          <TextInput
            label="Actor user id"
            placeholder="UUID"
            value={actorUserId}
            onChange={(e) => resetToFirstPage(setActorUserId)(e.currentTarget.value)}
            w={260}
          />
          <DateInput
            label="From"
            placeholder="Start date"
            clearable
            valueFormat="YYYY-MM-DD"
            value={from}
            onChange={resetToFirstPage(setFrom)}
            w={150}
          />
          <DateInput
            label="To"
            placeholder="End date"
            clearable
            valueFormat="YYYY-MM-DD"
            value={to}
            onChange={resetToFirstPage(setTo)}
            w={150}
          />
          <Button
            leftSection={<IconSearch size={16} />}
            variant="default"
            onClick={() => query.refetch()}
          >
            Refresh
          </Button>
        </Group>

        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>When</Table.Th>
              <Table.Th>Actor</Table.Th>
              <Table.Th>Action</Table.Th>
              <Table.Th>Entity</Table.Th>
              <Table.Th>IP</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {query.data?.items.map((e) => (
              <Table.Tr key={e.id}>
                <Table.Td>{new Date(e.occurredAt).toLocaleString()}</Table.Td>
                <Table.Td>{e.actorUsername}</Table.Td>
                <Table.Td>
                  <Badge color={actionColor(e.action)} variant="light">
                    {e.action}
                  </Badge>
                </Table.Td>
                <Table.Td>{e.entityType ?? '—'}</Table.Td>
                <Table.Td>{e.ip ?? '—'}</Table.Td>
                <Table.Td>
                  <Button size="xs" variant="subtle" onClick={() => setSelected(e)}>
                    Details
                  </Button>
                </Table.Td>
              </Table.Tr>
            ))}
            {query.data && query.data.items.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={6}>
                  <Text c="dimmed">No audit events match these filters.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>

        {query.data && query.data.totalPages > 1 && (
          <Group justify="flex-end" mt="md">
            <Pagination
              total={query.data.totalPages}
              value={page + 1}
              onChange={(v) => setPage(v - 1)}
            />
          </Group>
        )}
      </Paper>

      <Modal
        opened={selected !== null}
        onClose={() => setSelected(null)}
        title="Audit event detail"
        size="lg"
      >
        {selected && (
          <Stack gap="sm">
            <Text size="sm">
              <b>{selected.action}</b> on {selected.entityType ?? '—'}{' '}
              {selected.entityId ? `(${selected.entityId})` : ''} by{' '}
              <b>{selected.actorUsername}</b> at {new Date(selected.occurredAt).toLocaleString()}
            </Text>
            <Text size="sm" c="dimmed">
              IP {selected.ip ?? '—'} · request {selected.requestId ?? '—'}
            </Text>
            <div>
              <Text size="sm" fw={600}>
                Before
              </Text>
              <Code block>{prettyJson(selected.beforeJson)}</Code>
            </div>
            <div>
              <Text size="sm" fw={600}>
                After
              </Text>
              <Code block>{prettyJson(selected.afterJson)}</Code>
            </div>
          </Stack>
        )}
      </Modal>
    </Stack>
  );
}
