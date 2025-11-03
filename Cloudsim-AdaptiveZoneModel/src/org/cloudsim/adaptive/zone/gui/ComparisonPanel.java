package org.cloudsim.adaptive.zone.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Panel for comparing different VM allocation algorithms
 */
public class ComparisonPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private JTable comparisonTable;
    private DefaultTableModel tableModel;

    public ComparisonPanel() {
        initializeComponents();
    }

    /**
     * Initialize comparison components
     */
    private void initializeComponents() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Title
        JLabel titleLabel = new JLabel("Algorithm Performance Comparison", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        add(titleLabel, BorderLayout.NORTH);

        // Create comparison table
        createComparisonTable();

        // Add table to panel
        JScrollPane scrollPane = new JScrollPane(comparisonTable);
        add(scrollPane, BorderLayout.CENTER);

        // Add performance charts placeholder
        JPanel chartsPanel = createChartsPanel();
        add(chartsPanel, BorderLayout.SOUTH);
    }

    /**
     * Create comparison table
     */
    private void createComparisonTable() {
        String[] columnNames = {
            "Algorithm", 
            "Total VMs", 
            "Avg Response Time (ms)", 
            "Resource Utilization (%)", 
            "Migration Count",
            "Energy Efficiency"
        };

        Object[][] data = {
            {"Adaptive Zone Model", "40", "125.5", "87.2", "8", "High"},
            {"Best Fit", "40", "156.3", "79.4", "12", "Medium"},
            {"First Fit", "40", "178.9", "72.8", "5", "Low"},
            {"Round Robin", "40", "165.7", "75.1", "15", "Medium"}
        };

        tableModel = new DefaultTableModel(data, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table read-only
            }
        };

        comparisonTable = new JTable(tableModel);
        comparisonTable.setFont(new Font("Arial", Font.PLAIN, 12));
        comparisonTable.setRowHeight(25);

        // Color code the best performing algorithm
        comparisonTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, 
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (row == 0) { // Adaptive Zone Model row
                    c.setBackground(new Color(200, 255, 200)); // Light green
                } else {
                    c.setBackground(Color.WHITE);
                }

                return c;
            }
        });
    }

    /**
     * Create charts panel (placeholder for performance charts)
     */
    private JPanel createChartsPanel() {
        JPanel chartsPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        chartsPanel.setBorder(BorderFactory.createTitledBorder("Performance Charts"));
        chartsPanel.setPreferredSize(new Dimension(0, 200));

        // Response Time Chart
        JPanel responseChart = createChartPlaceholder("Response Time Comparison");
        chartsPanel.add(responseChart);

        // Resource Utilization Chart  
        JPanel utilizationChart = createChartPlaceholder("Resource Utilization");
        chartsPanel.add(utilizationChart);

        return chartsPanel;
    }

    /**
     * Create placeholder for charts
     */
    private JPanel createChartPlaceholder(String title) {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw simple bar chart placeholder
                drawSimpleBarChart(g2d, title);
            }
        };
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    /**
     * Draw simple bar chart
     */
    private void drawSimpleBarChart(Graphics2D g2d, String title) {
        int width = g2d.getClipBounds().width;
        int height = g2d.getClipBounds().height;

        if (width <= 0 || height <= 0) return;

        // Sample data for visualization
        String[] algorithms = {"Adaptive", "Best Fit", "First Fit", "Round Robin"};
        int[] values = {87, 79, 73, 75}; // Sample performance values
        Color[] colors = {Color.GREEN, Color.BLUE, Color.ORANGE, Color.MAGENTA};

        int barWidth = width / (algorithms.length + 1);
        int maxHeight = height - 40;

        for (int i = 0; i < algorithms.length; i++) {
            int barHeight = (values[i] * maxHeight) / 100;
            int x = (i + 1) * barWidth - barWidth/2;
            int y = height - barHeight - 20;

            // Draw bar
            g2d.setColor(colors[i]);
            g2d.fillRect(x, y, barWidth/2, barHeight);

            // Draw value label
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.PLAIN, 10));
            String valueStr = String.valueOf(values[i]);
            FontMetrics fm = g2d.getFontMetrics();
            int labelWidth = fm.stringWidth(valueStr);
            g2d.drawString(valueStr, x + barWidth/4 - labelWidth/2, y - 5);

            // Draw algorithm name
            g2d.drawString(algorithms[i], x, height - 5);
        }
    }

    /**
     * Update comparison data
     */
    public void updateComparison() {
        // In a real implementation, this would update with actual simulation data
        repaint();
    }

    /**
     * Reset comparison panel
     */
    public void reset() {
        // Reset to initial state
        repaint();
    }

    /**
     * Update the comparison table with real simulation data
     */
    public void setComparisonData(java.util.List<Object[]> rows) {
        tableModel.setRowCount(0); // Clear existing data
        for (Object[] row : rows) {
            tableModel.addRow(row);
        }
        repaint();
    }
}