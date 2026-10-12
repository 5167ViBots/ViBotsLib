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

- `https://5167vibots.github.io/ViBotsLib/ViBotsLib-frc2026-latest.json`
- `https://5167vibots.github.io/ViBotsLib/ViBotsLib-CTRE-frc2026-latest.json` (CTRE robots; install Phoenix 6 first)

Use the year of the robot project's season. Each season has its own URL, so a new season's release never
shows up as an update in last season's robot code.

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

1. Bump `libVersion` in `gradle.properties` and commit, on `main` or a season branch (see below).
2. Tag that commit and push: `git tag v2026.1.1 && git push origin v2026.1.1`.
3. The **Release** workflow builds, tests, and adds the version to the `gh-pages` branch. Older versions
   stay, because robot projects pin exact versions. Robot projects see it via *Check for updates*.

The workflow refuses a tag that doesn't match `libVersion`, and refuses to republish an existing version.

## Seasons and branches

`main` is the newest season. When the next season starts, the previous one moves to a branch named after
its year, so both can still be released:

```
main   frcYear=2027   tags v2027.0.0-beta.1, v2027.1.0, ...   -> ViBotsLib-frc2027-latest.json
2026   frcYear=2026   tags v2026.1.3, ...                     -> ViBotsLib-frc2026-latest.json
```

A late 2026 fix goes on the `2026` branch (cherry-pick it from main, or commit it there) and is tagged
there. Nothing on main gets reverted. Both seasons publish into the same GitHub Pages site side by side.
CI and the daily Phoenix check cover `main` and every branch named like `2026`, each against its own
season's Phoenix.

### New season

The Phoenix check opens a "Phoenix 6 for 2027 is available" issue when CTRE publishes next season (often a
beta in the fall). Then:

1. `git branch 2026 main && git push origin 2026` - keeps 2026 releasable.
2. On `main`, in `gradle.properties`: `frcYear`, `wpilibVersion`, `phoenixVersion`, and `libVersion`
   (`2027.0.0-beta.1` while WPILib/Phoenix are in beta, `2027.1.0` for the kickoff release).
3. Update the Gradle wrapper and Java version to match the new WPILib project template.
4. Fix what breaks. Season changes can move hardware paths: check `LogCleanup` (`/home/lvuser/logs`),
   `RobotStartup` (USB at `/u`) and `NetworkLatencyMonitor` (`10.TE.AM.x` addresses) against the new
   control system.
5. Tag a release from `main`.

## GitHub Actions

| Workflow | When | What |
|---|---|---|
| CI | push to main or a season branch, pull requests | `./gradlew build` |
| Release | `v*` tag | publishes to GitHub Pages |
| Phoenix compatibility | daily, or manually | builds ViBotsLib-CTRE on each season branch against that season's newest Phoenix 6. Opens an issue if it fails, and a one-time issue when next season's Phoenix appears. |

GitHub pauses scheduled workflows after 60 days with no commits (e.g. over summer). Re-enable it in the
Actions tab at kickoff.

## First-time GitHub setup

1. Create `5167ViBots/ViBotsLib` (**public**: robot builds download without logging in), and push `main`.
2. Push the first tag (`v2026.1.0`). The Release workflow creates the `gh-pages` branch.
3. Repo *Settings → Pages*: Source = *Deploy from a branch*, branch `gh-pages`, folder `/ (root)`.
4. Check `https://5167vibots.github.io/ViBotsLib/ViBotsLib-frc2026-latest.json` loads.
