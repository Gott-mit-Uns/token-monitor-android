"""Fail-closed validation of test-only, run-local history identities; no private wire values."""
import json
import re

PHASES = {"CACHED", "QUEUED", "STARTED", "RESOLVED", "FAILED", "CANCELLED", "STALE", "AUTH_BLOCKED"}
TERMINALS = PHASES - {"CACHED", "QUEUED", "STARTED"}


def parse_history_events(transcript):
    events = []
    for raw in re.findall(r"(?m)^INSTRUMENTATION_STATUS: nativeHistoryEvent=(.*)\r?$", transcript):
        if len(raw) > 512:
            raise AssertionError("History identity event exceeds its safe bound")
        try:
            event = json.loads(raw)
        except (ValueError, TypeError):
            raise AssertionError("History identity event is malformed") from None
        if not isinstance(event, dict) or set(event) != {"atMs", "target", "changed", "cycle", "request", "attempt", "phase", "retryEligible", "revisionChanged", "bindingChanged"}:
            raise AssertionError("History identity event fields unavailable")
        if (any(type(event[name]) is not int or event[name] < 0
                for name in ("atMs", "target", "cycle", "request", "attempt")) or
                type(event["changed"]) is not bool or type(event["retryEligible"]) is not bool or
                type(event["revisionChanged"]) is not bool or type(event["bindingChanged"]) is not bool or
                not isinstance(event["phase"], str) or event["phase"] not in PHASES or
                event["target"] == 0 or event["cycle"] == 0 or event["attempt"] > 4):
            raise AssertionError("History identity event values unavailable")
        events.append(event)
    return events


def validate_history_events(events, bridge_events, history_reads):
    """Home has no manual/force refresh. Every new cycle must have a witnessed new target.

    App milliseconds and host nanoseconds are separate clocks: compare intervals within
    each clock only, associate STARTEDs by ordinal only after exact counts/identity checks.
    """
    if not events:
        raise AssertionError("Native history identity timeline unavailable")
    starts = []
    seen_cycles = {}
    pending = None
    previous_time = -1
    previous_target = 0
    previous_cycle = 0
    last_terminal = {}
    revision_cycles = set()
    binding_cycles = set()
    for event in events:
        at, target, cycle = event["atMs"], event["target"], event["cycle"]
        if at < previous_time or target < previous_target or cycle < previous_cycle:
            raise AssertionError("History identity timeline is not monotonic")
        if event["changed"] != (event["revisionChanged"] or event["bindingChanged"]):
            raise AssertionError("History target change reason is inconsistent")
        if event["revisionChanged"]: revision_cycles.add(cycle)
        if event["bindingChanged"]: binding_cycles.add(cycle)
        if target != previous_target:
            if target != previous_target + 1 or event["changed"] != (previous_target > 0):
                raise AssertionError("History target change was not witnessed")
            if cycle <= previous_cycle:
                raise AssertionError("New history target reused an old cycle")
        elif event["changed"] or (previous_cycle and cycle != previous_cycle):
            raise AssertionError("Home history cycle changed without a new target")
        previous_time, previous_target, previous_cycle = at, target, cycle
        phase = event["phase"]
        identity = (target, cycle, event["request"], event["attempt"])
        if phase == "STARTED":
            if pending is not None or event["request"] == 0:
                raise AssertionError("History request overlapped or has no identity")
            if starts and event["request"] <= starts[-1]["request"]:
                raise AssertionError("History request identity was reused")
            attempts = seen_cycles.setdefault(cycle, [])
            if event["attempt"] != len(attempts) + 1:
                raise AssertionError("History cycle attempt budget is inconsistent")
            if attempts:
                terminal = last_terminal.get(cycle)
                if terminal is None or not terminal["retryEligible"]:
                    raise AssertionError("History retry lacks an eligible terminal witness")
                if at - terminal["atMs"] < 4000:
                    raise AssertionError("Same-cycle history retry violated the four-second wait")
            attempts.append(event)
            starts.append(event)
            pending = identity
        elif phase in TERMINALS:
            if pending != identity:
                raise AssertionError("History terminal does not match an active request")
            last_terminal[cycle] = event
            pending = None
        elif event["request"] != 0 or event["attempt"] != 0:
            raise AssertionError("Queued/cached history receipt invented a request")
    if pending is not None:
        raise AssertionError("Native history request did not terminate")
    host_starts = [event for event in bridge_events if event["phase"] == "STARTED"]
    if not (len(starts) == len(host_starts) == history_reads):
        raise AssertionError("Native/bridge history starts could not be associated exactly")
    active = None
    host_time = -1
    ended = 0
    for event in bridge_events:
        if event["atNs"] < host_time:
            raise AssertionError("Bridge history timeline is not monotonic")
        host_time = event["atNs"]
        if event["phase"] == "STARTED":
            if active is not None or event["active"] != 1 or event["sequence"] != ended + 1:
                raise AssertionError("Bridge history requests overlapped or lost an ordinal")
            active = event["sequence"]
        elif event["phase"] == "ENDED":
            if active != event["sequence"] or event["active"] != 0:
                raise AssertionError("Bridge history end did not match its start")
            active = None
            ended += 1
        else:
            raise AssertionError("Bridge history phase unavailable")
    if active is not None or ended != history_reads:
        raise AssertionError("Bridge history requests did not all end")
    return {"reads": history_reads, "cycles": len(seen_cycles),
            "sameCycleRetries": history_reads - len(seen_cycles),
            "newTargetReads": len(seen_cycles) - 1,
            "revisionChanges": len(revision_cycles), "bindingChanges": len(binding_cycles),
            "shortNewTargetGaps": sum(right["atMs"] - left["atMs"] < 3500 and
                right["target"] != left["target"] for left, right in zip(starts, starts[1:]))}
