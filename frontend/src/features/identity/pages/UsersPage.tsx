import { useState } from 'react';
import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Modal,
  MultiSelect,
  Pagination,
  Paper,
  PasswordInput,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Title
} from '@mantine/core';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { IconEdit, IconKey, IconPlus, IconTrash } from '@tabler/icons-react';
import { identityApi } from '../api';
import type { UserStatus, UserSummary } from '../types';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

const baseSchema = {
  username: z.string().min(1).max(64),
  email: z.string().email(),
  displayName: z
    .string()
    .max(128)
    .nullable()
    .transform((v) => (v && v.trim().length > 0 ? v : null)),
  status: z.enum(['ACTIVE', 'INACTIVE', 'LOCKED']),
  roleIds: z.array(z.string()).min(1, 'At least one role is required')
};

const createSchema = z.object({
  ...baseSchema,
  password: z.string().min(12, 'Minimum 12 characters').max(128)
});

const updateSchema = z.object(baseSchema);

type CreateValues = z.infer<typeof createSchema>;
type UpdateValues = z.infer<typeof updateSchema>;

interface FormState {
  open: boolean;
  editing?: UserSummary;
}

export function UsersPage() {
  const queryClient = useQueryClient();
  const canWrite = useAuthStore((s) => s.hasPermission('user.write'));
  const [formState, setFormState] = useState<FormState>({ open: false });
  const [resetState, setResetState] = useState<{ open: boolean; user?: UserSummary }>({ open: false });
  const [page, setPage] = useState(0);

  const users = useQuery({
    queryKey: ['users', page],
    queryFn: () => identityApi.listUsers({ page, size: 25 })
  });
  const roles = useQuery({ queryKey: ['roles'], queryFn: identityApi.listRoles });

  const createMutation = useMutation({
    mutationFn: identityApi.createUser,
    onSuccess: () => {
      notifications.show({ message: 'User created', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['users'] });
      setFormState({ open: false });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateValues }) => identityApi.updateUser(id, body),
    onSuccess: () => {
      notifications.show({ message: 'User updated', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['users'] });
      setFormState({ open: false });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const deleteMutation = useMutation({
    mutationFn: identityApi.deleteUser,
    onSuccess: () => {
      notifications.show({ message: 'User deleted', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const resetMutation = useMutation({
    mutationFn: ({ id, password }: { id: string; password: string }) => identityApi.resetPassword(id, password),
    onSuccess: () => {
      notifications.show({ message: 'Password reset. Existing sessions revoked.', color: 'green' });
      setResetState({ open: false });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  const roleChoices = (roles.data ?? []).map((r) => ({ value: r.id, label: r.name }));

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Users</Title>
        {canWrite && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setFormState({ open: true })}>
            New user
          </Button>
        )}
      </Group>

      <Paper withBorder p="md">
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Username</Table.Th>
              <Table.Th>Email</Table.Th>
              <Table.Th>Display name</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th>Roles</Table.Th>
              <Table.Th>Last login</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {users.data?.items.map((u) => (
              <Table.Tr key={u.id}>
                <Table.Td>{u.username}</Table.Td>
                <Table.Td>{u.email}</Table.Td>
                <Table.Td>{u.displayName ?? '—'}</Table.Td>
                <Table.Td>
                  <Badge color={u.status === 'ACTIVE' ? 'green' : 'gray'} variant="light">
                    {u.status}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  {u.roles.map((r) => (
                    <Badge key={r.id} variant="light" mr={4}>
                      {r.name}
                    </Badge>
                  ))}
                </Table.Td>
                <Table.Td>{u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : '—'}</Table.Td>
                <Table.Td>
                  {canWrite && (
                    <Group gap={4} justify="flex-end">
                      <ActionIcon variant="subtle" onClick={() => setFormState({ open: true, editing: u })}>
                        <IconEdit size={14} />
                      </ActionIcon>
                      <ActionIcon
                        variant="subtle"
                        color="orange"
                        onClick={() => setResetState({ open: true, user: u })}
                        title="Reset password"
                      >
                        <IconKey size={14} />
                      </ActionIcon>
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        onClick={() => {
                          if (confirm(`Delete user "${u.username}"?`)) {
                            deleteMutation.mutate(u.id);
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
            {users.data && users.data.items.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={7}>
                  <Text c="dimmed">No users.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
        {users.data && users.data.totalPages > 1 && (
          <Group justify="flex-end" mt="md">
            <Pagination
              total={users.data.totalPages}
              value={page + 1}
              onChange={(v) => setPage(v - 1)}
            />
          </Group>
        )}
      </Paper>

      <Modal
        opened={formState.open}
        onClose={() => setFormState({ open: false })}
        title={formState.editing ? `Edit user — ${formState.editing.username}` : 'New user'}
      >
        {formState.open && (
          <UserForm
            editing={formState.editing}
            roleChoices={roleChoices}
            onCancel={() => setFormState({ open: false })}
            onSubmit={(values) => {
              if (formState.editing) {
                const { password: _ignored, ...rest } = values as CreateValues;
                updateMutation.mutate({ id: formState.editing.id, body: rest as UpdateValues });
              } else {
                createMutation.mutate(values as CreateValues);
              }
            }}
            isSubmitting={createMutation.isPending || updateMutation.isPending}
          />
        )}
      </Modal>

      <Modal
        opened={resetState.open}
        onClose={() => setResetState({ open: false })}
        title={resetState.user ? `Reset password — ${resetState.user.username}` : 'Reset password'}
      >
        {resetState.open && resetState.user && (
          <ResetPasswordForm
            onCancel={() => setResetState({ open: false })}
            onSubmit={(pw) => resetMutation.mutate({ id: resetState.user!.id, password: pw })}
            isSubmitting={resetMutation.isPending}
          />
        )}
      </Modal>
    </Stack>
  );
}

interface UserFormProps {
  editing?: UserSummary;
  roleChoices: { value: string; label: string }[];
  onCancel: () => void;
  onSubmit: (values: CreateValues | UpdateValues) => void;
  isSubmitting: boolean;
}

function UserForm({ editing, roleChoices, onCancel, onSubmit, isSubmitting }: UserFormProps) {
  const schema = editing ? updateSchema : createSchema;
  const { register, handleSubmit, setValue, watch, formState: { errors } } = useForm<CreateValues>({
    resolver: zodResolver(schema as unknown as z.ZodType<CreateValues>),
    defaultValues: {
      username: editing?.username ?? '',
      email: editing?.email ?? '',
      displayName: editing?.displayName ?? null,
      status: editing?.status ?? 'ACTIVE',
      roleIds: editing?.roles.map((r) => r.id) ?? [],
      password: ''
    }
  });

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <Stack>
        <TextInput label="Username" required error={errors.username?.message} {...register('username')} />
        <TextInput label="Email" required type="email" error={errors.email?.message} {...register('email')} />
        <TextInput
          label="Display name"
          {...register('displayName', { setValueAs: (v) => (v === '' ? null : v) })}
        />
        {!editing && (
          <PasswordInput
            label="Initial password (≥12 chars)"
            required
            error={(errors as Record<string, { message?: string }>).password?.message}
            {...register('password')}
          />
        )}
        <Select
          label="Status"
          required
          data={[
            { value: 'ACTIVE', label: 'Active' },
            { value: 'INACTIVE', label: 'Inactive' },
            { value: 'LOCKED', label: 'Locked' }
          ]}
          value={watch('status')}
          onChange={(v) => v && setValue('status', v as UserStatus)}
        />
        <MultiSelect
          label="Roles"
          required
          data={roleChoices}
          value={watch('roleIds')}
          onChange={(v) => setValue('roleIds', v)}
          error={errors.roleIds?.message}
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

function ResetPasswordForm({
  onCancel,
  onSubmit,
  isSubmitting
}: {
  onCancel: () => void;
  onSubmit: (pw: string) => void;
  isSubmitting: boolean;
}) {
  const schema = z.object({ password: z.string().min(12, 'Minimum 12 characters').max(128) });
  type Values = z.infer<typeof schema>;
  const { register, handleSubmit, formState: { errors } } = useForm<Values>({ resolver: zodResolver(schema) });
  return (
    <form onSubmit={handleSubmit((v) => onSubmit(v.password))}>
      <Stack>
        <Text size="sm" c="dimmed">
          Sets a new password and revokes any existing sessions.
        </Text>
        <PasswordInput
          label="New password"
          required
          error={errors.password?.message}
          {...register('password')}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onCancel}>
            Cancel
          </Button>
          <Button type="submit" loading={isSubmitting}>
            Reset
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
