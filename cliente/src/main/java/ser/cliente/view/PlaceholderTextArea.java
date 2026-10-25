package ser.cliente.view;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import javax.swing.JTextArea;

public final class PlaceholderTextArea extends JTextArea {
    private String placeholder;

    public PlaceholderTextArea(String placeholder) {
        this.placeholder = placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (placeholder == null || placeholder.isBlank() || !getText().isEmpty() || isFocusOwner()) {
            return;
        }

        Graphics2D g2d = (Graphics2D) graphics.create();
        g2d.setColor(new Color(140, 140, 140));
        Insets insets = getInsets();
        int textY = insets.top + graphics.getFontMetrics().getAscent() + 2;
        g2d.drawString(placeholder, insets.left + 5, textY);
        g2d.dispose();
    }
}