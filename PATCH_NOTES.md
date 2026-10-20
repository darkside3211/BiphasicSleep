# Patch Notes: v2.1.0 — Recovery Architecture & Biometric UI Overhaul

**Release Version:** `v2.1.0`  
**Platform:** Android (Kotlin + Jetpack Compose, Material 3 Dark)  
**Package:** `com.darkside3211.biphasicsleep`  
**Target:** Xiaomi HyperOS / Android 14+ / Wearable & Biometric Companion  

---

## Overview

Version **`v2.1.0`** delivers a complete architectural and visual overhaul of the **Recovery Tab** (`RecoveryScreen`), resolving four critical Compose rendering defects, eliminating text-crushing layout collisions, and elevating the screen to match the high-contrast biometric aesthetic (Oura, Whoop, Apple Health) established across the rest of the application.

---

## Visual Bugs Diagnosed & Resolved

### 1. Vertical Character Wrapping & 220dp Empty Voids in Recovery Steps
* **Symptom**: In the Recovery Protocol timeline, Step 1 and Step 3 squeezed the timestamp window (`04:27 Sun – 01:00 Mon`) into a narrow vertical column that wrapped character-by-character (`04:2 / 7 / Sun / – / 01:0 / 0 / Mon`). In Step 2 and Step 4, longer titles completely starved the label of width, wrapping it across ~18 invisible lines and inflating the card height to ~220dp, leaving a massive empty dark space above the description.
* **Root Cause**: In `VerticalTimelineStepItem`, `step.title` and `step.windowLabel` were placed inside a single `Row` without `Modifier.weight(1f)` on the title. When the title expanded, it pushed the window label into a sub-20dp column.
* **Resolution**:
  * Separated the layout into a **Top Metadata Row** containing the time window pill on the left and a guardrail status badge (`CRITICAL GUARDRAIL` or `PROTOCOL STEP`) on the right.
  * Rendered `step.title` on its own full-width line beneath the metadata row, guaranteeing zero horizontal collisions regardless of title length.
  * Formatted `step.description` with enhanced typographic contrast and generous line spacing (`18.sp`).

---

### 2. Double-Border & Zero-Padding Box Artifacts on Cards
* **Symptom**: Cards across the Recovery tab (Sleep Onset, Actual Wake, and all timeline step items) rendered with an outer rounded colored border, a dark hollow gap inside it, and an inner unrounded sharp rectangle where text touched the edge with zero internal padding.
* **Root Cause**: In Jetpack Compose, calling `.border(...).padding(14.dp)` on a `Surface(modifier = ...)` insets the `Surface` fill color *away* from its own border, causing the background to shrink into an inner unrounded box.
* **Resolution**: Moved all `.padding(14.dp)` modifiers inside the child `Column`/`Row` composables. Card backgrounds now fill flush to their rounded borders (`RoundedCornerShape(14.dp)` / `16.dp`) with consistent internal spacing.

---

### 3. Vertical Letter-Stacking in Quick-Adjust Steppers (`-30m`, `+30m`, `-15m`, `+15m`)
* **Symptom**: The `-30m` / `+30m` and `-15m` / `+15m` time stepper buttons rendered as tall, squished ovals with characters stacked vertically:
  $$\begin{matrix} - \\ 3 \\ 0 \\ m \end{matrix}$$
* **Root Cause**: Default Material 3 `OutlinedButton`s enforce a minimum horizontal `ContentPadding` of `48.dp` (`24.dp` start + `24.dp` end). Inside a half-width card, almost no horizontal width was left for the text.
* **Resolution**: Replaced standard buttons with a lightweight `QuickTimeAdjustButton` composable (`height = 32.dp`, `RoundedCornerShape(8.dp)`, `maxLines = 1`, and subtle accent surface fill). Labels now render cleanly on a single horizontal line.

---

### 4. Dangling & Disconnected Vertical Timeline Rail
* **Symptom**: The vertical timeline line beneath each step number dangled in mid-air, terminating before reaching the next step node.
* **Root Cause**: The line was hardcoded to `.height(60.dp)` and the parent column used `Arrangement.spacedBy(14.dp)`, decoupling the line length from the dynamic card height.
* **Resolution**: Applied `Modifier.height(IntrinsicSize.Min)` on each timeline step `Row`, giving the vertical line `Modifier.fillMaxHeight()` and setting the card's bottom margin to `12.dp` (omitted on the terminal step). The rail now forms an unbroken track connecting Step 1 → Step 2 → Step 3 → Step 4.

---

### 5. Inset Box Rendering on Simulation Preset Cards
* **Symptom**: Selecting simulation presets in the bottom sheet displayed the same double-box border artifact.
* **Resolution**: Relocated `.padding(14.dp)` from the preset `Surface` modifier into its child `Row`.

---

## UI/UX Redesign & Biometric Polish

```mermaid
flowchart TD
    subgraph S1["Section 1: Sleep Deviation Controller"]
        Header["Header: 'Log Sleep Deviation' + Emerald 'Woke Up Now' Pill"]
        TimeCards["2-Column Grid: SLEEP ONSET (-30m/+30m) & ACTUAL WAKE (-15m/+15m)"]
        Override["Override Toggle: Dynamic Cyan Glow & Status Caption"]
        Header --> TimeCards --> Override
    end

    subgraph S2["Section 2: Active Recovery Prescription & Bento Grid"]
        Prescription["Active Strategy Prescription Banner (plan.headline)"]
        Bento["2x2 Biometric Grid: Total Sleep, Wake Delta, Time Until Anchor, Overnight Cap"]
        Prescription --> Bento
    end

    subgraph S3["Section 3: Connected Recovery Protocol Timeline"]
        Step1["Step 1: Enforce Strict Zero-Nap Lockout (20h 33m)"]
        Step2["Step 2: Execute STATE_BRIDGE_TO_WORK (2 Cycles / 3h 14m)"]
        Step3["Step 3: Accumulate Homeostatic Pressure (8h 16m)"]
        Step4["Step 4: Execute STATE_WORK_CORE (3 Cycles / 4h 44m)"]
        Step1 === Step2 === Step3 === Step4
    end

    subgraph S4["Section 4: Incident Simulation Engine"]
        IncidentCard["Accidental Nap Trap Simulator + Metric Summary Chips"]
    end

    S1 --> S2 --> S3 --> S4
```

| Area | Before v2.1.0 | Redesigned in v2.1.0 |
| :--- | :--- | :--- |
| **"Woke Up Now" CTA** | Plain text link button | High-contrast pill action chip with `VerifiedEmeraldBg` surface, 1dp emerald border, and clock icon |
| **Time Cards** | Boxy inner rectangle, wrapped stepper buttons | Flush rounded corners, edit/tune indicator icon in top right, and sleek `QuickTimeAdjustButton` chips |
| **Override Switch** | Dim card with static text | Dynamic border glow (`AnchorCyan.copy(alpha = 0.5f)`) and `"ACTIVE • Overriding home screen..."` description when enabled |
| **Active Strategy Banner** | *Omitted from UI* | Prominently renders `plan.headline` (e.g., `ZERO-NAP LOCKOUT (20h 33m) + 2-CYCLE BRIDGE CAP`) with guardrail icon |
| **Metric Bento Grid** | Plain text-only tiles | Thematic biometric icons (`Bedtime`, `Schedule`, `Lock`, `ShieldMoon`) with bold tabular figures |
| **Incident Simulator** | Unstructured text block | Added incident summary chips (`20:00 Sat Onset` → `04:27 Sun Wake` • `5 Cycles (8h 27m)`) and warning pill |

---

## Architectural & Code Changes

### Modified Files

#### [`app/src/main/java/com/darkside3211/biphasicsleep/ui/MainActivity.kt`](file:///c:/Alarm%20Android%20App/app/src/main/java/com/darkside3211/biphasicsleep/ui/MainActivity.kt)
* Added `QuickTimeAdjustButton` composable with tight horizontal padding and single-line typography.
* Refactored `RecoveryScreen` to integrate the active recovery strategy banner and dynamic override toggle styling.
* Upgraded `MetricTile` to support optional `ImageVector` icons while preserving 100% backward compatibility with `ScheduleScreen`.
* Overhauled `VerticalTimelineStepItem` with `IntrinsicSize.Min` rail measurement and separated metadata/title layout.
* Corrected `SimulationBottomSheetContent` surface modifier ordering.

---

## Verification & Build Results

* **Unit Test Suite**: `.\gradlew.bat testDebugUnitTest`  
  * **Result**: `BUILD SUCCESSFUL` (All state machine transitions, circadian calculators, and lockouts verified).
* **Debug APK Compilation**: `.\gradlew.bat assembleDebug`  
  * **Result**: `BUILD SUCCESSFUL` in 7s.
* **Direct Device Deployment**: Verified on Xiaomi 15 (`24129PN74G` over wireless ADB TLS).
