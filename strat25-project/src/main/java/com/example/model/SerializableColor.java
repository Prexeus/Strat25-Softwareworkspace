package com.example.model;

import javafx.scene.paint.Color;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * SerializableColor
 * -----------------
 * Immutable, serializable RGBA color (0..1 doubles) for your domain model.
 * Includes helpers to convert to/from JavaFX Color.
 *
 * Note: The JavaFX Color itself is NOT serialized; it's cached transiently.
 */
public final class SerializableColor implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private final double r; // 0..1
    private final double g; // 0..1
    private final double b; // 0..1
    private final double a; // 0..1

    // Transient cache of the JavaFX Color instance (never serialized)
    private transient Color fxColor;

    // ---------- factories ----------------------------------------------------

    public static SerializableColor of(double r, double g, double b) {
        return new SerializableColor(r, g, b, 1.0);
    }

    public static SerializableColor of(double r, double g, double b, double a) {
        return new SerializableColor(r, g, b, a);
    }

    /** From 0..255 ints (alpha defaults to 255). */
    public static SerializableColor of255(int r, int g, int b) {
        return of255(r, g, b, 255);
    }

    /** From 0..255 ints including alpha. */
    public static SerializableColor of255(int r, int g, int b, int a) {
        return new SerializableColor(
                clamp01(r / 255.0),
                clamp01(g / 255.0),
                clamp01(b / 255.0),
                clamp01(a / 255.0)
        );
    }

    /** Parse #RRGGBB or #RRGGBBAA (case-insensitive). */
    public static SerializableColor fromHex(String hex) {
        String h = hex == null ? "" : hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() != 6 && h.length() != 8)
            throw new IllegalArgumentException("hex must be #RRGGBB or #RRGGBBAA");
        int ri = Integer.parseInt(h.substring(0, 2), 16);
        int gi = Integer.parseInt(h.substring(2, 4), 16);
        int bi = Integer.parseInt(h.substring(4, 6), 16);
        int ai = (h.length() == 8) ? Integer.parseInt(h.substring(6, 8), 16) : 255;
        return of255(ri, gi, bi, ai);
    }

    /** Create from a JavaFX Color. */
    public static SerializableColor fromFx(Color fx) {
        if (fx == null) return null;
        return new SerializableColor(fx.getRed(), fx.getGreen(), fx.getBlue(), fx.getOpacity());
    }

    // ---------- ctor ---------------------------------------------------------

    public SerializableColor(double r, double g, double b, double a) {
        this.r = clamp01(r);
        this.g = clamp01(g);
        this.b = clamp01(b);
        this.a = clamp01(a);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // ---------- accessors ----------------------------------------------------

    public double getR() { return r; }
    public double getG() { return g; }
    public double getB() { return b; }
    public double getA() { return a; }

    public int getR255() { return (int) Math.round(r * 255); }
    public int getG255() { return (int) Math.round(g * 255); }
    public int getB255() { return (int) Math.round(b * 255); }
    public int getA255() { return (int) Math.round(a * 255); }

    /** Returns #RRGGBB (alpha omitted). */
    public String toHexRgb() {
        return String.format("#%02X%02X%02X", getR255(), getG255(), getB255());
    }

    /** Returns #RRGGBBAA (includes alpha). */
    public String toHexRgba() {
        return String.format("#%02X%02X%02X%02X", getR255(), getG255(), getB255(), getA255());
    }

    /** Returns a copy with a different alpha. */
    public SerializableColor withAlpha(double newAlpha) {
        return new SerializableColor(r, g, b, newAlpha);
    }

    /** Convert to JavaFX Color (cached transiently). */
    public Color getFxColor() {
        Color c = fxColor;
        if (c == null) {
            c = new Color(r, g, b, a);
            fxColor = c;
        }
        return c;
    }

    // ---------- equality / hash / toString -----------------------------------

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SerializableColor other)) return false;
        return Double.compare(other.r, r) == 0 &&
               Double.compare(other.g, g) == 0 &&
               Double.compare(other.b, b) == 0 &&
               Double.compare(other.a, a) == 0;
    }

    @Override public int hashCode() {
        return Objects.hash(r, g, b, a);
    }

    @Override public String toString() {
        return "SerializableColor{" +
                "r=" + r + ", g=" + g + ", b=" + b + ", a=" + a +
                ", hex='" + toHexRgba() + '\'' +
                '}';
    }

    public Color toJavaFXColor() {
        return getFxColor();
    }

}
