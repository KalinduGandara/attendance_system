import { Box, Group, Stack, Text } from '@mantine/core';
import type { SegmentRequest, BreakRuleRequest } from '../types';

interface Props {
  segments: SegmentRequest[];
  breakRules: BreakRuleRequest[];
  color: string;
}

const MINUTES_PER_DAY = 1440;
const DAY_END_MINUTES = 1800; // show up to 30h so cross-midnight shifts render

function minuteToHHMM(m: number): string {
  const day = Math.floor(m / MINUTES_PER_DAY);
  const within = m % MINUTES_PER_DAY;
  const hh = String(Math.floor(within / 60)).padStart(2, '0');
  const mm = String(within % 60).padStart(2, '0');
  return day > 0 ? `${hh}:${mm} (+${day}d)` : `${hh}:${mm}`;
}

export function SchedulePreview({ segments, breakRules, color }: Props) {
  const totalSpan = DAY_END_MINUTES;
  const ticks = [0, 360, 720, 1080, 1440];

  return (
    <Stack gap="xs">
      <Text size="sm" fw={500}>
        Schedule preview
      </Text>
      <Box
        style={{
          position: 'relative',
          height: 56,
          border: '1px solid #e5e7eb',
          background: '#f9fafb',
          borderRadius: 4
        }}
      >
        {segments.map((seg, idx) => {
          const start = Math.max(0, Math.min(totalSpan, seg.startMinuteOfDay));
          const end = Math.max(0, Math.min(totalSpan, seg.endMinuteOfDay));
          const left = (start / totalSpan) * 100;
          const width = ((end - start) / totalSpan) * 100;
          if (width <= 0) return null;
          return (
            <Box
              key={idx}
              title={`${minuteToHHMM(seg.startMinuteOfDay)} – ${minuteToHHMM(seg.endMinuteOfDay)}`}
              style={{
                position: 'absolute',
                top: 8,
                bottom: 8,
                left: `${left}%`,
                width: `${width}%`,
                background: color,
                opacity: 0.85,
                borderRadius: 4
              }}
            />
          );
        })}
        {breakRules
          .filter((b) => b.earliestStartMinute != null && b.durationMinutes > 0)
          .map((b, idx) => {
            const start = Math.max(0, Math.min(totalSpan, b.earliestStartMinute ?? 0));
            const end = Math.max(0, Math.min(totalSpan, start + b.durationMinutes));
            const left = (start / totalSpan) * 100;
            const width = ((end - start) / totalSpan) * 100;
            if (width <= 0) return null;
            return (
              <Box
                key={`b${idx}`}
                title={`${b.name}: ${b.durationMinutes} min`}
                style={{
                  position: 'absolute',
                  top: 18,
                  bottom: 18,
                  left: `${left}%`,
                  width: `${width}%`,
                  background: 'rgba(0,0,0,0.35)',
                  borderRadius: 2
                }}
              />
            );
          })}
        {ticks.map((t) => (
          <Box
            key={t}
            style={{
              position: 'absolute',
              top: 0,
              bottom: 0,
              left: `${(t / totalSpan) * 100}%`,
              width: 1,
              background: '#d1d5db'
            }}
          />
        ))}
      </Box>
      <Group justify="space-between">
        {ticks.map((t) => (
          <Text key={t} size="xs" c="dimmed">
            {minuteToHHMM(t)}
          </Text>
        ))}
      </Group>
    </Stack>
  );
}
