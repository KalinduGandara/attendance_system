import { useState } from 'react';
import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Modal,
  Pagination,
  Paper,
  Select,
  Stack,
  Table,
  Text,
  Textarea,
  TextInput,
  Title
} from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { IconEdit, IconPlus, IconTrash } from '@tabler/icons-react';
import { deviceApi } from '../api';
import type { Device, DeviceStatus, DeviceType } from '../types';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

const DEVICE_TYPES: { value: DeviceType; label: string }[] = [
  { value: 'SIMULATED', label: 'Simulated' },
  { value: 'REST_VIRTUAL', label: 'REST virtual' },
  { value: 'EXTERNAL', label: 'External (SDK)' }
];

const schema = z.object({
  name: z.string().min(1).max(128),
  deviceType: z.enum(['SIMULATED', 'REST_VIRTUAL', 'EXTERNAL']),
  location: z
    .string()
    .max(255)
    .nullable()
    .transform((v) => (v && v.trim().length > 0 ? v : null)),
  status: z.enum(['ACTIVE', 'INACTIVE']),
  capabilitiesJson: z.string().refine(
    (v) => {
      if (!v || v.trim() === '') return true;
      try {
        const parsed = JSON.parse(v);
        return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed);
      } catch {
        return false;
      }
    },
    { message: 'Capabilities must be a JSON object' }
  )
});

type FormValues = z.infer<typeof schema>;

interface FormState {
  open: boolean;
  editing?: Device;
}

export function DevicesPage() {
  const queryClient = useQueryClient();
  const canWrite = useAuthStore((s) => s.hasPermission('device.write'));
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [debounced] = useDebouncedValue(search, 300);
  const [status, setStatus] = useState<DeviceStatus | null>(null);
  const [formState, setFormState] = useState<FormState>({ open: false });

  const devices = useQuery({
    queryKey: ['devices', { page, debounced, status }],
    queryFn: () =>
      deviceApi.searchDevices({
        q: debounced || undefined,
        status: status ?? undefined,
        page,
        size: 25,
        sort: 'name',
        direction: 'asc'
      })
  });

  const createMutation = useMutation({
    mutationFn: deviceApi.createDevice,
    onSuccess: onSaveSuccess('Device created'),
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: ReturnType<typeof toRequest> }) =>
      deviceApi.updateDevice(id, body),
    onSuccess: onSaveSuccess('Device updated'),
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const deleteMutation = useMutation({
    mutationFn: deviceApi.deleteDevice,
    onSuccess: () => {
      notifications.show({ message: 'Device deleted', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['devices'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  function onSaveSuccess(message: string) {
    return () => {
      notifications.show({ message, color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['devices'] });
      setFormState({ open: false });
    };
  }

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Devices</Title>
        {canWrite && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setFormState({ open: true })}>
            New device
          </Button>
        )}
      </Group>

      <Paper withBorder p="md">
        <Group mb="sm">
          <TextInput
            placeholder="Search by name or location"
            value={search}
            onChange={(e) => {
              setSearch(e.currentTarget.value);
              setPage(0);
            }}
            style={{ flex: 1 }}
          />
          <Select
            placeholder="Status"
            clearable
            data={[
              { value: 'ACTIVE', label: 'Active' },
              { value: 'INACTIVE', label: 'Inactive' }
            ]}
            value={status}
            onChange={(v) => {
              setStatus(v as DeviceStatus | null);
              setPage(0);
            }}
          />
        </Group>

        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Name</Table.Th>
              <Table.Th>Type</Table.Th>
              <Table.Th>Location</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th>Last seen</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {devices.data?.items.map((d) => (
              <Table.Tr key={d.id}>
                <Table.Td>{d.name}</Table.Td>
                <Table.Td>
                  <Badge variant="light">{d.deviceType}</Badge>
                </Table.Td>
                <Table.Td>{d.location ?? '—'}</Table.Td>
                <Table.Td>
                  <Badge color={d.status === 'ACTIVE' ? 'green' : 'gray'} variant="light">
                    {d.status}
                  </Badge>
                </Table.Td>
                <Table.Td>{d.lastSeenAt ? new Date(d.lastSeenAt).toLocaleString() : '—'}</Table.Td>
                <Table.Td>
                  {canWrite && (
                    <Group gap={4} justify="flex-end">
                      <ActionIcon
                        variant="subtle"
                        onClick={() => setFormState({ open: true, editing: d })}
                      >
                        <IconEdit size={14} />
                      </ActionIcon>
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        onClick={() => {
                          if (confirm(`Delete device "${d.name}"?`)) {
                            deleteMutation.mutate(d.id);
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
            {devices.data && devices.data.items.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={6}>
                  <Text c="dimmed">No devices configured.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
        {devices.data && devices.data.totalPages > 1 && (
          <Group justify="flex-end" mt="md">
            <Pagination
              total={devices.data.totalPages}
              value={page + 1}
              onChange={(v) => setPage(v - 1)}
            />
          </Group>
        )}
      </Paper>

      <Modal
        opened={formState.open}
        onClose={() => setFormState({ open: false })}
        title={formState.editing ? `Edit device — ${formState.editing.name}` : 'New device'}
      >
        {formState.open && (
          <DeviceForm
            editing={formState.editing}
            onCancel={() => setFormState({ open: false })}
            onSubmit={(values) => {
              const body = toRequest(values);
              if (formState.editing) {
                updateMutation.mutate({ id: formState.editing.id, body });
              } else {
                createMutation.mutate(body);
              }
            }}
            isSubmitting={createMutation.isPending || updateMutation.isPending}
          />
        )}
      </Modal>
    </Stack>
  );
}

function toRequest(values: FormValues) {
  let capabilities: Record<string, unknown> = {};
  const raw = values.capabilitiesJson?.trim();
  if (raw) {
    capabilities = JSON.parse(raw);
  }
  return {
    name: values.name,
    deviceType: values.deviceType,
    location: values.location,
    status: values.status,
    capabilities
  };
}

interface FormProps {
  editing?: Device;
  onCancel: () => void;
  onSubmit: (values: FormValues) => void;
  isSubmitting: boolean;
}

function DeviceForm({ editing, onCancel, onSubmit, isSubmitting }: FormProps) {
  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors }
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: editing?.name ?? '',
      deviceType: editing?.deviceType ?? 'SIMULATED',
      location: editing?.location ?? null,
      status: editing?.status ?? 'ACTIVE',
      capabilitiesJson: JSON.stringify(editing?.capabilities ?? {}, null, 2)
    }
  });

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <Stack>
        <TextInput label="Name" required error={errors.name?.message} {...register('name')} />
        <Select
          label="Type"
          required
          data={DEVICE_TYPES}
          value={watch('deviceType')}
          onChange={(v) => v && setValue('deviceType', v as DeviceType)}
        />
        <TextInput
          label="Location"
          {...register('location', { setValueAs: (v) => (v === '' ? null : v) })}
        />
        <Select
          label="Status"
          required
          data={[
            { value: 'ACTIVE', label: 'Active' },
            { value: 'INACTIVE', label: 'Inactive' }
          ]}
          value={watch('status')}
          onChange={(v) => v && setValue('status', v as DeviceStatus)}
        />
        <Textarea
          label="Capabilities (JSON object)"
          description={'e.g. {"check_in": true, "face": false}'}
          autosize
          minRows={4}
          error={errors.capabilitiesJson?.message}
          {...register('capabilitiesJson')}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onCancel}>
            Cancel
          </Button>
          <Button type="submit" loading={isSubmitting}>
            Save
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
