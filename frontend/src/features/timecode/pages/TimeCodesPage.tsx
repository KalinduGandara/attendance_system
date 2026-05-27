import { useState } from 'react';
import {
  ActionIcon,
  Badge,
  Button,
  ColorInput,
  Group,
  Modal,
  NumberInput,
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
import { IconEdit, IconPlus, IconTrash } from '@tabler/icons-react';
import { timeCodeApi } from '../api';
import type { TimeCode, TimeCodeCategory } from '../types';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

const CATEGORIES: { value: TimeCodeCategory; label: string }[] = [
  { value: 'ATTENDANCE', label: 'Attendance' },
  { value: 'OVERTIME', label: 'Overtime' },
  { value: 'LEAVE', label: 'Leave' }
];

const CATEGORY_COLORS: Record<TimeCodeCategory, string> = {
  ATTENDANCE: 'blue',
  OVERTIME: 'orange',
  LEAVE: 'violet'
};

const schema = z.object({
  code: z
    .string()
    .min(1)
    .max(32)
    .regex(/^[A-Za-z0-9._-]+$/, 'alphanumeric with . _ -'),
  name: z.string().min(1).max(128),
  category: z.enum(['ATTENDANCE', 'OVERTIME', 'LEAVE']),
  rate: z.number().min(0).max(10),
  color: z.string().regex(/^#[0-9A-Fa-f]{6}$/, 'must be #RRGGBB'),
  paid: z.boolean(),
  countsForAttendance: z.boolean(),
  description: z.string().max(255).nullable(),
  active: z.boolean()
});

type FormValues = z.infer<typeof schema>;

interface FormState {
  open: boolean;
  editing?: TimeCode;
}

export function TimeCodesPage() {
  const queryClient = useQueryClient();
  const canWrite = useAuthStore((s) => s.hasPermission('timecode.write'));
  const [categoryFilter, setCategoryFilter] = useState<TimeCodeCategory | null>(null);
  const [activeOnly, setActiveOnly] = useState(false);
  const [formState, setFormState] = useState<FormState>({ open: false });

  const codes = useQuery({
    queryKey: ['time-codes', { category: categoryFilter, activeOnly }],
    queryFn: () =>
      timeCodeApi.list({
        category: categoryFilter ?? undefined,
        activeOnly: activeOnly || undefined
      })
  });

  const onSaveSuccess = (message: string) => () => {
    notifications.show({ message, color: 'green' });
    queryClient.invalidateQueries({ queryKey: ['time-codes'] });
    setFormState({ open: false });
  };

  const createMutation = useMutation({
    mutationFn: timeCodeApi.create,
    onSuccess: onSaveSuccess('Time code created'),
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: ReturnType<typeof toRequest> }) =>
      timeCodeApi.update(id, body),
    onSuccess: onSaveSuccess('Time code updated'),
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const deleteMutation = useMutation({
    mutationFn: timeCodeApi.remove,
    onSuccess: () => {
      notifications.show({ message: 'Time code deleted', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['time-codes'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Time codes</Title>
        {canWrite && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setFormState({ open: true })}>
            New time code
          </Button>
        )}
      </Group>

      <Paper withBorder p="md">
        <Group mb="sm">
          <Select
            placeholder="Category"
            clearable
            data={CATEGORIES}
            value={categoryFilter}
            onChange={(v) => setCategoryFilter(v as TimeCodeCategory | null)}
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
              <Table.Th>Code</Table.Th>
              <Table.Th>Name</Table.Th>
              <Table.Th>Category</Table.Th>
              <Table.Th>Rate</Table.Th>
              <Table.Th>Color</Table.Th>
              <Table.Th>Paid</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {codes.data?.map((c) => (
              <Table.Tr key={c.id}>
                <Table.Td>
                  <Text fw={600} ff="monospace">
                    {c.code}
                  </Text>
                </Table.Td>
                <Table.Td>{c.name}</Table.Td>
                <Table.Td>
                  <Badge color={CATEGORY_COLORS[c.category]} variant="light">
                    {c.category}
                  </Badge>
                </Table.Td>
                <Table.Td>{c.rate}x</Table.Td>
                <Table.Td>
                  <Group gap="xs">
                    <div
                      style={{
                        width: 16,
                        height: 16,
                        borderRadius: 4,
                        background: c.color,
                        border: '1px solid #e5e7eb'
                      }}
                    />
                    <Text size="sm" ff="monospace">
                      {c.color}
                    </Text>
                  </Group>
                </Table.Td>
                <Table.Td>{c.paid ? 'Yes' : 'No'}</Table.Td>
                <Table.Td>
                  <Badge color={c.active ? 'green' : 'gray'} variant="light">
                    {c.active ? 'Active' : 'Inactive'}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  {canWrite && (
                    <Group gap={4} justify="flex-end">
                      <ActionIcon
                        variant="subtle"
                        onClick={() => setFormState({ open: true, editing: c })}
                      >
                        <IconEdit size={14} />
                      </ActionIcon>
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        onClick={() => {
                          if (confirm(`Delete time code "${c.code}"?`)) {
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
            {codes.data && codes.data.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={8}>
                  <Text c="dimmed">No time codes match the current filters.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>

      <Modal
        opened={formState.open}
        onClose={() => setFormState({ open: false })}
        title={formState.editing ? `Edit time code — ${formState.editing.code}` : 'New time code'}
      >
        {formState.open && (
          <TimeCodeForm
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
  return {
    code: values.code,
    name: values.name,
    category: values.category,
    rate: values.rate.toFixed(2),
    color: values.color,
    paid: values.paid,
    countsForAttendance: values.countsForAttendance,
    description: values.description && values.description.trim() ? values.description : null,
    active: values.active
  };
}

interface FormProps {
  editing?: TimeCode;
  onCancel: () => void;
  onSubmit: (values: FormValues) => void;
  isSubmitting: boolean;
}

function TimeCodeForm({ editing, onCancel, onSubmit, isSubmitting }: FormProps) {
  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors }
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      code: editing?.code ?? '',
      name: editing?.name ?? '',
      category: editing?.category ?? 'ATTENDANCE',
      rate: editing ? Number(editing.rate) : 1,
      color: editing?.color ?? '#3b82f6',
      paid: editing?.paid ?? true,
      countsForAttendance: editing?.countsForAttendance ?? true,
      description: editing?.description ?? '',
      active: editing?.active ?? true
    }
  });

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <Stack>
        <TextInput
          label="Code"
          required
          description="Short stable identifier (e.g. REG, OT-A)"
          error={errors.code?.message}
          {...register('code')}
        />
        <TextInput label="Name" required error={errors.name?.message} {...register('name')} />
        <Select
          label="Category"
          required
          data={CATEGORIES}
          value={watch('category')}
          onChange={(v) => v && setValue('category', v as TimeCodeCategory)}
        />
        <NumberInput
          label="Rate"
          required
          description="Pay multiplier between 0.00 and 10.00"
          min={0}
          max={10}
          step={0.25}
          decimalScale={2}
          fixedDecimalScale
          value={watch('rate')}
          onChange={(v) => setValue('rate', typeof v === 'number' ? v : Number(v) || 0)}
          error={errors.rate?.message}
        />
        <ColorInput
          label="Color"
          required
          format="hex"
          value={watch('color')}
          onChange={(v) => setValue('color', v)}
          error={errors.color?.message}
        />
        <Group grow>
          <Switch
            label="Paid"
            checked={watch('paid')}
            onChange={(e) => setValue('paid', e.currentTarget.checked)}
          />
          <Switch
            label="Counts for attendance"
            checked={watch('countsForAttendance')}
            onChange={(e) => setValue('countsForAttendance', e.currentTarget.checked)}
          />
        </Group>
        <Switch
          label="Active"
          checked={watch('active')}
          onChange={(e) => setValue('active', e.currentTarget.checked)}
        />
        <Textarea
          label="Description"
          autosize
          minRows={2}
          {...register('description', { setValueAs: (v) => (v === '' ? null : v) })}
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
