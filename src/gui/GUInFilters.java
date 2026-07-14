package gui;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DnDConstants;
import java.awt.datatransfer.DataFlavor;
import javax.imageio.ImageIO;
import javax.crypto.SecretKey;
import security.KeyGeneratorUtil;
import security.AESUtil;

public class GUInFilters extends javax.swing.JFrame {
    private BufferedImage originalImage;
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(GUInFilters.class.getName());
    private ServerSocket serverSocket;
    private boolean isServerRunning = false;

    public GUInFilters() {
        initComponents();
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {
        // Unused variables kept for backward compatibility
        jTable1 = new javax.swing.JTable();
        jScrollPane1 = new javax.swing.JScrollPane(jTable1);
        jTextField1 = new javax.swing.JTextField("Parallel Image Processing System");

        // Styling Colors Palette
        Color bgTheme = new Color(18, 18, 22);
        Color cardBg = new Color(28, 28, 35);
        Color borderCol = new Color(45, 45, 52);
        Color accentColor = new Color(99, 102, 241);      // Indigo Accent
        Color accentHover = new Color(79, 70, 229);
        Color accentPressed = new Color(67, 56, 202);
        
        Color seqColor = new Color(245, 158, 11);         // Amber/Orange
        Color seqHover = new Color(217, 119, 6);
        Color seqPressed = new Color(180, 83, 9);
        
        Color parColor = new Color(16, 185, 129);         // Emerald Green
        Color parHover = new Color(5, 150, 105);
        Color parPressed = new Color(4, 120, 87);
        
        Color neutralColor = new Color(63, 63, 70);       // Grey
        Color neutralHover = new Color(82, 82, 91);
        Color neutralPressed = new Color(39, 39, 42);

        // Instantiate Custom-Styled Interactive Components
        jButton1 = new ModernButton("Upload Source Image", accentColor, accentHover, accentPressed);
        jButton2 = new ModernButton("Grayscale Seq", seqColor, seqHover, seqPressed);
        jButton3 = new ModernButton("Grayscale Parallel", parColor, parHover, parPressed);
        
        jButtonSobelSeq = new ModernButton("Sobel Seq", seqColor, seqHover, seqPressed);
        jButtonSobelParallel = new ModernButton("Sobel Parallel", parColor, parHover, parPressed);
        
        jButtonBlurSeq = new ModernButton("Blur Seq", seqColor, seqHover, seqPressed);
        jButtonBlurParallel = new ModernButton("Blur Parallel", parColor, parHover, parPressed);
        
        jButton4 = new ModernButton("Brightness (+40)", neutralColor, neutralHover, neutralPressed);
        jButton5 = new ModernButton("Negative Filter", neutralColor, neutralHover, neutralPressed);
        
        jButtonStartServer = new ModernButton("Start Server Node", parColor, parHover, parPressed);
        jButtonTransmit = new ModernButton("Transmit Payload", accentColor, accentHover, accentPressed);

        jTextFieldIP = new JTextField("127.0.0.1");
        jTextFieldIP.setBackground(cardBg);
        jTextFieldIP.setForeground(Color.WHITE);
        jTextFieldIP.setCaretColor(Color.WHITE);
        jTextFieldIP.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        jTextFieldIP.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderCol, 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        jTextFieldPort = new JTextField("8080");
        jTextFieldPort.setBackground(cardBg);
        jTextFieldPort.setForeground(Color.WHITE);
        jTextFieldPort.setCaretColor(Color.WHITE);
        jTextFieldPort.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        jTextFieldPort.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderCol, 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        jLabelMetadata = new JLabel("Dimensions: -- | Size: -- | Quality: --");
        jLabelMetadata.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        jLabelMetadata.setForeground(new Color(180, 180, 190));
        jLabelMetadata.setHorizontalAlignment(SwingConstants.CENTER);

        console = new RetroConsole();
        histogramPanel1 = new HistogramPanel();

        jComboBox1 = new JComboBox<>(new String[] { "2", "4", "8" });
        jComboBox1.setBackground(cardBg);
        jComboBox1.setForeground(Color.WHITE);
        jComboBox1.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        jComboBox1.setBorder(BorderFactory.createLineBorder(borderCol, 1));
        
        jComboBox1.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setBackground(isSelected ? accentColor : cardBg);
                label.setForeground(Color.WHITE);
                label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                return label;
            }
        });

        jLabel1 = new ImageLabel("Drag image or click 'Upload Source Image' to begin");
        jLabel2 = new ImageLabel("Processed output will appear here");

        // Forward mouse clicks on image label to click the upload button
        jLabel1.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        jLabel1.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                jButton1ActionPerformed(null);
            }
        });

        // Set Drag-and-Drop Drop Target for source panel
        jLabel1.setDropTarget(new DropTarget() {
            @Override
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    java.util.List<File> droppedFiles = (java.util.List<File>)
                        evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (droppedFiles != null && !droppedFiles.isEmpty()) {
                        File file = droppedFiles.get(0);
                        originalImage = ImageIO.read(file);
                        jLabel1.setIcon(new ImageIcon(originalImage));
                        logMessage("SYSTEM", "Loaded dropped image: " + file.getName());
                        if (graphGenerator1 != null) {
                            graphGenerator1.clear();
                        }
                        if (histogramPanel1 != null) {
                            histogramPanel1.updateHistogram(originalImage);
                        }
                        updateMetadata(file);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    logMessage("SYSTEM ERROR", "Failed to drop file: " + ex.getMessage());
                }
            }
        });

        JLabel seqValLabel = new JLabel("--");
        seqValLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        seqValLabel.setForeground(Color.WHITE);
        
        JLabel parValLabel = new JLabel("--");
        parValLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        parValLabel.setForeground(Color.WHITE);
        
        JLabel speedupValLabel = new JLabel("--");
        speedupValLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        speedupValLabel.setForeground(new Color(150, 150, 160));

        // INITIALIZE THE GRAPH GENERATOR HERE
        graphGenerator1 = new GraphGenerator();

        jLabel3 = new PerformanceLabel(seqValLabel, parValLabel, speedupValLabel, graphGenerator1);
        jLabel3.setText("Time");

        // Cryptography UI elements
        jPasswordField1 = new JPasswordField();
        jPasswordField1.setBackground(cardBg);
        jPasswordField1.setForeground(Color.WHITE);
        jPasswordField1.setCaretColor(Color.WHITE);
        jPasswordField1.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        jPasswordField1.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderCol, 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        jButtonEncrypt = new ModernButton("AES Encrypt", new Color(139, 92, 246), new Color(124, 58, 237), new Color(109, 40, 217));
        jButtonDecrypt = new ModernButton("AES Decrypt", neutralColor, neutralHover, neutralPressed);

        // Action Listeners
        jButton1.addActionListener(this::jButton1ActionPerformed);
        jButton2.addActionListener(this::jButton2ActionPerformed);
        jButton3.addActionListener(this::jButton3ActionPerformed);
        
        jButtonSobelSeq.addActionListener(e -> runSobel(false));
        jButtonSobelParallel.addActionListener(e -> runSobel(true));
        jButtonBlurSeq.addActionListener(e -> runBlur(false));
        jButtonBlurParallel.addActionListener(e -> runBlur(true));
        
        jButton4.addActionListener(this::jButton4ActionPerformed);
        jButton5.addActionListener(this::jButton5ActionPerformed);
        jComboBox1.addActionListener(this::jComboBox1ActionPerformed);
        jButtonEncrypt.addActionListener(this::jButtonEncryptActionPerformed);
        jButtonDecrypt.addActionListener(this::jButtonDecryptActionPerformed);

        jButtonStartServer.addActionListener(e -> {
            if (isServerRunning) {
                stopServer();
            } else {
                startServer();
            }
        });
        jButtonTransmit.addActionListener(e -> transmitPayload());

        // Window Configuration
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Parallel & Distributed Image Cryptography Dashboard");
        setPreferredSize(new Dimension(1220, 860));
        getContentPane().setBackground(bgTheme);
        
        JPanel rootPanel = new JPanel(new BorderLayout(15, 15));
        rootPanel.setBackground(bgTheme);
        rootPanel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        
        JLabel titleLabel = new JLabel("Parallel & Distributed Image Cryptography Dashboard");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);
        
        JLabel subLabel = new JLabel("Real-time Multithreading Metrics & Client-Server Socket Cryptography Channel");
        subLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subLabel.setForeground(new Color(150, 150, 160));
        
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(subLabel, BorderLayout.SOUTH);
        rootPanel.add(headerPanel, BorderLayout.NORTH);

        // Sidebar Assembly wrapped in JScrollPane to prevent vertical clipping/overflow
        JPanel sidebarContent = new JPanel();
        sidebarContent.setOpaque(false);
        sidebarContent.setLayout(new GridBagLayout());
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 10, 0);
        gbc.weightx = 1.0;
        gbc.gridx = 0;

        // 1. Source Image upload card
        CardPanel sourceCard = new CardPanel(10, cardBg, borderCol);
        sourceCard.setLayout(new BorderLayout(10, 10));
        JLabel sourceTitle = new JLabel("1. SOURCE IMAGE");
        sourceTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        sourceTitle.setForeground(accentColor);
        sourceCard.add(sourceTitle, BorderLayout.NORTH);
        jButton1.setPreferredSize(new Dimension(0, 36));
        sourceCard.add(jButton1, BorderLayout.CENTER);
        gbc.gridy = 0;
        sidebarContent.add(sourceCard, gbc);

        // 2. Parallel Workers Configuration
        CardPanel configCard = new CardPanel(10, cardBg, borderCol);
        configCard.setLayout(new GridBagLayout());
        GridBagConstraints cgbc = new GridBagConstraints();
        cgbc.fill = GridBagConstraints.HORIZONTAL;
        cgbc.weightx = 1.0;
        cgbc.gridx = 0;
        
        JLabel configTitle = new JLabel("2. CONFIGURATION");
        configTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        configTitle.setForeground(accentColor);
        cgbc.gridy = 0;
        cgbc.insets = new Insets(0, 0, 8, 0);
        configCard.add(configTitle, cgbc);
        
        JLabel threadLabel = new JLabel("Parallel Worker Threads:");
        threadLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        threadLabel.setForeground(new Color(200, 200, 210));
        cgbc.gridy = 1;
        cgbc.insets = new Insets(4, 0, 4, 0);
        configCard.add(threadLabel, cgbc);
        
        jComboBox1.setPreferredSize(new Dimension(0, 32));
        cgbc.gridy = 2;
        cgbc.insets = new Insets(4, 0, 4, 0);
        configCard.add(jComboBox1, cgbc);
        gbc.gridy = 1;
        sidebarContent.add(configCard, gbc);

        // 3. Execution Control Panel (All Filters grid)
        CardPanel filtersCard = new CardPanel(10, cardBg, borderCol);
        filtersCard.setLayout(new GridBagLayout());
        GridBagConstraints fgbc = new GridBagConstraints();
        fgbc.fill = GridBagConstraints.HORIZONTAL;
        fgbc.weightx = 1.0;
        fgbc.gridx = 0;
        
        JLabel filtersTitle = new JLabel("3. RUN FILTERS (SEQ & PARALLEL)");
        filtersTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        filtersTitle.setForeground(accentColor);
        fgbc.gridy = 0;
        fgbc.insets = new Insets(0, 0, 8, 0);
        filtersCard.add(filtersTitle, fgbc);
        
        JPanel filtersGrid = new JPanel(new GridLayout(4, 2, 8, 8));
        filtersGrid.setOpaque(false);
        jButton2.setPreferredSize(new Dimension(0, 32));
        jButton3.setPreferredSize(new Dimension(0, 32));
        jButtonSobelSeq.setPreferredSize(new Dimension(0, 32));
        jButtonSobelParallel.setPreferredSize(new Dimension(0, 32));
        jButtonBlurSeq.setPreferredSize(new Dimension(0, 32));
        jButtonBlurParallel.setPreferredSize(new Dimension(0, 32));
        jButton4.setPreferredSize(new Dimension(0, 32));
        jButton5.setPreferredSize(new Dimension(0, 32));
        
        filtersGrid.add(jButton2);             filtersGrid.add(jButton3);
        filtersGrid.add(jButtonSobelSeq);      filtersGrid.add(jButtonSobelParallel);
        filtersGrid.add(jButtonBlurSeq);       filtersGrid.add(jButtonBlurParallel);
        filtersGrid.add(jButton4);             filtersGrid.add(jButton5);
        
        fgbc.gridy = 1;
        fgbc.insets = new Insets(4, 0, 4, 0);
        filtersCard.add(filtersGrid, fgbc);
        gbc.gridy = 2;
        sidebarContent.add(filtersCard, gbc);

        // 4. Image Cryptography & Socket Panel
        CardPanel cryptoCard = new CardPanel(10, cardBg, borderCol);
        cryptoCard.setLayout(new GridBagLayout());
        GridBagConstraints crgbc = new GridBagConstraints();
        crgbc.fill = GridBagConstraints.HORIZONTAL;
        crgbc.weightx = 1.0;
        crgbc.gridx = 0;

        JLabel cryptoTitle = new JLabel("4. CRYPTO & DISTRIBUTED NODE");
        cryptoTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        cryptoTitle.setForeground(accentColor);
        crgbc.gridy = 0;
        crgbc.insets = new Insets(0, 0, 8, 0);
        cryptoCard.add(cryptoTitle, crgbc);

        JLabel passLabel = new JLabel("Decryption Passphrase:");
        passLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        passLabel.setForeground(new Color(200, 200, 210));
        crgbc.gridy = 1;
        crgbc.insets = new Insets(4, 0, 4, 0);
        cryptoCard.add(passLabel, crgbc);

        jPasswordField1.setPreferredSize(new Dimension(0, 32));
        crgbc.gridy = 2;
        crgbc.insets = new Insets(4, 0, 8, 0);
        cryptoCard.add(jPasswordField1, crgbc);

        JPanel cryptoButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        cryptoButtons.setOpaque(false);
        jButtonEncrypt.setPreferredSize(new Dimension(0, 32));
        jButtonDecrypt.setPreferredSize(new Dimension(0, 32));
        cryptoButtons.add(jButtonEncrypt);
        cryptoButtons.add(jButtonDecrypt);
        crgbc.gridy = 3;
        crgbc.insets = new Insets(4, 0, 4, 0);
        cryptoCard.add(cryptoButtons, crgbc);

        JSeparator cryptoSep = new JSeparator();
        cryptoSep.setForeground(new Color(63, 63, 70));
        cryptoSep.setBackground(new Color(0, 0, 0, 0));
        crgbc.gridy = 4;
        crgbc.insets = new Insets(8, 0, 8, 0);
        cryptoCard.add(cryptoSep, crgbc);

        JLabel netTitle = new JLabel("DISTRIBUTED SOCKET LINK (TCP)");
        netTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        netTitle.setForeground(accentColor);
        crgbc.gridy = 5;
        crgbc.insets = new Insets(0, 0, 6, 0);
        cryptoCard.add(netTitle, crgbc);

        JPanel netParamsPanel = new JPanel(new GridBagLayout());
        netParamsPanel.setOpaque(false);
        GridBagConstraints npgbc = new GridBagConstraints();
        npgbc.fill = GridBagConstraints.HORIZONTAL;
        npgbc.weightx = 0.6;
        npgbc.gridx = 0;
        npgbc.gridy = 0;
        npgbc.insets = new Insets(0, 0, 0, 4);
        
        JLabel ipLabel = new JLabel("Receiver IP:");
        ipLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        ipLabel.setForeground(new Color(200, 200, 210));
        JPanel ipCol = new JPanel(new BorderLayout(2, 2));
        ipCol.setOpaque(false);
        ipCol.add(ipLabel, BorderLayout.NORTH);
        ipCol.add(jTextFieldIP, BorderLayout.CENTER);
        netParamsPanel.add(ipCol, npgbc);
        
        npgbc.weightx = 0.4;
        npgbc.gridx = 1;
        npgbc.insets = new Insets(0, 4, 0, 0);
        JLabel portLabel = new JLabel("Port:");
        portLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        portLabel.setForeground(new Color(200, 200, 210));
        JPanel portCol = new JPanel(new BorderLayout(2, 2));
        portCol.setOpaque(false);
        portCol.add(portLabel, BorderLayout.NORTH);
        portCol.add(jTextFieldPort, BorderLayout.CENTER);
        netParamsPanel.add(portCol, npgbc);
        
        crgbc.gridy = 6;
        crgbc.insets = new Insets(4, 0, 8, 0);
        cryptoCard.add(netParamsPanel, crgbc);

        JPanel netButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        netButtons.setOpaque(false);
        jButtonStartServer.setPreferredSize(new Dimension(0, 32));
        jButtonTransmit.setPreferredSize(new Dimension(0, 32));
        netButtons.add(jButtonStartServer);
        netButtons.add(jButtonTransmit);
        
        crgbc.gridy = 7;
        crgbc.insets = new Insets(4, 0, 4, 0);
        cryptoCard.add(netButtons, crgbc);

        gbc.gridy = 3;
        sidebarContent.add(cryptoCard, gbc);

        // 5. Performance Metrics Dashboard
        CardPanel perfCard = new CardPanel(10, cardBg, borderCol);
        perfCard.setLayout(new GridBagLayout());
        GridBagConstraints pgbc = new GridBagConstraints();
        pgbc.fill = GridBagConstraints.HORIZONTAL;
        pgbc.gridx = 0;
        pgbc.weightx = 1.0;
        
        JLabel perfTitle = new JLabel("5. PERFORMANCE METRICS");
        perfTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        perfTitle.setForeground(accentColor);
        pgbc.gridy = 0;
        pgbc.insets = new Insets(0, 0, 10, 0);
        perfCard.add(perfTitle, pgbc);
        
        JPanel perfGrid = new JPanel(new GridBagLayout());
        perfGrid.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        
        JLabel l1 = new JLabel("Sequential Time:");
        l1.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        l1.setForeground(new Color(200, 200, 210));
        g.gridx = 0; g.gridy = 0; g.weightx = 0.6; g.insets = new Insets(4, 0, 4, 0);
        g.anchor = GridBagConstraints.WEST;
        perfGrid.add(l1, g);
        g.gridx = 1; g.weightx = 0.4; g.anchor = GridBagConstraints.EAST;
        perfGrid.add(seqValLabel, g);
        
        JLabel l2 = new JLabel("Parallel Time:");
        l2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        l2.setForeground(new Color(200, 200, 210));
        g.gridx = 0; g.gridy = 1; g.weightx = 0.6; g.anchor = GridBagConstraints.WEST;
        perfGrid.add(l2, g);
        g.gridx = 1; g.weightx = 0.4; g.anchor = GridBagConstraints.EAST;
        perfGrid.add(parValLabel, g);
        
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(63, 63, 70));
        sep.setBackground(new Color(0, 0, 0, 0));
        g.gridx = 0; g.gridy = 2; g.gridwidth = 2; g.weightx = 1.0; g.insets = new Insets(8, 0, 8, 0);
        perfGrid.add(sep, g);
        
        JLabel l3 = new JLabel("Speedup Gain:");
        l3.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l3.setForeground(Color.WHITE);
        g.gridx = 0; g.gridy = 3; g.gridwidth = 1; g.weightx = 0.6; g.anchor = GridBagConstraints.WEST; g.insets = new Insets(4, 0, 4, 0);
        perfGrid.add(l3, g);
        g.gridx = 1; g.weightx = 0.4; g.anchor = GridBagConstraints.EAST;
        perfGrid.add(speedupValLabel, g);
        
        pgbc.gridy = 1;
        pgbc.insets = new Insets(0, 0, 4, 0);
        perfCard.add(perfGrid, pgbc);
        
        // ADD GRAPH TO UI HERE
        pgbc.gridy = 2;
        pgbc.insets = new Insets(10, 0, 4, 0);
        perfCard.add(graphGenerator1, pgbc);
        
        jLabel3.setVisible(false);
        pgbc.gridy = 3;
        perfCard.add(jLabel3, pgbc);
        
        gbc.gridy = 4;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        sidebarContent.add(perfCard, gbc);
        
        // Setup Sidebar JScrollPane
        JScrollPane sidebarScroll = new JScrollPane(sidebarContent);
        sidebarScroll.setOpaque(false);
        sidebarScroll.getViewport().setOpaque(false);
        sidebarScroll.setBorder(null);
        sidebarScroll.setPreferredSize(new Dimension(320, 0));
        sidebarScroll.getVerticalScrollBar().setUnitIncrement(12);
        
        rootPanel.add(sidebarScroll, BorderLayout.WEST);
 
        // Right workspace
        JPanel workspace = new JPanel(new GridLayout(1, 2, 16, 0));
        workspace.setOpaque(false);
        
        JPanel originalContainer = new JPanel(new BorderLayout(5, 5));
        originalContainer.setOpaque(false);
        JLabel origTitle = new JLabel("ORIGINAL SOURCE");
        origTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        origTitle.setForeground(new Color(150, 150, 160));
        originalContainer.add(origTitle, BorderLayout.NORTH);
        originalContainer.add(jLabel1, BorderLayout.CENTER);
        originalContainer.add(jLabelMetadata, BorderLayout.SOUTH);
        
        JPanel processedContainer = new JPanel(new BorderLayout(5, 5));
        processedContainer.setOpaque(false);
        JLabel procTitle = new JLabel("PROCESSED RESULT");
        procTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        procTitle.setForeground(new Color(150, 150, 160));
        processedContainer.add(procTitle, BorderLayout.NORTH);
        processedContainer.add(jLabel2, BorderLayout.CENTER);
        processedContainer.add(histogramPanel1, BorderLayout.SOUTH);
        
        workspace.add(originalContainer);
        workspace.add(processedContainer);
        
        rootPanel.add(workspace, BorderLayout.CENTER);

        // Add Live Retro Diagnostics Console at bottom
        rootPanel.add(console, BorderLayout.SOUTH);
        
        getContentPane().add(rootPanel);
        pack();
        setLocationRelativeTo(null);

        // Log Hardware and System Initialization Details
        int cores = Runtime.getRuntime().availableProcessors();
        long maxMem = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        logMessage("SYSTEM", "Parallel & Distributed Dashboard initialized.");
        logMessage("SYSTEM", "Detected Hardware Cores: " + cores);
        logMessage("SYSTEM", "JVM Allocated Memory limit: " + maxMem + " MB");
        logMessage("SYSTEM", "OS Target: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
    }

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {                                         
        try {
            JFileChooser chooser = new JFileChooser();
            int result = chooser.showOpenDialog(this);

            if (result == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                originalImage = ImageIO.read(file);

                ImageIcon icon = new ImageIcon(originalImage);
                jLabel1.setIcon(icon);
                
                if (graphGenerator1 != null) {
                    graphGenerator1.clear();
                }
                if (histogramPanel1 != null) {
                    histogramPanel1.updateHistogram(originalImage);
                }
                updateMetadata(file);
                logMessage("SYSTEM", "Loaded source image: " + file.getName() + " (" + originalImage.getWidth() + "x" + originalImage.getHeight() + ")");
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to load image: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            logMessage("SYSTEM ERROR", "Failed to load image: " + e.getMessage());
        }
    }                                        

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {                                         
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Please upload a source image first.", "No Image", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            long start = System.currentTimeMillis();
            BufferedImage gray = toGrayScale(originalImage);
            long end = System.currentTimeMillis();
            long time = end - start;
            jLabel2.setIcon(new ImageIcon(gray));
            jLabel3.setText("Sequential Time: " + time + " ms");
            if (histogramPanel1 != null) {
                histogramPanel1.updateHistogram(gray);
            }
            logMessage("GRAYSCALE SEQ", "Completed Grayscale Filter in " + time + " ms");
        } catch (Exception e) {
            e.printStackTrace();
            logMessage("GRAYSCALE SEQ ERROR", e.getMessage());
        }
    }                                        

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {                                         
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Please upload a source image first.", "No Image", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            long start = System.currentTimeMillis();
            BufferedImage gray = toGrayScaleParallel(originalImage);
            long end = System.currentTimeMillis();
            long time = end - start;
            jLabel2.setIcon(new ImageIcon(gray));
            jLabel3.setText("PARALLEL Time: " + time + " ms");
            if (histogramPanel1 != null) {
                histogramPanel1.updateHistogram(gray);
            }
            logMessage("GRAYSCALE PARALLEL", "Completed Grayscale Parallel in " + time + " ms");
        } catch (Exception e) {
            e.printStackTrace();
            logMessage("GRAYSCALE PARALLEL ERROR", e.getMessage());
        }
    }                                        

    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {                                         
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Please upload a source image first.", "No Image", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            long start = System.currentTimeMillis();
            BufferedImage bright = brightnessFilter(originalImage, 40);
            long end = System.currentTimeMillis();
            long time = end - start;
            jLabel2.setIcon(new ImageIcon(bright));
            jLabel3.setText("Brightness Time: " + time + " ms");
            if (histogramPanel1 != null) {
                histogramPanel1.updateHistogram(bright);
            }
            logMessage("BRIGHTNESS", "Completed Brightness (+40) Filter in " + time + " ms");
        } catch (Exception e) {
            e.printStackTrace();
            logMessage("BRIGHTNESS ERROR", e.getMessage());
        }
    }                                        

    private void jTextField1ActionPerformed(java.awt.event.ActionEvent evt) {}                                           

    private void jButton5ActionPerformed(java.awt.event.ActionEvent evt) {                                         
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Please upload a source image first.", "No Image", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            long start = System.currentTimeMillis();
            BufferedImage negative = negativeFilter(originalImage);
            long end = System.currentTimeMillis();
            long time = end - start;
            jLabel2.setIcon(new ImageIcon(negative));
            jLabel3.setText("Negative Time: " + time + " ms");
            if (histogramPanel1 != null) {
                histogramPanel1.updateHistogram(negative);
            }
            logMessage("NEGATIVE", "Completed Negative Filter in " + time + " ms");
        } catch (Exception e) {
            e.printStackTrace();
            logMessage("NEGATIVE ERROR", e.getMessage());
        }
    }                                        

    private void jComboBox1ActionPerformed(java.awt.event.ActionEvent evt) {}                                          

    // CRYPTOGRAPHY ACTIONS
    private void jButtonEncryptActionPerformed(java.awt.event.ActionEvent evt) {
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Please upload a source image first.", "No Image", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String password = new String(jPasswordField1.getPassword());
        if (password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a passphrase to encrypt.", "Empty Password", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            logMessage("CRYPTO", "Initiating image serialization for encryption...");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(originalImage, "png", baos);
            byte[] imageBytes = baos.toByteArray();

            logMessage("CRYPTO", "Generating AES keys and IV parameters...");
            byte[] salt = KeyGeneratorUtil.generateSalt(16);
            byte[] iv = KeyGeneratorUtil.generateIV();

            logMessage("CRYPTO", "Deriving PBKDF2 symmetric key from passphrase...");
            SecretKey key = KeyGeneratorUtil.deriveKeyFromPassword(password, salt);
            
            logMessage("CRYPTO", "Encrypting payload via AES-256-GCM...");
            byte[] ciphertext = AESUtil.encryptChunk(imageBytes, key, iv);

            byte[] combined = new byte[salt.length + iv.length + ciphertext.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(iv, 0, combined, salt.length, iv.length);
            System.arraycopy(ciphertext, 0, combined, salt.length + iv.length, ciphertext.length);

            File outFile = new File("encrypted_image.enc");
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(combined);
            }

            BufferedImage scrambled = generateVisualNoise(originalImage.getWidth(), originalImage.getHeight(), combined);
            jLabel2.setIcon(new ImageIcon(scrambled));
            jLabel3.setText("Encrypted: Saved to " + outFile.getName());
            if (histogramPanel1 != null) {
                histogramPanel1.updateHistogram(scrambled);
            }
            logMessage("CRYPTO SUCCESS", "Encrypted payload of " + combined.length + " bytes saved to '" + outFile.getName() + "'");
            JOptionPane.showMessageDialog(this, "Image encrypted successfully and saved as 'encrypted_image.enc'!", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
            logMessage("CRYPTO ERROR", "Encryption failed: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Encryption failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void jButtonDecryptActionPerformed(java.awt.event.ActionEvent evt) {
        File inFile = new File("encrypted_image.enc");
        if (!inFile.exists()) {
            JOptionPane.showMessageDialog(this, "No encrypted file 'encrypted_image.enc' found to decrypt.", "File Not Found", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String password = new String(jPasswordField1.getPassword());
        if (password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter the passphrase to decrypt.", "Empty Password", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            logMessage("CRYPTO", "Reading encrypted payload from disk...");
            byte[] combined = Files.readAllBytes(inFile.toPath());
            if (combined.length < 28) {
                logMessage("CRYPTO ERROR", "Encrypted file header is corrupted.");
                JOptionPane.showMessageDialog(this, "Encrypted file header is corrupted.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            byte[] salt = new byte[16];
            byte[] iv = new byte[12];
            byte[] ciphertext = new byte[combined.length - 28];

            System.arraycopy(combined, 0, salt, 0, 16);
            System.arraycopy(combined, 16, iv, 0, 12);
            System.arraycopy(combined, 28, ciphertext, 0, ciphertext.length);

            logMessage("CRYPTO", "Deriving PBKDF2 key for decryption...");
            SecretKey key = KeyGeneratorUtil.deriveKeyFromPassword(password, salt);
            
            logMessage("CRYPTO", "Decrypting payload via AES-256-GCM authentication...");
            byte[] decryptedBytes = AESUtil.decryptChunk(ciphertext, key, iv);

            ByteArrayInputStream bais = new ByteArrayInputStream(decryptedBytes);
            BufferedImage decryptedImage = ImageIO.read(bais);

            if (decryptedImage != null) {
                jLabel2.setIcon(new ImageIcon(decryptedImage));
                jLabel3.setText("Decrypted: Image restored successfully.");
                if (histogramPanel1 != null) {
                    histogramPanel1.updateHistogram(decryptedImage);
                }
                logMessage("CRYPTO SUCCESS", "Restored decrypted image buffer: " + decryptedImage.getWidth() + "x" + decryptedImage.getHeight() + " px");
                JOptionPane.showMessageDialog(this, "Image decrypted successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                logMessage("CRYPTO ERROR", "Decrypted payload is not a valid image format.");
                JOptionPane.showMessageDialog(this, "Decrypted data is not a valid image format.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (javax.crypto.AEADBadTagException e) {
            logMessage("CRYPTO ERROR", "Authentication Failed: Incorrect passphrase or payload tampered.");
            JOptionPane.showMessageDialog(this, "Decryption failed: Incorrect password or tampered file.", "Authentication Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
            logMessage("CRYPTO ERROR", "Decryption failed: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Decryption failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private BufferedImage generateVisualNoise(int width, int height, byte[] seedBytes) {
        BufferedImage noise = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.util.Random rand = new java.util.Random(seedBytes.length);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = rand.nextInt(256);
                int g = rand.nextInt(256);
                int b = rand.nextInt(256);
                int rgb = (r << 16) | (g << 8) | b;
                noise.setRGB(x, y, rgb);
            }
        }
        return noise;
    }

    private BufferedImage negativeFilter(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int r = 255 - ((rgb >> 16) & 0xff);
                int g = 255 - ((rgb >> 8) & 0xff);
                int b = 255 - (rgb & 0xff);
                int newRGB = (r << 16) | (g << 8) | b;
                result.setRGB(x, y, newRGB);
            }
        }
        return result;
    }  

    private BufferedImage brightnessFilter(BufferedImage img, int value) {
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                r = Math.min(255, r + value);
                g = Math.min(255, g + value);
                b = Math.min(255, b + value);
                int newRGB = (r << 16) | (g << 8) | b;
                result.setRGB(x, y, newRGB);
            }
        }
        return result;
    }
      
    private BufferedImage toGrayScaleParallel(BufferedImage img) throws InterruptedException {
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int numThreads = Integer.parseInt(jComboBox1.getSelectedItem().toString());
        Thread[] threads = new Thread[numThreads];
        int rowsPerThread = height / numThreads;
       
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            int startRow = t * rowsPerThread;
            int endRow = (t == numThreads - 1) ? height : startRow + rowsPerThread;

            threads[t] = new Thread(() -> {
                logMessage("THREAD-" + threadId, "Grayscale: Rows " + startRow + " to " + (endRow - 1));
                long tStart = System.currentTimeMillis();
                for (int y = startRow; y < endRow; y++) {
                    for (int x = 0; x < width; x++) {
                        int rgb = img.getRGB(x, y);
                        int r = (rgb >> 16) & 0xff;
                        int g = (rgb >> 8) & 0xff;
                        int b = rgb & 0xff;
                        int gray = (r + g + b) / 3;
                        int newRGB = (gray << 16) | (gray << 8) | gray;
                        result.setRGB(x, y, newRGB);
                    }
                }
                long tEnd = System.currentTimeMillis();
                logMessage("THREAD-" + threadId, "Grayscale: Completed in " + (tEnd - tStart) + " ms");
            });
            threads[t].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }
        return result;
    }
    
    private BufferedImage toGrayScale(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage grayImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int gray = (r + g + b) / 3;
                int newRGB = (gray << 16) | (gray << 8) | gray;
                grayImg.setRGB(x, y, newRGB);
            }
        }
        return grayImg;
    }

    // --- NEW SOBEL FILTER IMPLEMENTATION ---
    private BufferedImage sobelFilter(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int p00 = getGray(img.getRGB(x - 1, y - 1));
                int p01 = getGray(img.getRGB(x, y - 1));
                int p02 = getGray(img.getRGB(x + 1, y - 1));
                int p10 = getGray(img.getRGB(x - 1, y));
                int p12 = getGray(img.getRGB(x + 1, y));
                int p20 = getGray(img.getRGB(x - 1, y + 1));
                int p21 = getGray(img.getRGB(x, y + 1));
                int p22 = getGray(img.getRGB(x + 1, y + 1));

                int gx = (p02 + 2 * p12 + p22) - (p00 + 2 * p10 + p20);
                int gy = (p20 + 2 * p21 + p22) - (p00 + 2 * p01 + p02);

                int g = (int) Math.min(255, Math.sqrt(gx * gx + gy * gy));
                result.setRGB(x, y, (g << 16) | (g << 8) | g);
            }
        }
        return result;
    }
    
    private int getGray(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return (r + g + b) / 3;
    }

    private BufferedImage sobelFilterParallel(BufferedImage img) throws InterruptedException {
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int numThreads = Integer.parseInt(jComboBox1.getSelectedItem().toString());
        Thread[] threads = new Thread[numThreads];
        int rowsPerThread = height / numThreads;

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            int startRow = t * rowsPerThread;
            int endRow = (t == numThreads - 1) ? height : startRow + rowsPerThread;

            threads[t] = new Thread(() -> {
                logMessage("THREAD-" + threadId, "Sobel: Rows " + startRow + " to " + (endRow - 1));
                long tStart = System.currentTimeMillis();
                
                int sY = Math.max(1, startRow);
                int eY = Math.min(height - 1, endRow);
                for (int y = sY; y < eY; y++) {
                    for (int x = 1; x < width - 1; x++) {
                        int p00 = getGray(img.getRGB(x - 1, y - 1));
                        int p01 = getGray(img.getRGB(x, y - 1));
                        int p02 = getGray(img.getRGB(x + 1, y - 1));
                        int p10 = getGray(img.getRGB(x - 1, y));
                        int p12 = getGray(img.getRGB(x + 1, y));
                        int p20 = getGray(img.getRGB(x - 1, y + 1));
                        int p21 = getGray(img.getRGB(x, y + 1));
                        int p22 = getGray(img.getRGB(x + 1, y + 1));

                        int gx = (p02 + 2 * p12 + p22) - (p00 + 2 * p10 + p20);
                        int gy = (p20 + 2 * p21 + p22) - (p00 + 2 * p01 + p02);

                        int g = (int) Math.min(255, Math.sqrt(gx * gx + gy * gy));
                        result.setRGB(x, y, (g << 16) | (g << 8) | g);
                    }
                }
                long tEnd = System.currentTimeMillis();
                logMessage("THREAD-" + threadId, "Sobel: Completed in " + (tEnd - tStart) + " ms");
            });
            threads[t].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }
        return result;
    }

    // --- NEW GAUSSIAN BLUR IMPLEMENTATION ---
    private BufferedImage blurFilter(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int rSum = 0, gSum = 0, bSum = 0;
                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int rgb = img.getRGB(x + kx, y + ky);
                        rSum += (rgb >> 16) & 0xff;
                        gSum += (rgb >> 8) & 0xff;
                        bSum += rgb & 0xff;
                    }
                }
                int r = rSum / 9;
                int g = gSum / 9;
                int b = bSum / 9;
                result.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return result;
    }

    private BufferedImage blurFilterParallel(BufferedImage img) throws InterruptedException {
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int numThreads = Integer.parseInt(jComboBox1.getSelectedItem().toString());
        Thread[] threads = new Thread[numThreads];
        int rowsPerThread = height / numThreads;

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            int startRow = t * rowsPerThread;
            int endRow = (t == numThreads - 1) ? height : startRow + rowsPerThread;

            threads[t] = new Thread(() -> {
                logMessage("THREAD-" + threadId, "Blur: Rows " + startRow + " to " + (endRow - 1));
                long tStart = System.currentTimeMillis();
                
                int sY = Math.max(1, startRow);
                int eY = Math.min(height - 1, endRow);
                for (int y = sY; y < eY; y++) {
                    for (int x = 1; x < width - 1; x++) {
                        int rSum = 0, gSum = 0, bSum = 0;
                        for (int ky = -1; ky <= 1; ky++) {
                            for (int kx = -1; kx <= 1; kx++) {
                                int rgb = img.getRGB(x + kx, y + ky);
                                rSum += (rgb >> 16) & 0xff;
                                gSum += (rgb >> 8) & 0xff;
                                bSum += rgb & 0xff;
                            }
                        }
                        int r = rSum / 9;
                        int g = gSum / 9;
                        int b = bSum / 9;
                        result.setRGB(x, y, (r << 16) | (g << 8) | b);
                    }
                }
                long tEnd = System.currentTimeMillis();
                logMessage("THREAD-" + threadId, "Blur: Completed in " + (tEnd - tStart) + " ms");
            });
            threads[t].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }
        return result;
    }

    // --- NEW TIMING RUNNERS ---
    private void runSobel(boolean parallel) {
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Please upload a source image first.", "No Image", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            long start = System.currentTimeMillis();
            BufferedImage res;
            if (parallel) {
                res = sobelFilterParallel(originalImage);
            } else {
                res = sobelFilter(originalImage);
            }
            long end = System.currentTimeMillis();
            long time = end - start;
            jLabel2.setIcon(new ImageIcon(res));
            jLabel3.setText((parallel ? "PARALLEL" : "Sequential") + " Time: " + time + " ms");
            if (histogramPanel1 != null) {
                histogramPanel1.updateHistogram(res);
            }
            logMessage(parallel ? "SOBEL PARALLEL" : "SOBEL SEQ", "Completed Sobel Edge Detection in " + time + " ms");
        } catch (Exception e) {
            e.printStackTrace();
            logMessage("SOBEL ERROR", e.getMessage());
        }
    }

    private void runBlur(boolean parallel) {
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Please upload a source image first.", "No Image", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            long start = System.currentTimeMillis();
            BufferedImage res;
            if (parallel) {
                res = blurFilterParallel(originalImage);
            } else {
                res = blurFilter(originalImage);
            }
            long end = System.currentTimeMillis();
            long time = end - start;
            jLabel2.setIcon(new ImageIcon(res));
            jLabel3.setText((parallel ? "PARALLEL" : "Sequential") + " Time: " + time + " ms");
            if (histogramPanel1 != null) {
                histogramPanel1.updateHistogram(res);
            }
            logMessage(parallel ? "BLUR PARALLEL" : "BLUR SEQ", "Completed Gaussian Blur in " + time + " ms");
        } catch (Exception e) {
            e.printStackTrace();
            logMessage("BLUR ERROR", e.getMessage());
        }
    }

    // --- NEW DISTRIBUTED SERVER METHODS ---
    private void startServer() {
        if (isServerRunning) {
            logMessage("SERVER", "Server is already running.");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(jTextFieldPort.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid Port Number", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        isServerRunning = true;
        jButtonStartServer.setText("Stop Server");
        jButtonStartServer.setBackground(new Color(239, 68, 68)); // Red indicating stop

        Thread serverThread = new Thread(() -> {
            logMessage("SERVER", "Starting Distributed Server on port " + port + "...");
            try {
                serverSocket = new ServerSocket(port);
                logMessage("SERVER", "Listening for incoming node connections...");
                while (isServerRunning) {
                    try {
                        Socket socket = serverSocket.accept();
                        logMessage("SERVER", "Node connected: " + socket.getRemoteSocketAddress());
                        new Thread(() -> handleServerConnection(socket)).start();
                    } catch (IOException e) {
                        if (!isServerRunning) {
                            break;
                        }
                        logMessage("SERVER ERROR", "Accept failed: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                logMessage("SERVER ERROR", "Could not listen on port " + port + ": " + e.getMessage());
            } finally {
                stopServerCleanup();
            }
        });
        serverThread.start();
    }

    private void handleServerConnection(Socket socket) {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            
            logMessage("SERVER", "Receiving handshake protocol payload...");
            String password = dis.readUTF();
            String metaInfo = dis.readUTF();
            long fileSize = dis.readLong();
            logMessage("SERVER", "Metadata: " + metaInfo + " (" + fileSize + " bytes)");
            
            byte[] combinedPayload = new byte[(int) fileSize];
            dis.readFully(combinedPayload);
            logMessage("SERVER", "Network transmission completed.");
            
            if (combinedPayload.length < 28) {
                dos.writeUTF("ERROR: Payload corrupted or empty.");
                return;
            }
            
            byte[] salt = new byte[16];
            byte[] iv = new byte[12];
            byte[] ciphertext = new byte[combinedPayload.length - 28];
            
            System.arraycopy(combinedPayload, 0, salt, 0, 16);
            System.arraycopy(combinedPayload, 16, iv, 0, 12);
            System.arraycopy(combinedPayload, 28, ciphertext, 0, ciphertext.length);
            
            logMessage("SERVER", "Deriving PBKDF2 decryption key...");
            SecretKey key = KeyGeneratorUtil.deriveKeyFromPassword(password, salt);
            
            logMessage("SERVER", "Decrypting image via AES-256-GCM...");
            byte[] decryptedBytes = AESUtil.decryptChunk(ciphertext, key, iv);
            
            ByteArrayInputStream bais = new ByteArrayInputStream(decryptedBytes);
            BufferedImage decryptedImage = ImageIO.read(bais);
            
            if (decryptedImage != null) {
                SwingUtilities.invokeLater(() -> {
                    jLabel2.setIcon(new ImageIcon(decryptedImage));
                    jLabel3.setText("Decrypted: Received via Distributed Node.");
                    if (histogramPanel1 != null) {
                        histogramPanel1.updateHistogram(decryptedImage);
                    }
                    logMessage("SERVER SUCCESS", "Decrypted and rendered image of size " + decryptedImage.getWidth() + "x" + decryptedImage.getHeight());
                });
                dos.writeUTF("SUCCESS: Decrypted and displayed payload on receiver node.");
            } else {
                dos.writeUTF("ERROR: Decrypted data is not a valid image format.");
            }
        } catch (javax.crypto.AEADBadTagException e) {
            logMessage("SERVER ERROR", "Decryption failed: Incorrect password or corrupted tag.");
            try {
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                dos.writeUTF("ERROR: Incorrect decryption password or corrupted tag.");
            } catch (IOException ignored) {}
        } catch (Exception e) {
            logMessage("SERVER ERROR", "Failed: " + e.getMessage());
            try {
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                dos.writeUTF("ERROR: Server node failed to process image: " + e.getMessage());
            } catch (IOException ignored) {}
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private void stopServer() {
        isServerRunning = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logMessage("SERVER ERROR", "Error closing socket: " + e.getMessage());
            }
        }
        stopServerCleanup();
    }

    private void stopServerCleanup() {
        isServerRunning = false;
        SwingUtilities.invokeLater(() -> {
            jButtonStartServer.setText("Start Server Node");
            jButtonStartServer.setBackground(new Color(16, 185, 129));
        });
        logMessage("SERVER", "Server stopped.");
    }

    // --- NEW DISTRIBUTED CLIENT METHODS ---
    private void transmitPayload() {
        File encFile = new File("encrypted_image.enc");
        if (!encFile.exists()) {
            JOptionPane.showMessageDialog(this, "Please encrypt the image first (creates 'encrypted_image.enc').", "No Encrypted Image", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String ip = jTextFieldIP.getText().trim();
        int port;
        try {
            port = Integer.parseInt(jTextFieldPort.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid Port Number", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String password = new String(jPasswordField1.getPassword());
        if (password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter the passphrase to send for decryption.", "Empty Password", JOptionPane.WARNING_MESSAGE);
            return;
        }

        logMessage("CLIENT", "Connecting to Distributed Node at " + ip + ":" + port + "...");
        
        new Thread(() -> {
            try (Socket socket = new Socket(ip, port);
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                 DataInputStream dis = new DataInputStream(socket.getInputStream())) {
                
                logMessage("CLIENT", "Socket connected! Handshaking...");
                dos.writeUTF(password);
                
                byte[] payload = Files.readAllBytes(encFile.toPath());
                String metadata = originalImage != null ? 
                    (originalImage.getWidth() + "x" + originalImage.getHeight() + " px") : "Unknown";
                dos.writeUTF(metadata);
                dos.writeLong(payload.length);
                
                dos.write(payload);
                dos.flush();
                logMessage("CLIENT", "Payload transmission complete. Waiting for feedback...");
                
                String response = dis.readUTF();
                logMessage("CLIENT", "Remote Node Response: " + response);
                
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, response, "Distributed Network Response", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception e) {
                logMessage("CLIENT ERROR", "Transmission failed: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Transmission failed: " + e.getMessage(), "Network Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    // --- NEW LOGGING UTILITY ---
    private void logMessage(String tag, String message) {
        if (console != null) {
            console.log(tag, message);
        } else {
            logger.info("[" + tag + "] " + message);
        }
    }

    // --- NEW METADATA HELPERS ---
    private void updateMetadata(File file) {
        if (originalImage == null) return;
        int w = originalImage.getWidth();
        int h = originalImage.getHeight();
        long bytes = file != null ? file.length() : 0;
        String sizeStr = bytes > 0 ? String.format("%.2f MB", bytes / (1024.0 * 1024.0)) : "Memory Stream";
        String quality = getImageQuality(w, h);
        jLabelMetadata.setText(String.format("Res: %dx%d (%s) | Size: %s | Aspect Ratio: %s", 
            w, h, quality, sizeStr, getAspectRatio(w, h)));
    }

    private String getImageQuality(int width, int height) {
        long pixels = (long) width * height;
        if (pixels >= 8294400) return "4K UHD";
        if (pixels >= 2073600) return "Full HD";
        if (pixels >= 921600) return "HD";
        if (pixels >= 300000) return "SD";
        return "Low Res";
    }

    private String getAspectRatio(int width, int height) {
        int gcd = gcd(width, height);
        return (width / gcd) + ":" + (height / gcd);
    }

    private int gcd(int a, int b) {
        while (b > 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }

        java.awt.EventQueue.invokeLater(() -> new GUInFilters().setVisible(true));
    }

    private static class CardPanel extends JPanel {
        private final int radius;
        private final Color borderCol;

        public CardPanel(int radius, Color bg, Color border) {
            this.radius = radius;
            this.borderCol = border;
            setOpaque(false);
            setBackground(bg);
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.setColor(borderCol);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class ModernButton extends JButton {
        private final Color normalColor;
        private final Color hoverColor;
        private final Color pressedColor;
        private boolean isHovered = false;
        private boolean isPressed = false;

        public ModernButton(String text, Color normal, Color hover, Color pressed) {
            super(text);
            this.normalColor = normal;
            this.hoverColor = hover;
            this.pressedColor = pressed;
            
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(new Font("Segoe UI", Font.BOLD, 13));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent evt) {
                    isHovered = true;
                    repaint();
                }
                @Override
                public void mouseExited(MouseEvent evt) {
                    isHovered = false;
                    repaint();
                }
                @Override
                public void mousePressed(MouseEvent evt) {
                    if (isEnabled()) {
                        isPressed = true;
                        repaint();
                    }
                }
                @Override
                public void mouseReleased(MouseEvent evt) {
                    isPressed = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (!isEnabled()) {
                g2.setColor(new Color(63, 63, 70));
            } else if (isPressed) {
                g2.setColor(pressedColor);
            } else if (isHovered) {
                g2.setColor(hoverColor);
            } else {
                g2.setColor(normalColor);
            }

            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class ImageLabel extends JLabel {
        private BufferedImage image;
        private final String placeholderText;

        public ImageLabel(String placeholderText) {
            super("", CENTER);
            this.placeholderText = placeholderText;
            setOpaque(false);
            setBackground(new Color(24, 24, 28));
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        }

        @Override
        public void setIcon(Icon icon) {
            if (icon instanceof ImageIcon) {
                Image img = ((ImageIcon) icon).getImage();
                this.image = toBufferedImage(img);
            } else if (icon == null) {
                this.image = null;
            }
            repaint();
        }

        private BufferedImage toBufferedImage(Image img) {
            if (img instanceof BufferedImage) {
                return (BufferedImage) img;
            }
            new ImageIcon(img).getImage();
            
            int w = img.getWidth(null);
            int h = img.getHeight(null);
            if (w <= 0 || h <= 0) {
                w = 1;
                h = 1;
            }
            BufferedImage bimage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D bGr = bimage.createGraphics();
            bGr.drawImage(img, 0, 0, null);
            bGr.dispose();
            return bimage;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

            g2.setColor(new Color(45, 45, 52));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

            if (image == null) {
                g2.setColor(new Color(120, 120, 130));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                FontMetrics fm = g2.getFontMetrics();
                
                int x = (getWidth() - fm.stringWidth(placeholderText)) / 2;
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                
                if (x < 10) {
                    if (placeholderText.contains("to begin")) {
                        String line1 = "Drag image or click";
                        String line2 = "'Upload Source Image' to begin";
                        int x1 = (getWidth() - fm.stringWidth(line1)) / 2;
                        int x2 = (getWidth() - fm.stringWidth(line2)) / 2;
                        g2.drawString(line1, x1, y - 10);
                        g2.drawString(line2, x2, y + 10);
                    } else {
                        g2.drawString(placeholderText, 10, y);
                    }
                } else {
                    g2.drawString(placeholderText, x, y);
                }

                g2.setColor(new Color(63, 63, 70));
                float[] dash = {6.0f, 4.0f};
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));
                g2.drawRoundRect(10, 10, getWidth() - 21, getHeight() - 21, 8, 8);
            } else {
                int imgW = image.getWidth();
                int imgH = image.getHeight();
                int panelW = getWidth() - 12;
                int panelH = getHeight() - 12;

                double scaleX = (double) panelW / imgW;
                double scaleY = (double) panelH / imgH;
                double scale = Math.min(scaleX, scaleY);

                int newW = (int) (imgW * scale);
                int newH = (int) (imgH * scale);

                int x = (getWidth() - newW) / 2;
                int y = (getHeight() - newH) / 2;

                g2.drawImage(image, x, y, newW, newH, null);

                g2.setColor(new Color(15, 15, 20, 200));
                g2.fillRoundRect(15, getHeight() - 35, 120, 20, 6, 6);
                g2.setColor(new Color(220, 220, 225));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                FontMetrics fm = g2.getFontMetrics();
                String resStr = imgW + " × " + imgH + " px";
                int rx = 15 + (120 - fm.stringWidth(resStr)) / 2;
                g2.drawString(resStr, rx, getHeight() - 21);
            }
            g2.dispose();
        }
    }

    private static class PerformanceLabel extends JLabel {
        private final JLabel seqLabel;
        private final JLabel parLabel;
        private final JLabel speedupLabel;
        private final GraphGenerator graphGenerator;
        private long lastSeqTime = 0;
        private long lastParTime = 0;

        public PerformanceLabel(JLabel seq, JLabel par, JLabel speedup, GraphGenerator graphGenerator) {
            this.seqLabel = seq;
            this.parLabel = par;
            this.speedupLabel = speedup;
            this.graphGenerator = graphGenerator;
        }

        @Override
        public void setText(String text) {
            super.setText(text);
            try {
                if (text != null && text.contains("Time:") && text.contains("ms")) {
                    int colonIdx = text.indexOf(":");
                    int msIdx = text.indexOf("ms");
                    if (colonIdx != -1 && msIdx != -1 && colonIdx < msIdx) {
                        String timeStr = text.substring(colonIdx + 1, msIdx).trim();
                        long timeVal = Long.parseLong(timeStr);
                        
                        if (text.startsWith("PARALLEL")) {
                            lastParTime = timeVal;
                            if (parLabel != null) {
                                parLabel.setText(timeVal + " ms");
                            }
                            updateSpeedup();
                        } else {
                            lastSeqTime = timeVal;
                            if (seqLabel != null) {
                                seqLabel.setText(timeVal + " ms");
                            }
                            lastParTime = 0;
                            if (parLabel != null) {
                                parLabel.setText("--");
                            }
                            if (speedupLabel != null) {
                                speedupLabel.setText("--");
                                speedupLabel.setForeground(new Color(150, 150, 160));
                            }
                            if (graphGenerator != null) {
                                graphGenerator.setTimes(lastSeqTime, 0);
                            }
                        }
                    }
                }
            } catch (Exception e) {}
        }

        private void updateSpeedup() {
            if (lastSeqTime > 0 && lastParTime > 0 && speedupLabel != null) {
                double speedup = (double) lastSeqTime / lastParTime;
                speedupLabel.setText(String.format("%.2fx", speedup));
                if (speedup > 1.05) speedupLabel.setForeground(new Color(16, 185, 129));
                else if (speedup < 0.95) speedupLabel.setForeground(new Color(239, 68, 68));
                else speedupLabel.setForeground(Color.WHITE);
                
                if (graphGenerator != null) {
                    graphGenerator.setTimes(lastSeqTime, lastParTime);
                }
            }
        }
    }
    
    // Variables declaration - do not modify                     
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton5;
    private javax.swing.JComboBox<String> jComboBox1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable jTable1;
    private javax.swing.JTextField jTextField1;
    // End of variables declaration                   

    // Custom Variables for Cryptography Module
    private javax.swing.JPasswordField jPasswordField1;
    private javax.swing.JButton jButtonEncrypt;
    private javax.swing.JButton jButtonDecrypt;
    
    // Custom Variables for Graph Generator Module
    private GraphGenerator graphGenerator1;

    // Custom Variables for New Subsystems
    private javax.swing.JButton jButtonStartServer;
    private javax.swing.JButton jButtonTransmit;
    private javax.swing.JTextField jTextFieldIP;
    private javax.swing.JTextField jTextFieldPort;
    private javax.swing.JLabel jLabelMetadata;
    private HistogramPanel histogramPanel1;
    private RetroConsole console;

    private javax.swing.JButton jButtonSobelSeq;
    private javax.swing.JButton jButtonSobelParallel;
    private javax.swing.JButton jButtonBlurSeq;
    private javax.swing.JButton jButtonBlurParallel;
    
    /**
     * Custom Swing panel that dynamically renders a vertical bar chart
     * comparing Sequential vs. Parallel execution times.
     */
    public static class GraphGenerator extends JPanel {
        private long sequentialTime = 0;
        private long parallelTime = 0;
        private static final Color BG_COLOR = new Color(24, 24, 30);
        private static final Color BORDER_COLOR = new Color(45, 45, 52);
        private static final Color GRID_COLOR = new Color(45, 45, 52, 100);
        private static final Color TEXT_COLOR = new Color(200, 200, 210);
        private static final Color MUTE_COLOR = new Color(120, 120, 130);
        
        private static final Color SEQ_COLOR = new Color(245, 158, 11);
        private static final Color PAR_COLOR = new Color(16, 185, 129);
        
       public GraphGenerator() {
            setOpaque(false);
            setPreferredSize(new Dimension(280, 140));
            setMinimumSize(new Dimension(280, 140)); // Add this to prevent squishing!
            setMaximumSize(new Dimension(280, 140)); // Add this to enforce bounds!
        }
        public void setTimes(long seq, long par) {
            this.sequentialTime = seq;
            this.parallelTime = par;
            repaint();
        }
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
            int w = getWidth(), h = getHeight();
            g2.setColor(BG_COLOR);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
            g2.setColor(BORDER_COLOR);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
            int paddingLeft = 45, paddingRight = 15, paddingTop = 25, paddingBottom = 25;
            int chartW = w - paddingLeft - paddingRight;
            int chartH = h - paddingTop - paddingBottom;
            long maxVal = Math.max(sequentialTime, parallelTime);
            if (maxVal == 0) maxVal = 100; 
            for (int i = 0; i <= 2; i++) {
                int yLine = paddingTop + (chartH * i / 2);
                g2.setColor(GRID_COLOR);
                g2.drawLine(paddingLeft, yLine, w - paddingRight, yLine);
                long scaleVal = maxVal - (maxVal * i / 2);
                g2.setColor(MUTE_COLOR);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                g2.drawString(scaleVal + " ms", 8, yLine + 4);
            }
            int barW = Math.max(15, chartW / 4);
            int gap = chartW / 3;
            drawBar(g2, paddingLeft + gap - (barW / 2), sequentialTime, maxVal, barW, chartH, paddingTop, SEQ_COLOR, "Seq");
            drawBar(g2, paddingLeft + (gap * 2) - (barW / 2), parallelTime, maxVal, barW, chartH, paddingTop, PAR_COLOR, "Par");
            g2.dispose();
        }
        private void drawBar(Graphics2D g2, int x, long val, long maxVal, int barW, int chartH, int paddingTop, Color color, String label) {
            int barH = (maxVal > 0 && val > 0) ? (int) (chartH * val / maxVal) : 0;
            int y = paddingTop + chartH - barH;
            if (val > 0) {
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
                g2.fillRoundRect(x, y, barW, barH, 4, 4);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(x, y, barW, barH, 4, 4);
                g2.fillRoundRect(x, y, barW, 4, 4, 4);
                
                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                String valStr = val + " ms";
                g2.drawString(valStr, x + (barW - g2.getFontMetrics().stringWidth(valStr)) / 2, y - 6);
            } else {
                g2.setColor(new Color(63, 63, 70));
                float[] dash = {3.0f, 3.0f};
                g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));
                g2.drawRoundRect(x, paddingTop + chartH - 10, barW, 10, 4, 4);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                g2.setColor(MUTE_COLOR);
                g2.drawString("--", x + (barW - g2.getFontMetrics().stringWidth("--")) / 2, paddingTop + chartH - 15);
            }
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.setColor(TEXT_COLOR);
            g2.drawString(label, x + (barW - g2.getFontMetrics().stringWidth(label)) / 2, paddingTop + chartH + 16);
        }
    }

    public static class RetroConsole extends JPanel {
        private final JTextArea textArea;
        public RetroConsole() {
            setLayout(new BorderLayout());
            textArea = new JTextArea();
            textArea.setBackground(new Color(10, 10, 12));
            textArea.setForeground(new Color(34, 197, 94)); // Emerald green
            textArea.setFont(new Font("Consolas", Font.PLAIN, 12));
            textArea.setEditable(false);
            textArea.setMargin(new Insets(8, 8, 8, 8));
            
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(45, 45, 52), 1));
            scrollPane.getVerticalScrollBar().setBackground(new Color(15, 15, 20));
            add(scrollPane, BorderLayout.CENTER);
            setPreferredSize(new Dimension(0, 160));
        }
        public void log(String tag, String message) {
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date());
            String logLine = String.format("[%s] [%s] %s\n", timestamp, tag.toUpperCase(), message);
            SwingUtilities.invokeLater(() -> {
                textArea.append(logLine);
                textArea.setCaretPosition(textArea.getDocument().getLength());
            });
        }
        public void clear() {
            SwingUtilities.invokeLater(() -> textArea.setText(""));
        }
    }

    public static class HistogramPanel extends JPanel {
        private int[] redFreq = new int[256];
        private int[] greenFreq = new int[256];
        private int[] blueFreq = new int[256];
        private boolean hasData = false;

        public HistogramPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(280, 140));
            setMinimumSize(new Dimension(280, 140));
            setMaximumSize(new Dimension(280, 140));
        }

        public void updateHistogram(BufferedImage img) {
            if (img == null) {
                hasData = false;
                repaint();
                return;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            int[] r = new int[256];
            int[] g = new int[256];
            int[] b = new int[256];

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = img.getRGB(x, y);
                    int red = (rgb >> 16) & 0xff;
                    int green = (rgb >> 8) & 0xff;
                    int blue = rgb & 0xff;
                    r[red]++;
                    g[green]++;
                    b[blue]++;
                }
            }
            this.redFreq = r;
            this.greenFreq = g;
            this.blueFreq = b;
            this.hasData = true;
            repaint();
        }

        public void clear() {
            hasData = false;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Background card
            g2.setColor(new Color(24, 24, 30));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
            g2.setColor(new Color(45, 45, 52));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

            if (!hasData) {
                g2.setColor(new Color(120, 120, 130));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                FontMetrics fm = g2.getFontMetrics();
                String placeholder = "RGB Color Balance Histogram";
                int x = (w - fm.stringWidth(placeholder)) / 2;
                int y = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(placeholder, x, y);
                
                // Dotted border
                g2.setColor(new Color(63, 63, 70));
                float[] dash = {3.0f, 3.0f};
                g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));
                g2.drawRoundRect(10, 10, w - 21, h - 21, 6, 6);
                g2.dispose();
                return;
            }

            int maxFreq = 0;
            for (int i = 0; i < 256; i++) {
                maxFreq = Math.max(maxFreq, redFreq[i]);
                maxFreq = Math.max(maxFreq, greenFreq[i]);
                maxFreq = Math.max(maxFreq, blueFreq[i]);
            }
            if (maxFreq == 0) maxFreq = 1;

            int padLeft = 15;
            int padRight = 15;
            int padTop = 15;
            int padBottom = 15;
            int drawW = w - padLeft - padRight;
            int drawH = h - padTop - padBottom;

            // Draw curves
            paintCurve(g2, redFreq, maxFreq, drawW, drawH, padLeft, padTop, new Color(239, 68, 68, 120), new Color(239, 68, 68));
            paintCurve(g2, greenFreq, maxFreq, drawW, drawH, padLeft, padTop, new Color(16, 185, 129, 120), new Color(16, 185, 129));
            paintCurve(g2, blueFreq, maxFreq, drawW, drawH, padLeft, padTop, new Color(59, 130, 246, 120), new Color(59, 130, 246));

            g2.dispose();
        }

        private void paintCurve(Graphics2D g2, int[] freq, int maxFreq, int drawW, int drawH, int padLeft, int padTop, Color fillColor, Color strokeColor) {
            Polygon poly = new Polygon();
            poly.addPoint(padLeft, padTop + drawH);

            for (int i = 0; i < 256; i++) {
                int x = padLeft + (int) (i * (double) drawW / 255.0);
                int valH = (int) (freq[i] * (double) drawH / maxFreq);
                int y = padTop + drawH - valH;
                poly.addPoint(x, y);
            }
            poly.addPoint(padLeft + drawW, padTop + drawH);

            g2.setColor(fillColor);
            g2.fill(poly);

            g2.setColor(strokeColor);
            g2.setStroke(new BasicStroke(1.2f));
            for (int i = 0; i < poly.npoints - 3; i++) {
                g2.drawLine(poly.xpoints[i + 1], poly.ypoints[i + 1], poly.xpoints[i + 2], poly.ypoints[i + 2]);
            }
        }
    }
}
