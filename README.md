## 🤖 **Android System Overlay App**

A simple, conceptual Android application that demonstrates how to create and manage a persistent system overlay (floating window) on top of other running applications.

## 🚀 **Features**

- **Persistent Overlay**: A window that floats over other apps, managed by a foreground Service.
  
- **Draggable Interface**: The overlay can be moved around the screen.
  
- **System Permissions Handling**: Demonstrates checking for and requesting the necessary SYSTEM_ALERT_WINDOW permission.
  
- **Foreground Service**: Uses a Notification to ensure the service runs reliably and informs the user.

## 🛠️ **Requirements**

- **Android Studio**
  
- **Minimum SDK: 26 (Android 8.0 Oreo) or higher (due to updated WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY usage).**

## 🔥**Running the App**

- Clone the repository.
   
- Build and run the application on an Android device (emulators often require special handling for overlays).
 
- The main activity will prompt the user to grant the "Display over other apps" permission.
 
- Once permission is granted, a button press in the main activity should start the OverlayService.
 
- A floating window will appear on the screen, which can be dragged around.

## 💡 **How it Works**

- The core functionality is housed in the OverlayService.kt:
  
- It uses getSystemService(Context.WINDOW_SERVICE) to get the WindowManager.
  
- It creates a WindowManager.LayoutParams object with the type set to TYPE_APPLICATION_OVERLAY.
  
- A view (e.g., a simple button or text view) is inflated and attached to the WindowManager using addView().
  
- The service is started as a Foreground Service with a permanent notification to ensure it isn't killed by the Android system.

## ✍️ **Contribution**

Feel free to open issues or submit pull requests to enhance the overlay functionality, add better UI/UX, or improve drag handling.
