import { useMemo, useState } from 'react';
import {
  Badge,
  Group,
  Paper,
  SegmentedControl,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Title
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import { timecardApi } from '../api';
import type { DailyTimeCardStatus, TimeCard } from '../types';
import { STATUS_COLORS, fmtMinutes, toDateInputString } from '../format';
import { TimeCardDetailDrawer } from '../components/TimeCardDetailDrawer';
import '../components/calendar.css';

type ViewMode = 'calendar' | 'list';

const STATUS_OPTS = ['PRESENT', 'ABSENT', 'LEAVE', 'HOLIDAY', 'OFF', 'PARTIAL'];

function startOfMonth(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), 1);
}

function endOfMonth(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth() + 1, 0);
}

export function TimeCardDashboardPage() {
  const { t } = useTranslation();
  const [view, setView] = useState<ViewMode>('calendar');
  const [employeeId, setEmployeeId] = useState('');
  const [status, setStatus] = useState<string | null>(null);
  const [month, setMonth] = useState<Date>(startOfMonth(new Date()));
  const [listFrom, setListFrom] = useState<Date | null>(null);
  const [listTo, setListTo] = useState<Date | null>(null);
  const [selectedTimeCardId, setSelectedTimeCardId] = useState<string | null>(null);

  const calendarRange = useMemo(() => {
    const from = startOfMonth(month);
    const to = endOfMonth(month);
    return {
      from: toDateInputString(from),
      to: toDateInputString(to)
    };
  }, [month]);

  const params =
    view === 'calendar'
      ? {
          employeeId: employeeId || undefined,
          status: status || undefined,
          from: calendarRange.from,
          to: calendarRange.to
        }
      : {
          employeeId: employeeId || undefined,
          status: status || undefined,
          from: listFrom ? toDateInputString(listFrom) : undefined,
          to: listTo ? toDateInputString(listTo) : undefined
        };

  const cards = useQuery({
    queryKey: ['timecards', view, params],
    queryFn: () => timecardApi.listTimeCards(params)
  });

  const events = useMemo(() => toCalendarEvents(cards.data ?? []), [cards.data]);

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>{t('timecard.title')}</Title>
        <SegmentedControl
          value={view}
          onChange={(v) => setView(v as ViewMode)}
          data={[
            { value: 'calendar', label: t('timecard.view.calendar') },
            { value: 'list', label: t('timecard.view.list') }
          ]}
        />
      </Group>

      <Paper withBorder p="md">
        <Group mb="sm" align="flex-end">
          <TextInput
            label={t('timecard.filters.employeeId')}
            placeholder={t('timecard.filters.employeeIdPlaceholder')}
            value={employeeId}
            onChange={(e) => setEmployeeId(e.currentTarget.value)}
            style={{ flex: 1 }}
          />
          <Select
            label={t('timecard.filters.status')}
            data={STATUS_OPTS.map((s) => ({ value: s, label: t(`timecard.status.${s}`) }))}
            value={status}
            onChange={setStatus}
            clearable
            style={{ width: 160 }}
          />
          {view === 'list' && (
            <>
              <DateInput
                label={t('timecard.filters.from')}
                value={listFrom}
                onChange={setListFrom}
                clearable
                style={{ width: 160 }}
              />
              <DateInput
                label={t('timecard.filters.to')}
                value={listTo}
                onChange={setListTo}
                clearable
                style={{ width: 160 }}
              />
            </>
          )}
        </Group>

        {view === 'calendar' ? (
          <FullCalendar
            plugins={[dayGridPlugin, interactionPlugin]}
            initialView="dayGridMonth"
            height="auto"
            firstDay={1}
            events={events}
            initialDate={month}
            datesSet={(arg) => {
              const start = startOfMonth(arg.view.currentStart);
              if (start.getTime() !== month.getTime()) {
                setMonth(start);
              }
            }}
            eventClick={(info) => {
              setSelectedTimeCardId(info.event.extendedProps.timeCardId as string);
            }}
            dayMaxEvents={4}
          />
        ) : (
          <CardsTable cards={cards.data ?? []} onSelect={setSelectedTimeCardId} />
        )}
      </Paper>

      <TimeCardDetailDrawer
        timeCardId={selectedTimeCardId}
        onClose={() => setSelectedTimeCardId(null)}
      />
    </Stack>
  );
}

interface CalendarEvent {
  id: string;
  title: string;
  start: string;
  allDay: true;
  backgroundColor: string;
  borderColor: string;
  textColor: string;
  extendedProps: { timeCardId: string };
}

function toCalendarEvents(cards: TimeCard[]): CalendarEvent[] {
  return cards.map((c) => {
    // Prefer the resolved shift color; fall back to a status-derived color so OFF/ABSENT
    // are visually distinct from the working days.
    const tone = c.resolvedShift?.color ?? statusFallbackColor(c.status);
    return {
      id: c.id,
      title: buildCalendarTitle(c),
      start: c.workDate,
      allDay: true,
      backgroundColor: tone,
      borderColor: tone,
      textColor: pickReadableTextColor(tone),
      extendedProps: { timeCardId: c.id }
    };
  });
}

function buildCalendarTitle(c: TimeCard): string {
  const parts: string[] = [c.status];
  if (c.workedMinutes > 0) {
    parts.push(fmtMinutes(c.workedMinutes));
  }
  if (c.exceptions.length > 0) {
    parts.push(`⚠ ${c.exceptions.length}`);
  }
  return parts.join(' · ');
}

function statusFallbackColor(status: DailyTimeCardStatus): string {
  switch (status) {
    case 'PRESENT':
      return '#22c55e';
    case 'ABSENT':
      return '#ef4444';
    case 'LEAVE':
      return '#3b82f6';
    case 'HOLIDAY':
      return '#14b8a6';
    case 'PARTIAL':
      return '#eab308';
    case 'OFF':
    default:
      return '#9ca3af';
  }
}

function pickReadableTextColor(bg: string): string {
  // Strip the leading '#' and parse R/G/B; default to white if anything goes wrong.
  const hex = bg.startsWith('#') ? bg.slice(1) : bg;
  if (hex.length !== 6) return '#fff';
  const r = parseInt(hex.slice(0, 2), 16);
  const g = parseInt(hex.slice(2, 4), 16);
  const b = parseInt(hex.slice(4, 6), 16);
  if ([r, g, b].some(Number.isNaN)) return '#fff';
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.6 ? '#111827' : '#fff';
}

interface CardsTableProps {
  cards: TimeCard[];
  onSelect: (id: string) => void;
}

function CardsTable({ cards, onSelect }: CardsTableProps) {
  const { t } = useTranslation();
  return (
    <Table striped highlightOnHover>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>{t('timecard.table.date')}</Table.Th>
          <Table.Th>{t('timecard.table.employee')}</Table.Th>
          <Table.Th>{t('timecard.table.shift')}</Table.Th>
          <Table.Th>{t('timecard.table.status')}</Table.Th>
          <Table.Th>{t('timecard.table.worked')}</Table.Th>
          <Table.Th>{t('timecard.table.overtime')}</Table.Th>
          <Table.Th>{t('timecard.table.late')}</Table.Th>
          <Table.Th>{t('timecard.table.exceptions')}</Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {cards.map((c) => (
          <Table.Tr
            key={c.id}
            style={{ cursor: 'pointer' }}
            onClick={() => onSelect(c.id)}
          >
            <Table.Td>{c.workDate}</Table.Td>
            <Table.Td>{c.employee?.name ?? '—'}</Table.Td>
            <Table.Td>{c.resolvedShift?.name ?? '—'}</Table.Td>
            <Table.Td>
              <Badge color={STATUS_COLORS[c.status]} variant="light">
                {t(`timecard.status.${c.status}`)}
              </Badge>
            </Table.Td>
            <Table.Td>{fmtMinutes(c.workedMinutes)}</Table.Td>
            <Table.Td>{c.overtimeMinutes > 0 ? fmtMinutes(c.overtimeMinutes) : '—'}</Table.Td>
            <Table.Td>{c.lateMinutes > 0 ? `${c.lateMinutes}m` : '—'}</Table.Td>
            <Table.Td>
              {c.exceptions.length === 0 ? (
                '—'
              ) : (
                <Group gap={4}>
                  {c.exceptions.map((ex) => (
                    <Badge key={ex.id} color="orange" variant="outline" size="sm">
                      {ex.type}
                    </Badge>
                  ))}
                </Group>
              )}
            </Table.Td>
          </Table.Tr>
        ))}
        {cards.length === 0 && (
          <Table.Tr>
            <Table.Td colSpan={8}>
              <Text c="dimmed">{t('timecard.empty')}</Text>
            </Table.Td>
          </Table.Tr>
        )}
      </Table.Tbody>
    </Table>
  );
}
