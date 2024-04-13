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

import com.pokescape.PokescapeConfig;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Desktop;
import java.awt.FontMetrics;
import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PokescapePanel extends PluginPanel {
    private static final ImageIcon INFO_ICON;
    private static final ImageIcon SITE_ICON;
    private static final ImageIcon DISCORD_ICON;
    private static final EmptyBorder DEFAULT_BORDER = new EmptyBorder(10, 10, 10, 10);

    private static final String WEBSITE_URL = "https://pokescape.com/";
    private static final String DISCORD_URL = "https://discord.com/invite/dmfF6yMV9m";

    static {
        INFO_ICON = com.pokescape.ui.Icon.INFO.getIcon(img -> ImageUtil.resizeImage(img, 16, 16));
        SITE_ICON = com.pokescape.ui.Icon.SITE.getIcon(img -> ImageUtil.resizeImage(img, 16, 16));
        DISCORD_ICON = com.pokescape.ui.Icon.DISCORD.getIcon(img -> ImageUtil.resizeImage(img, 16, 16));
    }

    private JPanel serverInfoPanel;
    private JLabel versionLabel;
    private JLabel serverStatusLabel;
    private JTextArea serverMessage;
    private JLabel pokescapeTeamLabel;
    private JLabel totalLevelLabel;
    private JLabel dexCountLabel;
    private JLabel tempoVerificationLabel;
    private JLabel gotrVerificationLabel;
    public boolean tempoVerified;
    public boolean gotrVerified;

    @Inject
    public PokescapePanel() {
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());
        setBorder(DEFAULT_BORDER);

        JPanel layoutPanel = new JPanel();
        layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));

        JPanel tabPanel = createTabPanel();
        layoutPanel.add(tabPanel);

        add(layoutPanel, BorderLayout.NORTH);
    }

    private JPanel createTabPanel() {
        JPanel tabPanel = new JPanel();
        tabPanel.setLayout(new BoxLayout(tabPanel, BoxLayout.Y_AXIS));

        JPanel activeTabPanel = new JPanel();
        activeTabPanel.setLayout(new BoxLayout(activeTabPanel, BoxLayout.Y_AXIS));
        MaterialTabGroup tabGroup = new MaterialTabGroup(activeTabPanel);
        tabGroup.setLayout(new GridLayout(1, 4, 10, 10));
        tabGroup.setBorder(new EmptyBorder(0, 0, 10, 0));
        tabPanel.add(tabGroup);
        tabPanel.add(activeTabPanel);

        JPanel masterPanel = createMasterPanel();
        activeTabPanel.add(masterPanel);
        createTab(INFO_ICON, "Info", tabGroup, masterPanel, "tab", null).select();

        JPanel dexPanel = createDexBtnPanel();
        createTab(SITE_ICON, "RuneDex", tabGroup, dexPanel, "button", WEBSITE_URL);

        JPanel discordPanel = createDiscordBtnPanel();
        createTab(DISCORD_ICON, "Discord", tabGroup, discordPanel, "button", DISCORD_URL);

        return tabPanel;
    }

    private JPanel createMasterPanel() {
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));

        serverInfoPanel = new JPanel(new GridBagLayout());
        serverInfoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        serverInfoPanel.setBorder(DEFAULT_BORDER);

        // Set the server annoucement
        setServerAnnoucement(0, "");
        infoPanel.add(serverInfoPanel);

        // Create the team panel
        JPanel teamPanelContent = createTabModal();
        pokescapeTeamLabel = new JLabel();
        setPokescapeTeam("","");
        teamPanelContent.add(pokescapeTeamLabel, createGbc(0, 0, 50, 0));
        totalLevelLabel = new JLabel();
        setTotalLevel("-");
        teamPanelContent.add(totalLevelLabel, createGbc(3, 0, 0, 0));
        dexCountLabel = new JLabel();
        setDexCount("-/-");
        teamPanelContent.add(dexCountLabel, createGbc(48, 0, 0, 0));
        collapseModal teamPanel = new collapseModal("PokeScape Team", teamPanelContent);
        infoPanel.add(teamPanel);

        // Create the verification info text
        JPanel verificationContent = createTabModal();
        String verificationText = "Some activities require additional verification checks after the start of the competition. Drops from the activities below are invalid until you validate the activity:";
        JTextArea verificationTextArea = createTextArea(verificationText);
        verificationContent.add(verificationTextArea, createGbc(0, -6, 105, -6));

        // Verification indicators: Tempoross
        JLabel tempoLabel = new JLabel("Tempoross");
        tempoLabel.setBorder(new EmptyBorder(0, 0, 0, 0));
        tempoLabel.setFont(FontManager.getRunescapeBoldFont());
        verificationContent.add(tempoLabel, createGbc(18, 0, 0, 0));
        tempoVerificationLabel = new JLabel();
        setTemporossVerification(false);
        if (!getTemporossVerification()) tempoVerificationLabel.setToolTipText("Use the \"Net\" option on the Reward Pool to verify your reward permits. \r\nYou must have 0 reward permits to pass verification.");
        verificationContent.add(tempoVerificationLabel, createGbc(60, 0, 0, 0));
        // Verification indicators: GoTR
        JLabel gotrLabel = new JLabel("Guardians of the Rift");
        gotrLabel.setBorder(new EmptyBorder(0, 0, 0, 0));
        gotrLabel.setFont(FontManager.getRunescapeBoldFont());
        verificationContent.add(gotrLabel, createGbc(113, 0, 0, 0));
        gotrVerificationLabel = new JLabel();
        setGotrVerification(false);
        if (!getGotrVerification()) gotrVerificationLabel.setToolTipText("Use the \"Check\" option on the Rewards Guardian to verify your energy. \r\nYou must have 0 catalytic and 0 elemental energy to pass verification.");
        verificationContent.add(gotrVerificationLabel, createGbc(155, 0, 0, 0));
        collapseModal verificationPanel = new collapseModal("Minigame Verification", verificationContent);
        infoPanel.add(verificationPanel);

        return infoPanel;
    }

    private MaterialTab createTab(ImageIcon icon, String toolTipText, MaterialTabGroup tabGroup, JComponent content, String modal, String url) {
        MaterialTab tab = new MaterialTab(icon, tabGroup, content);
        switch (modal) {
            // Add a tab
            case "tab":
                tab.setToolTipText(toolTipText);
                tabGroup.addTab(tab);
                break;
            // Add a button
            case "button":
                JButton button = new JButton();
                button.setToolTipText(toolTipText);
                button.setIcon(icon);
                button.setBorderPainted(false);
                button.setFocusPainted(false);
                button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                button.addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) {
                        button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
                    }
                    public void mouseExited(MouseEvent e) {
                        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                    }
                });
                button.addActionListener(e -> {
                    try { Desktop.getDesktop().browse(java.net.URI.create(url)); }
                    catch (java.io.IOException i) { log.debug(i.getMessage()); }
                });
                tabGroup.add(button);
                break;
            default:
        }
        return tab;
    }

    public void setServerAnnoucement(int status, String message) {
        if (serverMessage != null) serverInfoPanel.remove(serverMessage);
        if (versionLabel != null) serverInfoPanel.remove(versionLabel);
        if (serverStatusLabel != null) serverInfoPanel.remove(serverStatusLabel);

        int lineOffset = 16;
        serverMessage = createTextArea(message);
        serverMessage.setSize(200,200);
        lineOffset = lineOffset*countLines(serverMessage);
        if (!message.isEmpty()) serverInfoPanel.add(serverMessage, createGbc(35, -6, -5, -6));
        else { serverInfoPanel.add(serverMessage, createGbc(0, 0, 0, 0)); lineOffset = -3; }

        versionLabel = new JLabel("Version: "+ PokescapeConfig.PLUGIN_VERSION);
        serverInfoPanel.add(versionLabel, createGbc(-20-lineOffset, 0, 0, 0));

        serverStatusLabel = new JLabel();
        setServerStatusText(status);
        serverInfoPanel.add(serverStatusLabel, createGbc(12-lineOffset, 0, 0, 0));
    }

    private int countLines(JTextArea textArea) {
        int width = (int)textArea.getSize().getWidth();
        FontMetrics fontMetrics = textArea.getFontMetrics(textArea.getFont());
        String text = textArea.getText();
        String currentLine = "";
        int numOfLines = 1;
        int lastWhiteSpaceIndex = 0;

        for (int i = 0; i < text.length(); i++) {
            currentLine = currentLine + text.charAt(i);
            if (text.charAt(i) == ' ') lastWhiteSpaceIndex = i;
            if (fontMetrics.stringWidth(currentLine) > width) {
                i = lastWhiteSpaceIndex;
                currentLine = "";
                numOfLines++;
            }
        }
        return numOfLines;
    }

    private JPanel createDexBtnPanel() {
        JPanel dexBtnPanel = new JPanel();
        dexBtnPanel.setLayout(new BoxLayout(dexBtnPanel, BoxLayout.Y_AXIS));
        return dexBtnPanel;
    }

    private JPanel createDiscordBtnPanel() {
        JPanel discordBtnPanel = new JPanel();
        discordBtnPanel.setLayout(new BoxLayout(discordBtnPanel, BoxLayout.Y_AXIS));
        return discordBtnPanel;
    }

    private GridBagConstraints createGbc(int top, int left, int bottom, int right) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.gridheight = 1;

        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.insets = new Insets(top, left, bottom, right);
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        return gbc;
    }

    private JPanel createTabModal() {
        JPanel tabContentPanel = new JPanel(new GridBagLayout());
        tabContentPanel.setBorder(DEFAULT_BORDER);
        tabContentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        return tabContentPanel;
    }

    private JTextArea createTextArea(String text) {
        JTextArea textArea = new JTextArea(0, 0);
        textArea.setText(text);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setOpaque(false);
        return textArea;
    }

    public void setServerStatusText(int status) {
        String statusColor = "#FF0000";
        String statusText = "Unreachable";
        switch (status) {
            // Unreachable
            case 0:
                statusColor = "#FF0000";
                statusText = "Unreachable";
                break;
            // Online
            case 1:
                statusColor = "#00FF00";
                statusText = "Online";
                break;
            // Offline
            case 2:
                statusColor = "#FF0000";
                statusText = "Offline";
                break;
            // Closed
            case 3:
                statusColor = "#FFAA00";
                statusText = "Closed";
                break;
        }
        serverStatusLabel.setText("<html><nobr>Server Status: <font color='"+statusColor+"'>"+statusText+"</font></nobr></html>");
    }

    public void setPokescapeTeam(String team, String color) {
        String teamName = team.isEmpty() ? "" : team;
        String hyphen = team.isEmpty() ? " -" : " ";
        String teamColor = color.isEmpty() ? "#1E1E1E" : color;
        pokescapeTeamLabel.setText("<html><nobr><font style='font-weight: bold;'>Team:</font>"+hyphen+"<font style='font-size: 1.3em;' color='"+teamColor+"'>⬛</font> <font>"+teamName+"</font></nobr></html>");
    }

    public void setTotalLevel(String totalLevel) {
        totalLevelLabel.setText("<html><nobr><font style='font-weight: bold;'>Total Level:</font> <font>"+totalLevel+"</font></nobr></html>");
    }

    public void setDexCount(String dexCount) {
        dexCountLabel.setText("<html><nobr><font style='font-weight: bold;'>Dex Count:</font> <font>"+dexCount+"</font></nobr></html>");
    }

    public void setTemporossVerification(boolean verified) {
        tempoVerified = verified;
        String statusColor = verified ? "#00FF00" : "#FF0000";
        String isVerified = verified ? "Verified" : "Unverified";
        tempoVerificationLabel.setText("<html><nobr> <font style='font-size: 1.3em;' color='"+statusColor+"'>⬛</font> <font>"+isVerified+"</font></nobr></html>");
    }
    public boolean getTemporossVerification() { return tempoVerified; }

    public void setGotrVerification(boolean verified) {
        gotrVerified = verified;
        String statusColor = verified ? "#00FF00" : "#FF0000";
        String isVerified = verified ? "Verified" : "Unverified";
        gotrVerificationLabel.setText("<html><nobr> <font style='font-size: 1.3em;' color='"+statusColor+"'>⬛</font> <font>"+isVerified+"</font></nobr></html>");
    }
    public boolean getGotrVerification() { return gotrVerified; }
}
