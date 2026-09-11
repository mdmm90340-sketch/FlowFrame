# Error Prone annotations reference a javac-only enum, absent on Android.
# This rule applies only to the instrumentation APK, never to the shipped app.
-dontwarn javax.lang.model.element.Modifier
