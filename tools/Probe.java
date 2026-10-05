public class Probe {
    public static void main(String[] args) throws Exception {
        for (String name : new String[]{
            "android.window.ScreenCapture", "android.window.ScreenCaptureInternal",
            "android.window.ScreenCaptureInternal$CaptureArgs$Builder",
            "android.window.ScreenCaptureInternal$ScreenCaptureListener",
            "android.window.ScreenCaptureInternal$SynchronousScreenCaptureListener",
            "android.view.IWindowManager", "android.view.OplusWindowManager"}) {
            try {
                Class<?> type = Class.forName(name);
                System.out.println("CLASS " + name);
                for (java.lang.reflect.Constructor<?> c : type.getDeclaredConstructors()) System.out.println(c);
                for (java.lang.reflect.Method m : type.getDeclaredMethods()) {
                    if (name.contains("Capture") || m.getName().toLowerCase().contains("capture") || m.getName().toLowerCase().contains("screenshot")) System.out.println(m);
                }
            } catch (Throwable t) { System.out.println(t); }
        }
    }
}
