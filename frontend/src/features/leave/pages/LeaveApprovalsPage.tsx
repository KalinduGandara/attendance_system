import { useState } from 'react';
import {
  Badge,
  Button,
  Group,
  Modal,
  Paper,
  Select,
  Stack,
  Table,
  Text,
  Textarea,
  Title
} from '@mantine/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { leaveApi } from '../api';
import type { LeaveRequest, LeaveRequestStatus } from '../types';
import { describeApiError } from '../../../lib/apiError';

const STATUS_COLORS: Record<LeaveRequestStatus, string> = {
  PENDING: 'yellow',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'gray'
};

export function LeaveApprovalsPage() {
  const qc = useQueryClient();
  const [status, setStatus] = useState<string | null>('PENDING');
  const [rejectTarget, setRejectTarget] = useState<LeaveRequest | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const requests = useQuery({
    queryKey: ['leave-requests', 'approval', status],
    queryFn: () =>
      leaveApi.listRequests({
        status: (status as LeaveRequestStatus) || undefined
      })
  });

  const approve = useMutation({
    mutationFn: (id: string) => leaveApi.approveRequest(id),
    onSuccess: () => {
      notifications.show({ message: 'Approved — time cards refreshed', color: 'green' });
      qc.invalidateQueries({ queryKey: ['leave-requests'] });
      qc.invalidateQueries({ queryKey: ['leave-balances'] });
      qc.invalidateQueries({ queryKey: ['timecards'] });
      qc.invalidateQueries({ queryKey: ['exceptions'] });
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  const reject = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      leaveApi.rejectRequest(id, { rejectionReason: reason }),
    onSuccess: () => {
      notifications.show({ message: 'Rejected', color: 'green' });
      qc.invalidateQueries({ queryKey: ['leave-requests'] });
      setRejectTarget(null);
      setRejectReason('');
    },
    onError: (err) => notifications.show({ message: describeApiError(err), color: 'red' })
  });

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Leave approvals</Title>
        <Select
          label="Filter"
          data={['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED']}
          value={status}
          onChange={setStatus}
          clearable
          style={{ width: 180 }}
        />
      </Group>

      <Paper withBorder p="md">
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Employee</Table.Th>
              <Table.Th>Type</Table.Th>
              <Table.Th>Range</Table.Th>
              <Table.Th>Days</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th>Reason</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {requests.data?.map((r) => (
              <Table.Tr key={r.id}>
                <Table.Td>{r.employeeName ?? r.employeeId.slice(0, 8)}</Table.Td>
                <Table.Td>{r.leaveTypeName}</Table.Td>
                <Table.Td>
                  {r.startDate}
                  {r.startDate !== r.endDate ? ` → ${r.endDate}` : ''}
                </Table.Td>
                <Table.Td>{Number(r.daysRequested).toFixed(2)}</Table.Td>
                <Table.Td>
                  <Badge color={STATUS_COLORS[r.status]} variant="light">
                    {r.status}
                  </Badge>
                  {r.retroactive && (
                    <Badge size="xs" ml={4} variant="outline">
                      retroactive
                    </Badge>
                  )}
                </Table.Td>
                <Table.Td>
                  <Text size="xs">{r.reason ?? '—'}</Text>
                </Table.Td>
                <Table.Td>
                  {r.status === 'PENDING' && (
                    <Group gap="xs">
                      <Button
                        size="xs"
                        color="green"
                        onClick={() => approve.mutate(r.id)}
                        loading={approve.isPending}
                      >
                        Approve
                      </Button>
                      <Button
                        size="xs"
                        color="red"
                        variant="outline"
                        onClick={() => setRejectTarget(r)}
                      >
                        Reject
                      </Button>
                    </Group>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
            {requests.data && requests.data.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={7}>
                  <Text c="dimmed">No requests in this view.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Paper>

      <Modal
        opened={rejectTarget !== null}
        onClose={() => setRejectTarget(null)}
        title="Reject leave request"
      >
        <Stack>
          <Textarea
            label="Reason"
            description="Required — shared with the requester."
            value={rejectReason}
            onChange={(e) => setRejectReason(e.currentTarget.value)}
            autosize
            minRows={3}
            withAsterisk
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setRejectTarget(null)}>
              Cancel
            </Button>
            <Button
              color="red"
              loading={reject.isPending}
              onClick={() => {
                if (!rejectTarget) return;
                if (rejectReason.trim().length === 0) {
                  notifications.show({ message: 'Reason is required', color: 'red' });
                  return;
                }
                reject.mutate({ id: rejectTarget.id, reason: rejectReason });
              }}
            >
              Reject
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}
