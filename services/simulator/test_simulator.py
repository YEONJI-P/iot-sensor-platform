import unittest
from datetime import datetime
from unittest.mock import patch
from zoneinfo import ZoneInfo

from simulator import (
    SyntheticGenerator,
    is_active_at,
    parse_active_days,
    parse_active_hours,
    synthetic_worker,
)


class RecordingStopEvent:
    def __init__(self, stop_after_wait=False):
        self.waits = []
        self.stopped = False
        self.stop_after_wait = stop_after_wait

    def is_set(self):
        return self.stopped

    def wait(self, seconds):
        self.waits.append(seconds)
        if self.stop_after_wait:
            self.stopped = True
        return self.stopped


class ScheduleTest(unittest.TestCase):
    def test_weekday_day_shift(self):
        days = parse_active_days("mon-fri")
        hours = parse_active_hours("08:00-18:00")
        timezone = ZoneInfo("Asia/Seoul")

        self.assertTrue(is_active_at(datetime(2026, 7, 20, 8, 0, tzinfo=timezone), days, hours))
        self.assertFalse(is_active_at(datetime(2026, 7, 20, 18, 0, tzinfo=timezone), days, hours))
        self.assertFalse(is_active_at(datetime(2026, 7, 19, 12, 0, tzinfo=timezone), days, hours))

    def test_overnight_shift_belongs_to_start_day(self):
        days = parse_active_days("fri")
        hours = parse_active_hours("22:00-06:00")
        timezone = ZoneInfo("Asia/Seoul")

        self.assertTrue(is_active_at(datetime(2026, 7, 17, 23, 0, tzinfo=timezone), days, hours))
        self.assertTrue(is_active_at(datetime(2026, 7, 18, 5, 59, tzinfo=timezone), days, hours))
        self.assertFalse(is_active_at(datetime(2026, 7, 18, 6, 0, tzinfo=timezone), days, hours))


class SyntheticGeneratorTest(unittest.TestCase):
    def test_same_seed_reproduces_same_sequence(self):
        specs = {"temperature": {"normal": 10.0, "noise": 0.2, "anomaly": 20.0}}
        first = SyntheticGenerator(specs, seed=42, anomaly_rate=0.1)
        second = SyntheticGenerator(specs, seed=42, anomaly_rate=0.1)

        self.assertEqual(
            [first.next_measurements() for _ in range(20)],
            [second.next_measurements() for _ in range(20)],
        )

    def test_anomaly_mode_moves_value_toward_anomaly_target(self):
        specs = {"temperature": {"normal": 10.0, "noise": 0.0, "anomaly": 20.0}}
        generator = SyntheticGenerator(specs, seed=1, anomaly_rate=1.0)

        values = [generator.next_measurements()["temperature"] for _ in range(3)]

        self.assertGreater(values[0], 10.0)
        self.assertGreater(values[2], values[0])


class SyntheticWorkerTest(unittest.TestCase):
    PRESET = {
        "code": "TEST-DEVICE",
        "label": "test",
        "synthetic": {"temperature": {"normal": 10.0, "noise": 0.0, "anomaly": 20.0}},
    }

    @patch("simulator.send", side_effect=[False, True, False, True])
    def test_failure_backoff_resets_after_success_and_limit_stops_without_final_wait(self, send_mock):
        stop_event = RecordingStopEvent()

        synthetic_worker(
            self.PRESET, 1.0, 4, "http://example", 1, 0.0,
            set(range(7)), None, "UTC", stop_event,
        )

        self.assertEqual(send_mock.call_count, 4)
        self.assertEqual(stop_event.waits, [2.0, 1.0, 2.0])

    @patch("simulator.send", return_value=False)
    def test_failure_backoff_is_capped_at_sixty_seconds(self, send_mock):
        stop_event = RecordingStopEvent()

        synthetic_worker(
            self.PRESET, 1.0, 8, "http://example", 1, 0.0,
            set(range(7)), None, "UTC", stop_event,
        )

        self.assertEqual(send_mock.call_count, 8)
        self.assertEqual(stop_event.waits, [2.0, 4.0, 8.0, 16.0, 32.0, 60.0, 60.0])

    @patch("simulator.send", return_value=True)
    def test_stop_event_ends_unlimited_stream(self, send_mock):
        stop_event = RecordingStopEvent(stop_after_wait=True)

        synthetic_worker(
            self.PRESET, 1.0, 0, "http://example", 1, 0.0,
            set(range(7)), None, "UTC", stop_event,
        )

        self.assertEqual(send_mock.call_count, 1)
        self.assertTrue(stop_event.is_set())


if __name__ == "__main__":
    unittest.main()
