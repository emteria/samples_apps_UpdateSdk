# Emteria OS Update SDK Sample

A minimal Android app showing how to use the **emteria Update SDK** to drive
over-the-air (OTA) OS updates from a third-party app. It binds to the update
service that ships with emteria OS and demonstrates every operation the SDK
exposes.

## What this sample demonstrates

- Bind to / unbind from the emteria OTA service
- Query the **currently installed emteria OS version**
- Check for available OS updates on the configured channel
- Download a selected update in the background (with progress)
- Install a downloaded update (with progress and reboot-required signal)

All of this lives in a single screen — see
[`MainActivity.java`](app/src/main/java/com/emteria/sample/sdk/update/MainActivity.java).

## Prerequisites & compatibility

- **Runs only on emteria OS.** The sample binds to the service
  `com.emteria.update/.UpdateService`, which is part of emteria OS. On stock
  Android / AOSP or a plain emulator the bind fails and the operations do
  nothing.
- **No special permission is required.** The update service is exported and
  freely bindable — the app only needs to run on a device where the service is
  present.
- Build targets: `compileSdk 34`, `targetSdk 34`, `minSdk 30` (Android 11).
- Bundled SDK: `app/src/main/libs/emteria-update-sdk-v1.7.jar`.

## How it works

The SDK is a thin contract over Android's bound-service **Messenger** IPC. There
is no synchronous API — every call is a message, and every result comes back
asynchronously on a callback handler.

1. Bind to the service using the intent from
   `MessengerConfig.getServiceBindIntent()`. On connect you receive a request
   `Messenger`.
2. Build a request message with the relevant `*Contract` class, attaching your
   own response `Messenger`, and `send()` it.
3. The service replies with a message whose `what` is an ordinal of
   `MessengerConfig.ResponseReason`. Your `Handler` maps that reason to the
   matching `*Contract` extractor to read the payload out of the `Bundle`.

```java
// Bind (see MainActivity#bindUpdateService)
Intent bindIntent = MessengerConfig.getServiceBindIntent();
bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE);

// Request (example: OS version)
Message request = OsVersionContract.OsVersionRequest.buildMessage(mResponseMessenger);
mRequestMessenger.send(request);
```

Responses are dispatched in `MainActivity.CallbackHandler#handleMessage`, which
switches on the `ResponseReason`.

## Integrating the SDK in your own app

1. Copy the SDK `.jar` from `app/src/main/libs` into your project.
2. Add the JAR to your module's `build.gradle`:

   ```groovy
   implementation(fileTree("src/main/libs") { include("*.jar") })
   ```

3. Declare service visibility in `AndroidManifest.xml` so the OTA service is
   resolvable on Android 11+ (package visibility filtering):

   ```xml
   <queries>
       <package android:name="com.emteria.update" />
   </queries>
   ```

That is the complete setup — no permissions to request.

## Operations

Each operation sends one request and is answered by one or more of the response
reasons below.

### Get OS version

- **Request:** `OsVersionContract.OsVersionRequest.buildMessage(responseMessenger)`
- **Result:** `GET_OS_VERSION_RESULT` →
  `OsVersionContract.OsVersionResultResponse.extractOsVersion(payload)` returns
  the version string.
- **Error:** `GET_OS_VERSION_ERROR` →
  `OsVersionContract.OsVersionErrorResponse.extractErrorMessage(payload)`.

The version is the value of the system property `ro.vendor.release.version`
(e.g. `13.1.33`), with any `-debug` suffix stripped. On a non-emteria system it
reads as `0.0.0`.

### Check for updates

- **Request:** `UpdateMetadataContract.MetadataRequest.buildMessage(responseMessenger, false)`
- **Results:**
  - `GET_UPDATES_LIST` →
    `UpdateMetadataContract.MetadataListResponse.extractUpdateList(payload)`
    returns a `List<UpdatePackage>`. Each `UpdatePackage` exposes
    `getUpdateId()`, `getVersion()`, `getChannel()`, and `getFileSize()`.
  - `GET_UPDATES_UP_TO_DATE` → no newer version is available.
- **Error:** `GET_UPDATES_ERROR` →
  `UpdateMetadataContract.MetadataErrorResponse.extractErrorMessage(payload)`.

### Download an update

- **Request:** `UpdateDownloadContract.DownloadRequest.buildMessage(responseMessenger, updateId)`
- **Results:**
  - `DOWNLOAD_UPDATE_PROGRESS` →
    `UpdateDownloadContract.DownloadProgressResponse.extractDownloadProgress(payload)`
    returns 0–100 (or `-1` for indeterminate).
  - `DOWNLOAD_UPDATE_SUCCESS` →
    `UpdateDownloadContract.DownloadSuccessResponse.extractUpdateId(payload)`.
- **Error:** `DOWNLOAD_UPDATE_ERROR` →
  `UpdateDownloadContract.DownloadErrorResponse.extractErrorMessage(payload)`.

### Install an update

- **Request:** `UpdateInstallationContract.InstallationRequest.buildMessage(responseMessenger, updateId)`
- **Results:**
  - `INSTALL_UPDATE_PROGRESS` →
    `UpdateInstallationContract.InstallationProgressResponse.extractInstallationProgress(payload)`.
  - `INSTALL_UPDATE_REBOOT_REQUIRED` → installation finished; the device must
    reboot to apply the update.
- **Error:** `INSTALL_UPDATE_ERROR` →
  `UpdateInstallationContract.InstallationErrorResponse.extractErrorMessage(payload)`.

## Response reason reference

The full set of response codes is defined in
`MessengerConfig.ResponseReason`. Those handled by this sample:

| Reason                           | Meaning                                   |
|----------------------------------|-------------------------------------------|
| `GET_OS_VERSION_RESULT`          | Installed OS version returned             |
| `GET_OS_VERSION_ERROR`           | Version lookup failed                     |
| `GET_UPDATES_LIST`               | One or more updates available             |
| `GET_UPDATES_UP_TO_DATE`         | System is up to date                      |
| `GET_UPDATES_ERROR`              | Update check failed                       |
| `DOWNLOAD_UPDATE_PROGRESS`       | Download progress (0–100, `-1` = pending) |
| `DOWNLOAD_UPDATE_SUCCESS`        | Download complete                         |
| `DOWNLOAD_UPDATE_ERROR`          | Download failed                           |
| `INSTALL_UPDATE_PROGRESS`        | Installation progress                     |
| `INSTALL_UPDATE_REBOOT_REQUIRED` | Installed; reboot needed to apply         |
| `INSTALL_UPDATE_ERROR`           | Installation failed                       |

`InvalidUpdatePayloadException` is thrown by the extractors if a response
payload is malformed; the sample catches it in `handleMessage`.

## Build & run

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch the app on an emteria OS device and use the on-screen buttons — *Get OS
version*, *Check for updates*, *Download update*, *Install update* — in that
order. The text box shows the current state and the progress bar reflects
download/installation progress.

## Troubleshooting

- **"Service not bound, try again"** — the OTA service could not be bound.
  Confirm you are on an emteria OS device and that the `<queries>` entry above is
  present in your manifest.
- **All operations silent / no response** — same cause as above; the service is
  absent on non-emteria systems.
- **OS version reads `0.0.0`** — the app is not running on emteria OS.

## License

See the repository for licensing information. For SDK questions, contact
[emteria support](https://emteria.com).
