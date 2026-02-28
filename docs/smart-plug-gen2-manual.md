# THIRDREALITY Zigbee Smart Plug Gen2 — Device Manual

**Model:** 3RSP03028BZ
**Source:** [Official User Manual (PDF)](https://thirdreality.com/wp-content/uploads/2024/02/7-Smart-Plug-Gen2-%E7%94%B5%E5%AD%90%E7%89%88%E8%AF%B4%E6%98%8E%E4%B9%A6.pdf) · [Updated Manual 20240925 (PDF)](https://3reality.com/wp-content/uploads/2024/09/Smart-Plug-Gen2_UM_20240925.88.pdf)

---

## Electrical Specifications

| Parameter | Value |
|---|---|
| Input voltage | 100–120 V AC |
| Max current | 15 A |
| Frequency | 50/60 Hz |
| Wireless protocol | Zigbee 3.0 |
| Dimensions | 2.7 × 1.48 × 1.13 in (68.6 × 37.6 × 28.7 mm) |
| Weight | 2.47 oz (70 g) |

---

## Features

- **Real-time energy monitoring** — tracks power (W), voltage (V), current (A), and total consumption (kWh). Supported on Home Assistant, SmartThings, Hubitat, and Third Reality Smart Hub Gen2.
- **Zigbee repeater** — acts as a Zigbee mesh extender, increasing range for other Zigbee devices.
- **Power-state customization** — configurable default On/Off state when power is restored after an outage (see [Default power-on state](#default-power-on-state) below).
- **OTA firmware updates** — available via Third Reality app (requires Third Reality hub), Home Assistant ZHA/Zigbee2MQTT (requires Zigbee dongle), or Hubitat.

---

## LED Indicator

| LED behavior | Meaning |
|---|---|
| Flashes rapidly — red | Pairing mode (Zigbee) |
| Off | Pairing completed, or plug is switched OFF |
| Solid red | Plug switched ON |
| Flashes red + green alternating | Bluetooth (BLE) pairing mode |

---

## Pairing (Zigbee)

### First-time pairing

When the plug is first inserted into an outlet it enters Zigbee pairing mode automatically (LED flashes red). Before pairing, update the hub firmware and phone app to the latest version.

### Re-entering pairing mode

Press and hold the side button for **≥ 10 seconds** until the LED flashes rapidly red. The plug exits pairing mode automatically after **3 minutes** if it is not successfully paired.

### Platform-specific steps

#### Amazon Echo / Alexa

1. Insert the plug near the Echo — LED flashes red.
2. In the Alexa app tap **+** → **Add Device** → **Other** → **Zigbee**, or say *"Alexa, discover devices."*

#### Samsung SmartThings

1. Update SmartThings Hub firmware first.
2. Insert the plug near the hub.
3. In the SmartThings app tap **+** → **Scan for nearby devices**.

#### Third Reality Hub

1. In the Third Reality app tap **+** (upper right) → **Plug icon**.
2. Choose your hub, then tap **Pair**. Pairing completes within seconds.
3. Power meter and total consumption are visible on the device page. Customize **Default Safety Setting** (post-outage state) from this page.

#### Hubitat Elevation

1. Check for Hubitat firmware updates in **Settings → Firmware Update**.
2. In Hubitat go to **Devices → Add Device → Zigbee**.
3. The plug will appear once paired.

> For the custom Groovy driver used in this project, see [driver installation instructions](../README.md#installation).

#### Home Assistant (ZHA / Zigbee2MQTT)

1. Confirm your ZHA or Zigbee2MQTT integration is running.
2. Go to **Settings → Devices & Services** → select the Zigbee integration → **Add Devices**.
3. The plug appears after successful pairing.

---

## Switching to Bluetooth (BLE) Mode

If you need to pair the plug via Bluetooth instead of Zigbee:

1. Long-press the button, then insert the plug into the outlet.
2. Continue holding for **10 seconds** until the LED turns red, then release.
3. Quickly **double-click** the button. The LED flashes red + green alternately, indicating BLE pairing mode.

---

## Factory Reset

Press and hold the side button for **≥ 10 seconds** until the LED flashes rapidly red. This clears all pairing information and re-enters Zigbee pairing mode.

---

## Default Power-On State

After a power outage the plug defaults to **OFF** when power returns (factory default). This is why a watchdog app or the `StartUpOnOff` Zigbee attribute (cluster `0x0006`, attribute `0x4003`) is needed to restore the ON state automatically.

The default can be changed:
- **Third Reality Hub app** — "Default Safety Setting" on the device page.
- **Zigbee attribute** — Write attribute `0x4003` on cluster `0x0006`:
  - `0x00` = Off (default)
  - `0x01` = On
  - `0xFF` = Restore previous state

---

## Compatible Hubs

| Platform | Notes |
|---|---|
| Amazon Echo (4th Gen) | Built-in Zigbee hub |
| Amazon Echo Plus (1st / 2nd Gen) | Built-in Zigbee hub |
| Amazon Echo Show 10 (2nd / 3rd Gen) | Built-in Zigbee hub |
| Amazon Echo Studio | Built-in Zigbee hub |
| Eero 6 / Eero Pro 6 | Built-in Zigbee hub |
| Samsung SmartThings 2015 / 2018 | |
| Aeotec Smart Home Hub | |
| Hubitat Elevation | Energy monitoring supported |
| Homey Bridge / Homey Pro | |
| Home Assistant (ZHA / Zigbee2MQTT) | Energy monitoring supported |
| Third Reality Hub / Smart Bridge | Energy monitoring + OTA firmware |

---

## Regulatory & Safety

### FCC Compliance

This device complies with **Part 15 of the FCC Rules**. Operation is subject to the following two conditions:

1. This device may not cause harmful interference.
2. This device must accept any interference received, including interference that may cause undesired operation.

This equipment has been tested and found to comply with the limits for a **Class B digital device** pursuant to Part 15 of the FCC Rules. These limits provide reasonable protection against harmful interference in a residential installation.

This equipment complies with **FCC radiation exposure limits** set forth for an uncontrolled environment. The transmitter must not be co-located or operated in conjunction with any other antenna or transmitter.

### Safety Warnings

- Do not exceed the rated 15 A / 120 V AC load.
- For indoor residential use only.
- Keep away from water and moisture.
- Do not disassemble.

---

## Support

| Resource | Link / Contact |
|---|---|
| Support portal | www.3reality.com/devicesupport |
| Email | info@3reality.com |
| Product page | https://thirdreality.com/product/smart-plug-gen2-with-energy-monitoring/ |
| Release notes | https://thirdreality.com/release_note/zigbee-smart-plug/ |

---

## Observed Values (Real Device — This Project)

| Measurement | Value |
|---|---|
| Voltage | ~122.2 V |
| Current | ~0.68 A |
| Power factor | 0.94 (under load) |
| Frequency | 60 Hz |
| Post-outage switch state | OFF (factory default) |
