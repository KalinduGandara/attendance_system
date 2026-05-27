import { useState } from 'react';
import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Paper,
  Select,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
  Title
} from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { IconEdit, IconPlus, IconTrash } from '@tabler/icons-react';
import { Link, useNavigate } from 'react-router-dom';
import { shiftApi } from '../api';
import type { ShiftType } from '../types';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

const TYPES: { value: ShiftType; label: string }[] = [
  { value: 'FIXED', label: 'Fixed' },
  { value: 'FLEXIBLE', label: 'Flexible' },
  { value: 'FLOATING', label: 'Floating' }
];

const TYPE_COLORS: Record<ShiftType, string> = {
  FIXED: 'blue',
  FLEXIBLE: 'teal',
  FLOATING: 'orange'
};

export function ShiftsListPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const canWrite = useAuthStore((s) => s.hasPermission('shift.write'));
  const [search, setSearch] = useState('');
  const [debounced] = useDebouncedValue(search, 300);
  const [type, setType] = useState<ShiftType | null>(null);
  const [activeOnly, setActiveOnly] = useState(false);

  const shifts = useQuery({
    queryKey: ['shifts', { debounced, type, activeOnly }],
    queryFn: () =>
      shiftApi.list({
        q: debounced || undefined,
        type: type ?? undefined,
        active: activeOnly || undefined
      })
  });

  const deleteMutation = useMutation({
    mutationFn: shiftApi.remove,
    onSuccess: () => {
      notifications.show({ message: 'Shift deleted', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['shifts'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Shifts</Title>
        {canWrite && (
          <Button
            leftSection={<IconPlus size={16} />}
            component={Link}
            to="/shifts/new"
          >
            New shift
          </Button>
        )}
      </Group>

      <Paper withBorder p="md">
        <Group mb="sm">
          <TextInput
            placeholder="Search by name"
            value={search}
            onChange={(e) => setSearch(e.currentTarget.value)}
            style={{ flex: 1 }}
          />
          <Select
            placeholder="Type"
            clearable
            data={TYPES}
            value={type}
            onChange={(v) => setType(v as ShiftType | null)}
          />
          <Switch
            label="Active only"
            checked={activeOnly}
            onChange={(e) => setActiveOnly(e.currentTarget.checked)}
          />
        </Group>

        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Name</Table.Th>
              <Table.Th>Type</Table.Th>
              <Table.Th>Color</Table.Th>
              <Table.Th>Segments</Table.Th>
              <Table.Th>OT tiers</Table.Th>
              <Table.Th>Active</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {shifts.data?.map((s) => (
              <Table.Tr
                key={s.id}
                style={{ cursor: 'pointer' }}
                onClick={() => navigate(`/shifts/${s.id}`)}
              >
                <Table.Td>{s.name}</Table.Td>
                <Table.Td>
                  <Badge color={TYPE_COLORS[s.shiftType]} variant="light">
                    {s.shiftType}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  <Group gap="xs">
                    <div
                      style={{
                        width: 16,
                        height: 16,
                        borderRadius: 4,
                        background: s.color,
                        border: '1px solid #e5e7eb'
                      }}
                    />
                    <Text size="sm" ff="monospace">
                      {s.color}
                    </Text>
                  </Group>
                </Table.Td>
                <Table.Td>{s.segments.length}</Table.Td>
                <Table.Td>{s.overtimeRules.length}</Table.Td>
                <Table.Td>
                  <Badge color={s.active ? 'green' : 'gray'} variant="light">
                    {s.active ? 'Active' : 'Inactive'}
                  </Badge>
                </Table.Td>
                <Table.Td onClick={(e) => e.stopPropagation()}>
                  {canWrite && (
                    <Group gap={4} justify="flex-end">
                      <ActionIcon
                        variant="subtle"
                        component={Link}
                        to={`/shifts/${s.id}`}
                      >
                        <IconEdit size={14} />
                      </ActionIcon>
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        onClick={() => {
                          if (confirm(`Delete shift "${s.name}"?`)) {
                            deleteMutation.mutate(s.id);
                          }
                        }}
                      >
                        <IconTrash size={14} />
                      </ActionIcon>
                    </Group>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
            {shifts.data && shifts.data.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={7}>
                  <Text c="dimmed">No shifts match the current filters.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>
    </Stack>
  );
}
