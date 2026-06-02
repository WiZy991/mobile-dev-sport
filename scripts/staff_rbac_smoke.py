#!/usr/bin/env python3
"""Basic smoke checks for staff RBAC endpoints."""

from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass


@dataclass
class HttpResult:
    code: int
    body: dict


def request_json(url: str, method: str, payload: dict | None = None, token: str | None = None) -> HttpResult:
    data = None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url=url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return HttpResult(resp.getcode(), json.loads(resp.read().decode("utf-8")))
    except urllib.error.HTTPError as err:
        body = err.read().decode("utf-8")
        return HttpResult(err.code, json.loads(body) if body else {})


def expect(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def run(base_url: str) -> None:
    email = "rbac-smoke-manager@example.com"
    password = "smoke123"

    reg = request_json(
        f"{base_url}/api/v1/staff/auth/register",
        "POST",
        {"email": email, "name": "Smoke Manager", "password": password, "role": "ROLE_MANAGER"},
    )
    # Re-run friendly behavior: if already exists, proceed to login.
    expect(reg.code in (200, 409), f"register failed: {reg.code} {reg.body}")

    login = request_json(
        f"{base_url}/api/v1/staff/auth/login",
        "POST",
        {"email": email, "password": password},
    )
    expect(login.code == 200, f"login failed: {login.code} {login.body}")
    access = login.body["token"]

    config = request_json(f"{base_url}/api/v1/staff/config", "GET", token=access)
    expect(config.code == 200, f"config failed: {config.code} {config.body}")
    expect("admin.write" in config.body.get("adminActions", []), "ROLE_MANAGER must have admin.write")

    allowed_write = request_json(
        f"{base_url}/api/v1/staff/admin/action-check",
        "POST",
        {"section": "clients", "action": "write"},
        token=access,
    )
    expect(allowed_write.code == 200, f"manager write check failed: {allowed_write.code} {allowed_write.body}")

    print("Staff RBAC smoke test passed")


if __name__ == "__main__":
    base = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8000"
    run(base.rstrip("/"))
