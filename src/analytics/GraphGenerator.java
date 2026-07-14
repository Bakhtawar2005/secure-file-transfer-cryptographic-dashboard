package analytics;

import javax.swing.*;
import java.awt.*;

/**
 * GraphGenerator is a custom Swing panel that dynamically renders a vertical bar chart
 * comparing Sequential vs. Parallel execution times.
 * 
 * DESIGN RATIONALE FOR INTERVIEWS:
 * 1. Instead of raw text numbers, this provides instant visual data visualization.
 * 2. Painted entirely using Java's native Graphics2D (rendering quality details like anti-aliasing, grid lines, and custom color gradients).
 * 3. Amber representing Sequential (Warning/Standard) and Emerald representing Parallel (Success/Optimized) for standard visual patterns.
 */
public class GraphGenerator extends JPanel {

    private long sequentialTime = 0;
    private long parallelTime = 0;

    // Theme Colors
    private static final Color BG_COLOR = new Color(24, 24, 30);       // Slightly darker card bg
    private static final Color BORDER_COLOR = new Color(45, 45, 52);   // Card border
    private static final Color GRID_COLOR = new Color(45, 45, 52, 100); // Faded grid lines
    private static final Color TEXT_COLOR = new Color(200, 200, 210);  // Clean off-white text
    private static final Color MUTE_COLOR = new Color(120, 120, 130);  // Secondary text
    
    private static final Color SEQ_COLOR = new Color(245, 158, 11);    // Amber
    private static final Color PAR_COLOR = new Color(16, 185, 129);    // Emerald Green

    public GraphGenerator() {
        setOpaque(false);
        setPreferredSize(new Dimension(280, 140)); // Standard sidebar width fit
    }

    /**
     * Updates the benchmark times and triggers a repaint of the bar chart.
     * 
     * @param seq Time in ms for sequential execution
     * @param par Time in ms for parallel execution
     */
    public void setTimes(long seq, long par) {
        this.sequentialTime = seq;
        this.parallelTime = par;
        repaint();
    }

    /**
     * Resets the chart state.
     */
    public void clear() {
        this.sequentialTime = 0;
        this.parallelTime = 0;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // 1. Draw Card Background
        g2.setColor(BG_COLOR);
        g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
        g2.setColor(BORDER_COLOR);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

        // Define padding and margins
        int paddingLeft = 45;
        int paddingRight = 15;
        int paddingTop = 25;
        int paddingBottom = 25;

        int chartW = w - paddingLeft - paddingRight;
        int chartH = h - paddingTop - paddingBottom;

        // 2. Draw Y-Axis Grid Lines & Labels
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.setColor(MUTE_COLOR);
        
        long maxVal = Math.max(sequentialTime, parallelTime);
        if (maxVal == 0) {
            maxVal = 100; // Default scale when empty
        }

        // Draw 3 horizontal grid lines (0%, 50%, 100% of max value)
        for (int i = 0; i <= 2; i++) {
            int yLine = paddingTop + (chartH * i / 2);
            g2.setColor(GRID_COLOR);
            g2.drawLine(paddingLeft, yLine, w - paddingRight, yLine);
            
            // Draw scale values (e.g. "120 ms")
            long scaleVal = maxVal - (maxVal * i / 2);
            g2.setColor(MUTE_COLOR);
            g2.drawString(scaleVal + " ms", 8, yLine + 4);
        }

        // 3. Draw Columns/Bars
        int barW = Math.max(15, chartW / 4); // Adaptive bar width
        int gap = chartW / 3;

        // Draw Sequential Bar (Amber)
        int xSeq = paddingLeft + gap - (barW / 2);
        drawBar(g2, xSeq, sequentialTime, maxVal, barW, chartH, paddingTop, SEQ_COLOR, "Seq");

        // Draw Parallel Bar (Emerald)
        int xPar = paddingLeft + (gap * 2) - (barW / 2);
        drawBar(g2, xPar, parallelTime, maxVal, barW, chartH, paddingTop, PAR_COLOR, "Par");

        g2.dispose();
    }

    private void drawBar(Graphics2D g2, int x, long val, long maxVal, int barW, int chartH, int paddingTop, Color color, String label) {
        int barH = 0;
        if (maxVal > 0 && val > 0) {
            barH = (int) (chartH * val / maxVal);
        }
        int y = paddingTop + chartH - barH;

        if (val > 0) {
            // Draw filled bar with slight transparency
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
            g2.fillRoundRect(x, y, barW, barH, 4, 4);

            // Draw solid border and header caps
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(x, y, barW, barH, 4, 4);
            g2.fillRoundRect(x, y, barW, 4, 4, 4); // Solid top cap
            
            // Draw time value on top of bar
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.setColor(color);
            String valStr = val + " ms";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(valStr, x + (barW - fm.stringWidth(valStr)) / 2, y - 6);
        } else {
            // Draw empty placeholder dotted outline if time is 0
            g2.setColor(new Color(63, 63, 70));
            float[] dash = {3.0f, 3.0f};
            g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));
            g2.drawRoundRect(x, paddingTop + chartH - 10, barW, 10, 4, 4);
            
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g2.setColor(MUTE_COLOR);
            g2.drawString("--", x + (barW - g2.getFontMetrics().stringWidth("--")) / 2, paddingTop + chartH - 15);
        }

        // Draw x-axis label (e.g., "Seq", "Par")
        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g2.setColor(TEXT_COLOR);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, x + (barW - fm.stringWidth(label)) / 2, paddingTop + chartH + 16);
    }
}
