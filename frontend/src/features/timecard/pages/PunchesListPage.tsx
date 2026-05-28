import { useState } from 'react';
import { Badge, Group, Paper, Select, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { timecardApi } from '../api';
import type { PunchEventStatus } from '../types';

const STATUS_COLORS: Record<PunchEventStatus, string> = {
  PROCESSED: 'green',
  UNRESOLVED: 'orange',
  INVALID: 'red',
  SUPERSEDED: 'gray'
};

interface PunchesListPageProps {
  defaultStatus?: PunchEventStatus;
  title?: string;
}

export function PunchesListPage({ defaultStatus, title = 'Punches' }: PunchesListPageProps) {
  const [employeeId, setEmployeeId] = useState('');
  const [status, setStatus] = useState<string | null>(defaultStatus ?? null);

  const punches = useQuery({
    queryKey: ['punch-events', employeeId, status],
    queryFn: () =>
      timecardApi.listPunches({
        employeeId: employeeId || undefined,
        status: (status as PunchEventStatus) || undefined,
        size: 100
      })
  });

  return (
    <Stack>
      <Title order={2}>{title}</Title>

      <Paper withBorder p="md">
        <Group mb="sm">
          <TextInput
            label="Employee ID"
            placeholder="Optional"
            value={employeeId}
            onChange={(e) => setEmployeeId(e.currentTarget.value)}
            style={{ flex: 1 }}
          />
          <Select
            label="Status"
            data={['PROCESSED', 'UNRESOLVED', 'INVALID', 'SUPERSEDED']}
            value={status}
            onChange={setStatus}
            clearable
            style={{ width: 180 }}
          />
        </Group>

        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>When (UTC)</Table.Th>
              <Table.Th>Type</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th>Employee ID</Table.Th>
              <Table.Th>External ID</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {punches.data?.map((p) => (
              <Table.Tr key={p.id}>
                <Table.Td>{p.eventTimeUtc}</Table.Td>
                <Table.Td>
                  <Badge variant="light">{p.eventType}</Badge>
                </Table.Td>
                <Table.Td>
                  <Badge color={STATUS_COLORS[p.status]} variant="light">
                    {p.status}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  <Text size="xs" c="dimmed" ff="monospace">
                    {p.employeeId ?? '—'}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="xs" ff="monospace">
                    {p.externalEventId}
                  </Text>
                </Table.Td>
              </Table.Tr>
            ))}
            {punches.data && punches.data.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={5}>
                  <Text c="dimmed">No punches.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>
    </Stack>
  );
}
