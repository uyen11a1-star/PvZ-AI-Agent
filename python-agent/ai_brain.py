from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any
from openai import OpenAI

SYSTEM_PROMPT = r'''
You are the decision engine for a mobile tower-defense game automation prototype.
Analyze the current screenshot and choose EXACTLY ONE safe action.

Return ONLY valid JSON:
{
  "action": "wait" | "tap" | "swipe",
  "x": number | null,
  "y": number | null,
  "x2": number | null,
  "y2": number | null,
  "duration_ms": number | null,
  "confidence": number,
  "reason": string
}

Rules:
- Prefer WAIT when the screenshot is ambiguous.
- Never invent controls that are not visible.
- Keep coordinates inside the screenshot.
- One action only per response.
- For TAP use x/y. For SWIPE use x/y/x2/y2/duration_ms.
- Confidence must be 0..1.
- Do not output markdown.
'''

@dataclass
class Action:
    action: str
    x: int | None
    y: int | None
    x2: int | None
    y2: int | None
    duration_ms: int | None
    confidence: float
    reason: str

    @classmethod
    def from_payload(cls, payload: dict[str, Any], width: int, height: int) -> 'Action':
        action = payload.get('action')
        if action not in {'wait', 'tap', 'swipe'}:
            raise ValueError(f'Unsupported action: {action!r}')
        def maybe_int(name: str):
            v = payload.get(name)
            return None if v is None else int(round(float(v)))
        x, y, x2, y2 = (maybe_int(k) for k in ('x','y','x2','y2'))
        for name, value, limit in (('x',x,width),('x2',x2,width),('y',y,height),('y2',y2,height)):
            if value is not None and not (0 <= value < limit):
                raise ValueError(f'{name} out of bounds: {value}')
        confidence = max(0.0, min(1.0, float(payload.get('confidence',0.0))))
        reason = str(payload.get('reason',''))[:240]
        if action == 'tap' and (x is None or y is None): raise ValueError('tap requires x/y')
        if action == 'swipe' and None in (x,y,x2,y2): raise ValueError('swipe requires coordinates')
        return cls(action,x,y,x2,y2,maybe_int('duration_ms'),confidence,reason)

class OmniRouteBrain:
    def __init__(self, base_url: str, api_key: str, model: str, timeout: float = 25) -> None:
        if not api_key: raise ValueError('OMNI_API_KEY is empty')
        self.client = OpenAI(base_url=base_url, api_key=api_key, timeout=timeout)
        self.model = model

    def decide(self, image_data_url: str, width: int, height: int) -> Action:
        prompt = f'Screenshot resolution: {width}x{height}. Choose one safe action. If uncertain, wait.'
        response = self.client.chat.completions.create(
            model=self.model,
            temperature=0,
            response_format={'type':'json_object'},
            messages=[
                {'role':'system','content':SYSTEM_PROMPT},
                {'role':'user','content':[{'type':'text','text':prompt},{'type':'image_url','image_url':{'url':image_data_url}}]}
            ],
        )
        payload = json.loads(response.choices[0].message.content or '{}')
        return Action.from_payload(payload,width,height)
