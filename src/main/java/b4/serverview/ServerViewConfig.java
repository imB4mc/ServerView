package b4.serverview;

public class ServerViewConfig {
    // Main Toggle
    public static boolean masterToggle = true;

    // Feature Toggles
    public static boolean entityFreezeEnabled = true;       // For "Fixed lazy entity rendering"
    public static boolean borderChunkRenderingEnabled = true; // For "Fixed lazy block rendering"
    public static boolean entitySyncEnabled = false;        // For "Permanent entity synchronization"
    public static boolean illegalBlockDetector = false;     // For "Illegal block detector"
    public static double ghostBlockTransparency = 0.5;      // For "Ghost block transparency" (0.0 to 1.0)

    // Piston Features
    public static boolean smoothPistons = false;
    public static boolean noLightFlickers = false;
}