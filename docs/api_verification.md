# PrivacyGuard — Android & Shizuku API Verification Report

This report documents the verification of all target Android platform APIs and Shizuku integration APIs against the installed SDK (`C:\Android\Sdk\platforms\android-36.1\android.jar`), SDK tools (`36.1.0`), and Java 17/21 runtime.

---

## 1. Verified API Signatures Matrix

| Need | API / Signature | Min API | Status in SDK | Behavior & Gotchas |
|------|-----------------|---------|---------------|-------------------|
| Foreground Start | `UsageEvents.Event.ACTIVITY_RESUMED` (= 1) | API 29 | Public constant | On API 26–28, use `MOVE_TO_FOREGROUND` (= 1). Both share numeric value `1`. |
| Foreground End | `UsageEvents.Event.ACTIVITY_STOPPED` (= 23) | API 29 | Public constant | On API 26–28, use `MOVE_TO_BACKGROUND` (= 2). Note: `ACTIVITY_PAUSED` is also 2; on API 29+ `ACTIVITY_STOPPED` (23) prevents false closes during transient pauses. |
| Query Events | `UsageStatsManager.queryEvents(long beginTime, long endTime)` | API 21 | Public method | Returns `UsageEvents`. Must handle empty/closed iterator with `getNextEvent`. Requires `PACKAGE_USAGE_STATS`. |
| Permission Baseline | `PackageManager.getPackageInfo(String, int)` -> `requestedPermissions`, `requestedPermissionsFlags` | API 16 | Public | Test `requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED` (= 2). On API 33+, overload with `PackageManager.PackageInfoFlags.of(long)` is available; int flag `GET_PERMISSIONS` is maintained for backwards compatibility. |
| AppOps Access (Hidden) | `AppOpsManager.getPackagesForOps(int[])` | Hidden | `@SystemApi` / hidden | Not exposed in public `android.jar`. Must execute via Shizuku privileged `UserService` or hidden reflection/binder wrapper. |
| AppOps Access (Shell) | `appops get <pkg>` / `cmd appops get <pkg>` | API 21+ | Shell CLI | Executed via Shizuku shell/binder. Relative timestamps ("time=+...ago") require conversion using query clock; parses latest access per op. |
| Suspend Packages | `DevicePolicyManager.setPackagesSuspended(ComponentName admin, String[] packageNames, boolean suspended)` | API 24 | Public method | Returns `String[]` of packages that could NOT be suspended (failed packages). Caller must inspect this array for `not_blockable` logic. |
| Query Suspend | `DevicePolicyManager.isPackageSuspended(ComponentName admin, String packageName)` | API 24 | Public method | Throws `PackageManager.NameNotFoundException` if target app is uninstalled. Must catch in Kotlin host. |
| Suspended Details Action | `android.intent.action.SHOW_SUSPENDED_APP_DETAILS` | API 28 | System Intent Action | Protected intent sent by OS when user taps suspended app. Not in public `Intent.java` constants; defined as string. Activity requires permission `android.permission.SEND_SHOW_SUSPENDED_APP_DETAILS`. Extra passed is `android.content.Intent.EXTRA_PACKAGE_NAME` (`"android.intent.extra.PACKAGE_NAME"`). |
| Device Owner Check | `DevicePolicyManager.isDeviceOwnerApp(String packageName)` | API 18 | Public method | Returns `boolean`. Confirmed in `android-36.1`. |
| Clear Device Owner | `DevicePolicyManager.clearDeviceOwnerApp(String packageName)` | API 21 | Deprecated in API 26 | Public method in `android.jar`. Note: on production builds without test-keys or `android:testOnly="true"`, clearing Device Owner is restricted by the OS. Must test Release Control thoroughly. |
| Usage Access Check | `AppOpsManager.checkOpNoThrow(String op, int uid, String packageName)` | API 19 | Public method | Check `OPSTR_GET_USAGE_STATS` (`"android:get_usage_stats"`). Redirect to `Settings.ACTION_USAGE_ACCESS_SETTINGS`. |

---

## 2. Shizuku API Specification (v13.1.5)

| Component / Method | Signature | Purpose |
|--------------------|-----------|---------|
| `Shizuku.pingBinder()` | `boolean pingBinder()` | Validates IPC binder connectivity to Shizuku service. |
| `Shizuku.checkSelfPermission()` | `int checkSelfPermission()` | Returns `PackageManager.PERMISSION_GRANTED` (0) or `PERMISSION_DENIED` (-1). |
| `Shizuku.requestPermission()` | `void requestPermission(int requestCode)` | Requests user authorization in Shizuku Manager. |
| `Shizuku.addBinderReceivedListenerSticky` | `void addBinderReceivedListenerSticky(OnBinderReceivedListener)` | Observes binder initialization. |
| `Shizuku.addBinderDeadListener` | `void addBinderDeadListener(OnBinderDeadListener)` | Detects service termination/reboot for graceful degradation. |
| `Shizuku.bindUserService` | `void bindUserService(UserServiceArgs, ServiceConnection)` | Launches and binds privileged background process for AppOps inspection without restricted hidden API blocks. |

---

## 3. Discrepancies and Nuances Identified

1. **`SHOW_SUSPENDED_APP_DETAILS` constant visibility**:
   The action string `"android.intent.action.SHOW_SUSPENDED_APP_DETAILS"` and protection permission `"android.permission.SEND_SHOW_SUSPENDED_APP_DETAILS"` are system-level intents and permissions not defined in public SDK `Intent.java`. They must be declared directly as string literals in the AndroidManifest and activity code.
2. **`isPackageSuspended` exception contract**:
   `dpm.isPackageSuspended(admin, pkg)` explicitly throws `PackageManager.NameNotFoundException`. The Kotlin wrapper must wrap the call in a `try-catch` block rather than assuming a silent boolean return.
3. **`clearDeviceOwnerApp` deprecation & test-only constraint**:
   AOSP deprecated `clearDeviceOwnerApp` in API 26 and enforces that Device Owner cannot be removed on production devices without a factory reset unless `testOnly="true"` is set in debug builds or development keys are present.
4. **Usage event constants across API levels**:
   `ACTIVITY_RESUMED` (= 1) matches legacy `MOVE_TO_FOREGROUND` (= 1), but `ACTIVITY_STOPPED` (= 23) does NOT match legacy `MOVE_TO_BACKGROUND` (= 2). A version-branching parser must check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q`.
