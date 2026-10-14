package com.star.app;

/** Runs as shell via app_process to read real Binder transaction codes.
 *  Shell user is not subject to the Android 14 hidden-API block. */
public class BinderProbe {
    public static void main(String[] args) {
        print("android.hardware.display.IDisplayManager$Stub",
              new String[]{"TRANSACTION_createVirtualDisplay",
                           "TRANSACTION_releaseVirtualDisplay",
                           "TRANSACTION_getDisplayIds",
                           "TRANSACTION_getDisplayInfo"});
        print("android.app.IActivityTaskManager$Stub",
              new String[]{"TRANSACTION_startActivity",
                           "TRANSACTION_startActivityAsUser",
                           "TRANSACTION_moveTaskToDisplay"});
        print("android.hardware.input.IInputManager$Stub",
              new String[]{"TRANSACTION_injectInputEvent"});
    }

    private static void print(String cls, String[] names) {
        try {
            Class<?> c = Class.forName(cls);
            for (String n : names) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField(n);
                    f.setAccessible(true);
                    int v = f.getInt(null);
                    System.out.println("CODE " + n + " = " + v);
                } catch (Throwable t) {
                    System.out.println("CODE " + n + " = MISSING (" + t.getClass().getSimpleName() + ")");
                }
            }
        } catch (Throwable t) {
            System.out.println("CLASS " + cls + " MISSING: " + t.getMessage());
        }
    }
}
