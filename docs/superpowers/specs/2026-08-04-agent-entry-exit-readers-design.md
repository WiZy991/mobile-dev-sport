# Design: раздельные считыватели вход/выход в FitnessClub Agent

Дата: 2026-08-04  
Статус: approved / implemented

## Проблема

1. Один считыватель на входе сейчас может засчитать и вход, и выход: агент шлёт `/gateway/access/entry`, а CRM при «уже в зале» сам пишет exit.
2. Кнопка «Открыть» шлёт `exdev_open` с `equipment.exdev_number` без выбора номера считывателя (0/1).

У PERCo: `direction` всегда `0`, различаются `number` 0 и 1.

## Решение

### Агент — конфиг оборудования

Новые поля (опциональные, **без значений по умолчанию** — пусто, пока оператор не укажет):

- `entry_reader_number: int | null`
- `exit_reader_number: int | null`

В GUI вкладки «Оборудование»:

- поля «Считыватель вход (number)» и «Считыватель выход (number)» (пустые = не задано; оператор указывает сам один раз в настройках);
- «Роль точки» (`gate_role`) оставить как fallback, если оба номера не заданы (старые установки);
- кнопки открытия двери **без выбора номера каждый раз**:
  - «Открыть вход» → `exdev_open(entry_reader_number, direction=0)` (активна, только если номер входа задан);
  - «Открыть выход» → `exdev_open(exit_reader_number, direction=0)` (активна, только если номер выхода задан);
  - если оба номера пустые — одна кнопка «Открыть» как сейчас (`exdev_number`).

### Агент — логика скана

По событию C01 `card` / `pass_personal` берём `number` из события:

| Условие | Действие |
|--------|----------|
| Заданы entry и/или exit, `number == entry` | `POST …/access/entry` с `allow_exit_toggle: false` |
| Заданы entry и/или exit, `number == exit` | `POST …/access/exit` |
| Заданы номера, `number` не совпал | Отказ локально, в CRM не слать; лог «неизвестный считыватель» |
| Оба номера не заданы | Как сейчас: `gate_role` → entry или exit endpoint; на entry CRM может toggle (совместимость) |

Открытие турникета после grant — тем же `number`/`direction` из события (уже так).

### CRM — `/gateway/access/entry`

Тело может содержать `allow_exit_toggle` (bool, default `true` для совместимости).

- `allow_exit_toggle: false` и пользователь уже в зале → **не** писать exit; ответ `access_granted: false`, `reason: already_inside` (HTTP 403).
- иначе поведение как сейчас (grace + auto-exit).

`/gateway/access/exit` без изменений.

### Совместимость

- Старый конфиг без новых полей → работает через `gate_role`.
- Номера не задаём в коде по умолчанию; оператор вводит сам в агенте и сохраняет.

## Вне scope

- Пересборка Windows installer на Mac (отдельный шаг на Windows).
- Изменение direction (всегда 0).
