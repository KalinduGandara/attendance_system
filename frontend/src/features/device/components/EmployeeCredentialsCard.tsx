import { useState } from 'react';
import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Modal,
  Paper,
  PasswordInput,
  Select,
  Stack,
  Table,
  Text,
  Title
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { IconBan, IconPlus, IconTrash } from '@tabler/icons-react';
import dayjs from 'dayjs';
import { deviceApi } from '../api';
import type { Credential, CredentialStatus, CredentialType } from '../types';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

const TYPES: { value: CredentialType; label: string }[] = [
  { value: 'RFID', label: 'RFID card' },
  { value: 'QR', label: 'QR code' },
  { value: 'MOBILE', label: 'Mobile' },
  { value: 'FACE', label: 'Face (reference)' },
  { value: 'FINGER', label: 'Fingerprint (reference)' },
  { value: 'PIN', label: 'PIN' }
];

const schema = z.object({
  credentialType: z.enum(['RFID', 'QR', 'MOBILE', 'FACE', 'FINGER', 'PIN']),
  value: z.string().min(1, 'Value is required').max(255),
  validFrom: z.string().min(1, 'Valid-from date is required'),
  validTo: z.string().nullable(),
  status: z.enum(['ACTIVE', 'REVOKED', 'EXPIRED'])
});

type FormValues = z.infer<typeof schema>;

interface Props {
  employeeId: string;
}

export function EmployeeCredentialsCard({ employeeId }: Props) {
  const queryClient = useQueryClient();
  const canWrite = useAuthStore((s) => s.hasPermission('employee.write'));
  const [formOpen, setFormOpen] = useState(false);

  const credentials = useQuery({
    queryKey: ['credentials', employeeId],
    queryFn: () => deviceApi.listCredentials(employeeId)
  });

  const createMutation = useMutation({
    mutationFn: (body: FormValues) => deviceApi.createCredential(employeeId, toRequest(body)),
    onSuccess: () => {
      notifications.show({ message: 'Credential added', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['credentials', employeeId] });
      setFormOpen(false);
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const revokeMutation = useMutation({
    mutationFn: (credentialId: string) => deviceApi.revokeCredential(employeeId, credentialId),
    onSuccess: () => {
      notifications.show({ message: 'Credential revoked', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['credentials', employeeId] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const deleteMutation = useMutation({
    mutationFn: (credentialId: string) => deviceApi.deleteCredential(employeeId, credentialId),
    onSuccess: () => {
      notifications.show({ message: 'Credential deleted', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['credentials', employeeId] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  return (
    <Paper withBorder p="lg">
      <Group justify="space-between" mb="sm">
        <Title order={4}>Credentials</Title>
        {canWrite && (
          <Button
            size="xs"
            leftSection={<IconPlus size={14} />}
            onClick={() => setFormOpen(true)}
          >
            Add credential
          </Button>
        )}
      </Group>

      <Text size="xs" c="dimmed" mb="sm">
        Cards, QR codes, PINs, or biometric references this employee can use to punch.
        Values are hashed on submit and never displayed in clear.
      </Text>

      <Table>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Type</Table.Th>
            <Table.Th>Fingerprint</Table.Th>
            <Table.Th>Valid from</Table.Th>
            <Table.Th>Valid to</Table.Th>
            <Table.Th>Status</Table.Th>
            <Table.Th />
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {credentials.data?.map((c) => (
            <Table.Tr key={c.id}>
              <Table.Td>
                <Badge variant="light">{c.credentialType}</Badge>
              </Table.Td>
              <Table.Td>
                <code>{c.valueMasked}</code>
              </Table.Td>
              <Table.Td>{c.validFrom}</Table.Td>
              <Table.Td>{c.validTo ?? '—'}</Table.Td>
              <Table.Td>{statusBadge(c)}</Table.Td>
              <Table.Td>
                {canWrite && (
                  <Group gap={4} justify="flex-end">
                    {c.status === 'ACTIVE' && (
                      <ActionIcon
                        variant="subtle"
                        color="orange"
                        title="Revoke"
                        onClick={() => {
                          if (confirm('Revoke this credential? It will stop resolving immediately.')) {
                            revokeMutation.mutate(c.id);
                          }
                        }}
                      >
                        <IconBan size={14} />
                      </ActionIcon>
                    )}
                    <ActionIcon
                      variant="subtle"
                      color="red"
                      title="Delete"
                      onClick={() => {
                        if (confirm('Delete this credential entirely? Cannot be undone.')) {
                          deleteMutation.mutate(c.id);
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
          {credentials.data && credentials.data.length === 0 && (
            <Table.Tr>
              <Table.Td colSpan={6}>
                <Text c="dimmed">No credentials issued.</Text>
              </Table.Td>
            </Table.Tr>
          )}
        </Table.Tbody>
      </Table>

      <Modal opened={formOpen} onClose={() => setFormOpen(false)} title="Add credential">
        {formOpen && (
          <CredentialForm
            onCancel={() => setFormOpen(false)}
            onSubmit={(v) => createMutation.mutate(v)}
            isSubmitting={createMutation.isPending}
          />
        )}
      </Modal>
    </Paper>
  );
}

function statusBadge(c: Credential) {
  switch (c.status) {
    case 'ACTIVE':
      return (
        <Badge color="green" variant="light">
          Active
        </Badge>
      );
    case 'REVOKED':
      return (
        <Badge color="red" variant="light">
          Revoked
        </Badge>
      );
    case 'EXPIRED':
      return (
        <Badge color="gray" variant="light">
          Expired
        </Badge>
      );
  }
}

function toRequest(v: FormValues) {
  return {
    credentialType: v.credentialType,
    value: v.value,
    validFrom: v.validFrom,
    validTo: v.validTo,
    status: v.status
  };
}

interface FormProps {
  onCancel: () => void;
  onSubmit: (v: FormValues) => void;
  isSubmitting: boolean;
}

function CredentialForm({ onCancel, onSubmit, isSubmitting }: FormProps) {
  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors }
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      credentialType: 'RFID',
      value: '',
      validFrom: dayjs().format('YYYY-MM-DD'),
      validTo: null,
      status: 'ACTIVE'
    }
  });

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <Stack>
        <Select
          label="Type"
          required
          data={TYPES}
          value={watch('credentialType')}
          onChange={(v) => v && setValue('credentialType', v as CredentialType)}
        />
        <PasswordInput
          label="Value"
          description="Stored as a SHA-256 hash; the raw value cannot be retrieved later."
          required
          error={errors.value?.message}
          {...register('value')}
        />
        <Group grow>
          <DateInput
            label="Valid from"
            required
            valueFormat="YYYY-MM-DD"
            error={errors.validFrom?.message}
            value={watch('validFrom') ? new Date(watch('validFrom')) : null}
            onChange={(d) => setValue('validFrom', d ? dayjs(d).format('YYYY-MM-DD') : '')}
          />
          <DateInput
            label="Valid to (optional)"
            valueFormat="YYYY-MM-DD"
            value={watch('validTo') ? new Date(watch('validTo')!) : null}
            onChange={(d) => setValue('validTo', d ? dayjs(d).format('YYYY-MM-DD') : null)}
          />
        </Group>
        <Select
          label="Initial status"
          required
          data={[
            { value: 'ACTIVE', label: 'Active' },
            { value: 'REVOKED', label: 'Revoked' },
            { value: 'EXPIRED', label: 'Expired' }
          ]}
          value={watch('status')}
          onChange={(v) => v && setValue('status', v as CredentialStatus)}
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
