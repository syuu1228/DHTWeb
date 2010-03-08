package org.slf4j.impl;

import java.awt.Font;
import java.awt.Rectangle;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class LogWindow {

    private static final int MAX_LINE = 1024;
    private static LogWindow instance = new LogWindow();
    private JFrame frame;
    private JTextArea textArea;
    private JScrollPane scrollPane;
    private JViewport viewpoint;
    private int lastScrollHeight = -1;
    private int lastViewHeight = -1;
    private boolean changedFlg = false;
    private StringBuffer buffer = new StringBuffer();

    private LogWindow() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        frame = new JFrame("Log window");
        frame.setBounds(16, 16, 640, 480);
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);

        textArea = new JTextArea();
        textArea.setFont(font);
        textArea.setEditable(false);

        scrollPane = new JScrollPane(textArea);
        viewpoint = scrollPane.getViewport();

        {
            viewpoint.addChangeListener(new ChangeListener() {

                @Override
                public void stateChanged(ChangeEvent arg0) {
                    Rectangle rect = viewpoint.getViewRect();

                    if (lastScrollHeight == textArea.getHeight()
                            && lastViewHeight == rect.height
                            && changedFlg == false) {
                        return;
                    }

                    if (lastScrollHeight - lastViewHeight == rect.y) {
                        rect.setLocation(rect.x, textArea.getHeight()
                                - rect.height);
                        textArea.scrollRectToVisible(rect);
                    }

                    lastScrollHeight = textArea.getHeight();
                    lastViewHeight = rect.height;
                    changedFlg = false;
                }
            });
        }

        frame.add(scrollPane);
        new LogAppender().start();
    }

    public static LogWindow getInstance() {
        return instance;
    }

    public void show() {
        frame.setVisible(true);
        frame.setState(JFrame.NORMAL);
    }

    public void close() {
        instance = null;

        frame.dispose();
        frame = null;
    }

    public Rectangle getBounds() {
        return frame.getBounds();
    }

    public void setBounds(Rectangle r) {
        frame.setBounds(r);
    }

    public void append(String str) {
        synchronized (buffer) {
            buffer.append(str);
            buffer.notify();
        }
    }

    public void append(byte[] b) {
        synchronized (buffer) {
            buffer.append(b);
            buffer.notify();
        }
    }

    public void append(byte[] b, int off, int len) {
        append(new String(b, off, len));
    }

    public void append(int b) {
        synchronized (buffer) {
            buffer.append(b);
            buffer.notify();
        }

    }

    private class LogAppender extends Thread {

        public void run() {
            while (true) {
                synchronized (buffer) {
                    try {
                        buffer.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    textArea.append(buffer.toString());
                    buffer.delete(0, buffer.length());
                }
                if (textArea.getLineCount() > MAX_LINE) {
                    try {
                        int offset = textArea.getLineEndOffset(textArea.getLineCount()
                                - MAX_LINE - 1);
                        textArea.getDocument().remove(0, offset);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                changedFlg = true;
            }
        }
    }
}
