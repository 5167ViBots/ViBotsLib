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

1. Bump `libVersion` in `gradle.properties`. Commit and push it, on `main` or a season branch (see below).
2. Create a tag named `v` + that version (e.g. `v2026.1.1`) on that commit, in any one of these ways:

   | Where | How |
   |---|---|
   | github.com | *Releases* → *Draft a new release* → type the new tag name, pick the branch → write a title/notes → *Publish release* |
   | Command line | `git tag v2026.1.1 && git push origin v2026.1.1` |
   | VS Code | Command palette → *Git: Create Tag*, then *Git: Push Tags* |
   | GitHub Desktop | *History* → right-click the commit → *Create Tag…*, then push |

   A tag that only exists on your computer does nothing. It has to be pushed to GitHub.
3. The **Release** workflow (repo *Actions* tab) then:
   - builds and tests both libraries
   - adds the version to the `gh-pages` branch (the Maven repo + vendordep JSONs). Older versions stay,
     because robot projects pin exact versions. Robot projects see it via *Check for updates*.
   - attaches the jars (plus sources/javadoc jars) and the two vendordep JSONs to the tag's GitHub Release.
     A Release made on github.com keeps your title and notes; the files are added to it. For a tag pushed
     from git / VS Code / Desktop, the workflow creates the Release itself, with the install URLs and notes
     generated from the commits since the last release. Versions with a `-` (e.g. `2027.0.0-beta.1`) are
     marked as pre-releases.

The workflow stops without publishing if the tag doesn't match `libVersion`, or that version was already
published. A tag runs the workflow file from its own commit, so workflow changes only apply to tags made
on commits after the change. `v2026.1.0` came before files were attached to Releases, so its Release has
none.

## Seasons and branches

`main` is always the newest season. Every year, before starting on the new season, the current state of
`main` is archived as a branch named after its year. `main` then moves on to the new season:

```
main   frcYear=2027   tags v2027.0.0-beta.1, v2027.1.0, ...   -> ViBotsLib-frc2027-latest.json
2026   frcYear=2026   tags v2026.1.3, ...                     -> ViBotsLib-frc2026-latest.json
2025   frcYear=2025   (archived, still releasable)            -> ViBotsLib-frc2025-latest.json
```

- A late fix for an old season goes on that season's branch and is tagged there. Cherry-pick it from main
  or commit it directly. Nothing on `main` gets reverted.
- CI and the daily Phoenix check cover `main` and every branch named like `2026`. Each is checked against
  its own season's Phoenix.
- Season branches are never merged back into `main`. Cherry-pick individual fixes across instead.

### Yearly update (archive the season, start the next)

The Phoenix check opens a "Phoenix 6 for 2027 is available" issue when CTRE publishes next season (often a
beta in the fall). Then, in this order:

1. **Archive the current season.** Make sure everything for the current season is merged to `main`, then:
   ```
   git checkout main && git pull
   git branch 2026 && git push origin 2026
   ```
   The `2026` branch is now the permanent home for the 2026 season and stays releasable.
2. **Move `main` to the new season.** In `gradle.properties` set `frcYear=2027`, the 2027 `wpilibVersion`
   and `phoenixVersion`, and `libVersion` (`2027.0.0-beta.1` while WPILib/Phoenix are in beta,
   `2027.1.0` for the kickoff release).
3. **Match the new WPILib project template:** the Gradle wrapper version, and the Java version if it changed.
4. **Fix what breaks.** Season changes can move hardware paths. Check these against the new control system:
   - `LogCleanup`: `/home/lvuser/logs`
   - `RobotStartup`: USB stick at `/u`
   - `NetworkLatencyMonitor`: `10.TE.AM.x` addresses
5. **Release from `main`:** commit, then tag (e.g. `v2027.0.0-beta.1`) and push the tag.
6. **Close the "Phoenix 6 for 2027 is available" issue.** It won't be reopened.
7. **Share the new URLs** with the team: robot projects for 2027 install from the `frc2027` JSONs.
8. **Workflow fixes later:** if `.github/workflows/` changes on `main` afterward, cherry-pick the change to
   any season branch you still release from. A tag runs the workflow files from its own branch. The daily
   Phoenix check always runs from `main`, and it already covers every season branch.

## Published files (Maven repo + vendordeps)

Releases are published to the `gh-pages` branch, served at `https://5167vibots.github.io/ViBotsLib/`. It
holds every release from every season, organized by version, not by source branch:

```
gh-pages
|-- repo/com/vibots5167/vibotslib-java/
|   |-- 2026.1.0/          <- released from main in 2026
|   |-- 2026.1.3/          <- released from the 2026 branch later
|   |-- 2027.1.0/          <- released from main
|   `-- maven-metadata.xml
|-- repo/com/vibots5167/vibotslib-ctre-java/  (same layout)
|-- ViBotsLib-frc2026-latest.json        (+ ViBotsLib-CTRE-frc2026-latest.json)
`-- ViBotsLib-frc2027-latest.json        (+ ViBotsLib-CTRE-frc2027-latest.json)
```

- Creating, archiving or deleting source branches never touches `gh-pages`. A release only adds its own
  version folders and updates its own season's JSONs.
- Robot projects pin an exact version and their season's JSON URL. Which branch a release came from doesn't
  matter to them, and a new season's release never shows up as an update for an old season's robot.
- Version numbers must be unique across all branches. The year prefix takes care of that, and the release
  workflow refuses to republish an existing version.
- Two releases at once (e.g. a 2026 patch and a 2027 release) are safe. The release workflow runs one at a
  time, and the second starts from what the first published.
- `maven-metadata.xml` lists the most recently released version as "latest", even if that's an older
  season's patch. Vendordeps always use exact versions, so this is cosmetic.
- **Never rewrite, force-push or delete files on `gh-pages`.** If a version folder disappears, every robot
  project pinned to that version stops building, including old seasons' code.

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
