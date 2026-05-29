import { useEffect, useState } from 'react';
import {
  Button,
  Group,
  NumberInput,
  Paper,
  Stack,
  Switch,
  Table,
  Text,
  Textarea,
  TextInput,
  Title
} from '@mantine/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { adminApi } from '../api';
import type { SystemSetting } from '../types';
import { describeApiError } from '../../../lib/apiError';

export function SystemSettingsPage() {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<Record<string, string>>({});

  const query = useQuery({ queryKey: ['system-settings'], queryFn: adminApi.listSettings });

  // Seed the editable draft once the settings load (and after a save refetch).
  useEffect(() => {
    if (query.data) {
      setDraft(Object.fromEntries(query.data.map((s) => [s.key, s.value])));
    }
  }, [query.data]);

  const save = useMutation({
    mutationFn: (changes: Record<string, string>) => adminApi.updateSettings(changes),
    onSuccess: () => {
      notifications.show({ message: 'Settings saved', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['system-settings'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  const settings = query.data ?? [];
  const changed = settings.filter((s) => draft[s.key] !== undefined && draft[s.key] !== s.value);

  function set(key: string, value: string) {
    setDraft((d) => ({ ...d, [key]: value }));
  }

  function onSave() {
    const changes = Object.fromEntries(changed.map((s) => [s.key, draft[s.key]]));
    if (Object.keys(changes).length > 0) {
      save.mutate(changes);
    }
  }

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>System settings</Title>
        <Button onClick={onSave} disabled={changed.length === 0} loading={save.isPending}>
          Save {changed.length > 0 ? `(${changed.length})` : ''}
        </Button>
      </Group>

      <Paper withBorder p="md">
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th style={{ width: 240 }}>Key</Table.Th>
              <Table.Th>Value</Table.Th>
              <Table.Th>Description</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {settings.map((s) => (
              <Table.Tr key={s.key}>
                <Table.Td>
                  <Text ff="monospace" size="sm">
                    {s.key}
                  </Text>
                </Table.Td>
                <Table.Td>{renderEditor(s, draft[s.key] ?? s.value, (v) => set(s.key, v))}</Table.Td>
                <Table.Td>
                  <Text size="sm" c="dimmed">
                    {s.description ?? '—'}
                  </Text>
                </Table.Td>
              </Table.Tr>
            ))}
            {settings.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={3}>
                  <Text c="dimmed">No settings configured.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>
    </Stack>
  );
}

function renderEditor(setting: SystemSetting, value: string, onChange: (v: string) => void) {
  switch (setting.valueType) {
    case 'BOOLEAN':
      return (
        <Switch
          checked={value === 'true'}
          onChange={(e) => onChange(e.currentTarget.checked ? 'true' : 'false')}
          label={value === 'true' ? 'Enabled' : 'Disabled'}
        />
      );
    case 'NUMBER':
      return (
        <NumberInput
          value={value === '' ? '' : Number(value)}
          onChange={(v) => onChange(v === '' ? '' : String(v))}
          w={160}
          hideControls
        />
      );
    case 'JSON':
      return (
        <Textarea value={value} onChange={(e) => onChange(e.currentTarget.value)} autosize minRows={2} />
      );
    default:
      return <TextInput value={value} onChange={(e) => onChange(e.currentTarget.value)} w={260} />;
  }
}
