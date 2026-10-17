# APK verification report

APK: `dist/MBGA-v1.5.0-libxposed102-vc170.apk`

- size: **6923101 bytes**
- sha256: `15E38319B41BE2998249FCB4E14450A331AEA0CB64D7562CA162ED81E96AF6C7`

## 1. libxposed metadata inside the APK

```
META-INF/xposed/module.prop      555 bytes | LF-terminated: True | BOM: False
# libxposed module metadata (Properties format)
# See https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API
id=top.trangle.mbga
name=MBGA
version=1.5.0
versionCode=170
author=cledwynl
description=Make Bilibili Great Again!

# This module is built against, and targets, libxposed API 102
minApiVersion=102
targetApiVersion=102

# Scope is fixed by scope.list, users should not apply the module to other apps
staticScope=true

# Keep the original behaviour when a hook throws: do not crash the host
exceptionMode=protective

META-INF/xposed/java_init.list   32 bytes | LF-terminated: True | BOM: False
top.trangle.mbga.hook.HookEntry

META-INF/xposed/scope.list       36 bytes | LF-terminated: True | BOM: False
com.bilibili.app.in
tv.danmaku.bili

entries: 957
dex files: classes.dex, classes2.dex
legacy assets/xposed_init present: False
legacy META-INF/yukihookapi_init present: False

```

## 2. Manifest

```
package: name='top.trangle.mbga' versionCode='170' versionName='1.5.0' platformBuildVersionName='14' platformBuildVersionCode='34' compileSdkVersion='34' compileSdkVersionCodename='14'
sdkVersion:'27'
targetSdkVersion:'34'
application-label:'MBGA'
application: label='MBGA' icon='res/BW.xml'

        A: http://schemas.android.com/apk/res/android:label(0x01010001)=@0x51100020
        A: http://schemas.android.com/apk/res/android:description(0x01010020)=@0x51100110
            A: http://schemas.android.com/apk/res/android:label(0x01010001)=@0x51100022
```

## 3. DEX: entry class and API usage

```
  Class descriptor  : 'Ltop/trangle/mbga/hook/HookEntry;'
  Access flags      : 0x0011 (PUBLIC FINAL)
  Superclass        : 'Lio/github/libxposed/api/XposedModule;'

io/github/libxposed/api/XposedModule           4
io/github/libxposed/api/XposedInterface        6
io/github/libxposed/api/XposedModuleInterface  2
getRemotePreferences                           2
de/robv/android/xposed (legacy)                0
highcapable/yukihookapi                        0

```

## 4. Signature

```
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Signer #1 certificate DN: CN=MBGA, OU=MBGA, O=MBGA, L=Beijing, ST=Beijing, C=CN
Signer #1 certificate SHA-256 digest: 3827ce0d0d00512cbddac21a5d30b0dee78111735e0b269b043a373eeb1834c9
Signer #1 public key SHA-256 digest: f7fba5da291b228c8e8f64763408fe0b5f89283e76b42782751f1fa89e5bb455
```
