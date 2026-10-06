from __future__ import annotations

import argparse
import os
import time
from collections import deque

from dotenv import load_dotenv

from android_bridge import AndroidBridge, BridgeError
from ai_brain import OmniRouteBrain, Action
from vision import image_fingerprint, image_to_data_url

load_dotenv()


def env_bool(name: str, default: bool) -> bool:
    v = os.getenv(name)
    return default if v is None else v.strip().lower() in {'1','true','yes','on'}


def execute(bridge: AndroidBridge, action: Action, dry_run: bool, min_conf: float) -> None:
    if action.action == 'wait':
        print(f'[wait] conf={action.confidence:.2f} {action.reason}')
        return
    if action.confidence < min_conf:
        print(f'[skip] low confidence={action.confidence:.2f} {action.reason}')
        return
    if action.action == 'tap':
        print(f'[tap] {action.x},{action.y} conf={action.confidence:.2f} {action.reason}')
        if not dry_run: bridge.tap(action.x, action.y)  # type: ignore[arg-type]
    elif action.action == 'swipe':
        print(f'[swipe] {action.x},{action.y}->{action.x2},{action.y2} {action.duration_ms}ms conf={action.confidence:.2f} {action.reason}')
        if not dry_run: bridge.swipe(action.x, action.y, action.x2, action.y2, action.duration_ms or 250)  # type: ignore[arg-type]


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument('--dry-run', action='store_true')
    args = p.parse_args()

    base = os.getenv('BRIDGE_BASE_URL','http://127.0.0.1:8765')
    bridge = AndroidBridge(base)
    try:
        print('[bridge]', bridge.health())
        w,h = bridge.screen_size()
        print(f'[screen] {w}x{h}')
    except BridgeError as exc:
        print('[bridge-error]', exc)
        print('Enable PvZ AI Bridge in Android Accessibility settings first.')
        return

    dry_run = args.dry_run or env_bool('DRY_RUN', True)
    interval = float(os.getenv('DECISION_INTERVAL_SEC','1.4'))
    timeout = float(os.getenv('API_TIMEOUT_SEC','25'))
    max_actions = int(os.getenv('MAX_ACTIONS_PER_MINUTE','40'))
    min_conf = float(os.getenv('MIN_CONFIDENCE','0.55'))
    brain = OmniRouteBrain(os.getenv('OMNI_BASE_URL','http://127.0.0.1:20128/v1'), os.getenv('OMNI_API_KEY',''), os.getenv('OMNI_MODEL','auto'), timeout)
    print('Mode:', 'DRY RUN' if dry_run else 'LIVE')
    print('Transport: Android Accessibility bridge (no ADB/Pillow/OpenCV)')

    previous = None
    action_times: deque[float] = deque()
    last = 0.0
    try:
        while True:
            now = time.monotonic()
            while action_times and now-action_times[0] > 60: action_times.popleft()
            if len(action_times) >= max_actions:
                time.sleep(1)
                continue
            if now-last < interval:
                time.sleep(0.05)
                continue
            img = bridge.screenshot()
            fp = image_fingerprint(img)
            if fp == previous:
                print('[screen] unchanged')
                time.sleep(0.15)
                continue
            previous = fp
            data_url, size = image_to_data_url(img)
            print(f'[vision] {size[0]}x{size[1]} jpeg={len(img)/1024:.1f}KB')
            try:
                action = brain.decide(data_url,size[0],size[1])
                print(f'[ai] {action.action} conf={action.confidence:.2f}: {action.reason}')
                execute(bridge,action,dry_run,min_conf)
                if action.action != 'wait' and action.confidence >= min_conf:
                    action_times.append(time.monotonic())
            except Exception as exc:
                print(f'[ai-error] {type(exc).__name__}: {exc}')
            last = time.monotonic()
    except KeyboardInterrupt:
        print('Stopped.')

if __name__ == '__main__':
    main()
