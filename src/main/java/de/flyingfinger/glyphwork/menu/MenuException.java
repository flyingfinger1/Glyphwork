package de.flyingfinger.glyphwork.menu;

/** A problem in a menu file, with the place it occurred ("warps.yml: elements[2].x: ..."). */
public final class MenuException extends Exception {

    public MenuException(String message) {
        super(message);
    }
}
