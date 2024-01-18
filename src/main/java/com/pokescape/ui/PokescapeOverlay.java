/*
 * Copyright (c) 2024, Quo <https://github.com/Quoded>
 * Copyright (c) 2022, cmsu224 <https://github.com/cmsu224>
 * Copyright (c) 2022, Brianmm94 <https://github.com/Brianmm94>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.pokescape.ui;

import com.pokescape.PokescapeConfig;
import net.runelite.api.VarClientInt;
import net.runelite.api.Varbits;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Point;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;
import javax.inject.Inject;

public class PokescapeOverlay extends OverlayPanel {
    private @Inject Client client;
    private @Inject PokescapeConfig config;
    private int redrawDelay;
    private int collapsedTabsState;
    private boolean overlaySnapped;

    private static final int CHATBOX = ComponentID.CHATBOX_PARENT;
    private static final int INV_CLASSIC = ComponentID.RESIZABLE_VIEWPORT_INVENTORY_PARENT;
    private static final int INV_MODERN = ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_PARENT;
    private static final int MODERN_TABS1 = ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_TABS1;
    private static final int MODERN_TABS2 = ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_TABS2;

    private @Inject PokescapeOverlay() {
        setPosition(OverlayPosition.CANVAS_TOP_RIGHT);
        getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "PokeScape Overlay"));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        String password = config.eventPassword();
        Color passColor = config.passwordColor();
        Color timeColor = config.timestampColor();

        if (passColor.toString().equals(timeColor.toString())) {
            passColor = Color.GREEN;
            timeColor = Color.WHITE;
        }

        if (password.matches(".*\\w.*") && config.overlayVisibility()) {
            password = password.trim();
            panelComponent.getChildren().add(LineComponent.builder().left(password).leftColor(passColor).build());

            if (config.timeStampVisibility()) {
                password = password + " " + timeUTC();
                List<LayoutableRenderableEntity> elem = panelComponent.getChildren();
                ((LineComponent) elem.get(0)).setRight(timeUTC());
                ((LineComponent) elem.get(0)).setRightColor(timeColor);
            }

            panelComponent.setPreferredSize(new Dimension(graphics.getFontMetrics().stringWidth(password)+10, 0));
            if (!overlaySnapped) overlaySnapped = snapToWidget();
        }
        return super.render(graphics);
    }

    private static String timeUTC() {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy HH:mm");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(date) + " UTC";
    }

    public void recalcOverlay(boolean resized) {
        if (resized) redrawDelay = (!client.isResized()) ? -20 : -80;
        overlaySnapped = false;
    }
    public void setCollapsedTabsState(int state) { collapsedTabsState = state; }
    public int getCollapsedTabsState() { return collapsedTabsState; }

    private boolean snapToWidget() {
        // Return if snapping is turned off
        if (config.overlaySnapping() == PokescapeConfig.SnapMode.OFF) return true;

        int widgetID = calcSnapWidget();
        Widget snapParent = client.getWidget(widgetID);
        redrawDelay++;
        if (snapParent == null || redrawDelay < 10) return false;

        int invX = snapParent.getCanvasLocation().getX() + snapParent.getWidth();
        int invY = snapParent.getCanvasLocation().getY();
        int xOffset = (int)panelComponent.getBounds().getWidth()+2;
        int yOffset = (int)panelComponent.getBounds().getHeight()+2;
        if (invX <= 0 || invY <= 0 || xOffset <= 12 || yOffset <= 12) return false;

        int finalX = (config.overlaySnapping() == PokescapeConfig.SnapMode.CHATBOX || !client.isResized())
                ? snapParent.getCanvasLocation().getX()+2 : invX-xOffset;

        Point snapLocation = new Point(finalX, invY-yOffset);
        setPreferredLocation(snapLocation);
        redrawDelay = 0;
        return true;
    }

    private int calcSnapWidget() {
        // If chatbox is selected, return the chatbox
        if (config.overlaySnapping() == PokescapeConfig.SnapMode.CHATBOX) return CHATBOX;

        // If inventory is selected calculate where the overlay needs to be depending on the client layout
        String clientLayout = (client.getVarbitValue(Varbits.SIDE_PANELS) == 0) ? "res_classic" : "res_modern";
        if (!client.isResized()) clientLayout = "fixed";

        int widgetID;
        switch(clientLayout) {
            case "fixed": widgetID = CHATBOX; break;
            case "res_classic": widgetID = INV_CLASSIC; break;
            case "res_modern": widgetID = INV_MODERN; break;
            default: widgetID = CHATBOX;
        }

        // Calculates where the overlay will be if all tabs are closed on modern layout
        if (clientLayout.equals("res_modern") && client.getVarcIntValue(VarClientInt.INVENTORY_TAB) == -1) {
            int tabs1Y = Objects.requireNonNull(client.getWidget(MODERN_TABS1)).getCanvasLocation().getY();
            int tabs2Y = Objects.requireNonNull(client.getWidget(MODERN_TABS2)).getCanvasLocation().getY();
            widgetID = (tabs1Y > tabs2Y) ? MODERN_TABS2 : MODERN_TABS1;
        }
        return widgetID;
    }
}
