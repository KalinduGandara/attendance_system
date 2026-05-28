import { useEffect, useState } from 'react';
import { Button, Group, Modal, Select, Stack, Textarea } from '@mantine/core';
import { DateTimePicker } from '@mantine/dates';
import { notifications } from '@mantine/notifications';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { timecardApi } from '../api';
import type {
  PunchEvent,
  PunchEventType,
  TimeCardDetail,
  TimeCardEditChangeType
} from '../types';
import { describeApiError } from '../../../lib/apiError';

export interface EditPunchModalProps {
  opened: boolean;
  onClose: () => void;
  timeCardId: string;
  mode: 'add' | 'edit' | 'delete';
  punch?: PunchEvent;
  /** Required when adding a brand-new punch — caller supplies the day's start in UTC. */
  defaultTime?: string;
  onSaved?: (detail: TimeCardDetail) => void;
}

const PUNCH_TYPE_OPTIONS = [
  { value: 'CHECK_IN', label: 'Check in' },
  { value: 'CHECK_OUT', label: 'Check out' },
  { value: 'BREAK_START', label: 'Break start' },
  { value: 'BREAK_END', label: 'Break end' }
];

export function EditPunchModal({
  opened,
  onClose,
  timeCardId,
  mode,
  punch,
  defaultTime,
  onSaved
}: EditPunchModalProps) {
  const qc = useQueryClient();
  const [eventType, setEventType] = useState<PunchEventType>('CHECK_IN');
  const [time, setTime] = useState<Date | null>(null);
  const [reason, setReason] = useState('');

  useEffect(() => {
    if (!opened) return;
    if (punch) {
      setEventType(punch.eventType);
      setTime(new Date(punch.eventTimeUtc));
    } else if (defaultTime) {
      setEventType('CHECK_IN');
      setTime(new Date(defaultTime));
    } else {
      setEventType('CHECK_IN');
      setTime(new Date());
    }
    setReason('');
  }, [opened, punch, defaultTime]);

  const mutation = useMutation({
    mutationFn: async () => {
      const changeType: TimeCardEditChangeType =
        mode === 'add' ? 'PUNCH_ADD' : mode === 'edit' ? 'PUNCH_EDIT' : 'PUNCH_DELETE';
      return timecardApi.editTimeCard(timeCardId, {
        changeType,
        punchEventId: punch?.id ?? null,
        eventType: mode === 'delete' ? null : eventType,
        newEventTime: mode === 'delete' ? null : time?.toISOString() ?? null,
        reason
      });
    },
    onSuccess: (detail) => {
      notifications.show({ message: 'Time card updated', color: 'green' });
      qc.invalidateQueries({ queryKey: ['timecards'] });
      qc.invalidateQueries({ queryKey: ['timecard', timeCardId] });
      onSaved?.(detail);
      onClose();
    },
    onError: (err) =>
      notifications.show({ message: describeApiError(err), color: 'red' })
  });

  function submit() {
    if (reason.trim().length === 0) {
      notifications.show({
        message: 'Reason is required for every manual edit',
        color: 'red'
      });
      return;
    }
    if (mode !== 'delete' && !time) {
      notifications.show({ message: 'Pick an event time', color: 'red' });
      return;
    }
    mutation.mutate();
  }

  const title =
    mode === 'add' ? 'Add punch' : mode === 'edit' ? 'Edit punch' : 'Delete punch';

  return (
    <Modal opened={opened} onClose={onClose} title={title} size="md">
      <Stack>
        {mode !== 'delete' && (
          <>
            <Select
              label="Event type"
              data={PUNCH_TYPE_OPTIONS}
              value={eventType}
              onChange={(v) => setEventType((v as PunchEventType) ?? 'CHECK_IN')}
              required
              allowDeselect={false}
            />
            <DateTimePicker
              label="Event time"
              value={time}
              onChange={setTime}
              required
              withSeconds={false}
            />
          </>
        )}
        <Textarea
          label="Reason"
          description="Required — appended to the time card's audit log."
          placeholder="e.g. Device clock drift; verified by team lead"
          value={reason}
          onChange={(e) => setReason(e.currentTarget.value)}
          autosize
          minRows={3}
          required
          withAsterisk
          data-testid="edit-reason"
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button
            color={mode === 'delete' ? 'red' : 'blue'}
            loading={mutation.isPending}
            onClick={submit}
            data-testid="edit-submit"
          >
            {mode === 'delete' ? 'Delete' : 'Save'}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
