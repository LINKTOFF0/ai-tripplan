#!/usr/bin/env python3
"""查询指定城市的天气信息，用于旅行建议。"""

import json
import sys
import urllib.request
import urllib.error


def fetch_weather(city: str) -> dict:
    """从 wttr.in 获取天气（免费，无需 API Key），返回结构化数据。"""
    url = f"https://wttr.in/{city}?format=j1"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "AiTripPlan/1.0"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            current = data.get("current_condition", [{}])[0]
            forecast = data.get("weather", [])
            return {
                "city": city,
                "temperature_c": current.get("temp_C"),
                "humidity": current.get("humidity"),
                "weather_desc": current.get("weatherDesc", [{}])[0].get("value"),
                "wind_speed_kmph": current.get("windspeedKmph"),
                "forecast": [
                    {
                        "date": day.get("date"),
                        "max_temp_c": day.get("maxtempC"),
                        "min_temp_c": day.get("mintempC"),
                        "weather_desc": day.get("hourly", [{}])[4].get(
                            "weatherDesc", [{}]
                        )[0].get("value"),
                    }
                    for day in forecast[:7]
                ],
            }
    except (urllib.error.URLError, urllib.error.HTTPError, json.JSONDecodeError) as e:
        print(f"天气查询失败: {e}", file=sys.stderr)
        return {"city": city, "error": str(e)}


def main():
    if len(sys.argv) < 2:
        print("用法: python weather_check.py <城市名>", file=sys.stderr)
        sys.exit(1)

    city = sys.argv[1]
    weather = fetch_weather(city)
    print(json.dumps(weather, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()