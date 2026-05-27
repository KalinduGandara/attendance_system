import { useState } from 'react';
import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  CopyButton,
  Group,
  Modal,
  Paper,
  Select,
  Stack,
  Switch,
  Table,
  Text,
  Textarea,
  TextInput,
  Title
} from '@mantine/core';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconCheck,
  IconCopy,
  IconEdit,
  IconKey,
  IconPlus,
  IconTrash
} from '@tabler/icons-react';
import { deviceApi } from '../api';
import type { IngestionSource, IngestionSourceType } from '../types';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

const SOURCE_TYPES: { value: IngestionSourceType; label: string }[] = [
  { value: 'REST', label: 'REST' },
  { value: 'DEVICE_SDK', label: 'Device SDK' },
  { value: 'EXTERNAL_DB', label: 'External DB' },
  { value: 'CSV', label: 'CSV' }
];

const schema = z.object({
  name: z.string().min(1).max(128),
  sourceType: z.enum(['REST', 'DEVICE_SDK', 'EXTERNAL_DB', 'CSV']),
  enabled: z.boolean(),
  configJson: z.string().refine(
    (v) => {
      if (!v || v.trim() === '') return true;
      try {
        const parsed = JSON.parse(v);
        return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed);
      } catch {
        return false;
      }
    },
    { message: 'Config must be a JSON object' }
  )
});

type FormValues = z.infer<typeof schema>;

interface FormState {
  open: boolean;
  editing?: IngestionSource;
}

export function IngestionSourcesPage() {
  const queryClient = useQueryClient();
  const canWrite = useAuthStore((s) => s.hasPermission('device.write'));
  const [formState, setFormState] = useState<FormState>({ open: false });
  const [revealedKey, setRevealedKey] = useState<{ source: IngestionSource; apiKey: string } | null>(
    null
  );

  const sources = useQuery({ queryKey: ['ingestion-sources'], queryFn: deviceApi.listSources });

  const createMutation = useMutation({
    mutationFn: deviceApi.createSource,
    onSuccess: (result) => {
      notifications.show({ message: 'Ingestion source created', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['ingestion-sources'] });
      setFormState({ open: false });
      setRevealedKey({ source: result.source, apiKey: result.apiKey });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: ReturnType<typeof toRequest> }) =>
      deviceApi.updateSource(id, body),
    onSuccess: () => {
      notifications.show({ message: 'Ingestion source updated', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['ingestion-sources'] });
      setFormState({ open: false });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const rotateMutation = useMutation({
    mutationFn: deviceApi.rotateSourceKey,
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['ingestion-sources'] });
      setRevealedKey({ source: result.source, apiKey: result.apiKey });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const deleteMutation = useMutation({
    mutationFn: deviceApi.deleteSource,
    onSuccess: () => {
      notifications.show({ message: 'Ingestion source deleted', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['ingestion-sources'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Ingestion sources</Title>
        {canWrite && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setFormState({ open: true })}>
            New source
          </Button>
        )}
      </Group>

      <Paper withBorder p="md">
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Name</Table.Th>
              <Table.Th>Type</Table.Th>
              <Table.Th>Enabled</Table.Th>
              <Table.Th>API key</Table.Th>
              <Table.Th>Events</Table.Th>
              <Table.Th>Last event</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {sources.data?.map((s) => (
              <Table.Tr key={s.id}>
                <Table.Td>{s.name}</Table.Td>
                <Table.Td>
                  <Badge variant="light">{s.sourceType}</Badge>
                </Table.Td>
                <Table.Td>
                  <Badge color={s.enabled ? 'green' : 'gray'} variant="light">
                    {s.enabled ? 'Yes' : 'No'}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  {s.apiKeyConfigured ? (
                    <Badge color="blue" variant="light">
                      Configured
                    </Badge>
                  ) : (
                    <Badge color="gray" variant="light">
                      None
                    </Badge>
                  )}
                </Table.Td>
                <Table.Td>
                  {s.eventsTotal.toLocaleString()}{' '}
                  {s.eventsRejected > 0 && (
                    <Text component="span" c="red" size="xs">
                      ({s.eventsRejected.toLocaleString()} rejected)
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>{s.lastEventAt ? new Date(s.lastEventAt).toLocaleString() : '—'}</Table.Td>
                <Table.Td>
                  {canWrite && (
                    <Group gap={4} justify="flex-end">
                      <ActionIcon variant="subtle" onClick={() => setFormState({ open: true, editing: s })}>
                        <IconEdit size={14} />
                      </ActionIcon>
                      <ActionIcon
                        variant="subtle"
                        color="orange"
                        title="Rotate API key"
                        onClick={() => {
                          if (
                            confirm(
                              `Rotate API key for "${s.name}"? The current key will stop working immediately.`
                            )
                          ) {
                            rotateMutation.mutate(s.id);
                          }
                        }}
                      >
                        <IconKey size={14} />
                      </ActionIcon>
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        onClick={() => {
                          if (confirm(`Delete source "${s.name}"?`)) {
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
            {sources.data && sources.data.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={7}>
                  <Text c="dimmed">No ingestion sources configured.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>

      <Modal
        opened={formState.open}
        onClose={() => setFormState({ open: false })}
        title={formState.editing ? `Edit source — ${formState.editing.name}` : 'New ingestion source'}
      >
        {formState.open && (
          <SourceForm
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

      <Modal
        opened={revealedKey !== null}
        onClose={() => setRevealedKey(null)}
        title={revealedKey ? `API key for ${revealedKey.source.name}` : ''}
      >
        {revealedKey && (
          <Stack>
            <Alert color="yellow" icon={<IconAlertTriangle size={16} />} title="Copy this key now.">
              The plaintext key is shown only once. After closing this dialog you will not be able to
              retrieve it again — only rotate it.
            </Alert>
            <TextInput
              label="API key"
              value={revealedKey.apiKey}
              readOnly
              rightSection={
                <CopyButton value={revealedKey.apiKey} timeout={1500}>
                  {({ copied, copy }) => (
                    <ActionIcon variant="subtle" onClick={copy} title="Copy">
                      {copied ? <IconCheck size={14} /> : <IconCopy size={14} />}
                    </ActionIcon>
                  )}
                </CopyButton>
              }
            />
            <Text size="sm" c="dimmed">
              Provide this value in the <code>X-Source-Api-Key</code> header on{' '}
              <code>POST /api/v1/ingestion/punches</code>.
            </Text>
            <Group justify="flex-end">
              <Button onClick={() => setRevealedKey(null)}>I have saved it</Button>
            </Group>
          </Stack>
        )}
      </Modal>
    </Stack>
  );
}

function toRequest(values: FormValues) {
  let config: Record<string, unknown> = {};
  const raw = values.configJson?.trim();
  if (raw) {
    config = JSON.parse(raw);
  }
  return {
    name: values.name,
    sourceType: values.sourceType,
    enabled: values.enabled,
    config
  };
}

interface FormProps {
  editing?: IngestionSource;
  onCancel: () => void;
  onSubmit: (values: FormValues) => void;
  isSubmitting: boolean;
}

function SourceForm({ editing, onCancel, onSubmit, isSubmitting }: FormProps) {
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
      sourceType: editing?.sourceType ?? 'REST',
      enabled: editing?.enabled ?? true,
      configJson: JSON.stringify(editing?.config ?? {}, null, 2)
    }
  });

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <Stack>
        <TextInput label="Name" required error={errors.name?.message} {...register('name')} />
        <Select
          label="Type"
          required
          data={SOURCE_TYPES}
          value={watch('sourceType')}
          onChange={(v) => v && setValue('sourceType', v as IngestionSourceType)}
        />
        <Switch
          label="Enabled"
          checked={watch('enabled')}
          onChange={(e) => setValue('enabled', e.currentTarget.checked)}
        />
        <Textarea
          label="Config (JSON object)"
          description="Adapter-specific settings"
          autosize
          minRows={4}
          error={errors.configJson?.message}
          {...register('configJson')}
        />
        {!editing && (
          <Alert color="blue" title="An API key will be generated and shown once on save." />
        )}
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
