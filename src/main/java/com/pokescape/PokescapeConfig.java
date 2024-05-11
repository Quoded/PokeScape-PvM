/*
 * Copyright (c) 2024, Quo <https://github.com/Quoded>
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
package com.pokescape;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("pokescape")
public interface PokescapeConfig extends Config {
	String PLUGIN_VERSION = "0.5.3";

	enum SnapMode {
		INV("Top of Inventory"), CHATBOX("Top of Chatbox"), OFF("Off (Alt-Drag)");
		private final String stringValue;
		SnapMode(final String s) { stringValue = s; }
		public String toString() { return stringValue; }
	}

	@ConfigItem(
			position = 1,
			keyName = "panel_visibility",
			name = "Show Side Panel",
			description = "Displays PokeScape PvM in the side panel."
	)
	default boolean showPokescapeSidePanel() {
		return true;
	}

	@ConfigSection(
			name = "Overlay",
			description = "Overlay configuration.",
			position = 2
	)
	String overlaySection = "Overlay section";

	@ConfigItem(
			position = 3,
			keyName = "overlay_visibility",
			name = "Display Overlay",
			description = "Displays the overlay on your game screen.",
			section = overlaySection
	)
	default boolean overlayVisibility() { return true; }

	@ConfigItem(
			position = 4,
			keyName = "overlay_timestamp",
			name = "Timestamp",
			description = "Adds a timestamp to the overlay.",
			section = overlaySection
	)
	default boolean timeStampVisibility() { return true; }

	@ConfigItem(
			position = 5,
			keyName = "overlay_password",
			name = "Event Password:",
			description = "Adds the event password to the overlay.",
			section = overlaySection
	)
	default String eventPassword() { return ""; }

	@ConfigItem(
			position = 5,
			keyName = "overlay_password",
			name = "Event Password:",
			description = "Adds the event password to the overlay.",
			section = overlaySection,
			hidden = true
	)
	void setEventPassword(String password);

	@ConfigItem(
			position = 6,
			keyName = "overlay_snapmode",
			name = "Snap Position",
			description = "Controls the snap position of the overlay.",
			section = overlaySection
	)
	default SnapMode overlaySnapping() { return SnapMode.INV; }

	@ConfigItem(
			position = 7,
			keyName = "overlay_passcolor",
			name = "Password Color",
			description = "Sets the color of the event password.",
			section = overlaySection
	)
	default Color passwordColor() { return Color.GREEN; }

	@ConfigItem(
			position = 8,
			keyName = "overlay_passtime",
			name = "Timestamp Color",
			description = "Sets the color of the timestamp.",
			section = overlaySection
	)
	default Color timestampColor() { return Color.WHITE; }

}
