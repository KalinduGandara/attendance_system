import { useState } from 'react';
import {
  ActionIcon,
  Box,
  Button,
  Group,
  Modal,
  Paper,
  Select,
  Stack,
  Text,
  TextInput,
  Title
} from '@mantine/core';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { IconChevronRight, IconEdit, IconPlus, IconTrash } from '@tabler/icons-react';
import { orgApi } from '../api';
import type { Department, DepartmentNode } from '../types';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

const schema = z.object({
  name: z.string().min(1).max(128),
  parentId: z.string().nullable(),
  timezone: z
    .string()
    .max(64)
    .nullable()
    .transform((v) => (v && v.trim().length > 0 ? v : null))
});

type FormValues = z.infer<typeof schema>;

interface FormState {
  open: boolean;
  editing?: Department;
  defaultParentId?: string | null;
}

export function DepartmentsPage() {
  const queryClient = useQueryClient();
  const canWrite = useAuthStore((s) => s.hasPermission('employee.write'));
  const [formState, setFormState] = useState<FormState>({ open: false });

  const tree = useQuery({ queryKey: ['departments', 'tree'], queryFn: orgApi.departmentTree });
  const flat = useQuery({ queryKey: ['departments'], queryFn: orgApi.listDepartments });

  const createMutation = useMutation({
    mutationFn: orgApi.createDepartment,
    onSuccess: onSaveSuccess('Department created'),
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: FormValues }) => orgApi.updateDepartment(id, body),
    onSuccess: onSaveSuccess('Department updated'),
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const deleteMutation = useMutation({
    mutationFn: orgApi.deleteDepartment,
    onSuccess: () => {
      notifications.show({ message: 'Department deleted', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['departments'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  function onSaveSuccess(message: string) {
    return () => {
      notifications.show({ message, color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['departments'] });
      setFormState({ open: false });
    };
  }

  function startCreate(parentId: string | null = null) {
    setFormState({ open: true, defaultParentId: parentId });
  }

  function startEdit(department: Department) {
    setFormState({ open: true, editing: department });
  }

  function onDelete(department: Department) {
    if (confirm(`Delete department "${department.name}"?`)) {
      deleteMutation.mutate(department.id);
    }
  }

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Departments</Title>
        {canWrite && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => startCreate(null)}>
            New department
          </Button>
        )}
      </Group>

      <Paper withBorder p="md">
        {tree.isLoading && <Text>Loading…</Text>}
        {tree.isError && <Text c="red">{describeApiError(tree.error)}</Text>}
        {tree.data && tree.data.length === 0 && <Text c="dimmed">No departments yet.</Text>}
        {tree.data && tree.data.length > 0 && (
          <Stack gap="xs">
            {tree.data.map((node) => (
              <TreeNode
                key={node.id}
                node={node}
                depth={0}
                canWrite={canWrite}
                onAddChild={(parentId) => startCreate(parentId)}
                onEdit={(id) => {
                  const found = flat.data?.find((d) => d.id === id);
                  if (found) startEdit(found);
                }}
                onDelete={(id) => {
                  const found = flat.data?.find((d) => d.id === id);
                  if (found) onDelete(found);
                }}
              />
            ))}
          </Stack>
        )}
      </Paper>

      <Modal
        opened={formState.open}
        onClose={() => setFormState({ open: false })}
        title={formState.editing ? 'Edit department' : 'New department'}
      >
        {formState.open && (
          <DepartmentForm
            editing={formState.editing}
            defaultParentId={formState.defaultParentId ?? null}
            flatList={flat.data ?? []}
            onCancel={() => setFormState({ open: false })}
            onSubmit={(values) => {
              if (formState.editing) {
                updateMutation.mutate({ id: formState.editing.id, body: values });
              } else {
                createMutation.mutate(values);
              }
            }}
            isSubmitting={createMutation.isPending || updateMutation.isPending}
          />
        )}
      </Modal>
    </Stack>
  );
}

interface NodeProps {
  node: DepartmentNode;
  depth: number;
  canWrite: boolean;
  onAddChild: (parentId: string) => void;
  onEdit: (id: string) => void;
  onDelete: (id: string) => void;
}

function TreeNode({ node, depth, canWrite, onAddChild, onEdit, onDelete }: NodeProps) {
  return (
    <Box>
      <Group justify="space-between" pl={depth * 16}>
        <Group gap="xs">
          <IconChevronRight size={14} />
          <Text fw={500}>{node.name}</Text>
          {node.timezone && (
            <Text size="xs" c="dimmed">
              {node.timezone}
            </Text>
          )}
        </Group>
        {canWrite && (
          <Group gap={4}>
            <ActionIcon variant="subtle" onClick={() => onAddChild(node.id)} title="Add child">
              <IconPlus size={14} />
            </ActionIcon>
            <ActionIcon variant="subtle" onClick={() => onEdit(node.id)} title="Edit">
              <IconEdit size={14} />
            </ActionIcon>
            <ActionIcon variant="subtle" color="red" onClick={() => onDelete(node.id)} title="Delete">
              <IconTrash size={14} />
            </ActionIcon>
          </Group>
        )}
      </Group>
      {node.children.length > 0 && (
        <Stack gap="xs" mt="xs">
          {node.children.map((c) => (
            <TreeNode
              key={c.id}
              node={c}
              depth={depth + 1}
              canWrite={canWrite}
              onAddChild={onAddChild}
              onEdit={onEdit}
              onDelete={onDelete}
            />
          ))}
        </Stack>
      )}
    </Box>
  );
}

interface FormProps {
  editing?: Department;
  defaultParentId: string | null;
  flatList: Department[];
  onCancel: () => void;
  onSubmit: (values: FormValues) => void;
  isSubmitting: boolean;
}

function DepartmentForm({ editing, defaultParentId, flatList, onCancel, onSubmit, isSubmitting }: FormProps) {
  const { register, handleSubmit, setValue, watch, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: editing?.name ?? '',
      parentId: editing?.parentId ?? defaultParentId,
      timezone: editing?.timezone ?? null
    }
  });

  const parentChoices = flatList
    .filter((d) => !editing || d.id !== editing.id)
    .map((d) => ({ value: d.id, label: d.name }));

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <Stack>
        <TextInput label="Name" required error={errors.name?.message} {...register('name')} />
        <Select
          label="Parent"
          placeholder="None (top-level)"
          clearable
          data={parentChoices}
          value={watch('parentId') ?? null}
          onChange={(val) => setValue('parentId', val)}
        />
        <TextInput
          label="Timezone (IANA)"
          placeholder="e.g. Asia/Colombo"
          error={errors.timezone?.message}
          {...register('timezone', { setValueAs: (v) => (v === '' ? null : v) })}
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
