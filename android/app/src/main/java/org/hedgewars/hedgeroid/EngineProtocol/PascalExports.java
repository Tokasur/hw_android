/*
 * JNI bridge to the Free Pascal engine (libhwengine.so).
 *
 * The class lives in this legacy package because the engine's JNI export
 * names are fixed by Java_Prefix in hedgewars/options.inc:
 *   Java_org_hedgewars_hedgeroid_EngineProtocol_PascalExports_<name>
 *
 * Callers must ensure the engine and its dependencies are loaded first,
 * e.g. via EngineLoader.load(). Do not instantiate.
 */
package org.hedgewars.hedgeroid.EngineProtocol;

public final class PascalExports {
    private PascalExports() {}

    public static native int HWversionInfoNetProto();

    public static native String HWversionInfoVersion();

    /**
     * Generates a map preview and sends it to the frontend over the IPC
     * socket listening on the given loopback port. Blocks until done —
     * call from a worker thread, never from the UI thread.
     */
    public static native void HWGenLandPreview(int port);

    public static native int HWgetNumberOfWeapons();

    public static native int HWgetMaxNumberOfHogs();

    public static native int HWgetMaxNumberOfTeams();
}
