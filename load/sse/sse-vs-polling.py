#!/usr/bin/env python3
"""SSE와 폴링이 회차 변화를 알아채는 속도를 같은 순간에 나란히 재고, 이벤트 직후 몰리는 재조회의 응답 시간을 잰다.

폴링을 빼도 되는지 판단하려면 두 가지를 알아야 한다.
  1. 같은 변화를 SSE 연결과 폴링 사용자가 각각 몇 초 뒤에 아는가              → compare
  2. SSE는 모두에게 같은 순간에 알리므로 재조회도 한순간에 몰린다. 서버가 버티는가 → burst

  ulimit -n 10240
  python3 load/sse/sse-vs-polling.py compare https://api.yufesta.com 1000 100 30 240
      SSE 연결 1000개 + 30초 주기 폴링 사용자 100명을 240초 동안 둔다. "대기 중"이 보이면 회차를 마감·발표한다.
      SSE 연결은 이벤트를 받으면 실제 화면처럼 요약을 한 번 다시 조회한다.
  python3 load/sse/sse-vs-polling.py burst https://api.yufesta.com 1000 0
      미리 열어 둔 연결 1000개로 요약 조회를 같은 순간에 보낸다. 마지막 값은 요청을 흩뿌리는 구간(초)이다.
      0이면 한순간, 1이면 1초 안에 무작위로 나눠 보낸다(프론트가 재조회에 무작위 지연을 넣은 경우).
      연결을 미리 열어 두고 쓰므로 구간은 40초 이하로 잡는다(ALB가 60초 동안 조용한 연결을 닫는다).

환경변수
  ACCESS_TOKEN   있으면 쿠키로 보낸다(로그인 연결). round-published는 로그인 연결에만 간다
  REQUERY_JITTER compare에서 이벤트를 받은 뒤 재조회 전에 기다리는 최대 시간(초, 기본 0)

폴링 사용자는 시작 시점을 주기 안에서 무작위로 흩뜨린다. 실제 사용자도 페이지를 연 시각이 제각각이다.
"""
import asyncio
import os
import random
import re
import ssl
import sys
import time
from collections import Counter, defaultdict
from urllib.parse import urlparse

mode, base = sys.argv[1], sys.argv[2]
url = urlparse(base)
port = url.port or (443 if url.scheme == "https" else 80)
context = ssl.create_default_context() if url.scheme == "https" else None
token = os.environ.get("ACCESS_TOKEN")
cookie = f"Cookie: access_token={token}\r\n" if token else ""
requery_jitter = float(os.environ.get("REQUERY_JITTER", "0"))

SUMMARY = "/api/v1/match/summary"
STATE = re.compile(rb'"currentRound":\{"seq":(\d+),"status":"([A-Z]+)"')
EVENT_OF = {"OPEN": "round-opened", "CLOSED": "round-closed", "PUBLISHED": "round-published"}


def clock(at):
    return time.strftime("%H:%M:%S", time.localtime(at)) + f".{int(at * 1000) % 1000:03d}"


def spread(values, unit=1000):
    """평균·중앙값·p95·최대를 한 줄로. unit=1000이면 ms, 1이면 초"""
    if not values:
        return "없음"
    ordered = sorted(values)
    pick = lambda ratio: ordered[min(len(ordered) - 1, int(len(ordered) * ratio))] * unit
    suffix = "ms" if unit == 1000 else "초"
    digits = 0 if unit == 1000 else 1
    return (f"평균 {sum(ordered) / len(ordered) * unit:.{digits}f}{suffix}, 중앙값 {pick(0.5):.{digits}f}{suffix}, "
            f"p95 {pick(0.95):.{digits}f}{suffix}, 최대 {ordered[-1] * unit:.{digits}f}{suffix} ({len(ordered)}건)")


async def open_connection():
    # 제한이 없으면 연결 하나가 안 열릴 때 운영체제 제한(수 분)까지 전체 측정이 끝나지 않는다
    return await asyncio.wait_for(asyncio.open_connection(url.hostname, port, ssl=context), 10)


def request(path, close):
    return (f"GET {path} HTTP/1.1\r\nHost: {url.hostname}\r\nAccept: application/json\r\n{cookie}"
            f"Connection: {'close' if close else 'keep-alive'}\r\n\r\n").encode()


async def read_response(reader):
    """연결을 닫지 않고도 응답 하나의 끝을 알 수 있게 길이·청크를 직접 읽는다"""
    status = int((await reader.readline()).split()[1])
    length, chunked = None, False
    while True:
        line = (await reader.readline()).decode().strip()
        if not line:
            break
        name, _, value = line.partition(":")
        if name.lower() == "content-length":
            length = int(value)
        elif name.lower() == "transfer-encoding" and "chunked" in value.lower():
            chunked = True
    body = b""
    if chunked:
        while True:
            size = int((await reader.readline()).strip().split(b";")[0], 16)
            if size == 0:
                await reader.readline()
                break
            body += await reader.readexactly(size)
            await reader.readline()
    elif length is not None:
        body = await reader.readexactly(length)
    else:
        body = await reader.read()
    return status, body


def state_of(body):
    found = STATE.search(body)
    return (int(found.group(1)), found.group(2).decode()) if found else None


async def fetch_summary():
    """새 연결로 요약을 한 번 읽는다. (상태 코드, 회차 상태, 연결에 걸린 시간, 응답에 걸린 시간)"""
    started = time.time()
    reader, writer = await open_connection()
    opened = time.time()
    try:
        writer.write(request(SUMMARY, close=True))
        await writer.drain()
        status, body = await read_response(reader)
        return status, state_of(body), opened - started, time.time() - opened
    finally:
        writer.close()


# ---------------------------------------------------------------- compare

arrivals = defaultdict(list)       # SSE 이벤트 이름 → 도착 시각들
detections = defaultdict(list)     # 폴링이 본 새 상태 → 알아챈 시각들
requeries = []                     # (연결 시간, 응답 시간, 새 상태를 봤는가)
counts = Counter()


async def sse_client(index):
    await asyncio.sleep(index * 0.005)          # 초당 200개씩 연다
    try:
        reader, writer = await open_connection()
        writer.write((f"GET /api/v1/sse/match HTTP/1.1\r\nHost: {url.hostname}\r\n"
                      f"Accept: text/event-stream\r\n{cookie}Connection: keep-alive\r\n\r\n").encode())
        await writer.drain()
        if " 200" not in (await reader.readline()).decode():
            counts["sse 거절"] += 1
            return
        counts["sse 연결"] += 1
        event = None
        while True:
            raw = await reader.readline()
            if not raw:
                counts["sse 끊김"] += 1
                return
            line = raw.decode().strip()
            if line.startswith("event:"):
                event = line[6:].strip()
            elif line.startswith("data:") and event:
                if event.startswith("round-"):
                    arrivals[event].append(time.time())
                    asyncio.create_task(requery(event))
                event = None
            elif line.startswith(":"):
                counts["keepalive"] += 1
    except (OSError, asyncio.TimeoutError, asyncio.IncompleteReadError):
        counts["sse 오류"] += 1


async def requery(event):
    """이벤트를 받은 화면이 하는 일: 요약을 다시 조회한다"""
    if requery_jitter:
        await asyncio.sleep(random.uniform(0, requery_jitter))
    try:
        status, state, connect, respond = await asyncio.wait_for(fetch_summary(), 20)
        counts[f"재조회 {status}"] += 1
        requeries.append((connect, respond, state is not None and EVENT_OF.get(state[1]) == event))
    except (OSError, asyncio.TimeoutError, asyncio.IncompleteReadError, ValueError, IndexError):
        counts["재조회 실패"] += 1


async def poller(interval):
    await asyncio.sleep(random.uniform(0, interval))
    known = None
    while True:
        try:
            status, state, _, _ = await asyncio.wait_for(fetch_summary(), 20)
            counts[f"폴링 {status}"] += 1
            if state and known and state != known:
                detections[state].append(time.time())
            known = state or known
        except (OSError, asyncio.TimeoutError, asyncio.IncompleteReadError, ValueError, IndexError):
            counts["폴링 실패"] += 1
        await asyncio.sleep(interval)


async def compare(sse_count, poll_count, interval, seconds):
    tasks = [asyncio.create_task(sse_client(i)) for i in range(sse_count)]
    tasks += [asyncio.create_task(poller(interval)) for _ in range(poll_count)]
    await asyncio.sleep(max(sse_count * 0.005 + 3, interval + 2))   # 폴링 사용자가 모두 첫 조회를 마칠 때까지
    print(f"[{clock(time.time())}] SSE 연결 {counts['sse 연결']}/{sse_count}, 폴링 사용자 {poll_count}명({interval}초 주기). "
          f"{seconds}초 대기 중: 지금 회차를 마감·발표한다", flush=True)
    await asyncio.sleep(seconds)
    for task in tasks:
        task.cancel()
    await asyncio.gather(*tasks, return_exceptions=True)

    print("\n집계:", ", ".join(f"{name} {count}" for name, count in sorted(counts.items())))
    print("\n== SSE: 이벤트가 연결에 도착한 시각")
    for name, times in arrivals.items():
        print(f"{name}: {len(times)}개 연결, 첫 도착 {clock(min(times))}, 모두에게 퍼지는 데 {(max(times) - min(times)) * 1000:.0f} ms")
    if not arrivals:
        print("받은 회차 이벤트 없음")

    print(f"\n== 폴링({interval}초 주기): 같은 변화를 알아챈 시각. SSE 첫 도착을 0초로 본다")
    for (seq, status), times in detections.items():
        reference = arrivals.get(EVENT_OF.get(status))
        if reference:
            delays = [at - min(reference) for at in times]
            print(f"{seq}회차 {status}: {spread(delays, unit=1)}")
        else:
            print(f"{seq}회차 {status}: SSE 이벤트가 없어 비교 불가. 폴링 첫 감지 {clock(min(times))}, 마지막 {clock(max(times))} ({len(times)}명)")
    if not detections:
        print("폴링이 본 변화 없음")

    print("\n== 이벤트 직후 재조회(SSE 연결마다 한 번씩)")
    if requeries:
        print("응답 시간:", spread([respond for _, respond, _ in requeries]))
        print("연결 시간:", spread([connect for connect, _, _ in requeries]), "← 측정 장비 한 대가 연결을 한꺼번에 여는 비용. 실제 브라우저는 이미 열린 연결을 쓴다")
        print(f"새 상태를 본 재조회: {sum(1 for *_, fresh in requeries if fresh)}/{len(requeries)}")
    else:
        print("재조회 없음")


# ---------------------------------------------------------------- burst

async def burst(count, window):
    results, failures = [], Counter()
    fire = asyncio.Event()

    async def one(index):
        await asyncio.sleep(index * 0.005)
        try:
            reader, writer = await open_connection()
        except (OSError, asyncio.TimeoutError):
            failures["연결 실패"] += 1
            return
        counts["열림"] += 1
        await fire.wait()
        if window:
            await asyncio.sleep(random.uniform(0, window))
        sent = time.time()
        try:
            writer.write(request(SUMMARY, close=False))
            await writer.drain()
            status, body = await asyncio.wait_for(read_response(reader), 20)
            results.append((status, time.time() - sent))
        except (OSError, asyncio.TimeoutError, asyncio.IncompleteReadError, ValueError, IndexError):
            failures["응답 실패"] += 1
        finally:
            writer.close()

    tasks = [asyncio.create_task(one(i)) for i in range(count)]
    await asyncio.sleep(count * 0.005 + 3)
    started = time.time()
    print(f"[{clock(started)}] 연결 {counts['열림']}/{count} 열림. 요약 조회를 {'한순간에' if not window else f'{window}초에 걸쳐'} 보낸다", flush=True)
    fire.set()
    await asyncio.gather(*tasks, return_exceptions=True)
    print(f"전부 끝나기까지 {time.time() - started:.2f}초")
    print("상태 코드:", dict(Counter(status for status, _ in results)), dict(failures))
    print("응답 시간:", spread([took for _, took in results]))


if mode == "compare":
    asyncio.run(compare(int(sys.argv[3]), int(sys.argv[4]), float(sys.argv[5]), int(sys.argv[6])))
elif mode == "burst":
    asyncio.run(burst(int(sys.argv[3]), float(sys.argv[4])))
else:
    sys.exit(__doc__)
