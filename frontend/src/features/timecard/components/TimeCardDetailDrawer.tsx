import { useState } from 'react';
import {
  ActionIcon,
  Badge,
  Button,
  Divider,
  Drawer,
  Group,
  ScrollArea,
  Stack,
  Table,
  Text,
  Title,
  Tooltip
} from '@mantine/core';
import { IconCalendarOff, IconEdit, IconPlus, IconTrash } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { timecardApi } from '../api';
import type { PunchEvent, TimeCardDetail } from '../types';
import {
  PUNCH_STATUS_COLORS,
  PUNCH_TYPE_LABELS,
  STATUS_COLORS,
  fmtInstantLocal,
  fmtMinutes,
  fmtTimeLocal
} from '../format';
import { EditPunchModal } from './EditPunchModal';
import { useAuthStore } from '../../../lib/authStore';

interface DrawerProps {
  timeCardId: string | null;
  onClose: () => void;
}

export function TimeCardDetailDrawer({ timeCardId, onClose }: DrawerProps) {
  const navigate = useNavigate();
  const hasPermission = useAuthStore((s) => s.hasPermission);
  const canEdit = hasPermission('timecard.edit');
  const [editPunch, setEditPunch] = useState<PunchEvent | null>(null);
  const [editMode, setEditMode] = useState<'add' | 'edit' | 'delete' | null>(null);

  const detail = useQuery({
    queryKey: ['timecard', timeCardId],
    queryFn: () => timecardApi.getTimeCard(timeCardId as string),
    enabled: !!timeCardId
  });

  const d = detail.data;

  function openAdd() {
    setEditPunch(null);
    setEditMode('add');
  }

  function openEdit(p: PunchEvent) {
    setEditPunch(p);
    setEditMode('edit');
  }

  function openDelete(p: PunchEvent) {
    setEditPunch(p);
    setEditMode('delete');
  }

  return (
    <>
      <Drawer
        opened={!!timeCardId}
        onClose={onClose}
        position="right"
        size="lg"
        title={
          d ? (
            <Group gap="sm">
              <Title order={4}>{d.workDate}</Title>
              <Badge color={STATUS_COLORS[d.status]} variant="light">
                {d.status}
              </Badge>
              {d.resolvedShift && (
                <Badge variant="outline">{d.resolvedShift.name}</Badge>
              )}
            </Group>
          ) : (
            'Loading…'
          )
        }
      >
        <ScrollArea h="calc(100vh - 110px)">
          {detail.isLoading && <Text>Loading…</Text>}
          {detail.isError && <Text c="red">Failed to load time card.</Text>}
          {d && (
            <Stack>
              <SummaryGrid d={d} />

              <Divider label="Punches" labelPosition="left" />
              <Stack gap="xs">
                <Group justify="space-between">
                  <Text size="sm" c="dimmed">
                    {d.punches.length === 0 ? 'No punches recorded' : `${d.punches.length} punch(es)`}
                  </Text>
                  {canEdit && (
                    <Button size="xs" leftSection={<IconPlus size={14} />} onClick={openAdd}>
                      Add punch
                    </Button>
                  )}
                </Group>
                {d.punches.length > 0 && (
                  <Table withTableBorder striped highlightOnHover fz="sm">
                    <Table.Thead>
                      <Table.Tr>
                        <Table.Th>Time</Table.Th>
                        <Table.Th>Type</Table.Th>
                        <Table.Th>Status</Table.Th>
                        {canEdit && <Table.Th />}
                      </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                      {d.punches.map((p) => (
                        <Table.Tr key={p.id}>
                          <Table.Td>{fmtTimeLocal(p.eventTimeUtc)}</Table.Td>
                          <Table.Td>
                            <Badge variant="light">{PUNCH_TYPE_LABELS[p.eventType]}</Badge>
                          </Table.Td>
                          <Table.Td>
                            <Badge color={PUNCH_STATUS_COLORS[p.status]} variant="light">
                              {p.status}
                            </Badge>
                          </Table.Td>
                          {canEdit && (
                            <Table.Td>
                              <Group gap="xs">
                                <Tooltip label="Edit punch">
                                  <ActionIcon
                                    variant="subtle"
                                    onClick={() => openEdit(p)}
                                    aria-label={`Edit punch ${p.externalEventId}`}
                                  >
                                    <IconEdit size={14} />
                                  </ActionIcon>
                                </Tooltip>
                                <Tooltip label="Delete punch">
                                  <ActionIcon
                                    color="red"
                                    variant="subtle"
                                    onClick={() => openDelete(p)}
                                    aria-label={`Delete punch ${p.externalEventId}`}
                                  >
                                    <IconTrash size={14} />
                                  </ActionIcon>
                                </Tooltip>
                              </Group>
                            </Table.Td>
                          )}
                        </Table.Tr>
                      ))}
                    </Table.Tbody>
                  </Table>
                )}
              </Stack>

              <Divider label="Breakdown" labelPosition="left" />
              {d.breakdown.length === 0 ? (
                <Text size="sm" c="dimmed">No breakdown rows.</Text>
              ) : (
                <Table withTableBorder fz="sm">
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th>Code</Table.Th>
                      <Table.Th>Minutes</Table.Th>
                      <Table.Th>Rated</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {d.breakdown.map((b) => (
                      <Table.Tr key={`${b.timeCodeId}-${b.sequenceOrder}`}>
                        <Table.Td>{b.timeCode ?? b.timeCodeId.slice(0, 8)}</Table.Td>
                        <Table.Td>{fmtMinutes(b.minutes)}</Table.Td>
                        <Table.Td>{fmtMinutes(b.ratedMinutes)}</Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              )}

              <Divider label="Exceptions" labelPosition="left" />
              {d.exceptions.length === 0 ? (
                <Text size="sm" c="dimmed">No exceptions for this day.</Text>
              ) : (
                <Stack gap="xs">
                  {d.exceptions.map((ex) => (
                    <Badge
                      key={ex.id}
                      color="orange"
                      variant="light"
                      size="lg"
                      leftSection={<Text size="xs">{ex.severity}</Text>}
                    >
                      {ex.type} — {ex.status}
                    </Badge>
                  ))}
                </Stack>
              )}

              <Divider label="Edit history" labelPosition="left" />
              {d.edits.length === 0 ? (
                <Text size="sm" c="dimmed">No manual edits.</Text>
              ) : (
                <Stack gap={4}>
                  {d.edits.map((e) => (
                    <Group key={e.id} gap="xs" wrap="nowrap" align="flex-start">
                      <Badge size="sm" variant="light">
                        {e.changeType}
                      </Badge>
                      <Text size="xs" c="dimmed" style={{ whiteSpace: 'nowrap' }}>
                        {fmtInstantLocal(e.editedAt)}
                      </Text>
                      <Text size="sm">{e.reason}</Text>
                    </Group>
                  ))}
                </Stack>
              )}

              {canEdit && d.employee && (
                <>
                  <Divider />
                  <Group>
                    <Button
                      variant="default"
                      leftSection={<IconCalendarOff size={14} />}
                      onClick={() => {
                        // Phase 7 will own the leave form. Deep-link with prefilled query so the
                        // leave page can pick up the context once it exists.
                        const empId = d.employee?.id ?? '';
                        navigate(
                          `/leave-requests/new?employeeId=${empId}&date=${d.workDate}&retroactive=1`
                        );
                      }}
                    >
                      Register retroactive leave
                    </Button>
                  </Group>
                </>
              )}
            </Stack>
          )}
        </ScrollArea>
      </Drawer>

      {editMode && timeCardId && (
        <EditPunchModal
          opened={editMode !== null}
          onClose={() => setEditMode(null)}
          timeCardId={timeCardId}
          mode={editMode}
          punch={editPunch ?? undefined}
          defaultTime={d?.scheduledStart ?? d?.actualStart ?? new Date().toISOString()}
        />
      )}
    </>
  );
}

function SummaryGrid({ d }: { d: TimeCardDetail }) {
  return (
    <Stack gap={4}>
      {d.employee && (
        <Text size="sm">
          <strong>Employee:</strong> {d.employee.name}
        </Text>
      )}
      <Group gap="lg">
        <Text size="sm">
          <strong>Worked:</strong> {fmtMinutes(d.workedMinutes)}
        </Text>
        <Text size="sm">
          <strong>Break:</strong> {fmtMinutes(d.breakMinutes)}
        </Text>
        <Text size="sm">
          <strong>OT:</strong> {fmtMinutes(d.overtimeMinutes)}
        </Text>
      </Group>
      <Group gap="lg">
        <Text size="sm">
          <strong>Late:</strong> {d.lateMinutes}m
        </Text>
        <Text size="sm">
          <strong>Early out:</strong> {d.earlyOutMinutes}m
        </Text>
      </Group>
      <Text size="sm">
        <strong>Scheduled:</strong> {fmtTimeLocal(d.scheduledStart)} – {fmtTimeLocal(d.scheduledEnd)}
      </Text>
      <Text size="sm">
        <strong>Actual:</strong> {fmtTimeLocal(d.actualStart)} – {fmtTimeLocal(d.actualEnd)}
      </Text>
      {d.notes && (
        <Text size="sm" c="dimmed">
          <strong>Notes:</strong> {d.notes}
        </Text>
      )}
    </Stack>
  );
}
