
Solowheel Xtreme App for Android
================================

This application displays real-time information from the Solowheel Xtreme by Inventist over Bluetooth LE.

Introduction
------------

The wireless support used to connect to the Xtreme is based on the "Bluetooth LeGatt" sample code provided with the Android SDK.
The sample bluetooth code was modified to only look for Solowheel Xtremes.

Bluetooth LeGatt creates a [Service][1] for managing connection and data communication with a GATT server hosted on a given Bluetooth LE device.  
The Activities communicate with the Service, which in turn interacts with the [Bluetooth LE API][2].

[1]:http://developer.android.com/reference/android/app/Service.html
[2]:https://developer.android.com/reference/android/bluetooth/BluetoothGatt.html

The Xtreme uses the Texas Instruments CC254X Bluetooth LE chipset.
[TI Bluetooth low energy software stack and tools][3]
[3]:http://www.ti.com/tool/ble-stack

[Bluetooth LE on Wikipedia][4]
[4]:https://en.wikipedia.org/wiki/Bluetooth_low_energy

Prerequisites
-------------

- Solowheel Xtreme (The Solowheel Classic does not have Bluetooth LE hardware)
- Android device that supports Bluetooth LE
- Android SDK version 18 or greater

Screenshots
-------------

<img src="https://github.com/kroot/SolowheelXtreme/screenshots/Scan.png" height="300" alt="Screenshot"/> 
<img src="https://github.com/kroot/SolowheelXtreme/screenshots/GaugeFull.png" height="300" alt="Screenshot"/> 
<img src="https://github.com/kroot/SolowheelXtreme/screenshots/Gauge.png" height="300" alt="Screenshot"/> 

Getting Started
---------------

This Android app uses the Gradle build system. To build this project, use the
"gradlew build" command or use "Import Project" in Android Studio.

Support
-------

- Google Group: https://groups.google.com/forum/#!forum/solowheel-xtreme-app

Patches are encouraged, and may be submitted by forking this project and
submitting a pull request through GitHub. Please see CONTRIBUTING.md for more details.

License
-------

Copyright 2014 The Android Open Source Project, Inc.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.
