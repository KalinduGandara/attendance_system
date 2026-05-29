import { useEffect, useState } from 'react';
import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Modal,
  NumberInput,
  Paper,
  Select,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
  Title,
  Tooltip
} from '@mantine/core';
import { IconEdit, IconPlus, IconTrash } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { leaveApi } from '../api';
import { timeCodeApi } from '../../timecode/api';
import type { LeaveType, LeaveTypeRequest } from '../types';
import { describeApiError } from '../../../lib/apiError';
import { useAuthStore } from '../../../lib/authStore';

export function LeaveTypesPage() {
  const qc = useQueryClient();
  const hasPermission = useAuthStore((s) => s.hasPermission);
  const canEdit = hasPermission('leave.approve');
  const [editing, setEditing] = useState<LeaveType | null>(null);
  const [creating, setCreating] = useState(false);

  const types = useQuery({ queryKey: ['leave-types'], queryFn: leaveApi.listTypes });
  const codes = useQuery({
    queryKey: ['time-codes', 'leave'],
    queryFn: () => timeCodeApi.list({ category: 'LEAVE' })
  });

  const del = useMutation({
    mutationFn: (id: string) => leaveApi.deleteType(id),
    onSuccess: () => {
      notifications.show({ message: 'Leave type removed', color: 'green' });
      qc.invalidateQueries({ queryKey: ['leave-types'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Leave types</Title>
        {canEdit && (
          <Button leftSection={<IconPlus size={14} />} onClick={() => setCreating(true)}>
            New leave type
          </Button>
        )}
      </Group>

      <Paper withBorder p="md">
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Name</Table.Th>
              <Table.Th>Time code</Table.Th>
              <Table.Th>Annual days</Table.Th>
              <Table.Th>Approval required</Table.Th>
              <Table.Th>Active</Table.Th>
              {canEdit && <Table.Th />}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {types.data?.map((t) => (
              <Table.Tr key={t.id}>
                <Table.Td>{t.name}</Table.Td>
                <Table.Td>
                  <Badge variant="light">{t.timeCodeCode ?? t.timeCodeId.slice(0, 8)}</Badge>
                </Table.Td>
                <Table.Td>{t.defaultAnnualDays}</Table.Td>
                <Table.Td>{t.requiresApproval ? 'Yes' : 'No'}</Table.Td>
                <Table.Td>
                  <Badge color={t.active ? 'green' : 'gray'} variant="light">
                    {t.active ? 'Active' : 'Inactive'}
                  </Badge>
                </Table.Td>
                {canEdit && (
                  <Table.Td>
                    <Group gap="xs">
                      <Tooltip label="Edit">
                        <ActionIcon variant="subtle" onClick={() => setEditing(t)}>
                          <IconEdit size={14} />
                        </ActionIcon>
                      </Tooltip>
                      <Tooltip label="Delete">
                        <ActionIcon color="red" variant="subtle" onClick={() => del.mutate(t.id)}>
                          <IconTrash size={14} />
                        </ActionIcon>
                      </Tooltip>
                    </Group>
                  </Table.Td>
                )}
              </Table.Tr>
            ))}
            {types.data && types.data.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={canEdit ? 6 : 5}>
                  <Text c="dimmed">No leave types yet.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>

      <LeaveTypeFormModal
        opened={creating || editing !== null}
        onClose={() => {
          setCreating(false);
          setEditing(null);
        }}
        existing={editing}
        leaveTimeCodes={codes.data ?? []}
      />
    </Stack>
  );
}

interface FormModalProps {
  opened: boolean;
  onClose: () => void;
  existing: LeaveType | null;
  leaveTimeCodes: Array<{ id: string; code: string; name: string }>;
}

function LeaveTypeFormModal({ opened, onClose, existing, leaveTimeCodes }: FormModalProps) {
  const qc = useQueryClient();
  const [name, setName] = useState('');
  const [timeCodeId, setTimeCodeId] = useState<string | null>(null);
  const [defaultAnnualDays, setDefaultAnnualDays] = useState<number | string>(0);
  const [requiresApproval, setRequiresApproval] = useState(true);
  const [active, setActive] = useState(true);

  useEffect(() => {
    if (!opened) return;
    if (existing) {
      setName(existing.name);
      setTimeCodeId(existing.timeCodeId);
      setDefaultAnnualDays(existing.defaultAnnualDays);
      setRequiresApproval(existing.requiresApproval);
      setActive(existing.active);
    } else {
      setName('');
      setTimeCodeId(null);
      setDefaultAnnualDays(0);
      setRequiresApproval(true);
      setActive(true);
    }
  }, [opened, existing]);

  const mutation = useMutation({
    mutationFn: async () => {
      if (!timeCodeId) throw new Error('Pick a LEAVE time code');
      const body: LeaveTypeRequest = {
        name,
        timeCodeId,
        defaultAnnualDays,
        requiresApproval,
        active
      };
      return existing
        ? leaveApi.updateType(existing.id, body)
        : leaveApi.createType(body);
    },
    onSuccess: () => {
      notifications.show({ message: 'Saved', color: 'green' });
      qc.invalidateQueries({ queryKey: ['leave-types'] });
      onClose();
      // Reset so the next open starts blank.
      setName('');
      setTimeCodeId(null);
      setDefaultAnnualDays(0);
      setRequiresApproval(true);
      setActive(true);
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  return (
    <Modal opened={opened} onClose={onClose} title={existing ? 'Edit leave type' : 'New leave type'}>
      <Stack>
        <TextInput
          label="Name"
          value={name}
          onChange={(e) => setName(e.currentTarget.value)}
          required
        />
        <Select
          label="Leave time code"
          description="Must be a LEAVE-category time code."
          data={leaveTimeCodes.map((c) => ({ value: c.id, label: `${c.code} — ${c.name}` }))}
          value={timeCodeId}
          onChange={setTimeCodeId}
          required
          searchable
          allowDeselect={false}
        />
        <NumberInput
          label="Default annual days"
          value={defaultAnnualDays}
          onChange={setDefaultAnnualDays}
          min={0}
          max={365}
          step={0.5}
          decimalScale={2}
        />
        <Switch
          label="Requires approval"
          checked={requiresApproval}
          onChange={(e) => setRequiresApproval(e.currentTarget.checked)}
        />
        <Switch
          label="Active"
          checked={active}
          onChange={(e) => setActive(e.currentTarget.checked)}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={mutation.isPending} onClick={() => mutation.mutate()}>
            Save
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
