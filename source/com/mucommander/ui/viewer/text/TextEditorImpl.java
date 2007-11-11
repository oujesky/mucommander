/*
 * This file is part of muCommander, http://www.mucommander.com
 * Copyright (C) 2002-2007 Maxence Bernard
 *
 * muCommander is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * muCommander is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mucommander.ui.viewer.text;

import com.mucommander.file.AbstractFile;
import com.mucommander.io.EncodingDetector;
import com.mucommander.text.Translator;
import com.mucommander.ui.helper.MenuToolkit;
import com.mucommander.ui.helper.MnemonicHelper;
import com.mucommander.ui.theme.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Text editor implementation used by {@link TextViewer} and {@link TextEditor}.
 *
 * @author Maxence Bernard, Mariusz Jakubowski
 */
class TextEditorImpl implements ThemeListener, ActionListener {

    private boolean isEditable;

    private JTextArea textArea;

    private JMenuItem copyItem;
    private JMenuItem cutItem;
    private JMenuItem pasteItem;
    private JMenuItem selectAllItem;
    private JMenuItem findItem;
    private JMenuItem findNextItem;
    private JMenuItem findPreviousItem;

    private String searchString;


    public TextEditorImpl(boolean isEditable) {
        this.isEditable = isEditable;

        // Init text area
        initTextArea();
    }

    private void initTextArea() {
        textArea = new JTextArea();
        textArea.setEditable(isEditable);

        // Use theme colors and font
        textArea.setForeground(ThemeManager.getCurrentColor(Theme.EDITOR_FOREGROUND_COLOR));
        textArea.setCaretColor(ThemeManager.getCurrentColor(Theme.EDITOR_FOREGROUND_COLOR));
        textArea.setBackground(ThemeManager.getCurrentColor(Theme.EDITOR_BACKGROUND_COLOR));
        textArea.setSelectedTextColor(ThemeManager.getCurrentColor(Theme.EDITOR_SELECTED_FOREGROUND_COLOR));
        textArea.setSelectionColor(ThemeManager.getCurrentColor(Theme.EDITOR_SELECTED_BACKGROUND_COLOR));
        textArea.setFont(ThemeManager.getCurrentFont(Theme.EDITOR_FONT));
    }

    private void find() {
		searchString = ((String) JOptionPane.showInputDialog(null,
				Translator.get("text_viewer.find") + ":",
				Translator.get("text_viewer.find"),
				JOptionPane.PLAIN_MESSAGE, null, null, searchString)).toLowerCase();
		doSearch(0, true);
	}

    private void findNext() {
    	doSearch(textArea.getSelectionEnd(), true);
    }
    
    private void findPrevious() {
    	doSearch(textArea.getSelectionStart() - 1, false);
	}

    private String getTextLC() {
    	return textArea.getText().toLowerCase();
    }

    private void doSearch(int startPos, boolean forward) {
    	if (searchString == null || searchString.length() == 0)
    		return;
		int pos;
		if (forward) {
			pos = getTextLC().indexOf(searchString, startPos);
		} else {
			pos = getTextLC().lastIndexOf(searchString, startPos);
		}
		if (pos >= 0) {
			textArea.select(pos, pos + searchString.length());
		} else {
            // Beep when no match has been found.
            // The beep method is called from a separate thread because this method seems to lock until the beep has
            // been played entirely. If the 'Find next' shortcut is left pressed, a series of beeps will be played when
            // the end of the file is reached, and we don't want those beeps to played one after the other as to:
            // 1/ not lock the event thread
            // 2/ have those beeps to end rather sooner than later
            new Thread() {
                public void run() {
                    Toolkit.getDefaultToolkit().beep();
                }
            }.start();
        }
    }


    ////////////////////////////
    // Package-access methods //
    ////////////////////////////

    JTextArea getTextArea() {
        return textArea;
    }

    void addMenuItems(JMenu menu) {
        MnemonicHelper menuItemMnemonicHelper = new MnemonicHelper();

        // Edit menu
        copyItem = MenuToolkit.addMenuItem(menu, Translator.get("text_editor.copy"), menuItemMnemonicHelper, null, this);

        // These menu items are not available to text viewers
        if(isEditable) {
            cutItem = MenuToolkit.addMenuItem(menu, Translator.get("text_editor.cut"), menuItemMnemonicHelper, null, this);
            pasteItem = MenuToolkit.addMenuItem(menu, Translator.get("text_editor.paste"), menuItemMnemonicHelper, null, this);
        }

        selectAllItem = MenuToolkit.addMenuItem(menu, Translator.get("text_editor.select_all"), menuItemMnemonicHelper, null, this);
        menu.addSeparator();

        findItem = MenuToolkit.addMenuItem(menu, Translator.get("text_viewer.find"), menuItemMnemonicHelper, KeyStroke.getKeyStroke("control F"), this);
        findNextItem = MenuToolkit.addMenuItem(menu, Translator.get("text_viewer.find_next"), menuItemMnemonicHelper, KeyStroke.getKeyStroke("F3"), this);
        findPreviousItem = MenuToolkit.addMenuItem(menu, Translator.get("text_viewer.find_previous"), menuItemMnemonicHelper, KeyStroke.getKeyStroke("shift F3"), this);
    }


    void startEditing(AbstractFile file) throws IOException {
        // Auto-detect encoding
        InputStream in = file.getInputStream();
        String encoding = EncodingDetector.detectEncoding(in);
        in.close();

        // If encoding could not be detected, default to UTF-8
        if(encoding==null)
            encoding = "UTF-8";

        // Feed the file's contents to text area
        InputStreamReader isr = new InputStreamReader(file.getInputStream(), encoding);
        textArea.read(isr, null);
        isr.close();

        // Move cursor to the top
        textArea.setCaretPosition(0);

        // Listen to theme changes to update the text area if it is visible
        ThemeManager.addCurrentThemeListener(this);
    }


    ///////////////////////////////////
    // ActionListener implementation //
    ///////////////////////////////////

    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();

        // Edit menu
        if(source == copyItem)
            textArea.copy();
        else if(source == cutItem)
            textArea.cut();
        else if(source == pasteItem)
            textArea.paste();
        else if(source == selectAllItem)
            textArea.selectAll();
        else if(source == findItem)
        	find();
        else if(source == findNextItem)
        	findNext();
        else if(source == findPreviousItem)
        	findPrevious();
    }


    //////////////////////////////////
    // ThemeListener implementation //
    //////////////////////////////////

    /**
     * Receives theme color changes notifications.
     */
    public void colorChanged(ColorChangedEvent event) {
        switch(event.getColorId()) {
        case Theme.EDITOR_FOREGROUND_COLOR:
            textArea.setForeground(event.getColor());
            break;

        case Theme.EDITOR_BACKGROUND_COLOR:
            textArea.setBackground(event.getColor());
            break;

        case Theme.EDITOR_SELECTED_FOREGROUND_COLOR:
            textArea.setSelectedTextColor(event.getColor());
            break;

        case Theme.EDITOR_SELECTED_BACKGROUND_COLOR:
            textArea.setSelectionColor(event.getColor());
            break;
        }
    }

    /**
     * Receives theme font changes notifications.
     */
    public void fontChanged(FontChangedEvent event) {
        if(event.getFontId() == Theme.EDITOR_FONT)
            textArea.setFont(event.getFont());
    }
}