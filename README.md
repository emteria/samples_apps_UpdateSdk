# Emteria OS Update SDK Sample

Capabilities:
 * Bind to the OTA service
 * Retrieve information about available OS updates  
 * Invoke the download of the chosen update version in the background
 * Invoke the installation of the downloaded update

# Using SDK in third-party apps

1. Copy SDK .jar from `src/main/libs` to your project
2. Add to app's `build.gradle` file:

```
implementation(fileTree("src/main/libs") { include("*.jar") })
```

3. Add to app's `AndroidManifest.xml` file:

```
<uses-permission android:name="emteria.permission.MANAGE_OS_UPDATES" />
```
