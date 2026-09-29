#!/usr/bin/env python3
"""SSE 연결을 N개 열어 두고, 회차 이벤트가 각 연결에 언제 도착하는지 잰다(NFR-PF-02: 1,000 연결, 발표 5초 이내).

k6는 SSE 스트림을 읽지 못해(응답이 끝나야 결과를 준다) 표준 라이브러리만으로 만들었다.

  ulimit -n 10240
  python3 load/sse/sse-clients.py http://localhost:8080 1000 60
  python3 load/sse/sse-clients.py https://api.yufesta.com 1000 120

연결을 다 연 뒤 "대기 중"이 보이면 운영자 화면에서 회차를 마감·발표한다.
끝나면 이벤트별로 받은 연결 수와 첫 연결~마지막 연결 사이의 시간(모두에게 퍼지는 데 걸린 시간)을 출력한다.
"""
import asyncio
import ssl
import sys
import time
from collections import defaultdict
from urllib.parse import urlparse

base, count, seconds = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
url = urlparse(base)
port = url.port or (443 if url.scheme == "https" else 80)
context = ssl.create_default_context() if url.scheme == "https" else None

arrivals = defaultdict(list)      # 이벤트 이름 → 도착 시각들
connected = failed = keepalives = 0


async def client(index):
    global connected, failed, keepalives
    await asyncio.sleep(index * 0.005)          # 한꺼번에 몰아 열지 않는다(초당 200개)
    try:
        reader, writer = await asyncio.open_connection(url.hostname, port, ssl=context)
        writer.write((f"GET /api/v1/sse/match HTTP/1.1\r\nHost: {url.hostname}\r\n"
                      "Accept: text/event-stream\r\nConnection: keep-alive\r\n\r\n").encode())
        await writer.drain()
        status = (await reader.readline()).decode()
        if " 200" not in status:
            failed += 1
            return
        connected += 1
        event = None
        while True:
            line = (await reader.readline()).decode().strip()
            if line.startswith("event:"):
                event = line[6:].strip()
            elif line.startswith("data:") and event:
                arrivals[event].append(time.time())
                event = None
            elif line.startswith(":"):
                keepalives += 1
    except (OSError, asyncio.IncompleteReadError, asyncio.CancelledError):
        pass


async def main():
    tasks = [asyncio.create_task(client(i)) for i in range(count)]
    await asyncio.sleep(count * 0.005 + 3)
    print(f"연결 {connected}/{count} (실패 {failed}). {seconds}초 대기 중: 지금 회차를 마감·발표한다", flush=True)
    await asyncio.sleep(seconds)
    for task in tasks:
        task.cancel()
    await asyncio.gather(*tasks, return_exceptions=True)
    print(f"keepalive 수신 {keepalives}건")
    for name, times in arrivals.items():
        if name == "connected":
            continue
        spread = max(times) - min(times)
        at = time.strftime("%H:%M:%S", time.localtime(min(times)))
        print(f"{name}: {len(times)}개 연결이 받음, 첫 도착 {at}, 모두에게 퍼지는 데 {spread * 1000:.0f} ms")


asyncio.run(main())
