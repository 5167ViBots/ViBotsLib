# ViBotsLib

Team 5167's reusable robot code, packaged as two WPILib vendordeps:

| Vendordep | Artifact | Needs |
|---|---|---|
| **ViBotsLib** | `com.vibots5167:vibotslib-java` | WPILib + WPILib-New-Commands (every project has these) |
| **ViBotsLib-CTRE** | `com.vibots5167:vibotslib-ctre-java` | Phoenix 6 vendordep. Includes ViBotsLib automatically. |

## What's in it

**ViBotsLib** (`com.vibots5167.lib`)

| Package | Classes |
|---|---|
| `lib` | `RobotStartup`: one call in robotInit that cleans old logs, starts USB-only logging, and starts latency monitoring |
| `lib.controls` | `CommandGamepad` (one API for PS / Xbox / any mapped pad, auto-detected by DS name), `GamepadLayout`, `GamepadLayouts`, `GamepadProbeCommand` (maps a new controller) |
| `lib.drive` | `DriverInputShaper` (round deadband, rotation curve, acceleration limits) |
| `lib.logging` | `NetworkLatencyMonitor` (radio / PhotonVision / DS round-trip time to NT + log), `LogCleanup` (frees roboRIO flash) |
| `lib.util` | `Setpoint` (button-adjustable, clamped value with commands) |

**ViBotsLib-CTRE** (`com.vibots5167.lib.ctre`)

| Class | |
|---|---|
| `CtreStartup` | `RobotStartup` preset: Phoenix hoot logging only to a USB stick (auto-logging off otherwise), latency samples in the hoot log |

## Using it in a robot project

VS Code: *WPILib: Manage Vendor Libraries* → *Install new library (online)* → paste one of:

- `https://5167vibots.github.io/ViBotsLib/ViBotsLib.json`
- `https://5167vibots.github.io/ViBotsLib/ViBotsLib-CTRE.json` (CTRE robots; install Phoenix 6 first)

```java
// Robot constructor / robotInit - first thing
CtreStartup.standard().run();        // CTRE robot (remove other SignalLogger.start() calls)
RobotStartup.standard().run();       // non-CTRE robot

// RobotContainer
CommandGamepad driver = new CommandGamepad(0);
DriverInputShaper shaper = new DriverInputShaper(0.08, 9.0, 18.0, true); // deadband, m/s^2, rad/s^2, square rotation
```

A controller that isn't auto-detected: run `new GamepadProbeCommand(port, true)`, then
`GamepadLayouts.register(...)` the layout it prints.

## Developing

```
./gradlew build          # compile + test both libraries
./gradlew installLocal   # install into C:\Users\Public\wpilib\2026 - robot projects on this PC use it offline
./gradlew site           # what a release publishes, in build/site
```

`installLocal` also copies the JSONs to the WPILib `vendordeps` folder, so *Install new libraries (offline)*
lists them. Edit vendordep JSONs in `vendordep-templates/`. `@version@` and `@frcYear@` are filled in from
`gradle.properties`.

## Releasing

1. Bump `libVersion` in `gradle.properties` and commit to `main`.
2. Tag and push: `git tag v2026.1.1 && git push origin v2026.1.1`.
3. The **Release** workflow builds, tests, and adds the version to the `gh-pages` branch. Older versions
   stay, because robot projects pin exact versions. Robot projects see it via *Check for updates*.

The workflow refuses a tag that doesn't match `libVersion`, and refuses to republish an existing version.

## GitHub Actions

| Workflow | When | What |
|---|---|---|
| CI | push to main, pull requests | `./gradlew build` |
| Release | `v*` tag | publishes to GitHub Pages |
| Phoenix compatibility | daily, or manually | builds ViBotsLib-CTRE against the newest Phoenix 6. Opens an issue if it fails. |

GitHub pauses scheduled workflows after 60 days with no commits (e.g. over summer). Re-enable it in the
Actions tab at kickoff.

## New season checklist

1. `frcYear`, `wpilibVersion`, `phoenixVersion` and `libVersion` (e.g. `2027.1.0`) in `gradle.properties`.
2. The `uuid` in the CTRE template's `requires` entry, if CTRE changed Phoenix 6's vendordep UUID.
3. The Gradle wrapper version, to match the new WPILib project template.

## First-time GitHub setup

1. Create `5167ViBots/ViBotsLib` (**public**: robot builds download without logging in), and push `main`.
2. Push the first tag (`v2026.1.0`). The Release workflow creates the `gh-pages` branch.
3. Repo *Settings → Pages*: Source = *Deploy from a branch*, branch `gh-pages`, folder `/ (root)`.
4. Check `https://5167vibots.github.io/ViBotsLib/ViBotsLib.json` loads.
