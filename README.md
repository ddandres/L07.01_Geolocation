# L07.01_Geolocation
Lecture 07.01 Geolocation, DISCA - UPV, Development of apps for mobile devices.

Makes use two different mechanisms to get the current location of the device and translates the longitude and latitude into a human readable address:
- Android Location Framework: Simple and integrated into the Android SDK. Requires permission management. However, Google recommends the use of its own API.
- Google Location API: Requires Google Play Services and permission management, although it also provides automated location tracking, Geofences and activity recognition.

Whenever the app is paused, the location managers will stop receiving updates.
