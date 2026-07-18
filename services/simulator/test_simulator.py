import os
import tempfile
import unittest
from datetime import datetime
from unittest.mock import patch
from zoneinfo import ZoneInfo

import simulator
from simulator import (
    DEVICE_PRESETS,
    SyntheticGenerator,
    is_active_at,
    load_cmapss_batches,
    load_cnc_batches,
    main,
    parse_active_days,
    parse_active_hours,
    require_ingest_api_key,
    send,
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

    def test_display_only_channels_keep_normal_target_during_anomaly(self):
        specs = {
            "display": {"normal": 50.0, "noise": 0.0, "anomaly": 50.0},
            "alerted": {"normal": 10.0, "noise": 0.0, "anomaly": 20.0},
        }
        generator = SyntheticGenerator(specs, seed=1, anomaly_rate=1.0)

        measurements = generator.next_measurements()

        self.assertEqual(measurements["display"], 50.0)
        self.assertGreater(measurements["alerted"], 10.0)


class ReplayPresetTest(unittest.TestCase):
    def test_public_presets_have_three_devices_and_twenty_channels(self):
        self.assertEqual([preset["code"] for preset in DEVICE_PRESETS], [
            "CMAPSS-U1", "CMAPSS-U2", "CNC-EXP01",
        ])
        self.assertEqual([len(preset["channels"]) for preset in DEVICE_PRESETS], [6, 6, 8])
        self.assertEqual([len(preset["synthetic"]) for preset in DEVICE_PRESETS], [6, 6, 8])
        for preset in DEVICE_PRESETS:
            self.assertEqual(set(preset["channels"]), set(preset["synthetic"]))

    def test_cmapss_loader_builds_six_channel_batch_and_skips_partial_row(self):
        values = [1, 7, 0, 0, 0] + list(range(1, 22))
        short_values = values[:-1]
        other_unit_values = [2, 8, *values[2:]]
        with tempfile.TemporaryDirectory() as data_dir, \
                patch.object(simulator, "DATA_DIR", data_dir):
            path = os.path.join(data_dir, "cmapss_train_FD001.txt")
            with open(path, "w") as data_file:
                data_file.write(" ".join(map(str, values)) + "\n")
                data_file.write(" ".join(map(str, short_values)) + "\n")
                data_file.write(" ".join(map(str, other_unit_values)) + "\n")

            batches = load_cmapss_batches(1, DEVICE_PRESETS[0]["channels"])

        self.assertEqual(batches, [(7, {
            "s2": 2.0,
            "s4": 4.0,
            "s7": 7.0,
            "s11": 11.0,
            "s15": 15.0,
            "s21": 21.0,
        })])

    def test_cnc_loader_builds_eight_channel_batch_and_keeps_valid_partial_values(self):
        channels = DEVICE_PRESETS[2]["channels"]
        rows = [
            {channel: str(index + 1) for index, channel in enumerate(channels)},
            {channel: "bad" for channel in channels},
        ]
        rows[1]["S1_OutputPower"] = "0.2"
        with tempfile.TemporaryDirectory() as data_dir, \
                patch.object(simulator, "DATA_DIR", data_dir):
            path = os.path.join(data_dir, "experiment.csv")
            with open(path, "w", newline="") as data_file:
                writer = simulator.csv.DictWriter(data_file, fieldnames=list(channels.values()))
                writer.writeheader()
                writer.writerows(rows)

            batches = load_cnc_batches("experiment.csv", channels)

        self.assertEqual(len(batches[0][1]), 8)
        self.assertEqual(batches[1], (1, {"S1_OutputPower": 0.2}))


class IngestAuthenticationTest(unittest.TestCase):
    def test_missing_or_blank_key_fails_validation(self):
        for environ in ({}, {"INGEST_API_KEY": "   "}):
            with self.subTest(environ=environ):
                with self.assertRaisesRegex(ValueError, "INGEST_API_KEY"):
                    require_ingest_api_key(environ)

    def test_key_is_trimmed_before_use(self):
        self.assertEqual(require_ingest_api_key({"INGEST_API_KEY": "  shared-key  "}), "shared-key")

    @patch("simulator.requests.post")
    def test_send_adds_ingest_header(self, post_mock):
        post_mock.return_value.status_code = 200

        self.assertTrue(send(
            "http://example", "TEST-DEVICE", 7, {"temperature": 12.3}, "shared-key"
        ))

        post_mock.assert_called_once_with(
            "http://example/sensor-data",
            json={
                "deviceCode": "TEST-DEVICE",
                "sourceSeq": 7,
                "measurements": {"temperature": 12.3},
            },
            headers={"X-Ingest-Key": "shared-key"},
            timeout=5,
        )

    @patch("simulator.ThreadPoolExecutor")
    def test_replay_and_synthetic_fail_fast_without_key(self, executor_mock):
        for mode in ("replay", "synthetic"):
            with self.subTest(mode=mode), \
                    patch.dict(os.environ, {}, clear=True), \
                    patch("sys.argv", ["simulator.py", "--mode", mode, "--all", "--limit", "1"]):
                with self.assertRaises(SystemExit):
                    main()

        executor_mock.assert_not_called()


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
            self.PRESET, 1.0, 4, "http://example", "test-ingest-key", 1, 0.0,
            set(range(7)), None, "UTC", stop_event,
        )

        self.assertEqual(send_mock.call_count, 4)
        self.assertEqual(stop_event.waits, [2.0, 1.0, 2.0])

    @patch("simulator.send", return_value=False)
    def test_failure_backoff_is_capped_at_sixty_seconds(self, send_mock):
        stop_event = RecordingStopEvent()

        synthetic_worker(
            self.PRESET, 1.0, 8, "http://example", "test-ingest-key", 1, 0.0,
            set(range(7)), None, "UTC", stop_event,
        )

        self.assertEqual(send_mock.call_count, 8)
        self.assertEqual(stop_event.waits, [2.0, 4.0, 8.0, 16.0, 32.0, 60.0, 60.0])

    @patch("simulator.send", return_value=True)
    def test_stop_event_ends_unlimited_stream(self, send_mock):
        stop_event = RecordingStopEvent(stop_after_wait=True)

        synthetic_worker(
            self.PRESET, 1.0, 0, "http://example", "test-ingest-key", 1, 0.0,
            set(range(7)), None, "UTC", stop_event,
        )

        self.assertEqual(send_mock.call_count, 1)
        self.assertTrue(stop_event.is_set())


if __name__ == "__main__":
    unittest.main()
