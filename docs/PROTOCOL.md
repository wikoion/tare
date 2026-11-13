# Scale Bluetooth protocols

What Tare knows about how the Etekcity ESF-24 and ESF-551 talk, so it can be checked and
extended. They share a look and the retail name "Smart Fitness Scale" but run different
platforms and protocols: the ESF-24 is a QN-platform scale, the ESF-551 Etekcity's own (see
[ESF-551](#esf-551) below). The ESF-17 and ESF-18 are reported to be identical to the ESF-24. Credit for working this out goes to
[etekcity_esf551_ble](https://github.com/ronnnnnnnnnnnnn/etekcity_esf551_ble) and the people
who sent it captures. The implementation is in
[`core/src/commonMain/kotlin/tare/protocol/`](../core/src/commonMain/kotlin/tare/protocol).

## Discovery

The ESF-24 is a QN-platform ("QN-Scale") device. It advertises manufacturer data under the
catch-all company ID `0xFFFF`:

| Bytes | Meaning |
|---|---|
| 0–1 | model code, big-endian: 294 or 946 = ESF-24, 211 = ESF-17, 671 = ESF-18 |
| 2 | `0x01` in every capture |
| 3 | varies, not decoded |
| 4 | number of stored offline readings |
| 5–10 | MAC address, little-endian |

Other platforms check the MAC against the device address. iOS hides addresses, so Tare trusts
the model code, then falls back to a name starting `QN-Scale`.

## GATT

| | UUID |
|---|---|
| Service | `0000fff0-0000-1000-8000-00805f9b34fb` |
| Notify (scale → app) | `0000fff1-…` |
| Write (app → scale) | `0000fff2-…` |

## Frames

Every frame in both directions is `[opcode] [length] [vendor byte] [payload…] [checksum]`.
The checksum is the low byte of the sum of all preceding bytes. The vendor byte is `0x15` on
most ESF-24s and `0xff` on some. The scale states it in its first frame, and every reply must
echo it.

### A weigh-in

| # | Direction | Frame | Notes |
|---|---|---|---|
| 1 | scale → app | `12 0f 15 …` unit request | repeats every ~200 ms until answered; byte 2 is the vendor byte; bytes 3–8 are the MAC |
| 2 | app → scale | `13 09 15 0U 10 28 37 00 cs` set unit | `U`: 1 kg, 2 lb, 8 st |
| 3 | scale → app | `14 0b 15 …` measurement-init request | |
| 4 | app → scale | `20 08 15 tt tt tt tt cs` set time | little-endian seconds since 2000-01-01 00:00 **UTC+8** (Unix 946656000) |
| 5 | scale → app | `21 05 15 01 cs` time ack | some firmware sends length `04` |
| 6 | app → scale | `22 04 15 cs` stored-reading query | optional; see below |
| 7 | scale → app | `10 0b 15 WW WW SS R1 R1 R2 R2 cs` | streams while settling (`SS` = 0), then a final frame (`SS` = 1) |
| 8 | app → scale | `1f 05 15 10 cs` end measurement | without it the final frame repeats twice a second |

In the `10` frame, weight is big-endian in 0.01 kg whatever the display unit, R1 is the
50 kHz impedance and R2 the 500 kHz impedance, in ohms. Zero means not measured, e.g. with
socks on. A firmware variant sends 12 bytes with a pad byte before the checksum. One connection
can carry several weigh-ins.

### Stored offline readings

The scale keeps readings taken while nothing was connected. After the time ack, `22` asks
for them, and the scale sends one `23` record per reading. It deletes each record as it
delivers it, so the vendor app will not see a reading another app has fetched.

| Bytes | Meaning |
|---|---|
| 0–2 | `23 <length> <vendor>`; length 20, or 16 or 19 on other firmware |
| 3 | records in this batch (0 = store empty) |
| 4 | index of this record, from 1 |
| 5–8 | timestamp, little-endian, scale epoch as above |
| 9–10 | weight, big-endian, 0.01 kg |
| 11–12 | impedance at 50 kHz |
| 13–14 | impedance at 500 kHz |
| last | checksum |

A stored record can duplicate a live reading. Tare skips any within 15 minutes and 0.1 kg of
one it already has.

## Body composition

The scale only measures weight and impedance. Every other figure is computed in the app from
weight, height, age, sex and the 50 kHz impedance. The formulas, matched against the vendor app
on real hardware, are in
[`BodyComposition.kt`](../core/src/commonMain/kotlin/tare/body/BodyComposition.kt).

## ESF-551

### Discovery

The ESF-551 advertises as `Etekcity Smart Fitness Scale`, with manufacturer data under
Etekcity's company ID `0x06D0` (1744):

| Bytes | Meaning |
|---|---|
| 0 | header: the low nibble is the format generation (0–2), the upper bits are flags |
| 1–6 | MAC address, little-endian |
| 7–8 | model code, big-endian: 1 or 2 = ESF-551, 12 = ESF-551 (Japan) |
| 9– | model-specific |

Other Etekcity scales (EFS-A591S, EFS-C651) advertise the same name, and VeSync's air purifiers
the same manufacturer-data shape, so only the model code identifies an ESF-551. Tare lists an
Etekcity-format device with an unknown code as a possible scale only if its name has "scale" in it.

### Frames

The scale needs no handshake. Once the app subscribes to `fff1`, it notifies a 22-byte frame
for each weight update:

| Bytes | Meaning |
|---|---|
| 0–1 | `a5 02` |
| 2 | sequence number |
| 3–4 | `10 00` |
| 5 | checksum |
| 6–9 | `01 61 a1 00` |
| 10–12 | weight, little-endian, grams |
| 13–14 | impedance, little-endian, ohms |
| 19 | `1` once the weight has settled (final frame), else `0` |
| 20 | `1` when bytes 13–14 hold a measured impedance |
| 21 | display unit: 0 kg, 1 lb, 2 st |

Some units send the final frame twice, tens of milliseconds apart; Tare keeps one. There is
one impedance, no 500 kHz value, and no store of offline readings.

To change the display unit, write `a5 22 03 05 00 XX 01 63 a1 00 UU` to `fff2` without
response, where `UU` is the unit code and `XX` is 43 minus it. The Japanese model is kg-only,
and the command drops its connection, so Tare doesn't send it there.

## Unknown

- The meaning of bytes 4–7 (`10 28 37 00`) in the set-unit command. Another capture has
  `10 be 34 00`, which may be the user's profile.
- Bytes 9 onwards of the unit request.
- Whether the scale shows anything for an app-chosen user (the vendor app's multi-user mode).

If your scale behaves differently, Settings → Bluetooth log shows every frame. Please share it
in an issue.
