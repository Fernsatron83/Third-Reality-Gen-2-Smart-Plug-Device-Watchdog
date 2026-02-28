# Third Reality Gen2 Smart Plug — Device Watchdog

A Hubitat-based monitoring solution for Third Reality Smart Plug Gen2 (model **3RSP03028BZ**) devices used on refrigerator circuits. The system detects power loss and switch-off events, collects electrical telemetry, and sends notifications through configurable channels.

---

## Repository structure

```
drivers/
  ThirdRealityGen2Plug.groovy   — Production Zigbee device driver
apps/
  DeviceWatchdog.groovy         — Parent app (multi-instance manager)  [in progress]
  DeviceWatchdogChild.groovy    — Per-device monitoring child app       [in progress]
```

---

## Device driver (`drivers/ThirdRealityGen2Plug.groovy`)

### Capabilities exposed

| Capability / Attribute | Description |
|---|---|
| Switch | On/Off control and reporting |
| PowerMeter | Active power in Watts |
| VoltageMeasurement | RMS voltage in Volts |
| EnergyMeter | Cumulative energy in kWh |
| `amperage` | RMS current in Amps |
| `frequency` | Line frequency in Hz |
| `powerFactor` | Power factor (0–1) |
| `lastSeen` | Timestamp of last Zigbee report |
| `energyScalingStatus` | `unknown` / `ready` / `unsupported` |

### Zigbee clusters decoded

| Cluster | Attribute | Measurement |
|---|---|---|
| 0x0006 | 0x0000 | Switch on/off |
| 0x0B04 | 0x050B | Active power (W) |
| 0x0B04 | 0x0505 | RMS voltage (V) |
| 0x0B04 | 0x0508 | RMS current (A) |
| 0x0B04 | 0x0510 | Power factor |
| 0x0B04 | 0x0300 | Frequency (Hz) |
| 0x0B04 | 0x0600–0x0605 | Voltage/current/power multipliers & divisors |
| 0x0702 | 0x0000 | Summation energy (kWh) |
| 0x0702 | 0x0300–0x0301 | Metering multiplier & divisor |

### Driver preferences

| Preference | Default | Description |
|---|---|---|
| `logEnable` | false | Debug logging (auto-disables after 30 min) |
| `txtEnable` | true | Descriptive event logging |
| `enableReportingConfig` | true | Attempt Zigbee attribute reporting configuration on configure() |
| `allowEnergyGuess` | false | Publish raw ÷ 1000 as kWh when metering scaling is unavailable |
| `enableDriverPolling` | false | Driver-owned polling (leave OFF; let the app poll instead) |
| `driverPollSeconds` | 60 | Poll interval when driver polling is enabled (15–3600 s) |

### Energy scaling state machine

The driver tracks whether the device returns its metering multiplier and divisor:

- `unknown` — Not yet determined (initial state)
- `ready` — Both multiplier and divisor received; energy is decoded accurately
- `unsupported` — Device has not returned scaling data after repeated refresh cycles; energy reporting disabled (or raw guess published if `allowEnergyGuess` is enabled)

### Installation

1. In Hubitat, go to **Drivers Code** → **New Driver** → paste the contents of `drivers/ThirdRealityGen2Plug.groovy` → **Save**.
2. Pair your Third Reality Gen2 plug via Zigbee.
3. Change the device type to **Third Reality Gen2 Plug - Production**.
4. Click **Configure**, then **Refresh**.

---

## Device Watchdog app (in progress)

### Monitoring goals

- Detect **device offline** (no `lastSeen` updates within configurable threshold, target ≤ 5 min).
- Detect **switch OFF while online** (plug is reachable but output is off).
- Optional **auto-restore**: send `on()` after device comes back online until switch reports ON.
- Send notifications through configurable channels:
  - Push notifications
  - TTS / speech devices
- Repeat reminders at configurable intervals while device remains offline.
- Fire each transition alert **exactly once** (no duplicate speech on a single recovery event).

### Planned app structure

```
Parent app  — lists all monitored device instances, handles child app registration
Child app   — one per monitored plug
              - subscriptions: lastSeen, switch
              - scheduled: periodic offline check
              - state machine: offline / online
              - notifications: push, speech, per-event templates
```

### Known gaps / remaining work

| Area | Item |
|---|---|
| Driver | Add `StartUpOnOff` (cluster 0x0006, attr 0x4003) preference to set plug default-on behavior after power outage |
| App | Finalize parent/child structure and child app registration |
| App | Fix subscription de-duplication to prevent repeated online-recovery notifications |
| App | Implement switch-off monitoring with optional auto-on retry |
| App | Clean base message templates (offline, offline reminder, back online, switch turned off) |
| Influx | Define measurement schema and implement HTTP POST from Hubitat to InfluxDB v2 write API |
| Grafana | Build dashboards: power/amps over time, lastSeen age, switch state timeline, outage markers |

---

## Environment

- **Hubitat** hub on LAN (separate VLAN from Raspberry Pi, but routable)
- **Zigbee** device paired directly to Hubitat
- **Raspberry Pi** running Docker with InfluxDB v2 + Grafana (future integration target)

---

## Validated measurements (real device)

| Measurement | Observed value |
|---|---|
| Voltage | ~122.2 V |
| Current | ~0.68 A |
| Power factor | 0.94 (under load) |
| Frequency | 60 Hz |
| Switch | Defaults to OFF after power loss |

After a power loss the plug defaults to OFF when power returns. The watchdog app (or the `StartUpOnOff` attribute) is needed to restore the ON state automatically.
