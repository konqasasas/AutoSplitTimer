package com.konqasasas.ast.ui;

/** Lazily loaded fonts shared by native screens and the HUD. */
public final class AstFonts {
    private AstFonts() {}

    private static boolean batching;

    private static AstFont sans;
    private static AstFont semibold;
    private static AstFont japanese;
    private static AstFont japaneseBold;
    private static AstFont mono;

    public static AstFont sans() { return sans == null ? sans = load("IBMPlexSans-Medium.ttf") : sans; }
    public static AstFont semibold() { return semibold == null ? semibold = load("IBMPlexSans-SemiBold.ttf") : semibold; }
    public static AstFont japanese() { return japanese == null ? japanese = load("BIZUDPGothic-Regular.ttf") : japanese; }
    public static AstFont japaneseBold() { return japaneseBold == null ? japaneseBold = load("BIZUDPGothic-Bold.ttf") : japaneseBold; }
    public static AstFont mono() { return mono == null ? mono = load("IBMPlexMono-Medium.ttf") : mono; }

    private static AstFont load(String file) {
        return new AstFont("/assets/autosplittimer/fonts/" + file);
    }

    /** Parse the bundled font files while Minecraft is still loading. */
    public static void warmUp() {
        sans();
        semibold();
        japanese();
        japaneseBold();
        mono();
    }

    static void beginFrame() { batching = true; }

    static void endFrame() {
        batching = false;
        if (sans != null) sans.flush();
        if (semibold != null) semibold.flush();
        if (japanese != null) japanese.flush();
        if (japaneseBold != null) japaneseBold.flush();
        if (mono != null) mono.flush();
    }

    static boolean isBatching() { return batching; }

    public static boolean containsJapanese(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) > 0x024f) return true;
        return false;
    }

    public static AstFont ui(String text, boolean bold) {
        if (containsJapanese(text)) return bold ? japaneseBold() : japanese();
        return bold ? semibold() : sans();
    }

    public static AstFont ui(char character, boolean bold) {
        return character > 0x024f ? (bold ? japaneseBold() : japanese()) : (bold ? semibold() : sans());
    }
}
