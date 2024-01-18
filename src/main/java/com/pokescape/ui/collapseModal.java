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
package com.pokescape.ui;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class collapseModal extends JPanel {
	private final JPanel titleModal;
	private final JLabel titleText;
	private final JComponent modalContent;
	private boolean isExpanded;

	public collapseModal(String modalTitle, JComponent modalContent, boolean modalExpanded) {
		this.isExpanded = modalExpanded;
		this.modalContent = modalContent;

		setLayout(new BorderLayout(0, 1));
		setBorder(new EmptyBorder(10, 0, 0, 0));

		titleModal = new JPanel();
		titleModal.setBorder(new EmptyBorder(3, 3, 3, 3));
		titleModal.setBackground(modalExpanded ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR);
		titleModal.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		titleModal.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onMouseClick();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				onMouseHover(true);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				onMouseHover(false);
			}
		});

		titleText = new JLabel(modalTitle);
		titleText.setFont(FontManager.getRunescapeBoldFont());
		titleText.setForeground(modalExpanded ? Color.BLACK : Color.WHITE);
		titleModal.add(titleText);

		modalContent.setVisible(modalExpanded);

		add(titleModal, BorderLayout.NORTH);
		add(modalContent, BorderLayout.CENTER);
	}

	public collapseModal(String modalTitle, JComponent modalContent)
	{
		this(modalTitle, modalContent, false);
	}

	private void onMouseHover(boolean isHovering) {
		Color color = ColorScheme.DARKER_GRAY_COLOR;

		if (isHovering) {
			color = ColorScheme.DARKER_GRAY_HOVER_COLOR;
			if (isExpanded) color = ColorScheme.BRAND_ORANGE.darker();
		} else if (isExpanded) color = ColorScheme.BRAND_ORANGE;
		titleModal.setBackground(color);
	}

	private void onMouseClick() {
		isExpanded = !isExpanded;
		Color color = isExpanded ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR;
		Color textColor = isExpanded ? Color.BLACK : Color.WHITE;

		titleModal.setBackground(color);
		titleText.setForeground(textColor);
		modalContent.setVisible(isExpanded);
	}
}
