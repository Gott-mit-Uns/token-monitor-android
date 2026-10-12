"""Public fixtures for history verification. No Hub, device or credential access."""
import copy
import json
import unittest
from history_read_evidence import parse_history_events, validate_history_events


def event(at, phase, cycle=1, target=1, attempt=1, request=1, changed=False, eligible=False):
    return {"atMs": at, "target": target, "changed": changed, "cycle": cycle,
            "request": request, "attempt": attempt, "phase": phase, "retryEligible": eligible,
            "revisionChanged": changed, "bindingChanged": False}


def bridge_events(count):
    return [item for n in range(1, count + 1) for item in (
        {"sequence": n, "phase": "STARTED", "atNs": n * 10000, "active": 1},
        {"sequence": n, "phase": "ENDED", "atNs": n * 10000 + 1, "active": 0})]


class HistoryEvidenceTest(unittest.TestCase):
    def short_revision(self):
        return [event(63000, "QUEUED", attempt=0, request=0), event(63227, "STARTED"),
                event(63228, "RESOLVED"),
                event(64911, "QUEUED", cycle=2, target=2, attempt=0, request=0, changed=True),
                event(64912, "STARTED", cycle=2, target=2, request=2),
                event(64913, "RESOLVED", cycle=2, target=2, request=2)]

    def retry(self, gap=4000, terminal="FAILED", eligible=True):
        return [event(1, "QUEUED", attempt=0, request=0), event(63227, "STARTED"),
                event(63228, terminal, eligible=eligible),
                event(63228 + gap, "STARTED", attempt=2, request=2),
                event(63229 + gap, "RESOLVED", attempt=2, request=2)]

    def test_original_aggregate_rule_rejects_a_witnessed_legal_revision_change(self):
        # Preserve the old check as a negative control: it cannot distinguish these cycles.
        with self.assertRaises(AssertionError):
            assert (64912 - 63227) / 1000 >= 3.5
        result = validate_history_events(self.short_revision(), bridge_events(2), 2)
        self.assertEqual(1, result["shortNewTargetGaps"])
        self.assertEqual(0, result["sameCycleRetries"])

    def test_same_cycle_short_gap_and_3999_boundary_still_fail(self):
        for gap in (1684, 3999):
            with self.subTest(gap=gap), self.assertRaisesRegex(AssertionError, "four-second"):
                validate_history_events(self.retry(gap), bridge_events(2), 2)
        self.assertEqual(1, validate_history_events(self.retry(), bridge_events(2), 2)["sameCycleRetries"])

    def test_preview_backed_empty_result_requires_retry_eligibility_witness(self):
        self.assertEqual(1, validate_history_events(self.retry(terminal="RESOLVED"), bridge_events(2), 2)["sameCycleRetries"])
        with self.assertRaisesRegex(AssertionError, "eligible"):
            validate_history_events(self.retry(terminal="RESOLVED", eligible=False), bridge_events(2), 2)

    def test_revision_change_without_change_event_and_unexplained_cycle_reset_fail(self):
        events = self.short_revision()
        events[3]["changed"] = False
        with self.assertRaisesRegex(AssertionError, "reason"):
            validate_history_events(events, bridge_events(2), 2)
        events = self.short_revision()
        for item in events[3:]: item.update(target=1, changed=False, revisionChanged=False)
        with self.assertRaisesRegex(AssertionError, "without a new target"):
            validate_history_events(events, bridge_events(2), 2)

    def test_missing_native_start_and_wrong_terminal_request_fail(self):
        events = self.short_revision()
        events[-1]["request"] = 99
        with self.assertRaisesRegex(AssertionError, "terminal"):
            validate_history_events(events, bridge_events(2), 2)
        with self.assertRaisesRegex(AssertionError, "associated"):
            validate_history_events(self.short_revision(), bridge_events(3), 3)

    def test_binding_change_is_distinguished_from_a_history_revision_change(self):
        events = self.short_revision()
        events[3].update(revisionChanged=False, bindingChanged=True)
        result = validate_history_events(events, bridge_events(2), 2)
        self.assertEqual(0, result["revisionChanges"])
        self.assertEqual(1, result["bindingChanges"])

    def test_reused_native_request_identity_cannot_be_matched_by_route_count_alone(self):
        events = self.short_revision()
        events[-2]["request"] = 1
        with self.assertRaisesRegex(AssertionError, "reused"):
            validate_history_events(events, bridge_events(2), 2)

    def test_app_overlap_fails_even_with_different_revisions(self):
        events = self.short_revision()
        del events[2]
        with self.assertRaisesRegex(AssertionError, "overlapped"):
            validate_history_events(events, bridge_events(2), 2)

    def test_bridge_overlap_and_unclosed_process_fail(self):
        host = bridge_events(2)
        host[1], host[2] = host[2], host[1]
        host[1]["active"] = 2
        with self.assertRaisesRegex(AssertionError, "overlapped"):
            validate_history_events(self.short_revision(), host, 2)
        with self.assertRaisesRegex(AssertionError, "all end"):
            validate_history_events(self.short_revision(), bridge_events(2)[:-1], 2)

    def test_four_attempts_per_cycle_and_fifth_rejected_before_archiving(self):
        events = [event(0, "QUEUED", attempt=0, request=0)]
        for n in range(1, 5):
            events += [event(n * 5000, "STARTED", attempt=n, request=n),
                       event(n * 5000 + 1, "FAILED", attempt=n, request=n, eligible=n < 4)]
        self.assertEqual(3, validate_history_events(events, bridge_events(4), 4)["sameCycleRetries"])
        fifth = event(25000, "STARTED", attempt=5, request=5)
        with self.assertRaisesRegex(AssertionError, "values"):
            self.parse([fifth])

    def test_more_than_four_total_reads_require_more_than_one_witnessed_cycle(self):
        events = []
        for n in range(1, 6):
            events += [event(n * 1000, "QUEUED", cycle=n, target=n, request=0,
                             attempt=0, changed=n > 1),
                       event(n * 1000 + 1, "STARTED", cycle=n, target=n, request=n),
                       event(n * 1000 + 2, "RESOLVED", cycle=n, target=n, request=n)]
        result = validate_history_events(events, bridge_events(5), 5)
        self.assertEqual(5, result["cycles"])
        self.assertEqual(4, result["shortNewTargetGaps"])

    def parse(self, events):
        return parse_history_events("\n".join("INSTRUMENTATION_STATUS: nativeHistoryEvent=" + json.dumps(item) for item in events))

    def test_archive_parser_accepts_only_safe_schema_and_never_echoes_rejected_values(self):
        events = self.short_revision()
        self.assertEqual(events, self.parse(events))
        for field, value in (("signature", "PRIVATE-SENTINEL"), ("atMs", True), ("phase", "PRIVATE-SENTINEL")):
            unsafe = copy.deepcopy(events)
            unsafe[0][field] = value
            with self.assertRaises(AssertionError) as caught:
                self.parse(unsafe)
            self.assertNotIn("PRIVATE-SENTINEL", str(caught.exception))
        with self.assertRaisesRegex(AssertionError, "unavailable"):
            validate_history_events([], [], 0)


if __name__ == "__main__":
    unittest.main()
