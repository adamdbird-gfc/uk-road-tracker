"""Request-local pedestrian work limits; contains no coordinates or user identity."""
import asyncio
import time
from contextvars import ContextVar


class WalkingWorkLimit(Exception):
    def __init__(self, reason):
        self.reason = reason


class WalkingWorkBudget:
    def __init__(self, seconds=48.0, max_calls=96, call_seconds=10.0):
        self.started = time.monotonic()
        self.deadline = self.started + seconds
        self.max_calls = max_calls
        self.call_seconds = call_seconds
        self.calls = 0
        self.limited = 0

    def remaining(self):
        return max(0.0, self.deadline - time.monotonic())

    def exhausted(self):
        return self.remaining() <= 0 or self.calls >= self.max_calls

    async def call(self, operation):
        if self.exhausted():
            self.limited += 1
            raise WalkingWorkLimit('pedestrian_work_limit')
        self.calls += 1
        try:
            return await asyncio.wait_for(operation(), timeout=min(self.call_seconds, self.remaining()))
        except asyncio.TimeoutError as exc:
            self.limited += 1
            raise WalkingWorkLimit('pedestrian_upstream_timeout') from exc

    def summary(self):
        return {'policy_version': 2, 'router_calls': self.calls, 'limited_calls': self.limited,
                'elapsed_ms': int((time.monotonic()-self.started)*1000)}


walking_work = ContextVar('walking_work', default=None)
