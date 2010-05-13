/*
 * Copyright 2010 syuu, and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dhtfox;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Dialog;
import java.awt.Label;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;

public class ErrorDialog extends Dialog {

    /**
	 * 
	 */
	private static final long serialVersionUID = -6093896842533633107L;
	Label label = new Label();
    Button button = new Button();
    Panel panel = new Panel();

    public ErrorDialog(String title, String message) {
        super((Dialog) null, title, true);
        this.setResizable(false);
        this.enableEvents(AWTEvent.WINDOW_EVENT_MASK);
        label.setText(message);
        button.setLabel("OK");
        button.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent event) {
                close();
            }
        });
        panel.add(button);
        add(label);
        add(panel, BorderLayout.SOUTH);
        pack();
        setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        setVisible(true);
    }

    protected void close() {
        dispose();
    }

    @Override
    protected void processWindowEvent(WindowEvent event) {
        if (event.getID() == WindowEvent.WINDOW_CLOSING) {
            close();
        }
    }
}
