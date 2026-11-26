package org.cef.browser;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.cef.CefClient;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;

/**
 * An off-screen browser that hands its frames over as pixels (BGRA, premultiplied), with no window
 * or OpenGL of its own, so the desktop app can draw the page in Compose over its own background.
 * In org.cef.browser because the browser base class is package-private.
 */
public final class CefBrowserPixels extends CefBrowser_N implements CefRenderHandler {
    private final boolean transparent;
    private volatile Rectangle rect = new Rectangle(0, 0, 640, 480);
    private volatile double scale = 1.0;
    private final CopyOnWriteArrayList<Consumer<CefPaintEvent>> listeners = new CopyOnWriteArrayList<>();

    public CefBrowserPixels(CefClient client, String url, boolean transparent, CefRequestContext context) {
        super(client, url, context, null, null, null);
        this.transparent = transparent;
    }

    /** The page's size in CSS pixels and the screen's scale. */
    public void resize(int width, int height, double scaleFactor) {
        rect = new Rectangle(0, 0, Math.max(1, width), Math.max(1, height));
        scale = scaleFactor;
        if (getNativeRef("CefBrowser") != 0) {
            wasResized(rect.width, rect.height);
        }
    }

    @Override
    public void createImmediately() {
        if (getNativeRef("CefBrowser") == 0) createBrowser(getClient(), 0, getUrl(), true, transparent, null, getRequestContext());
    }

    @Override public Component getUIComponent() { return null; }
    @Override public CefRenderHandler getRenderHandler() { return this; }

    @Override
    protected CefBrowser_N createDevToolsBrowser(CefClient client, String url, CefRequestContext context, CefBrowser_N parent, Point inspectAt) {
        return null;
    }

    @Override public Rectangle getViewRect(CefBrowser browser) { return rect; }
    @Override public Point getScreenPoint(CefBrowser browser, Point viewPoint) { return new Point(viewPoint); }
    @Override public void onPopupShow(CefBrowser browser, boolean show) {}
    @Override public void onPopupSize(CefBrowser browser, Rectangle size) {}
    @Override public void addOnPaintListener(Consumer<CefPaintEvent> listener) { listeners.add(listener); }
    @Override public void setOnPaintListener(Consumer<CefPaintEvent> listener) { listeners.clear(); listeners.add(listener); }
    @Override public void removeOnPaintListener(Consumer<CefPaintEvent> listener) { listeners.remove(listener); }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
        if (popup || listeners.isEmpty()) return;
        CefPaintEvent event = new CefPaintEvent(browser, false, dirtyRects, buffer, width, height);
        for (Consumer<CefPaintEvent> l : listeners) l.accept(event);
    }

    @Override public boolean onCursorChange(CefBrowser browser, int cursorType) { return true; }
    @Override public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) { return false; }
    @Override public void updateDragCursor(CefBrowser browser, int operation) {}

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
        screenInfo.Set(scale, 32, 8, false, rect.getBounds(), rect.getBounds());
        return true;
    }

    @Override
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution) {
        return CompletableFuture.completedFuture(null);
    }
}
