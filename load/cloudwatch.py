#!/usr/bin/env python3
"""부하 테스트 구간의 CloudWatch 지표를 표(분 단위)나 그래프(PNG)로 뽑는다. 읽기 전용 API만 쓴다.

k6가 재는 값은 맥북에서 서울까지의 네트워크가 섞여 있다. 서버가 실제로 어땠는지는 CloudWatch로 본다.
Grafana·Prometheus 없이도 같은 그래프를 얻을 수 있고, 시각은 전부 KST로 넣고 KST로 나온다.

  export AWS_PROFILE=yufesta
  python3 load/cloudwatch.py table "2026-09-28 17:10" "2026-09-28 17:45"
  python3 load/cloudwatch.py graph "2026-09-28 17:10" "2026-09-28 17:45" 0928-anonymous "cache, anonymous" \
      300=17:14 600=17:18:30 1200=17:23:30 900=17:39:30

1분 단위 데이터는 15일만 보관된다(이후 5분 단위로 합쳐진다). 측정한 주에 뽑아 둘 것.
그래프 글자는 영어로 쓴다. CloudWatch가 서버에서 그림을 그리는데 한글 글꼴이 없다.
"""
import base64
import json
import subprocess
import sys
from datetime import datetime, timedelta, timezone

KST = timezone(timedelta(hours=9))
CLUSTER, SERVICE, DB, CACHE, ALB_NAME = "yufesta-cluster", "yufesta-api", "yufesta-mysql", "yufesta-cache-001", "yufesta-alb"


def aws(*args):
    result = subprocess.run(["aws", *args], capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"aws {' '.join(args[:2])} 실패: {result.stderr.strip()[:300]}")
    return result.stdout


def utc(kst_text):
    text = kst_text if kst_text.count(":") == 2 else kst_text + ":00"
    return datetime.strptime(text, "%Y-%m-%d %H:%M:%S").replace(tzinfo=KST).astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def load_balancer():
    arn = aws("elbv2", "describe-load-balancers", "--names", ALB_NAME,
              "--query", "LoadBalancers[0].LoadBalancerArn", "--output", "text").strip()
    return arn.split(":loadbalancer/")[1]


def dimensions(lb):
    return {
        "alb": ["LoadBalancer", lb],
        "ecs": ["ClusterName", CLUSTER, "ServiceName", SERVICE],
        "rds": ["DBInstanceIdentifier", DB],
        "cache": ["CacheClusterId", CACHE],
    }


# (열 이름, 네임스페이스, 지표, 차원 키, 통계, 출력 형식)
TABLE = [
    ("req/min", "AWS/ApplicationELB", "RequestCount", "alb", "Sum", "{:.0f}"),
    ("avg_s", "AWS/ApplicationELB", "TargetResponseTime", "alb", "Average", "{:.3f}"),
    ("max_s", "AWS/ApplicationELB", "TargetResponseTime", "alb", "Maximum", "{:.2f}"),
    ("5xx", "AWS/ApplicationELB", "HTTPCode_Target_5XX_Count", "alb", "Sum", "{:.0f}"),
    ("cpu_avg", "AWS/ECS", "CPUUtilization", "ecs", "Average", "{:.1f}"),
    ("cpu_max", "AWS/ECS", "CPUUtilization", "ecs", "Maximum", "{:.1f}"),
    ("rds_cpu", "AWS/RDS", "CPUUtilization", "rds", "Maximum", "{:.1f}"),
    ("db_conn", "AWS/RDS", "DatabaseConnections", "rds", "Maximum", "{:.0f}"),
    ("hits", "AWS/ElastiCache", "CacheHits", "cache", "Sum", "{:.0f}"),
    ("redis_get", "AWS/ElastiCache", "GetTypeCmds", "cache", "Sum", "{:.0f}"),
    ("redis_set", "AWS/ElastiCache", "SetTypeCmds", "cache", "Sum", "{:.0f}"),
    ("alb_conn", "AWS/ApplicationELB", "ActiveConnectionCount", "alb", "Sum", "{:.0f}"),
    ("mem_avg", "AWS/ECS", "MemoryUtilization", "ecs", "Average", "{:.1f}"),
]


def table(start, end):
    dims = dimensions(load_balancer())
    rows = {}
    for column, namespace, metric, key, stat, _ in TABLE:
        pairs = [f"Name={dims[key][i]},Value={dims[key][i + 1]}" for i in range(0, len(dims[key]), 2)]
        points = json.loads(aws("cloudwatch", "get-metric-statistics", "--namespace", namespace, "--metric-name", metric,
                                "--dimensions", *pairs, "--start-time", utc(start), "--end-time", utc(end),
                                "--period", "60", "--statistics", stat, "--output", "json"))["Datapoints"]
        for point in points:
            minute = datetime.fromisoformat(point["Timestamp"]).astimezone(KST).strftime("%H:%M")
            rows.setdefault(minute, {})[column] = point[stat]
    print("time\t" + "\t".join(column for column, *_ in TABLE))
    for minute in sorted(rows):
        print(minute + "\t" + "\t".join(
            fmt.format(rows[minute][column]) if column in rows[minute] else "-" for column, *_, fmt in TABLE))


def graph(start, end, name, label, marks):
    dims = dimensions(load_balancer())
    day = start.split(" ")[0]

    def metric(namespace, metric_name, key, **options):
        return [namespace, metric_name, *dims[key], options]

    def widget(title, metrics, left, right="", right_max=None):
        body = {
            "width": 1400, "height": 520, "start": utc(start), "end": utc(end), "period": 60, "timezone": "+0900",
            "title": f"{title} - {label}", "view": "timeSeries", "stacked": False, "metrics": metrics,
            "yAxis": {"left": {"min": 0, "label": left}, "right": {"min": 0, "label": right}},
        }
        if right_max:
            body["yAxis"]["right"]["max"] = right_max
        if marks:
            body["annotations"] = {"vertical": [
                {"label": rate, "value": utc(f"{day} {at}")} for rate, at in (mark.split("=") for mark in marks)]}
        return body

    charts = {
        "throughput-cpu": widget("Throughput vs App CPU", [
            metric("AWS/ApplicationELB", "RequestCount", "alb", stat="Sum", label="Requests per minute", color="#1f77b4"),
            metric("AWS/ECS", "CPUUtilization", "ecs", stat="Average", label="App CPU avg %", yAxis="right", color="#d62728"),
            metric("AWS/ECS", "CPUUtilization", "ecs", stat="Maximum", label="App CPU max %", yAxis="right", color="#ff9896"),
        ], "requests/min", "CPU %", 100),
        "latency": widget("Server response time", [
            metric("AWS/ApplicationELB", "TargetResponseTime", "alb", stat="Average", label="avg (s)", color="#2ca02c"),
            metric("AWS/ApplicationELB", "TargetResponseTime", "alb", stat="p95", label="p95 (s)", color="#ff7f0e"),
        ], "seconds"),
        "db-cache": widget("RDS CPU, DB connections, cache hits", [
            metric("AWS/RDS", "CPUUtilization", "rds", stat="Maximum", label="RDS CPU %", color="#9467bd"),
            metric("AWS/RDS", "DatabaseConnections", "rds", stat="Maximum", label="DB connections", color="#8c564b"),
            metric("AWS/ElastiCache", "CacheHits", "cache", stat="Sum", label="Cache hits per minute", yAxis="right", color="#17becf"),
        ], "% / connections", "hits/min"),
        # ActiveConnectionCount는 브라우저→ALB와 ALB→태스크를 함께 센다. SSE 연결 1,000개면 2,000으로 보인다
        "connections": widget("Open connections (ALB), App CPU and memory", [
            metric("AWS/ApplicationELB", "ActiveConnectionCount", "alb", stat="Sum", label="ALB active connections", color="#1f77b4"),
            metric("AWS/ECS", "CPUUtilization", "ecs", stat="Average", label="App CPU avg %", yAxis="right", color="#d62728"),
            metric("AWS/ECS", "MemoryUtilization", "ecs", stat="Average", label="App memory %", yAxis="right", color="#9467bd"),
        ], "connections", "%", 100),
    }
    for chart, body in charts.items():
        image = aws("cloudwatch", "get-metric-widget-image", "--metric-widget", json.dumps(body),
                    "--output-format", "png", "--query", "MetricWidgetImage", "--output", "text").strip()
        path = f"load/graphs/{name}-{chart}.png"
        with open(path, "wb") as file:
            file.write(base64.b64decode(image))
        print("저장", path)


if __name__ == "__main__":
    if len(sys.argv) >= 4 and sys.argv[1] == "table":
        table(sys.argv[2], sys.argv[3])
    elif len(sys.argv) >= 6 and sys.argv[1] == "graph":
        graph(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5], sys.argv[6:])
    else:
        sys.exit(__doc__)
