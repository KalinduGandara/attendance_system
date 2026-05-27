import { useState } from 'react';
import {
  ActionIcon,
  Badge,
  Button,
  Checkbox,
  Group,
  Modal,
  MultiSelect,
  Paper,
  Stack,
  Table,
  Text,
  Textarea,
  TextInput,
  Title
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { IconEdit, IconPlus, IconTrash } from '@tabler/icons-react';
import dayjs from 'dayjs';
import { orgApi } from '../api';
import type { Holiday } from '../types';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

const schema = z.object({
  name: z.string().min(1).max(128),
  holidayDate: z.string().min(1, 'Date is required'),
  recurringYearly: z.boolean(),
  paid: z.boolean(),
  description: z
    .string()
    .max(255)
    .nullable()
    .transform((v) => (v && v.trim().length > 0 ? v : null)),
  groupIds: z.array(z.string()).default([])
});

type FormValues = z.infer<typeof schema>;

interface FormState {
  open: boolean;
  editing?: Holiday;
}

export function HolidaysPage() {
  const queryClient = useQueryClient();
  const canWrite = useAuthStore((s) => s.hasPermission('employee.write'));
  const [formState, setFormState] = useState<FormState>({ open: false });

  const list = useQuery({ queryKey: ['holidays'], queryFn: () => orgApi.listHolidays() });
  const groups = useQuery({ queryKey: ['groups'], queryFn: orgApi.listGroups });

  const createMutation = useMutation({
    mutationFn: orgApi.createHoliday,
    onSuccess: onSaveSuccess('Holiday created'),
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: FormValues }) => orgApi.updateHoliday(id, body),
    onSuccess: onSaveSuccess('Holiday updated'),
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });
  const deleteMutation = useMutation({
    mutationFn: orgApi.deleteHoliday,
    onSuccess: () => {
      notifications.show({ message: 'Holiday deleted', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['holidays'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  function onSaveSuccess(message: string) {
    return () => {
      notifications.show({ message, color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['holidays'] });
      setFormState({ open: false });
    };
  }

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Holidays</Title>
        {canWrite && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setFormState({ open: true })}>
            New holiday
          </Button>
        )}
      </Group>

      <Paper withBorder p="md">
        <Table striped>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Date</Table.Th>
              <Table.Th>Name</Table.Th>
              <Table.Th>Recurring</Table.Th>
              <Table.Th>Paid</Table.Th>
              <Table.Th>Scope</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {list.data?.map((h) => (
              <Table.Tr key={h.id}>
                <Table.Td>{dayjs(h.holidayDate).format('YYYY-MM-DD')}</Table.Td>
                <Table.Td>{h.name}</Table.Td>
                <Table.Td>{h.recurringYearly ? 'Yes' : 'No'}</Table.Td>
                <Table.Td>{h.paid ? 'Yes' : 'No'}</Table.Td>
                <Table.Td>
                  {h.groups.length === 0 ? (
                    <Badge variant="light" color="gray">
                      All
                    </Badge>
                  ) : (
                    h.groups.map((g) => (
                      <Badge key={g.id} variant="light" mr={4}>
                        {g.name}
                      </Badge>
                    ))
                  )}
                </Table.Td>
                <Table.Td>
                  {canWrite && (
                    <Group gap={4} justify="flex-end">
                      <ActionIcon variant="subtle" onClick={() => setFormState({ open: true, editing: h })}>
                        <IconEdit size={14} />
                      </ActionIcon>
                      <ActionIcon
                        variant="subtle"
                        color="red"
                        onClick={() => {
                          if (confirm(`Delete holiday "${h.name}"?`)) {
                            deleteMutation.mutate(h.id);
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
                <Table.Td colSpan={6}>
                  <Text c="dimmed">No holidays defined.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>

      <Modal
        opened={formState.open}
        onClose={() => setFormState({ open: false })}
        title={formState.editing ? 'Edit holiday' : 'New holiday'}
      >
        {formState.open && (
          <HolidayForm
            editing={formState.editing}
            groupChoices={(groups.data ?? []).map((g) => ({ value: g.id, label: g.name }))}
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
  editing?: Holiday;
  groupChoices: { value: string; label: string }[];
  onCancel: () => void;
  onSubmit: (values: FormValues) => void;
  isSubmitting: boolean;
}

function HolidayForm({ editing, groupChoices, onCancel, onSubmit, isSubmitting }: FormProps) {
  const { register, handleSubmit, setValue, watch, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: editing?.name ?? '',
      holidayDate: editing?.holidayDate ?? '',
      recurringYearly: editing?.recurringYearly ?? false,
      paid: editing?.paid ?? true,
      description: editing?.description ?? null,
      groupIds: editing?.groups.map((g) => g.id) ?? []
    }
  });

  const dateValue = watch('holidayDate');

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <Stack>
        <TextInput label="Name" required error={errors.name?.message} {...register('name')} />
        <DateInput
          label="Date"
          required
          valueFormat="YYYY-MM-DD"
          error={errors.holidayDate?.message}
          value={dateValue ? new Date(dateValue) : null}
          onChange={(d) => setValue('holidayDate', d ? dayjs(d).format('YYYY-MM-DD') : '')}
        />
        <Checkbox
          label="Recurs yearly (year is ignored, MM-DD recurs)"
          checked={watch('recurringYearly')}
          onChange={(e) => setValue('recurringYearly', e.currentTarget.checked)}
        />
        <Checkbox
          label="Paid"
          checked={watch('paid')}
          onChange={(e) => setValue('paid', e.currentTarget.checked)}
        />
        <Textarea
          label="Description"
          autosize
          minRows={2}
          {...register('description', { setValueAs: (v) => (v === '' ? null : v) })}
        />
        <MultiSelect
          label="Group scope"
          description="Empty = applies to all employees"
          data={groupChoices}
          value={watch('groupIds')}
          onChange={(v) => setValue('groupIds', v)}
          searchable
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
