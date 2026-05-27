import { useState } from 'react';
import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Paper,
  Stack,
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
import { scheduleApi } from '../api';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

export function ScheduleTemplatesPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const canWrite = useAuthStore((s) => s.hasPermission('schedule.write'));
  const [search, setSearch] = useState('');
  const [debounced] = useDebouncedValue(search, 300);

  const templates = useQuery({
    queryKey: ['schedule-templates', debounced],
    queryFn: () => scheduleApi.listTemplates(debounced || undefined)
  });

  const deleteMutation = useMutation({
    mutationFn: scheduleApi.deleteTemplate,
    onSuccess: () => {
      notifications.show({ message: 'Template deleted', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['schedule-templates'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Schedule templates</Title>
        {canWrite && (
          <Button
            leftSection={<IconPlus size={16} />}
            component={Link}
            to="/schedule-templates/new"
          >
            New template
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
        </Group>

        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Name</Table.Th>
              <Table.Th>Cycle</Table.Th>
              <Table.Th>Length</Table.Th>
              <Table.Th>Days configured</Table.Th>
              <Table.Th>Description</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {templates.data?.map((t) => (
              <Table.Tr
                key={t.id}
                style={{ cursor: 'pointer' }}
                onClick={() => navigate(`/schedule-templates/${t.id}`)}
              >
                <Table.Td>{t.name}</Table.Td>
                <Table.Td>
                  <Badge color={t.cycleType === 'WEEKLY' ? 'blue' : 'teal'} variant="light">
                    {t.cycleType}
                  </Badge>
                </Table.Td>
                <Table.Td>{t.cycleLengthDays}d</Table.Td>
                <Table.Td>
                  {t.days.filter((d) => d.shiftId !== null).length} / {t.cycleLengthDays}
                </Table.Td>
                <Table.Td>
                  <Text size="sm" c="dimmed" lineClamp={1}>
                    {t.description || '—'}
                  </Text>
                </Table.Td>
                <Table.Td onClick={(e) => e.stopPropagation()}>
                  {canWrite && (
                    <Group gap={4} justify="flex-end">
                      <ActionIcon
                        variant="subtle"
                        component={Link}
                        to={`/schedule-templates/${t.id}`}
                      >
                        <IconEdit size={14} />
                      </ActionIcon>
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        onClick={() => {
                          if (confirm(`Delete template "${t.name}"?`)) {
                            deleteMutation.mutate(t.id);
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
            {templates.data && templates.data.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={6}>
                  <Text c="dimmed">No templates yet.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>
    </Stack>
  );
}
