import { useState } from 'react';
import { Badge, Group, Paper, Select, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useQuery } from '@tanstack/react-query';
import { timecardApi } from '../api';
import type { DailyTimeCardStatus } from '../types';

const STATUS_COLORS: Record<DailyTimeCardStatus, string> = {
  PRESENT: 'green',
  ABSENT: 'red',
  LEAVE: 'blue',
  HOLIDAY: 'teal',
  OFF: 'gray',
  PARTIAL: 'yellow'
};

function fmtMinutes(m: number) {
  const hours = Math.floor(m / 60);
  const minutes = m % 60;
  return `${hours}h${minutes.toString().padStart(2, '0')}`;
}

export function TimeCardsListPage() {
  const [employeeId, setEmployeeId] = useState<string>('');
  const [status, setStatus] = useState<string | null>(null);
  const [from, setFrom] = useState<Date | null>(null);
  const [to, setTo] = useState<Date | null>(null);

  const cards = useQuery({
    queryKey: ['timecards', employeeId, status, from?.toISOString(), to?.toISOString()],
    queryFn: () =>
      timecardApi.listTimeCards({
        employeeId: employeeId || undefined,
        status: status || undefined,
        from: from ? from.toISOString().slice(0, 10) : undefined,
        to: to ? to.toISOString().slice(0, 10) : undefined
      })
  });

  return (
    <Stack>
      <Title order={2}>Time cards</Title>

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
            data={['PRESENT', 'ABSENT', 'LEAVE', 'HOLIDAY', 'OFF', 'PARTIAL']}
            value={status}
            onChange={setStatus}
            clearable
            style={{ width: 160 }}
          />
          <DateInput label="From" value={from} onChange={setFrom} clearable style={{ width: 160 }} />
          <DateInput label="To" value={to} onChange={setTo} clearable style={{ width: 160 }} />
        </Group>

        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Date</Table.Th>
              <Table.Th>Employee</Table.Th>
              <Table.Th>Shift</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th>Worked</Table.Th>
              <Table.Th>Break</Table.Th>
              <Table.Th>OT</Table.Th>
              <Table.Th>Late</Table.Th>
              <Table.Th>Early out</Table.Th>
              <Table.Th>Exceptions</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {cards.data?.map((c) => (
              <Table.Tr key={c.id}>
                <Table.Td>{c.workDate}</Table.Td>
                <Table.Td>{c.employee?.name ?? '—'}</Table.Td>
                <Table.Td>
                  {c.resolvedShift ? (
                    <Badge color={c.resolvedShift.color.replace('#', '') ? undefined : 'gray'}>
                      {c.resolvedShift.name}
                    </Badge>
                  ) : (
                    '—'
                  )}
                </Table.Td>
                <Table.Td>
                  <Badge color={STATUS_COLORS[c.status]} variant="light">
                    {c.status}
                  </Badge>
                </Table.Td>
                <Table.Td>{fmtMinutes(c.workedMinutes)}</Table.Td>
                <Table.Td>{fmtMinutes(c.breakMinutes)}</Table.Td>
                <Table.Td>{c.overtimeMinutes > 0 ? fmtMinutes(c.overtimeMinutes) : '—'}</Table.Td>
                <Table.Td>{c.lateMinutes > 0 ? `${c.lateMinutes}m` : '—'}</Table.Td>
                <Table.Td>{c.earlyOutMinutes > 0 ? `${c.earlyOutMinutes}m` : '—'}</Table.Td>
                <Table.Td>
                  {c.exceptions.length === 0 ? (
                    '—'
                  ) : (
                    <Group gap={4}>
                      {c.exceptions.map((ex) => (
                        <Badge key={ex.id} color="orange" variant="outline" size="sm">
                          {ex.type}
                        </Badge>
                      ))}
                    </Group>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
            {cards.data && cards.data.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={10}>
                  <Text c="dimmed">No time cards in this window.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>
    </Stack>
  );
}
