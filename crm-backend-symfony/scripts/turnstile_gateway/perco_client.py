"""
Клиент PERCo-Web HTTP/JSON API для использования внутри LAN клуба.

Покрывает методы из официальной документации https://ru.percoweb.com/dev — в первую очередь
те, что реально нужны фитнес-CRM:

  Auth
    - POST /api/system/auth                  — login/password → token

  Устройства / Команды
    - GET  /api/devices                      — список устройств
    - GET  /api/devices/{id}                 — карточка устройства
    - POST /api/devices/{id}/command         — команда (открыть/закрыть/перевести в режим)

  Сотрудники / посетители (для синхронизации абонементов)
    - GET  /api/staff/persons                — список сотрудников
    - POST /api/staff/persons                — создать сотрудника
    - PUT  /api/staff/persons/{id}           — обновить сотрудника
    - DEL  /api/staff/persons/{id}           — удалить сотрудника
    - POST /api/users/{id}/mainCard          — выдать основной идентификатор

  Идентификаторы (карты / коды)
    - GET  /api/identifications              — список карт
    - POST /api/identifications              — добавить карту
    - DEL  /api/identifications/{id}         — удалить карту

  События
    - GET  /api/events                       — события системы
    - GET  /api/events/identifications       — события идентификаций (проходы)

  Отчёты доступа
    - GET  /api/reports/indoor               — отчёт «находящиеся внутри» (accessReportsIndoorGET)
    - GET  /api/reports/time                 — отчёт по времени (accessReportsTimeGET)

  Любой не покрытый метод можно вызвать через generic call(method, path, json|params).

Все методы автоматически авторизуются (повторная авторизация при 401).
Отключение проверки SSL — для стендов с самоподписанным сертификатом.
"""
from __future__ import annotations

import json
import ssl
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Optional


class PercoApiError(RuntimeError):
    def __init__(self, status: int, body: Any, method: str, path: str) -> None:
        super().__init__(f"PERCo {method} {path} → HTTP {status}: {body!r}")
        self.status = status
        self.body = body
        self.method = method
        self.path = path


class PercoClient:
    """Тонкий клиент PERCo-Web. Хранит JWT, обновляет при 401."""

    def __init__(
        self,
        base_url: str,
        login: str,
        password: str,
        verify_ssl: bool = True,
        timeout: float = 20.0,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.login = login
        self.password = password
        self.verify_ssl = verify_ssl
        self.timeout = timeout
        self._token: Optional[str] = None

    # ---------------------------- low-level ----------------------------------

    def _ssl_ctx(self) -> Optional[ssl.SSLContext]:
        if not self.base_url.lower().startswith("https") or self.verify_ssl:
            return None
        # Самоподписанный сертификат локального PERCo — отключаем проверку явно.
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        return ctx

    def _request(
        self,
        method: str,
        path: str,
        *,
        json_body: Any = None,
        params: Optional[dict[str, Any]] = None,
        skip_auth: bool = False,
    ) -> Any:
        url = self.base_url + path
        if params:
            url = url + "?" + urllib.parse.urlencode({k: v for k, v in params.items() if v is not None}, doseq=True)

        body_bytes = None
        if json_body is not None:
            body_bytes = json.dumps(json_body, ensure_ascii=False).encode("utf-8")

        headers = {"Accept": "application/json"}
        if body_bytes is not None:
            headers["Content-Type"] = "application/json"
        if not skip_auth:
            headers["Authorization"] = f"Bearer {self._ensure_token()}"

        req = urllib.request.Request(url, data=body_bytes, headers=headers, method=method.upper())
        try:
            with urllib.request.urlopen(req, timeout=self.timeout, context=self._ssl_ctx()) as r:
                raw = r.read().decode("utf-8", errors="replace")
                return json.loads(raw) if raw.strip() else {}
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8", errors="replace")
            # Токен истёк — один раз пробуем перезайти и повторить запрос.
            if e.code == 401 and not skip_auth and self._token is not None:
                self._token = None
                return self._request(method, path, json_body=json_body, params=params, skip_auth=False)
            try:
                payload = json.loads(raw) if raw.strip() else {}
            except json.JSONDecodeError:
                payload = {"raw": raw}
            raise PercoApiError(e.code, payload, method, path) from None

    def _ensure_token(self) -> str:
        if self._token:
            return self._token
        data = self._request("POST", "/api/system/auth", json_body={"login": self.login, "password": self.password}, skip_auth=True)
        token = data.get("token") if isinstance(data, dict) else None
        if not isinstance(token, str) or not token:
            raise PercoApiError(200, data, "POST", "/api/system/auth")
        self._token = token
        return token

    # ---------------------------- public API ---------------------------------

    def call(self, method: str, path: str, *, json_body: Any = None, params: Optional[dict[str, Any]] = None) -> Any:
        """Generic вызов — для эндпоинтов, не покрытых типизированными методами."""
        return self._request(method, path, json_body=json_body, params=params)

    # auth -------------------------------------------------------------------

    def authenticate(self) -> str:
        """Принудительно перезайти и вернуть токен (полезно для healthcheck)."""
        self._token = None
        return self._ensure_token()

    # devices ----------------------------------------------------------------

    def devices_list(self, **params: Any) -> Any:
        return self._request("GET", "/api/devices", params=params or None)

    def device_get(self, device_id: int) -> Any:
        return self._request("GET", f"/api/devices/{int(device_id)}")

    def device_command(
        self,
        device_id: int,
        cmd_number: int = 1,
        cmd_type: int = 1,
        cmd_value: int = 1,
        cmd_param: int = 0,
    ) -> Any:
        """Команда исполнительному устройству (открыть/закрыть/режим)."""
        return self._request(
            "POST",
            f"/api/devices/{int(device_id)}/command",
            json_body={
                "cmdNumber": cmd_number,
                "cmdType": cmd_type,
                "cmdValue": cmd_value,
                "cmdParam": cmd_param,
            },
        )

    # staff / users ----------------------------------------------------------

    def staff_list(self, **params: Any) -> Any:
        return self._request("GET", "/api/staff/persons", params=params or None)

    def staff_create(self, payload: dict) -> Any:
        return self._request("POST", "/api/staff/persons", json_body=payload)

    def staff_update(self, person_id: int, payload: dict) -> Any:
        return self._request("PUT", f"/api/staff/persons/{int(person_id)}", json_body=payload)

    def staff_delete(self, person_id: int) -> Any:
        return self._request("DELETE", f"/api/staff/persons/{int(person_id)}")

    def assign_main_card(self, user_id: int, identifier: str) -> Any:
        """Закрепить идентификатор как основной у пользователя PERCo."""
        return self._request(
            "POST",
            f"/api/users/{int(user_id)}/mainCard",
            json_body={"identifier": str(identifier)},
        )

    # identifications --------------------------------------------------------

    def identifications_list(self, **params: Any) -> Any:
        return self._request("GET", "/api/identifications", params=params or None)

    def identification_create(self, payload: dict) -> Any:
        return self._request("POST", "/api/identifications", json_body=payload)

    def identification_delete(self, identifier_id: int) -> Any:
        return self._request("DELETE", f"/api/identifications/{int(identifier_id)}")

    # events / reports -------------------------------------------------------

    def events(self, **params: Any) -> Any:
        return self._request("GET", "/api/events", params=params or None)

    def events_identifications(self, **params: Any) -> Any:
        return self._request("GET", "/api/events/identifications", params=params or None)

    def report_indoor(self, **params: Any) -> Any:
        """accessReportsIndoorGET — кто сейчас «внутри» по проходам."""
        return self._request("GET", "/api/reports/indoor", params=params or None)

    def report_time(self, **params: Any) -> Any:
        """accessReportsTimeGET — отчёт по времени проходов."""
        return self._request("GET", "/api/reports/time", params=params or None)
