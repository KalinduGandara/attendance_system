import { useState } from 'react';
import {
  ActionIcon,
  Badge,
  Button,
  Checkbox,
  Group,
  Modal,
  NumberInput,
  Paper,
  Select,
  Stack,
  Table,
  TagsInput,
  Text,
  TextInput,
  Title
} from '@mantine/core';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { IconEdit, IconPlus, IconTrash } from '@tabler/icons-react';
import { orgApi } from '../api';
import type { CustomFieldDefinition } from '../types';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

const schema = z.object({
  entityType: z.literal('EMPLOYEE'),
  fieldKey: z.string().regex(/^[a-z][a-z0-9_]*$/, 'must be lowercase snake_case').max(64),
  displayLabel: z.string().min(1).max(128),
  fieldType: z.enum(['STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'ENUM']),
  required: z.boolean(),
  options: z.array(z.string()).default([]),
  displayOrder: z.number().int().min(0)
});

type FormValues = z.infer<typeof schema>;

interface FormState {
  open: boolean;
  editing?: CustomFieldDefinition;
}

export function CustomFieldsPage() {
  const queryClient = useQueryClient();
  const canWrite = useAuthStore((s) => s.hasPermission('employee.write'));
  const [formState, setFormState] = useState<FormState>({ open: false });

  const list = useQuery({ queryKey: ['custom-fields'], queryFn: orgApi.listCustomFields });

  const createMutation = useMutation({
    mutationFn: orgApi.createCustomField,
    onSuccess: onSaveSuccess('Custom field created'),
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: FormValues }) => orgApi.updateCustomField(id, body),
    onSuccess: onSaveSuccess('Custom field updated'),
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const deleteMutation = useMutation({
    mutationFn: orgApi.deleteCustomField,
    onSuccess: () => {
      notifications.show({ message: 'Custom field deleted', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['custom-fields'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  function onSaveSuccess(message: string) {
    return () => {
      notifications.show({ message, color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['custom-fields'] });
      setFormState({ open: false });
    };
  }

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Custom fields (Employees)</Title>
        {canWrite && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setFormState({ open: true })}>
            New field
          </Button>
        )}
      </Group>

      <Paper withBorder p="md">
        <Table striped>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Order</Table.Th>
              <Table.Th>Key</Table.Th>
              <Table.Th>Label</Table.Th>
              <Table.Th>Type</Table.Th>
              <Table.Th>Required</Table.Th>
              <Table.Th>Options</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {list.data?.map((f) => (
              <Table.Tr key={f.id}>
                <Table.Td>{f.displayOrder}</Table.Td>
                <Table.Td>
                  <code>{f.fieldKey}</code>
                </Table.Td>
                <Table.Td>{f.displayLabel}</Table.Td>
                <Table.Td>
                  <Badge variant="light">{f.fieldType}</Badge>
                </Table.Td>
                <Table.Td>{f.required ? 'Yes' : 'No'}</Table.Td>
                <Table.Td>{f.options.length > 0 ? f.options.join(', ') : '—'}</Table.Td>
                <Table.Td>
                  {canWrite && (
                    <Group gap={4} justify="flex-end">
                      <ActionIcon variant="subtle" onClick={() => setFormState({ open: true, editing: f })}>
                        <IconEdit size={14} />
                      </ActionIcon>
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        onClick={() => {
                          if (confirm(`Delete custom field "${f.displayLabel}"? Existing values will be removed.`)) {
                            deleteMutation.mutate(f.id);
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
            {list.data && list.data.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={7}>
                  <Text c="dimmed">No custom fields defined.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>

      <Modal
        opened={formState.open}
        onClose={() => setFormState({ open: false })}
        title={formState.editing ? 'Edit custom field' : 'New custom field'}
      >
        {formState.open && (
          <CustomFieldForm
            editing={formState.editing}
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

interface FormProps {
  editing?: CustomFieldDefinition;
  onCancel: () => void;
  onSubmit: (values: FormValues) => void;
  isSubmitting: boolean;
}

function CustomFieldForm({ editing, onCancel, onSubmit, isSubmitting }: FormProps) {
  const { register, handleSubmit, setValue, watch, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      entityType: 'EMPLOYEE',
      fieldKey: editing?.fieldKey ?? '',
      displayLabel: editing?.displayLabel ?? '',
      fieldType: editing?.fieldType ?? 'STRING',
      required: editing?.required ?? false,
      options: editing?.options ?? [],
      displayOrder: editing?.displayOrder ?? 0
    }
  });

  const fieldType = watch('fieldType');
  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <Stack>
        <TextInput
          label="Field key"
          description="Used in CSV and API. Lowercase snake_case."
          required
          disabled={!!editing}
          error={errors.fieldKey?.message}
          {...register('fieldKey')}
        />
        <TextInput
          label="Display label"
          required
          error={errors.displayLabel?.message}
          {...register('displayLabel')}
        />
        <Select
          label="Type"
          required
          disabled={!!editing}
          data={[
            { value: 'STRING', label: 'String' },
            { value: 'NUMBER', label: 'Number' },
            { value: 'DATE', label: 'Date' },
            { value: 'BOOLEAN', label: 'Boolean' },
            { value: 'ENUM', label: 'Enum (dropdown)' }
          ]}
          value={fieldType}
          onChange={(v) => v && setValue('fieldType', v as FormValues['fieldType'])}
        />
        {fieldType === 'ENUM' && (
          <TagsInput
            label="Options"
            description="Press enter after each option"
            value={watch('options')}
            onChange={(v) => setValue('options', v)}
          />
        )}
        <Checkbox label="Required" checked={watch('required')} onChange={(e) => setValue('required', e.currentTarget.checked)} />
        <NumberInput
          label="Display order"
          min={0}
          value={watch('displayOrder')}
          onChange={(v) => setValue('displayOrder', typeof v === 'number' ? v : Number(v) || 0)}
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
