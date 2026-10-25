package ser.cliente.view;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

import ser.cliente.infrastructure.FilesUtils;
import ser.cliente.model.ClientAction;
import ser.cliente.model.ClientInfo;
import ser.cliente.model.DocumentCatalog;
import ser.cliente.model.EmotionResult;
import ser.cliente.model.FaceImage;
import ser.cliente.model.FileDocument;
import ser.cliente.model.MessageDocument;
import ser.cliente.model.ServerInfo;

public final class ClientView extends JFrame {
    private static final Color BLUE_BUTTON = new Color(27, 161, 226);
    private static final Color GREEN_BUTTON = new Color(39, 151, 80);
    private static final Color TABLE_HEADER = new Color(206, 231, 255);
    private static final Color TABLE_ALT_ROW = new Color(246, 250, 255);
    private static final Color CONSOLE_BG = new Color(243, 244, 246);

    private final JTextField ipField = new JTextField("");
    private final JTextField portField = new JTextField("");
    private final JTextField nameField = new JTextField("");
    private final RoundButton connectButton = new RoundButton("Conectar", 10);
    private final JRadioButton tcpRadio = new JRadioButton("TCP", true);
    private final JRadioButton udpRadio = new JRadioButton("UDP", false);
    private final JComboBox<ClientAction> actionCombo = new JComboBox<>(ClientAction.values());
    private final RoundButton sendButton = new RoundButton("Enviar", 10);

    private final CardLayout dataCards = new CardLayout();
    private final JPanel dataCardsPanel = new JPanel(dataCards);

    private final DefaultTableModel clientsTableModel = new ReadOnlyTableModel(new String[]{"ID", "Nombre", "IP", "Servidor"});
    private final DefaultTableModel serversTableModel = new ReadOnlyTableModel(new String[]{"ID", "Nombre", "Host:Puerto", "Estado", "Clientes"});
    private final DefaultTableModel filesTableModel = new ReadOnlyTableModel(new String[]{"ID", "Nombre", "Extension", "Tamaño (mb)", "Propietario"});
    private final DefaultTableModel messagesTableModel = new ReadOnlyTableModel(new String[]{"ID", "Asunto"});
    private final DefaultTableModel faceImagesTableModel = new ReadOnlyTableModel(
            new String[]{"ID", "Titulo", "Tamano (mb)", "Imagen"},
            new Class<?>[]{Integer.class, String.class, String.class, Icon.class}
    );
    private final JTable clientsTable = createStyledTable(clientsTableModel);
    private final JTable serversTable = createStyledTable(serversTableModel);
    private final JTable filesTable = createStyledTable(filesTableModel);
    private final JTable messagesTable = createStyledTable(messagesTableModel);
    private final JTable faceImagesTable = createStyledTable(faceImagesTableModel);

    private final JTextField destinatarioMsgField = new JTextField("");
    private final JTextField asuntoMsgField = new JTextField("");
    private final JTextField destinatarioFileField = new JTextField("");
    private final JTextField serverIdLogsField = new JTextField("");
    private final JTextArea sendMessageArea = new JTextArea("");
    private final JButton browseFilesButton = new JButton("Examinar");
    private final JList<DisplayFileItem> filesList = new JList<>();
    private final javax.swing.DefaultListModel<DisplayFileItem> filesListModel = new javax.swing.DefaultListModel<>();
    private final List<File> selectedFiles = new ArrayList<>();

    private final JTextField receiveMessageIdField = new JTextField("");
    private final PlaceholderTextArea receivedMessageArea = new PlaceholderTextArea("Contenido del mensaje recibido");

    private final JTextField receiveFileIdField = new JTextField("");
    private final javax.swing.JCheckBox hashCheckBox = new javax.swing.JCheckBox("Hash", false);
    private final javax.swing.JCheckBox encryptedCheckBox = new javax.swing.JCheckBox("Encriptado", false);
    private final JTextField receiveFilePathField = new JTextField("");
    private final JButton browseReceiveFileButton = new JButton("Examinar");

    private final JTextField logsPathField = new JTextField("");
    private final JButton browseLogsButton = new JButton("Examinar");

    private final EmotionImagePanel emotionImagePanel = new EmotionImagePanel();
    private final EmotionResultPanel emotionResultPanel = new EmotionResultPanel();
    private final JTextField emotionImageIdField = new JTextField("");
    private final JLabel emotionHintLabel = new JLabel("Tambien puedes procesar por la ID de la imagen.");
    private final RoundButton changeEmotionImageButton = new RoundButton("Cargar otra imagen", 10);
    private final List<EmotionCard> emotionCards = new ArrayList<>();
    private BufferedImage processedEmotionImage;
    private File selectedEmotionImageFile;

    private final JTextArea consoleArea = new JTextArea(6, 20);

    // Loading overlay
    private final LoadingOverlay loadingOverlay = new LoadingOverlay();
    private Timer spinnerTimer;

    public ClientView() {
        super("Cliente");
        configureWindow();
        buildUi();
        showActionCard(ClientAction.LIST_CLIENTS);
        setGlassPane(loadingOverlay);
    }

    private void configureWindow() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(580, 720));
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Color.WHITE);
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(Color.WHITE);
        top.add(buildConnectionPanel());
        top.add(Box.createVerticalStrut(6));
        top.add(new JSeparator(SwingConstants.HORIZONTAL));
        top.add(Box.createVerticalStrut(6));
        top.add(buildActionPanel());

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(Color.WHITE);
        center.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        center.add(buildCardsPanel(), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setBackground(Color.WHITE);
        bottom.add(new JSeparator(SwingConstants.HORIZONTAL), BorderLayout.NORTH);
        bottom.add(buildConsolePanel(), BorderLayout.CENTER);

        root.add(top, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("IP"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(ipField, gbc);

        connectButton.setBackground(BLUE_BUTTON);
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.ipadx = 20;
        panel.add(connectButton, gbc);
        gbc.ipadx = 0;

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Port"), gbc);

        portField.setPreferredSize(new Dimension(90, 28));
        nameField.setPreferredSize(new Dimension(220, 28));

        JPanel secondRowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        secondRowPanel.setBackground(Color.WHITE);
        secondRowPanel.add(portField);
        secondRowPanel.add(new JLabel("Nombre"));
        secondRowPanel.add(nameField);

        ButtonGroup group = new ButtonGroup();
        group.add(tcpRadio);
        group.add(udpRadio);
        secondRowPanel.add(tcpRadio);
        secondRowPanel.add(udpRadio);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1;
        gbc.gridwidth = 2;
        panel.add(secondRowPanel, gbc);

        return panel;
    }

    private JPanel buildActionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);

        actionCombo.setBackground(Color.WHITE);
        actionCombo.setBorder(BorderFactory.createLineBorder(new Color(164, 207, 255), 1));

        sendButton.setBackground(GREEN_BUTTON);
        // sendButton.setPreferredSize(new Dimension(140, 40));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.weightx = 1;
        panel.add(actionCombo, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        panel.add(sendButton, gbc);

        return panel;
    }

    private JPanel buildCardsPanel() {
        dataCardsPanel.setBackground(Color.WHITE);
        dataCardsPanel.add(buildListClientsCard(), ClientAction.LIST_CLIENTS.name());
        dataCardsPanel.add(buildListServersCard(), ClientAction.LIST_SERVERS.name());
        dataCardsPanel.add(buildListDocumentsCard(), ClientAction.LIST_DOCUMENTS.name());
        dataCardsPanel.add(buildSendMessageCard(), ClientAction.SEND_MESSAGE.name());
        dataCardsPanel.add(buildSendFileCard(), ClientAction.SEND_FILE.name());
        dataCardsPanel.add(buildReceiveMessageCard(), ClientAction.RECEIVE_MESSAGE.name());
        dataCardsPanel.add(buildReceiveFileCard(), ClientAction.RECEIVE_FILE.name());
        dataCardsPanel.add(buildReceiveLogsCard(), ClientAction.RECEIVE_LOGS.name());
        dataCardsPanel.add(buildListFaceImagesCard(), ClientAction.LIST_FACE_IMAGES.name());
        dataCardsPanel.add(buildIdentifyEmotionsCard(), ClientAction.IDENTIFY_EMOTIONS.name());
        return dataCardsPanel;
    }

    private JPanel buildListClientsCard() {
        JPanel panel = createDataCard();
        panel.add(createSectionTitle("Datos"), BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(clientsTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(208, 208, 208), 1));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildListServersCard() {
        JPanel panel = createDataCard();
        panel.add(createSectionTitle("Datos"), BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(serversTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(208, 208, 208), 1));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildListDocumentsCard() {
        JPanel panel = createDataCard();
        panel.add(createSectionTitle("Datos"), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setBackground(Color.WHITE);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel archivosTitle = createSubSectionTitle("Archivos");
        archivosTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        archivosTitle.setHorizontalAlignment(SwingConstants.LEFT);
        content.add(archivosTitle);
        JScrollPane filesScroll = new JScrollPane(filesTable);
        filesScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        filesScroll.setPreferredSize(new Dimension(300, 140));
        filesScroll.setBorder(BorderFactory.createLineBorder(new Color(208, 208, 208), 1));
        content.add(filesScroll);
        content.add(Box.createVerticalStrut(10));

        JLabel mensajesTitle = createSubSectionTitle("Mensajes");
        mensajesTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        mensajesTitle.setHorizontalAlignment(SwingConstants.LEFT);
        content.add(mensajesTitle);
        JScrollPane messagesScroll = new JScrollPane(messagesTable);
        messagesScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        messagesScroll.setPreferredSize(new Dimension(300, 120));
        messagesScroll.setBorder(BorderFactory.createLineBorder(new Color(208, 208, 208), 1));
        content.add(messagesScroll);

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSendMessageCard() {
        JPanel panel = createDataCard();
        panel.add(createSectionTitle("Datos"), BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        fields.add(new JLabel("Destinatario:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        destinatarioMsgField.setToolTipText("Dejar vacio para enviar a todos");
        fields.add(destinatarioMsgField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        fields.add(new JLabel("Asunto:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        fields.add(asuntoMsgField, gbc);

        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBackground(Color.WHITE);
        content.add(fields, BorderLayout.NORTH);

        sendMessageArea.setLineWrap(true);
        sendMessageArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(sendMessageArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(208, 208, 208), 1));
        content.add(scrollPane, BorderLayout.CENTER);

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSendFileCard() {
        JPanel panel = createDataCard();
        panel.add(createSectionTitle("Datos"), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setBackground(Color.WHITE);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel destRow = new JPanel(new GridBagLayout());
        destRow.setBackground(Color.WHITE);
        destRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        destRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        destRow.add(new JLabel("Destinatario:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        destinatarioFileField.setToolTipText("Dejar vacio para enviar a todos");
        destRow.add(destinatarioFileField, gbc);
        content.add(destRow);
        content.add(Box.createVerticalStrut(6));

        styleBrowseButton(browseFilesButton);
        JPanel browseRow = new JPanel(new BorderLayout());
        browseRow.setBackground(Color.WHITE);
        browseRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        browseRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, browseFilesButton.getPreferredSize().height));
        browseRow.add(browseFilesButton, BorderLayout.CENTER);
        content.add(browseRow);
        content.add(Box.createVerticalStrut(10));

        JLabel filesTitle = createSectionTitle("Archivos");
        filesTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        filesTitle.setHorizontalAlignment(SwingConstants.LEFT);
        content.add(filesTitle);
        content.add(Box.createVerticalStrut(4));

        filesList.setModel(filesListModel);
        filesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        filesList.setCellRenderer(new FilesListRenderer());
        JScrollPane scrollPane = new JScrollPane(filesList);
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(208, 208, 208), 1));
        scrollPane.setPreferredSize(new Dimension(300, 170));
        scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        content.add(scrollPane);

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildReceiveMessageCard() {
        JPanel panel = createDataCard();
        panel.add(createSectionTitle("Datos"), BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBackground(Color.WHITE);

        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        row.add(new JLabel("ID del mensaje"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        row.add(receiveMessageIdField, gbc);

        content.add(row, BorderLayout.NORTH);

        receivedMessageArea.setEditable(false);
        receivedMessageArea.setLineWrap(true);
        receivedMessageArea.setWrapStyleWord(true);
        JScrollPane messageScroll = new JScrollPane(receivedMessageArea);
        messageScroll.setBorder(BorderFactory.createLineBorder(new Color(208, 208, 208), 1));
        content.add(messageScroll, BorderLayout.CENTER);

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildReceiveFileCard() {
        JPanel panel = createDataCard();
        panel.add(createSectionTitle("Datos"), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setBackground(Color.WHITE);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel row1 = new JPanel(new GridBagLayout());
        row1.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;

        gbc.gridx = 0;
        gbc.weightx = 0;
        row1.add(new JLabel("ID del archivo"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.3;
        row1.add(receiveFileIdField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        row1.add(hashCheckBox, gbc);

        gbc.gridx = 3;
        row1.add(encryptedCheckBox, gbc);

        JPanel row2 = new JPanel(new GridBagLayout());
        row2.setBackground(Color.WHITE);
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.insets = new Insets(4, 4, 4, 4);
        gbc2.fill = GridBagConstraints.HORIZONTAL;
        gbc2.gridy = 0;

        gbc2.gridx = 0;
        gbc2.weightx = 0;
        row2.add(new JLabel("Guardar en:"), gbc2);

        gbc2.gridx = 1;
        gbc2.weightx = 1;
        row2.add(receiveFilePathField, gbc2);

        styleBrowseButton(browseReceiveFileButton);
        gbc2.gridx = 2;
        gbc2.weightx = 0;
        row2.add(browseReceiveFileButton, gbc2);

        content.add(row1);
        content.add(Box.createVerticalStrut(8));
        content.add(row2);

        panel.add(content, BorderLayout.NORTH);
        return panel;
    }

    private JPanel buildReceiveLogsCard() {
        JPanel panel = createDataCard();
        panel.add(createSectionTitle("Datos"), BorderLayout.NORTH);

        JPanel rows = new JPanel();
        rows.setBackground(Color.WHITE);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));

        JPanel serverRow = new JPanel(new GridBagLayout());
        serverRow.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;
        gbc.gridx = 0; gbc.weightx = 0;
        serverRow.add(new JLabel("ID Servidor:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        serverIdLogsField.setToolTipText("Dejar vacio para logs del servidor local");
        serverRow.add(serverIdLogsField, gbc);

        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(Color.WHITE);
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.insets = new Insets(4, 4, 4, 4);
        gbc2.fill = GridBagConstraints.HORIZONTAL;
        gbc2.gridy = 0;
        gbc2.gridx = 0; gbc2.weightx = 0;
        row.add(new JLabel("Guardar en:"), gbc2);
        gbc2.gridx = 1; gbc2.weightx = 1;
        row.add(logsPathField, gbc2);
        styleBrowseButton(browseLogsButton);
        gbc2.gridx = 2; gbc2.weightx = 0;
        row.add(browseLogsButton, gbc2);

        rows.add(serverRow);
        rows.add(Box.createVerticalStrut(4));
        rows.add(row);
        panel.add(rows, BorderLayout.NORTH);
        return panel;
    }

    private JPanel buildListFaceImagesCard() {
        JPanel panel = createDataCard();
        panel.add(createSectionTitle("Datos"), BorderLayout.NORTH);

        faceImagesTable.setRowHeight(96);
        faceImagesTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        faceImagesTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        faceImagesTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        faceImagesTable.getColumnModel().getColumn(3).setPreferredWidth(220);
        faceImagesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(faceImagesTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(208, 208, 208), 1));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildIdentifyEmotionsCard() {
        JPanel panel = createDataCard();

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBackground(Color.WHITE);

        JPanel mainContent = new JPanel();
        mainContent.setBackground(Color.WHITE);
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));

        JPanel processRow = new JPanel(new GridBagLayout());
        processRow.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;

        emotionImagePanel.setPreferredSize(new Dimension(430, 225));
        gbc.gridx = 0;
        processRow.add(emotionImagePanel, gbc);

        ProcessTriangleButton processButton = new ProcessTriangleButton();
        processButton.addActionListener(event -> processEmotionCrop());
        gbc.gridx = 1;
        gbc.insets = new Insets(4, 14, 4, 14);
        processRow.add(processButton, gbc);

        emotionResultPanel.setPreferredSize(new Dimension(200, 200));
        gbc.gridx = 2;
        gbc.insets = new Insets(4, 4, 4, 4);
        processRow.add(emotionResultPanel, gbc);

        JPanel processWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        processWrapper.setBackground(Color.WHITE);
        processWrapper.setAlignmentX(Component.CENTER_ALIGNMENT);
        processWrapper.add(processRow);

        changeEmotionImageButton.setBackground(new Color(108, 117, 125)); // Un gris oscuro/azulado
        changeEmotionImageButton.setPreferredSize(new Dimension(160, 32));
        changeEmotionImageButton.setFocusPainted(false);
        changeEmotionImageButton.setVisible(false);
        JPanel changeImageRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        changeImageRow.setBackground(Color.WHITE);
        changeImageRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        changeImageRow.add(changeEmotionImageButton);

        JPanel cardsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        cardsRow.setBackground(Color.WHITE);
        cardsRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        cardsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
        if (emotionCards.isEmpty()) {
            for (String emotion : List.of("Angry", "Disgust", "Fear", "Happy", "Sad", "Surprise", "Neutral")) {
                EmotionCard card = new EmotionCard(emotion);
                emotionCards.add(card);
                cardsRow.add(card);
            }
        }

        emotionHintLabel.setFont(emotionHintLabel.getFont().deriveFont(Font.ITALIC, 13f));
        emotionHintLabel.setForeground(new Color(100, 100, 100));
        emotionHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        emotionHintLabel.setHorizontalAlignment(SwingConstants.LEFT);
        emotionHintLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE,
            emotionHintLabel.getPreferredSize().height));

        emotionImageIdField.setForeground(new Color(255, 90, 90));
        emotionImageIdField.setToolTipText("Si esta vacio, se usara la imagen agregada arriba");
        emotionImageIdField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        emotionImageIdField.setAlignmentX(Component.LEFT_ALIGNMENT);
        emotionImageIdField.setHorizontalAlignment(SwingConstants.LEFT);

        JPanel idSection = new JPanel();
        idSection.setBackground(Color.WHITE);
        idSection.setLayout(new BoxLayout(idSection, BoxLayout.Y_AXIS));
        idSection.add(emotionHintLabel);
        idSection.add(Box.createVerticalStrut(4));
        idSection.add(emotionImageIdField);

        mainContent.add(processWrapper);
        mainContent.add(Box.createVerticalStrut(6));
        mainContent.add(changeImageRow);
        mainContent.add(Box.createVerticalStrut(8));
        mainContent.add(cardsRow);

        content.add(mainContent, BorderLayout.CENTER);
        content.add(idSection, BorderLayout.SOUTH);

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(Color.WHITE);

        JLabel title = createSectionTitle("Consola");
        panel.add(title, BorderLayout.NORTH);

        consoleArea.setEditable(false);
        consoleArea.setBackground(CONSOLE_BG);
        consoleArea.setBorder(new EmptyBorder(6, 6, 6, 6));
        JScrollPane scrollPane = new JScrollPane(consoleArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(208, 208, 208), 1));
        scrollPane.setPreferredSize(new Dimension(100, 150));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createDataCard() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        return panel;
    }

    private JLabel createSectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        return label;
    }

    private JLabel createSubSectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        label.setForeground(new Color(130, 130, 130));
        return label;
    }

    private JTable createStyledTable(DefaultTableModel tableModel) {
        JTable table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component component = super.prepareRenderer(renderer, row, column);
                if (isRowSelected(row)) {
                    component.setBackground(new Color(199, 229, 255));
                } else {
                    component.setBackground(row % 2 == 0 ? Color.WHITE : TABLE_ALT_ROW);
                }
                component.setForeground(Color.BLACK);
                return component;
            }
        };

        JTableHeader header = table.getTableHeader();
        header.setBackground(TABLE_HEADER);
        header.setReorderingAllowed(false);
        table.setRowHeight(24);
        table.setForeground(Color.BLACK);
        table.setSelectionForeground(Color.BLACK);
        return table;
    }

    private void styleBrowseButton(JButton button) {
        button.setBackground(new Color(236, 236, 236));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1));
    }

    private void loadDefaultFileSamples() {
        filesListModel.addElement(new DisplayFileItem("Pelicula.mp4", 1456.0));
        filesListModel.addElement(new DisplayFileItem("cancion.mp3", 1.2));
    }

    public void addConnectListener(java.awt.event.ActionListener listener) {
        connectButton.addActionListener(listener);
    }

    public void addSendListener(java.awt.event.ActionListener listener) {
        sendButton.addActionListener(listener);
    }

    public void addActionSelectionListener(java.awt.event.ActionListener listener) {
        actionCombo.addActionListener(listener);
    }

    public void addBrowseFilesListener(java.awt.event.ActionListener listener) {
        browseFilesButton.addActionListener(listener);
    }

    public void addBrowseReceiveFilePathListener(java.awt.event.ActionListener listener) {
        browseReceiveFileButton.addActionListener(listener);
    }

    public void addBrowseLogsPathListener(java.awt.event.ActionListener listener) {
        browseLogsButton.addActionListener(listener);
    }

    public void addBrowseEmotionImageListener(java.awt.event.ActionListener listener) {
        emotionImagePanel.setImageClickListener(listener);
        changeEmotionImageButton.addActionListener(listener);
    }

    public String getIp() {
        return ipField.getText().trim();
    }

    public String getPortText() {
        return portField.getText().trim();
    }

    public String getClientName() {
        return nameField.getText().trim();
    }

    public boolean isTcpSelected() {
        return tcpRadio.isSelected();
    }

    public ClientAction getSelectedAction() {
        ClientAction action = (ClientAction) actionCombo.getSelectedItem();
        return action == null ? ClientAction.LIST_CLIENTS : action;
    }

    public String getSendMessageText() {
        return sendMessageArea.getText();
    }

    public int getReceiveMessageId() {
        return Integer.parseInt(receiveMessageIdField.getText().trim());
    }

    public int getReceiveFileId() {
        return Integer.parseInt(receiveFileIdField.getText().trim());
    }

    public String getReceiveFilePath() {
        return receiveFilePathField.getText().trim();
    }

    public void setReceiveFilePath(String path) {
        receiveFilePathField.setText(path);
    }

    public String getLogsPath() {
        return logsPathField.getText().trim();
    }

    public void setLogsPath(String path) {
        logsPathField.setText(path);
    }

    public boolean isHashEnabled() {
        return hashCheckBox.isSelected();
    }

    public boolean isEncryptedEnabled() {
        return encryptedCheckBox.isSelected();
    }

    public void setClients(List<ClientInfo> clients) {
        clientsTableModel.setRowCount(0);
        for (ClientInfo client : clients) {
            clientsTableModel.addRow(new Object[]{client.id(), client.nombre(), client.ip(), client.servidor()});
        }
    }

    public void setServers(List<ServerInfo> servers) {
        serversTableModel.setRowCount(0);
        for (ServerInfo s : servers) {
            String estado = s.conectado() ? "Conectado" : "Desconectado";
            String clientes = s.clientes() != null ? String.join(", ", s.clientes()) : "";
            serversTableModel.addRow(new Object[]{s.id(), s.nombre(), s.host() + ":" + s.puerto(), estado, clientes});
        }
    }

    public void setDocuments(DocumentCatalog catalog) {
        filesTableModel.setRowCount(0);
        for (FileDocument document : catalog.archivos()) {
            filesTableModel.addRow(new Object[]{
                document.id(),
                document.nombre(),
                document.extension(),
                document.tamanoMb(),
                document.propietario()
            });
        }

        messagesTableModel.setRowCount(0);
        for (MessageDocument message : catalog.mensajes()) {
            messagesTableModel.addRow(new Object[]{message.id(), message.contenido()});
        }
    }

    public void setFaceImages(List<FaceImage> images) {
        faceImagesTableModel.setRowCount(0);
        for (FaceImage image : images) {
            faceImagesTableModel.addRow(new Object[]{
                    image.id(),
                    image.nombre(),
                    "%.2f".formatted(image.tamanoMb()),
                    createFaceThumbnail(image.image())
            });
        }
    }

    public void setSelectedFiles(List<File> files) {
        selectedFiles.clear();
        selectedFiles.addAll(files);
        filesListModel.clear();
        for (File file : files) {
            filesListModel.addElement(new DisplayFileItem(file.getName(), FilesUtils.toMb(file.length())));
        }
    }

    public List<File> getSelectedFiles() {
        return List.copyOf(selectedFiles);
    }

    public void setReceivedMessage(String content) {
        receivedMessageArea.setText(content);
    }

    public void showActionCard(ClientAction action) {
        dataCards.show(dataCardsPanel, action.name());
    }

    public void clearDataForAction(ClientAction action) {
        switch (action) {
            case LIST_CLIENTS -> clientsTableModel.setRowCount(0);
            case LIST_SERVERS -> serversTableModel.setRowCount(0);
            case LIST_DOCUMENTS -> {
                filesTableModel.setRowCount(0);
                messagesTableModel.setRowCount(0);
            }
            case LIST_FACE_IMAGES -> faceImagesTableModel.setRowCount(0);
            case SEND_MESSAGE -> sendMessageArea.setText("");
            case SEND_FILE -> {
                selectedFiles.clear();
                filesListModel.clear();
            }
            case RECEIVE_MESSAGE -> setReceivedMessage("");
            case IDENTIFY_EMOTIONS -> {
                processedEmotionImage = null;
                emotionResultPanel.setImage(null);
                resetEmotionCards();
            }
            case RECEIVE_FILE, RECEIVE_LOGS -> {
                // No data output to clear for these options.
            }
        }
    }

    public void appendConsole(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (!consoleArea.getText().isBlank()) {
            consoleArea.append(System.lineSeparator());
        }
        consoleArea.append(message);
    }

    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "Informacion", JOptionPane.INFORMATION_MESSAGE);
    }

    public String getDestinatarioMsg() { return destinatarioMsgField.getText().trim(); }
    public String getAsuntoMsg() { return asuntoMsgField.getText().trim(); }
    public String getDestinatarioFile() { return destinatarioFileField.getText().trim(); }
    public String getServerIdLogs() { return serverIdLogsField.getText().trim(); }
    public String getEmotionImageId() { return emotionImageIdField.getText().trim(); }
    public BufferedImage getProcessedEmotionImage() { return processedEmotionImage; }
    public File getSelectedEmotionImageFile() { return selectedEmotionImageFile; }

    public void chooseEmotionImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Imagenes", "jpg", "jpeg", "png", "bmp", "gif", "webp"));
        int selected = chooser.showOpenDialog(this);
        if (selected == JFileChooser.APPROVE_OPTION) {
            loadEmotionImage(chooser.getSelectedFile());
        }
    }

    public void loadEmotionImage(File file) {
        if (file == null) {
            return;
        }
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                showError("El archivo seleccionado no es una imagen valida");
                return;
            }
            selectedEmotionImageFile = file;
            processedEmotionImage = null;
            emotionImagePanel.setImage(image);
            emotionResultPanel.setImage(null);
            changeEmotionImageButton.setVisible(true);
            changeEmotionImageButton.getParent().revalidate();
            changeEmotionImageButton.getParent().repaint();
            resetEmotionCards();
            appendConsole("Imagen agregada: " + file.getName());
        } catch (IOException ex) {
            showError("No se pudo cargar la imagen: " + ex.getMessage());
        }
    }

    public void processEmotionCrop() {
        BufferedImage processed = emotionImagePanel.createProcessedCrop();
        if (processed == null) {
            showError("Agregue una imagen antes de procesar el recorte");
            return;
        }
        processedEmotionImage = processed;
        emotionResultPanel.setImage(processed);
        appendConsole("Recorte procesado en 48x48 blanco y negro");
    }

    public void setEmotionResult(EmotionResult result) {
        double threshold = 0.05;
        for (int i = 0; i < emotionCards.size(); i++) {
            double probability = result.probabilities()[i];
            emotionCards.get(i).setValue(
                    probability,
                    i == result.winnerIndex(),
                    probability >= threshold && i != result.winnerIndex()
            );
        }
    }

    public void resetEmotionCards() {
        for (EmotionCard card : emotionCards) {
            card.reset();
        }
    }

    private static ImageIcon createFaceThumbnail(BufferedImage image) {
        if (image == null) {
            return null;
        }
        int maxSize = 80;
        double scale = Math.min(maxSize / (double) image.getWidth(), maxSize / (double) image.getHeight());
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private static final class EmotionImagePanel extends JPanel {
        private static final Color PANEL_BG = new Color(255, 248, 209);
        private static final Color PANEL_BORDER = new Color(217, 183, 72);
        private static final Color CROP_COLOR = new Color(180, 75, 75);
        private BufferedImage originalImage;
        private Image displayImage;
        private double scale = 1.0;
        private int offsetX;
        private int offsetY;
        private int displayW;
        private int displayH;
        private int cropX = 40;
        private int cropY = 30;
        private int cropSize = 95;
        private Point dragStart;
        private boolean resizing;
        private java.awt.event.ActionListener imageClickListener;

        private EmotionImagePanel() {
            setBackground(PANEL_BG);
            setBorder(BorderFactory.createLineBorder(PANEL_BORDER, 1));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (originalImage == null && imageClickListener != null) {
                        imageClickListener.actionPerformed(new ActionEvent(
                                EmotionImagePanel.this,
                                ActionEvent.ACTION_PERFORMED,
                                "browseEmotionImage"
                        ));
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (originalImage == null) return;
                    int x = e.getX() - offsetX;
                    int y = e.getY() - offsetY;
                    resizing = Math.abs(x - (cropX + cropSize)) <= 12
                            && Math.abs(y - (cropY + cropSize)) <= 12;
                    if (resizing || insideCrop(x, y)) {
                        dragStart = new Point(x, y);
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragStart == null || originalImage == null) return;
                    int x = e.getX() - offsetX;
                    int y = e.getY() - offsetY;
                    if (resizing) {
                        int size = Math.max(40, Math.max(x - cropX, y - cropY));
                        cropSize = Math.min(size, Math.min(displayW - cropX, displayH - cropY));
                    } else {
                        cropX = clamp(cropX + x - dragStart.x, 0, displayW - cropSize);
                        cropY = clamp(cropY + y - dragStart.y, 0, displayH - cropSize);
                        dragStart = new Point(x, y);
                    }
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragStart = null;
                    resizing = false;
                }
            };
            addMouseListener(adapter);
            addMouseMotionListener(adapter);
        }

        private void setImage(BufferedImage image) {
            originalImage = image;
            fitImage();
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            repaint();
        }

        private void setImageClickListener(java.awt.event.ActionListener listener) {
            this.imageClickListener = listener;
        }

        private void fitImage() {
            if (originalImage == null) return;
            int panelW = Math.max(1, getPreferredSize().width);
            int panelH = Math.max(1, getPreferredSize().height);
            scale = Math.min(panelW / (double) originalImage.getWidth(), panelH / (double) originalImage.getHeight());
            displayW = Math.max(1, (int) Math.round(originalImage.getWidth() * scale));
            displayH = Math.max(1, (int) Math.round(originalImage.getHeight() * scale));
            offsetX = (panelW - displayW) / 2;
            offsetY = (panelH - displayH) / 2;
            displayImage = originalImage.getScaledInstance(displayW, displayH, Image.SCALE_SMOOTH);
            cropSize = Math.min(95, Math.min(displayW, displayH));
            cropX = Math.max(0, Math.min(40, displayW - cropSize));
            cropY = Math.max(0, Math.min(30, displayH - cropSize));
        }

        private BufferedImage createProcessedCrop() {
            if (originalImage == null) {
                return null;
            }
            double inverse = 1.0 / scale;
            int x = clamp((int) Math.round(cropX * inverse), 0, originalImage.getWidth() - 1);
            int y = clamp((int) Math.round(cropY * inverse), 0, originalImage.getHeight() - 1);
            int size = clamp((int) Math.round(cropSize * inverse), 1,
                    Math.min(originalImage.getWidth() - x, originalImage.getHeight() - y));
            BufferedImage crop = originalImage.getSubimage(x, y, size, size);
            BufferedImage gray48 = new BufferedImage(48, 48, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g2 = gray48.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(crop, 0, 0, 48, 48, null);
            g2.dispose();
            return gray48;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (originalImage == null) {
                g2.setColor(new Color(90, 90, 90));
                g2.setFont(getFont().deriveFont(Font.BOLD, 22f));
                drawCentered(g2, "+", getWidth() / 2, getHeight() / 2 - 20);
                g2.setFont(getFont().deriveFont(13f));
                drawCentered(g2, "Agregar imagen", getWidth() / 2, getHeight() / 2 + 22);
                g2.dispose();
                return;
            }
            g2.drawImage(displayImage, offsetX, offsetY, null);
            int x = offsetX + cropX;
            int y = offsetY + cropY;
            g2.setColor(CROP_COLOR);
            g2.setStroke(new BasicStroke(3));
            g2.drawRect(x, y, cropSize, cropSize);
            g2.setFont(getFont().deriveFont(11f));
            g2.drawString("", x + 10, y + 24);
            g2.drawString("", x + 10, y + 40);
            g2.fillOval(x + cropSize - 7, y + cropSize - 7, 14, 14);
            g2.dispose();
        }

        private boolean insideCrop(int x, int y) {
            return x >= cropX && x <= cropX + cropSize && y >= cropY && y <= cropY + cropSize;
        }
    }

    private static final class EmotionResultPanel extends JPanel {
        private BufferedImage image;

        private EmotionResultPanel() {
            setBackground(new Color(228, 216, 235));
            setBorder(BorderFactory.createLineBorder(new Color(150, 120, 165), 1));
        }

        private void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            if (image == null) {
                g2.setColor(Color.BLACK);
                drawCentered(g2, "Resultado", getWidth() / 2, getHeight() / 2 - 8);
                drawCentered(g2, "48x48 y blanco y negro", getWidth() / 2, getHeight() / 2 + 14);
            } else {
                g2.drawImage(image.getScaledInstance(getWidth() - 32, getHeight() - 40, Image.SCALE_FAST),
                        16, 14, null);
                g2.setColor(new Color(70, 70, 70));
                drawCentered(g2, "Resultado 48x48", getWidth() / 2, getHeight() - 10);
            }
            g2.dispose();
        }
    }

    private static final class ProcessTriangleButton extends JButton {
        private ProcessTriangleButton() {
            super("Procesar");
            setPreferredSize(new Dimension(105, 120));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int[] xs = {8, getWidth() - 10, 8};
            int[] ys = {8, getHeight() / 2, getHeight() - 8};
            g2.setColor(new Color(218, 232, 252));
            g2.fillPolygon(xs, ys, 3);
            g2.setColor(new Color(113, 146, 190));
            g2.setStroke(new BasicStroke(4));
            g2.drawPolygon(xs, ys, 3);
            g2.setColor(Color.BLACK);
            drawCentered(g2, getText(), getWidth() / 2 - 8, getHeight() / 2 + 4);
            g2.dispose();
        }
    }

    private static final class EmotionCard extends JPanel {
        private final String emotion;
        private double probability;
        private boolean winner;
        private boolean notable;

        private EmotionCard(String emotion) {
            this.emotion = emotion;
            setPreferredSize(new Dimension(88, 72));
            setOpaque(false);
        }

        private void setValue(double probability, boolean winner, boolean notable) {
            this.probability = probability;
            this.winner = winner;
            this.notable = notable;
            repaint();
        }

        private void reset() {
            probability = 0;
            winner = false;
            notable = false;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = winner ? new Color(0, 150, 0)
                    : notable ? new Color(145, 145, 145)
                    : new Color(226, 226, 226);
            Color fg = winner || notable ? Color.WHITE : new Color(60, 60, 60);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.setColor(fg);
            drawCentered(g2, emotion, getWidth() / 2, getHeight() / 2 - 8);
            String pct = probability > 0 ? "%.2f%%".formatted(probability * 100) : "0.00%";
            drawCentered(g2, pct, getWidth() / 2, getHeight() / 2 + 13);
            g2.dispose();
        }
    }

    private static void drawCentered(Graphics2D g2, String text, int centerX, int baselineY) {
        java.awt.FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, centerX - fm.stringWidth(text) / 2, baselineY);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ReadOnlyTableModel extends DefaultTableModel {
        private final Class<?>[] columnClasses;

        private ReadOnlyTableModel(String[] columnNames) {
            super(columnNames, 0);
            this.columnClasses = null;
        }

        private ReadOnlyTableModel(String[] columnNames, Class<?>[] columnClasses) {
            super(columnNames, 0);
            this.columnClasses = columnClasses;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnClasses != null && columnIndex >= 0 && columnIndex < columnClasses.length) {
                return columnClasses[columnIndex];
            }
            return Object.class;
        }
    }

    private record DisplayFileItem(String name, double sizeMb) {
    }

    private static final class FilesListRenderer extends JPanel implements ListCellRenderer<DisplayFileItem> {
        private final JLabel nameLabel = new JLabel();
        private final JLabel sizeLabel = new JLabel();

        private FilesListRenderer() {
            setLayout(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(4, 6, 4, 6));
            nameLabel.setIcon(UIManager.getIcon("FileView.fileIcon"));
            sizeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            add(nameLabel, BorderLayout.WEST);
            add(sizeLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends DisplayFileItem> list,
                DisplayFileItem value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            nameLabel.setText(value.name());
            sizeLabel.setText("%.2f mb".formatted(value.sizeMb()));

            if (isSelected) {
                setBackground(new Color(199, 229, 255));
            } else {
                setBackground(index % 2 == 0 ? Color.WHITE : TABLE_ALT_ROW);
            }
            return this;
        }
    }

    public void showLoading(String message) {
        loadingOverlay.setMessage(message != null ? message : "Procesando...");
        loadingOverlay.setVisible(true);
        if (spinnerTimer == null) {
            spinnerTimer = new Timer(50, e -> loadingOverlay.repaint());
            spinnerTimer.start();
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    }

    public void hideLoading() {
        loadingOverlay.setVisible(false);
        if (spinnerTimer != null) {
            spinnerTimer.stop();
            spinnerTimer = null;
        }
        setCursor(Cursor.getDefaultCursor());
    }

    public void updateLoadingMessage(String message) {
        loadingOverlay.setMessage(message);
        loadingOverlay.repaint();
    }

    private static final class LoadingOverlay extends JPanel {
        private String message = "Procesando...";
        private int angle = 0;

        private LoadingOverlay() {
            setOpaque(false);
            setVisible(false);
        }

        public void setMessage(String msg) {
            this.message = msg;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();

            // Fondo semi-transparente
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, getWidth(), getHeight());

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int centerX = getWidth() / 2;
            int centerY = getHeight() / 2;

            // Spinner
            int size = 40;
            int strokeWidth = 4;
            angle = (angle + 10) % 360;

            g2d.setColor(new Color(200, 200, 200));
            g2d.setStroke(new java.awt.BasicStroke(strokeWidth, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g2d.drawOval(centerX - size/2, centerY - size/2 - 20, size, size);

            g2d.setColor(BLUE_BUTTON);
            g2d.draw(new Arc2D.Double(centerX - size/2, centerY - size/2 - 20, size, size, angle, 90, Arc2D.OPEN));

            // Mensaje
            g2d.setColor(new Color(60, 60, 60));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
            java.awt.FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(message);
            g2d.drawString(message, centerX - textWidth/2, centerY + 40);

            g2d.dispose();
        }
    }
}
