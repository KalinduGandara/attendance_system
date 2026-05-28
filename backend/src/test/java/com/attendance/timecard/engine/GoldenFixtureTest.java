package com.attendance.timecard.engine;

import com.attendance.shift.domain.BreakKind;
import com.attendance.shift.domain.GraceKind;
import com.attendance.shift.domain.RoundingKind;
import com.attendance.shift.domain.RoundingMode;
import com.attendance.shift.domain.ShiftType;
import com.attendance.timecard.domain.PunchEventType;
import com.attendance.timecard.engine.snapshots.LeaveSnapshot;
import com.attendance.timecard.engine.snapshots.PunchSnapshot;
import com.attendance.timecard.engine.snapshots.ShiftSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generates one dynamic test per YAML fixture under
 * {@code src/test/resources/fixtures/timecard/}. Each fixture exercises one
 * scenario end-to-end through the engine; assertions are declarative.
 */
class GoldenFixtureTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private static final Map<String, UUID> CODES = Map.of(
            "ATTEND", EngineTestFixtures.ATTEND,
            "OT_A", EngineTestFixtures.OT_A,
            "OT_B", EngineTestFixtures.OT_B,
            "LUNCH", EngineTestFixtures.LUNCH,
            "LEAVE", EngineTestFixtures.LEAVE);

    @TestFactory
    Stream<DynamicTest> goldenFixtures() throws IOException, URISyntaxException {
        Path root = Paths.get(Objects.requireNonNull(
                getClass().getClassLoader().getResource("fixtures/timecard")).toURI());
        try (var stream = Files.list(root)) {
            List<Path> files = stream
                    .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                    .sorted()
                    .toList();
            return files.stream().map(p -> DynamicTest.dynamicTest(p.getFileName().toString(),
                    () -> runFixture(p)));
        }
    }

    private void runFixture(Path file) throws IOException {
        GoldenFixture.FixtureFile spec = YAML.readValue(file.toFile(), GoldenFixture.FixtureFile.class);
        ZoneId zone = ZoneId.of(spec.zone());
        LocalDate workDate = LocalDate.parse(spec.workDate());

        ShiftSnapshot shift = toShift(spec.shift());
        List<PunchSnapshot> punches = toPunches(spec.punches(), workDate, zone);
        Map<UUID, BigDecimal> rates = toRates(spec.rates());

        EngineInputs.ScheduledState state = EngineInputs.ScheduledState.valueOf(
                spec.scheduledState() == null ? "SCHEDULED" : spec.scheduledState());
        boolean holiday = Boolean.TRUE.equals(spec.holiday());
        LeaveSnapshot leave = spec.leave() == null
                ? null
                : new LeaveSnapshot(codeFor(spec.leave().timeCode()), spec.leave().halfDay());

        EngineInputs inputs = new EngineInputs(UUID.randomUUID(),
                workDate, zone, state, shift, punches, holiday, leave, rates);

        EngineOutput out = TimeCardCalculator.compute(inputs);

        GoldenFixture.ExpectedSpec exp = spec.expected();
        if (exp.status() != null) {
            assertThat(out.status().name()).as("status").isEqualTo(exp.status());
        }
        if (exp.workedMinutes() != null) {
            assertThat(out.workedMinutes()).as("workedMinutes").isEqualTo(exp.workedMinutes());
        }
        if (exp.breakMinutes() != null) {
            assertThat(out.breakMinutes()).as("breakMinutes").isEqualTo(exp.breakMinutes());
        }
        if (exp.overtimeMinutes() != null) {
            assertThat(out.overtimeMinutes()).as("overtimeMinutes").isEqualTo(exp.overtimeMinutes());
        }
        if (exp.lateMinutes() != null) {
            assertThat(out.lateMinutes()).as("lateMinutes").isEqualTo(exp.lateMinutes());
        }
        if (exp.earlyOutMinutes() != null) {
            assertThat(out.earlyOutMinutes()).as("earlyOutMinutes").isEqualTo(exp.earlyOutMinutes());
        }
        if (exp.breakdownCodes() != null) {
            List<String> actualCodes = out.breakdowns().stream()
                    .map(b -> codeName(b.timeCodeId()))
                    .toList();
            assertThat(actualCodes).as("breakdown time codes").isEqualTo(exp.breakdownCodes());
        }
        if (exp.exceptions() != null) {
            List<String> actualEx = out.exceptions().stream()
                    .map(e -> e.type().name())
                    .toList();
            assertThat(actualEx).as("exceptions").containsExactlyInAnyOrderElementsOf(exp.exceptions());
        }
    }

    private ShiftSnapshot toShift(GoldenFixture.ShiftSpec spec) {
        if (spec == null) {
            return null;
        }
        return new ShiftSnapshot(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "test shift",
                ShiftType.valueOf(spec.type()),
                codeFor(spec.attendanceTimeCode()),
                spec.segments() == null ? List.of() : spec.segments().stream()
                        .map(s -> new ShiftSnapshot.SegmentSnapshot(0, s.startMinute(), s.endMinute(), s.requiredMinutes()))
                        .toList(),
                spec.rounding() == null ? List.of() : spec.rounding().stream()
                        .map(r -> new ShiftSnapshot.RoundingRuleSnapshot(
                                RoundingKind.valueOf(r.kind()), r.unit(), RoundingMode.valueOf(r.mode())))
                        .toList(),
                spec.grace() == null ? List.of() : spec.grace().stream()
                        .map(g -> new ShiftSnapshot.GraceRuleSnapshot(GraceKind.valueOf(g.kind()), g.minutes()))
                        .toList(),
                spec.breaks() == null ? List.of() : spec.breaks().stream()
                        .map(b -> new ShiftSnapshot.BreakRuleSnapshot(
                                "break", BreakKind.valueOf(b.kind()),
                                b.durationMinutes(),
                                null,
                                b.afterHoursWorked(),
                                Boolean.TRUE.equals(b.paid()),
                                b.timeCode() == null ? null : codeFor(b.timeCode())))
                        .toList(),
                spec.overtime() == null ? List.of() : spec.overtime().stream()
                        .map(o -> new ShiftSnapshot.OvertimeTierSnapshot(
                                o.sequence(), o.afterMinutes(), codeFor(o.timeCode()), o.maxMinutes()))
                        .toList(),
                Set.of());
    }

    private List<PunchSnapshot> toPunches(List<GoldenFixture.PunchSpec> specs, LocalDate workDate, ZoneId zone) {
        if (specs == null) {
            return List.of();
        }
        List<PunchSnapshot> out = new ArrayList<>(specs.size());
        for (int i = 0; i < specs.size(); i++) {
            GoldenFixture.PunchSpec ps = specs.get(i);
            String time = ps.time();
            int dayOffset = 0;
            if (time.contains("+1d")) {
                time = time.replace("+1d", "");
                dayOffset = 1;
            }
            LocalTime lt = LocalTime.parse(time);
            var instant = workDate.plusDays(dayOffset).atTime(lt).atZone(zone).toInstant();
            UUID id = UUID.fromString(String.format("00000000-0000-0000-0000-%012d", i + 1));
            out.add(new PunchSnapshot(id, PunchEventType.valueOf(ps.type()), instant));
        }
        return out;
    }

    private Map<UUID, BigDecimal> toRates(Map<String, String> rates) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        // sensible defaults so fixtures don't have to repeat them
        out.put(EngineTestFixtures.ATTEND, BigDecimal.ONE);
        out.put(EngineTestFixtures.OT_A, new BigDecimal("1.5"));
        out.put(EngineTestFixtures.OT_B, new BigDecimal("2.0"));
        out.put(EngineTestFixtures.LUNCH, BigDecimal.ONE);
        out.put(EngineTestFixtures.LEAVE, BigDecimal.ONE);
        if (rates != null) {
            for (Map.Entry<String, String> e : rates.entrySet()) {
                out.put(codeFor(e.getKey()), new BigDecimal(e.getValue()));
            }
        }
        return out;
    }

    private static UUID codeFor(String token) {
        UUID id = CODES.get(token);
        if (id == null) {
            throw new IllegalArgumentException("Unknown time-code token: " + token);
        }
        return id;
    }

    private static String codeName(UUID id) {
        for (Map.Entry<String, UUID> e : CODES.entrySet()) {
            if (e.getValue().equals(id)) {
                return e.getKey();
            }
        }
        return id.toString();
    }
}
