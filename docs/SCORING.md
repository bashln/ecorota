# How EcoRota scores a trip

The score is not a black box. Every input has a unit, every formula is short, and every constant can be changed in `ScoringConfig` without touching the engine. This document states the defaults and the reasoning behind them.

The implementation lives in `core/domain/src/main/kotlin/app/ecorota/core/scoring/`.

## 1. Sampling

| Input | Source | Rate |
| --- | --- | --- |
| Position, speed, bearing, accuracy | Fused Location Provider | About every 3 s while recording |
| Forward and lateral acceleration | Accelerometer, gravity removed, projected on the vehicle axes | 50 Hz, reduced to peak values per location fix |
| Heading | GPS bearing when available | Per fix |

The accelerometer is preferred for acceleration events. When it is unavailable, the analyzer derives forward acceleration from the change in GPS speed and lateral acceleration from the change in bearing (`a = v × Δθ / Δt`). GPS-derived values are noisier, which is why the sensor path exists.

## 2. Event detection

| Event | Condition | Default threshold |
| --- | --- | --- |
| Hard acceleration | Forward acceleration at or above threshold | 2.5 m/s² |
| Hard braking | Forward deceleration at or above threshold | 3.0 m/s² |
| Sharp cornering | Lateral acceleration at or above threshold | 2.8 m/s² |
| Overspeed | Speed above the efficient speed | 90 km/h |
| Idling | Speed below 5 km/h for at least 45 s | 5 km/h, 45 s |

Rules that keep the event list honest:

- An acceleration event must last at least 500 ms. A single noisy sample does not count.
- After an event of a given type is emitted, the same type is suppressed for 3 s. This prevents one long maneuver from becoming five events.
- Overspeed uses a 4 km/h hysteresis band and needs 3 s above the limit. Hovering around 90 km/h does not create a stream of events.
- An idling stop only becomes an event after 45 s. The first 15 s of the stop are treated as a normal traffic light and subtracted from the reported duration. Only the remaining time is scored and costed.
- The list is capped by physics, not by a "maximum events" rule.

### Severity

For acceleration, braking, and cornering, severity compares the peak to the detection threshold:

| Ratio to threshold | Severity |
| --- | --- |
| below 1.25 | Mild |
| 1.25 to 1.60 | Moderate |
| 1.60 and above | Severe |

For overspeed, the bands are 10 and 25 km/h above the efficient speed. For idling, they are 120 s and 300 s of avoidable idle.

Severity multipliers are 0.6, 1.0, and 1.6. They weight the event in the component score and in the informational point value shown next to it.

## 3. Behavior components

Each of five categories produces a 0 to 100 sub-score. The overall behavior score is their weighted average.

### Acceleration, braking, and cornering

These use event density, not bare counts, so a long trip is not punished for having more opportunities to brake:

```
effectiveKm   = max(distanceKm, 1.0)
weighted      = sum(severityMultiplier for events in the category)
density10km   = weighted / effectiveKm * 10
excess        = max(0, density10km - referenceDensity)
component     = clamp(100 - excess * 3.0, 0, 100)
```

Default reference densities are 0.5 hard accelerations, 0.75 hard brakings, and 0.5 sharp corners per 10 km. They describe a smooth driver, not a perfect one. The 1 km floor keeps a 300 m trip from producing an absurd density from one event. The `3.0` loss scale converts excess events per 10 km into points.

### Speed

```
overspeedRatio = time above 90 km/h / moving time
component      = clamp(100 - max(0, overspeedRatio - 0.02) * 250, 0, 100)
```

Two percent of moving time at highway speed is tolerated as normal traffic flow. Above that, each additional 1% of time costs 2.5 points.

There is no road speed limit database. The efficient speed is a single tunable value, defaulting to 90 km/h, which is close to the point where aerodynamic drag starts to dominate fuel use for a typical car. This is a deliberate offline-first simplification.

### Idling

```
idleRatio = avoidable idle time / trip duration
component = clamp(100 - max(0, idleRatio - 0.02) * 400, 0, 100)
```

Only idling time that became an event counts. Two percent of the trip is free. Above that, each 1% of the trip spent idling unnecessarily costs 4 points.

### Weights

| Category | Default weight |
| --- | --- |
| Braking | 0.25 |
| Acceleration | 0.20 |
| Speed | 0.20 |
| Idling | 0.20 |
| Cornering | 0.15 |

Weights are normalized before use, so a custom set does not need to sum to one. Braking and acceleration carry the most weight because they are the behaviors with the largest measured effect on fuel use in urban driving.

```
behaviorScore = round(sum(componentScore * normalizedWeight))
```

## 4. Fuel estimate

### Total fuel

```
estimatedLiters = distanceKm * consumptionLitersPer100Km / 100
```

Consumption comes from the user's vehicle profile. It is the tank-to-wheel average the user already observes, which makes the total a realistic estimate rather than an optimistic laboratory number.

### Avoidable fuel

Each detected event contributes an estimated extra amount:

```
avoidable = min(estimatedLiters, idle + overspeed + braking + acceleration + cornering)
```

The cap matters. A short trip with a long idle can produce physics contributions larger than the fuel the car could have burned over that distance; reporting more waste than total fuel would be nonsense.

Energy is converted to liters with:

```
fuelEnergyPerLiter = 34.2 MJ/L * 0.28 drivetrain efficiency
liters = energyJoules / fuelEnergyPerLiter
```

### Idling

```
idleLiters = avoidableIdleHours * 0.6 L/h
```

0.6 L/h is a typical idle consumption for a small gasoline engine with accessories off. Larger engines burn more; the constant is tunable.

### Overspeed

Extra aerodynamic power above the efficient speed, using the drag equation:

```
P = 0.5 * airDensity * CdA * (v² - vRef²) * v     [watts]
overspeedLiters = P * durationSeconds / fuelEnergyPerLiter
```

Defaults: air density 1.2 kg/m³, `CdA` 0.7 m² (a typical hatchback or sedan). The estimate uses the mean speed of the overspeed segment, not the peak, to avoid overstating a brief spike.

### Braking

Hard braking destroys kinetic energy that a smooth driver would have partly recovered by coasting:

```
energy = 0.5 * mass * (vStart² - vEnd²) * 0.35
brakingLiters = energy / fuelEnergyPerLiter
```

The 0.35 factor is the estimated share of the lost kinetic energy that is truly avoidable rather than required by traffic. Mass defaults to 1400 kg.

### Acceleration

Hard acceleration runs the engine at a less efficient operating point:

```
energy = 0.5 * mass * (vEnd² - vStart²) * 0.15
accelerationLiters = energy / fuelEnergyPerLiter
```

The 0.15 factor represents the extra fuel burned because of the inefficient load point, not the energy needed to reach the new speed, which a smooth acceleration would also need.

### Cornering

Tire scrub during a corner:

```
energy = 0.01 * mass * lateralAcceleration * arcLength
arcLength = averageSpeed * durationSeconds
corneringLiters = energy / fuelEnergyPerLiter
```

This contribution is deliberately small. Cornering is a comfort and safety signal; claiming it burns a lot of fuel would be dishonest. It still affects the score through its component.

### Score from fuel

```
wasteRatio = avoidableLiters / estimatedLiters
fuelScore  = clamp(100 * (1 - wasteRatio / 0.35), 0, 100)
```

A trip that wastes 35% of its fuel scores zero on this component. That is rare; typical aggressive urban driving lands between 5% and 20%.

### Final score

```
overallScore = round(behaviorScore * (1 - 0.20) + fuelScore * 0.20)
```

The 20% fuel weight makes the score respond to estimated energy impact without letting one noisy physics term dominate the behavior analysis. Both weights are configurable.

## 5. Worked example

A 10 km, 15 minute trip, 8.5 L/100 km, with two severe braking events, one moderate acceleration, 30 s at 105 km/h, and 120 s of avoidable idle.

| Category | Events | Component | Weight | Contribution |
| --- | --- | --- | --- | --- |
| Braking | 2 severe | 93 | 0.25 | 23.3 |
| Acceleration | 1 moderate | 99 | 0.20 | 19.8 |
| Cornering | none | 100 | 0.15 | 15.0 |
| Speed | 3.8% above 90 km/h | 95 | 0.20 | 19.0 |
| Idling | 13.3% idle | 55 | 0.20 | 11.0 |

```
behaviorScore = 88
estimatedLiters = 10 * 8.5 / 100 = 0.85 L
physics waste   = 0.020 idle + 0.009 overspeed + 0.010 braking + 0.006 acceleration
                = 0.045 L  (5.3% of total)
fuelScore = 85
overall   = round(88 * 0.8 + 85 * 0.2) = 87
```

At BRL 6.09 per liter the estimated avoidable cost is about BRL 0.27.

The numbers are illustrative and rounded. Real results vary with traffic, vehicle, and sensor quality.

## 6. Tuning

`ScoringConfig` is one immutable data class. The Settings screen edits detection thresholds; all fields can be changed in code or by importing a configuration. The configuration is serialized to JSON and validated on read: out-of-range values are clamped, unknown keys are ignored, and malformed JSON falls back to defaults.

| Group | Fields |
| --- | --- |
| Detection | acceleration, braking, cornering, overspeed, idle thresholds, durations, cooldown |
| Normalization | reference densities, 1 km floor, loss scale, idle and speed allowances |
| Weights | five category weights, fuel weight |
| Severity | band ratios and multipliers |
| Event points | informational points per event type |
| Physics | mass, fuel energy, drivetrain efficiency, idle rate, drag area, air density, waste factors |

Changing a default invalidates comparability with older trips. The app stores a `scoringVersion` on every trip for that reason. Bump `ScoringVersion.CURRENT` when a default change would alter historical scores, and mention it in the pull request.

## 7. Known limitations

- GPS speed lags and can be noisy under bridges and in tunnels. Events are filtered, not invented, but short maneuvers can be missed.
- The accelerometer measures the phone, not the car. A loose phone in a cup holder adds noise. Secure mounting improves results.
- No road speed limit data is available offline. Overspeed is measured against one efficient speed.
- No OBD-II connection. Fuel use is estimated from the user's average consumption and physics, not measured.
- The model is calibrated for passenger cars. Motorcycles and heavy vehicles need different physics constants.
- The estimate is a decision aid, not a measurement. The app labels it as an estimate everywhere it appears.
