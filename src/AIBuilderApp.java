/*
  AIBuilderApp.java
  Enhanced version with improved math problem solving like Gemini/Canvas
  - Better step-by-step math solutions with clean formatting
  - Enhanced UI for math display
  - Improved response cleaning and formatting
*/

import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class AIBuilderApp {
    // API Configuration
    private static final String OPENROUTER_API_KEY = "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";
    private static final String MODEL_NAME = "nvidia/nemotron-nano-9b-v2:free";
    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

    // Data files
    private static final File AI_DATA_FILE = new File("ai_data.json");
    private static final File BANNED_AI_FILE = new File("banned_ai.json");

    // DSA structures
    private final HashMap<String, AIProfile> profiles = new HashMap<>();
    private final Set<String> bannedAIs = new HashSet<>();
    private final Queue<String> promptQueue = new LinkedBlockingQueue<>();
    private final Stack<String> chatStack = new Stack<>();

    // Current session
    private AIProfile currentProfile = null;
    private List<ChatMessage> currentChatHistory = new ArrayList<>();

    // UI components
    private JFrame mainFrame;
    private JPanel chatPanel;
    private JScrollPane chatScroll;
    private JTextField inputField;
    private JButton sendButton;

    public AIBuilderApp() {
        currentChatHistory = new ArrayList<>();
    }

    private String cleanResponse(String response) {
        if (response == null)
            return "";

        // Remove markdown code blocks but preserve HTML formatting
        response = response.replaceAll("(?s)```.*?```", "");
        response = response.replaceAll("`", "");

        // Remove LaTeX and special math symbols that might break HTML
        response = response.replaceAll("\\$+", "");
        response = response.replaceAll("\\\\\\(", "(");
        response = response.replaceAll("\\\\\\)", ")");
        response = response.replaceAll("\\\\\\[", "[");
        response = response.replaceAll("\\\\\\]", "]");

        // Clean up excessive whitespace but preserve intentional line breaks
        response = response.replaceAll("\\n{3,}", "\n\n");
        response = response.replaceAll("\\s+", " ");
        response = response.replaceAll("\\n \\n", "\n\n");

        // Ensure proper HTML structure for math responses
        if (response.contains("Step") && response.contains("Final Answer")) {
            response = response.replaceAll("(?i)Step\\s*(\\d+)", "<b>Step $1:</b>");
            response = response.replaceAll("(?i)Final Answer:", "<br><br><b>Final Answer:</b>");

            // Wrap math solutions in a clean container
            if (!response.contains("<div") && (response.contains("<b>Step") || response.contains("<b>Interpreted"))) {
                response = "<div style='background-color: #f8f9fa; border-left: 4px solid #4285f4; padding: 12px; margin: 8px 0; border-radius: 4px;'>"
                        +
                        response + "</div>";
            }
        }

        return response.trim();
    }

    private boolean isMathAI(String description) {
        if (description == null)
            return false;
        String lowerDesc = description.toLowerCase();
        return lowerDesc.contains("math") ||
                lowerDesc.contains("calculus") ||
                lowerDesc.contains("algebra") ||
                lowerDesc.contains("trigonometry") ||
                lowerDesc.contains("geometry") ||
                lowerDesc.contains("equation") ||
                lowerDesc.contains("solve") ||
                lowerDesc.contains("problem") ||
                lowerDesc.contains("mathematics") ||
                lowerDesc.contains("arithmetic") ||
                lowerDesc.contains("statistics");
    }

    private void createAndShowUI() {
        loadProfiles();
        loadBannedAIs();

        mainFrame = new JFrame("NovaMind AI - Create Focused AI Assistants");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(900, 600);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setLayout(new BorderLayout());

        JPanel mainPanel = buildMainPanel();
        mainFrame.add(mainPanel, BorderLayout.CENTER);

        mainFrame.setVisible(true);
    }

    private JPanel buildMainPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(248, 250, 252));

        // Simple header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(46, 137, 255));
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel title = new JLabel("NovaMind AI - Create Focused AI Assistants");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel(
                "Build AI assistants with specific purposes - They strictly follow their purpose only");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subtitle.setForeground(new Color(220, 235, 255));

        JPanel titlePanel = new JPanel(new BorderLayout(0, 3));
        titlePanel.setOpaque(false);
        titlePanel.add(title, BorderLayout.NORTH);
        titlePanel.add(subtitle, BorderLayout.CENTER);

        headerPanel.add(titlePanel, BorderLayout.WEST);

        // View profiles button
        if (!profiles.isEmpty()) {
            JButton viewProfilesBtn = new JButton("View My AIs (" + profiles.size() + ")");
            styleButton(viewProfilesBtn, false);
            viewProfilesBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
            viewProfilesBtn.addActionListener(e -> showProfileSelection());
            headerPanel.add(viewProfilesBtn, BorderLayout.EAST);
        }

        panel.add(headerPanel, BorderLayout.NORTH);

        // Main content
        JPanel createPanel = buildCreateProfilePanel();
        panel.add(createPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildCreateProfilePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 235, 240), 1),
                new EmptyBorder(25, 30, 25, 30)));

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;

        JLabel formTitle = new JLabel("Create New AI Profile");
        formTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        formTitle.setForeground(new Color(46, 137, 255));
        formTitle.setBorder(new EmptyBorder(0, 0, 15, 0));
        form.add(formTitle, gbc);

        gbc.gridwidth = 1;

        // Creator name
        gbc.gridy++;
        JLabel creatorLabel = new JLabel("Your Name:");
        creatorLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        form.add(creatorLabel, gbc);

        gbc.gridx = 1;
        JTextField creatorField = createTextField();
        creatorField.setPreferredSize(new Dimension(300, 35));
        creatorField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        form.add(creatorField, gbc);

        // AI name
        gbc.gridx = 0;
        gbc.gridy++;
        JLabel nameLabel = new JLabel("AI Name:");
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        form.add(nameLabel, gbc);

        gbc.gridx = 1;
        JTextField nameField = createTextField();
        nameField.setPreferredSize(new Dimension(300, 35));
        nameField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        form.add(nameField, gbc);

        // Description
        gbc.gridx = 0;
        gbc.gridy++;
        JLabel descLabel = new JLabel(
                "<html>AI Purpose:<br><small>Be specific - AI will ONLY answer directly related questions</small></html>");
        descLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        form.add(descLabel, gbc);

        gbc.gridx = 1;
        JTextArea descArea = new JTextArea(5, 35);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        descArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 210, 230)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        // Add compact placeholder text
        descArea.setText(
                "Describe the AI's specific purpose here...\nExample: Answer questions only about Java programming");
        descArea.setForeground(new Color(150, 150, 150));

        descArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (descArea.getText().equals(
                        "Describe the AI's specific purpose here...\nExample: Answer questions only about Java programming")) {
                    descArea.setText("");
                    descArea.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (descArea.getText().isEmpty()) {
                    descArea.setText(
                            "Describe the AI's specific purpose here...\nExample: Answer questions only about Java programming");
                    descArea.setForeground(new Color(150, 150, 150));
                }
            }
        });

        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setPreferredSize(new Dimension(300, 120));
        descScroll.setBorder(BorderFactory.createLineBorder(new Color(200, 210, 230)));
        form.add(descScroll, gbc);

        // Create button
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(20, 10, 10, 10);
        JButton createBtn = new JButton("🚀 Create AI Assistant");
        styleButton(createBtn, true);
        createBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        createBtn.setPreferredSize(new Dimension(250, 45));
        createBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        form.add(createBtn, gbc);

        createBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            String description = descArea.getText().trim();
            String creatorName = creatorField.getText().trim();

            // Check if description is still placeholder
            if (description.equals(
                    "Describe the AI's specific purpose here...\nExample: Answer questions only about Java programming")) {
                description = "";
            }

            if (creatorName.isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "Please enter your name.");
                return;
            }

            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "Please enter an AI name.");
                return;
            }

            if (description.length() < 15) {
                JOptionPane.showMessageDialog(mainFrame,
                        "Please provide a detailed description (at least 15 characters) of the AI's purpose.\n\n" +
                                "The AI will strictly follow this description and reject any questions outside this scope.",
                        "Insufficient Information",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (bannedAIs.contains(name.toLowerCase())) {
                JOptionPane.showMessageDialog(mainFrame,
                        "This AI name has been banned due to poor performance. Please choose a different name.",
                        "Banned AI Name",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            AIProfile profile = new AIProfile(name, description, null, creatorName);
            profiles.put(name, profile);
            saveProfiles();

            // Show appropriate success message based on AI type
            String aiType = isMathAI(description) ? "Math-focused " : "";
            JOptionPane.showMessageDialog(mainFrame,
                    "AI Profile Created Successfully!\n\n" +
                            "Name: " + name + "\n" +
                            "Creator: " + creatorName + "\n" +
                            "Type: " + aiType + "AI Assistant\n\n" +
                            "This AI will STRICTLY focus only on its defined purpose.",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE);

            // Clear form
            creatorField.setText("");
            nameField.setText("");
            descArea.setText(
                    "Describe the AI's specific purpose here...\nExample: Answer questions only about Java programming");
            descArea.setForeground(new Color(150, 150, 150));

            showChat(profile);
        });

        panel.add(form);
        return panel;
    }

    private JTextField createTextField() {
        JTextField field = new JTextField();
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 210, 230)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        return field;
    }

    private JButton styleButton(JButton button, boolean isPrimary) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setForeground(isPrimary ? Color.WHITE : new Color(46, 137, 255));
        button.setBackground(isPrimary ? new Color(46, 137, 255) : Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isPrimary ? new Color(46, 137, 255) : new Color(200, 210, 230), 1),
                BorderFactory.createEmptyBorder(8, 15, 8, 15)));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Subtle hover effect
        button.addMouseListener(new MouseAdapter() {
            Color origBg = button.getBackground();

            public void mouseEntered(MouseEvent e) {
                if (isPrimary) {
                    button.setBackground(origBg.brighter());
                } else {
                    button.setBackground(new Color(250, 252, 255));
                }
            }

            public void mouseExited(MouseEvent e) {
                button.setBackground(origBg);
            }
        });

        return button;
    }

    private void loadProfiles() {
        if (!AI_DATA_FILE.exists())
            return;
        try (FileReader fr = new FileReader(AI_DATA_FILE)) {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = fr.read()) != -1)
                sb.append((char) c);
            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("profiles");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject p = arr.getJSONObject(i);
                    AIProfile profile = AIProfile.fromJson(p);
                    if (!bannedAIs.contains(profile.name.toLowerCase())) {
                        profiles.put(profile.name, profile);
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void loadBannedAIs() {
        if (!BANNED_AI_FILE.exists())
            return;
        try (FileReader fr = new FileReader(BANNED_AI_FILE)) {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = fr.read()) != -1)
                sb.append((char) c);
            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("banned");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    bannedAIs.add(arr.getString(i).toLowerCase());
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void saveBannedAIs() {
        try {
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (String banned : bannedAIs) {
                arr.put(banned);
            }
            root.put("banned", arr);
            try (FileWriter fw = new FileWriter(BANNED_AI_FILE)) {
                fw.write(root.toString(2));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void banAI(String aiName) {
        bannedAIs.add(aiName.toLowerCase());
        profiles.remove(aiName);
        saveBannedAIs();
        saveProfiles();

        JOptionPane.showMessageDialog(mainFrame,
                "AI '" + aiName + "' has been removed from your list.",
                "AI Removed",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void saveProfiles() {
        try {
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (AIProfile p : profiles.values()) {
                if (!bannedAIs.contains(p.name.toLowerCase())) {
                    arr.put(p.toJson());
                }
            }
            root.put("profiles", arr);
            try (FileWriter fw = new FileWriter(AI_DATA_FILE)) {
                fw.write(root.toString(2));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void saveChat(AIProfile p, List<ChatMessage> messages) {
        try {
            JSONArray arr = new JSONArray();
            for (ChatMessage m : messages) {
                JSONObject o = new JSONObject();
                o.put("role", m.role);
                o.put("content", m.content);
                arr.put(o);
            }
            String safeName = p.name.replaceAll("[^a-zA-Z0-9_-]", "_");
            try (FileWriter fw = new FileWriter(safeName + "_chat.json")) {
                fw.write(arr.toString(2));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private List<ChatMessage> loadChat(AIProfile p) {
        String safeName = p.name.replaceAll("[^a-zA-Z0-9_-]", "_");
        File f = new File(safeName + "_chat.json");
        List<ChatMessage> messages = new ArrayList<>();
        if (!f.exists())
            return messages;
        try (FileReader fr = new FileReader(f)) {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = fr.read()) != -1)
                sb.append((char) c);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                messages.add(new ChatMessage(o.getString("role"), o.getString("content")));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return messages;
    }

    private void showProfileSelection() {
        JDialog dialog = new JDialog(mainFrame, "Select AI Assistant", true);
        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(mainFrame);
        dialog.setLayout(new BorderLayout());

        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(46, 137, 255));
        headerPanel.setBorder(new EmptyBorder(12, 15, 12, 15));
        JLabel titleLabel = new JLabel("Select an AI Assistant");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel);
        dialog.add(headerPanel, BorderLayout.NORTH);

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        for (AIProfile profile : profiles.values()) {
            if (bannedAIs.contains(profile.name.toLowerCase())) {
                continue;
            }

            JPanel profileCard = new JPanel(new BorderLayout(8, 5));
            profileCard.setBackground(Color.WHITE);
            profileCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 210, 230), 1),
                    new EmptyBorder(12, 15, 12, 15)));

            JPanel topPanel = new JPanel(new BorderLayout());
            topPanel.setOpaque(false);

            JLabel nameLabel = new JLabel(profile.name);
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

            // Add math badge if it's a math AI
            if (isMathAI(profile.description)) {
                JLabel mathBadge = new JLabel("🧮 Math");
                mathBadge.setFont(new Font("Segoe UI", Font.BOLD, 10));
                mathBadge.setForeground(new Color(46, 137, 255));
                mathBadge.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(46, 137, 255), 1),
                        BorderFactory.createEmptyBorder(2, 6, 2, 6)));
                topPanel.add(mathBadge, BorderLayout.EAST);
            }

            JLabel creatorLabel = new JLabel("by " + profile.creator);
            creatorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            creatorLabel.setForeground(new Color(100, 110, 120));

            JButton removeButton = new JButton("Remove");
            removeButton.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            styleButton(removeButton, false);
            removeButton.addActionListener(e -> {
                int result = JOptionPane.showConfirmDialog(dialog,
                        "Remove this AI? It will be permanently removed from your list.\n" +
                                "Reason: Not following its purpose properly.",
                        "Confirm Removal",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    banAI(profile.name);
                    dialog.dispose();
                    showProfileSelection();
                }
            });

            JPanel infoPanel = new JPanel(new BorderLayout(3, 3));
            infoPanel.setOpaque(false);
            infoPanel.add(nameLabel, BorderLayout.NORTH);
            infoPanel.add(creatorLabel, BorderLayout.CENTER);

            topPanel.add(infoPanel, BorderLayout.WEST);
            topPanel.add(removeButton, BorderLayout.EAST);

            JTextArea descArea = new JTextArea(profile.description);
            descArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            descArea.setLineWrap(true);
            descArea.setWrapStyleWord(true);
            descArea.setEditable(false);
            descArea.setOpaque(false);
            descArea.setBorder(new EmptyBorder(5, 0, 0, 0));

            profileCard.add(topPanel, BorderLayout.NORTH);
            profileCard.add(descArea, BorderLayout.CENTER);

            profileCard.setCursor(new Cursor(Cursor.HAND_CURSOR));
            profileCard.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    dialog.dispose();
                    showChat(profile);
                }
            });

            listPanel.add(profileCard);
            listPanel.add(Box.createVerticalStrut(8));
        }

        JScrollPane scrollPane = new JScrollPane(listPanel);
        dialog.add(scrollPane, BorderLayout.CENTER);

        dialog.setVisible(true);
    }

    private void showChat(AIProfile profile) {
        currentProfile = profile;
        currentChatHistory = loadChat(profile);

        mainFrame.getContentPane().removeAll();
        chatPanel = new JPanel();
        chatPanel.setLayout(new BorderLayout());
        chatPanel.setBackground(new Color(245, 247, 250));

        // AI Info Panel at the top
        JPanel aiInfoPanel = new JPanel(new BorderLayout());
        aiInfoPanel.setBackground(new Color(46, 137, 255));
        aiInfoPanel.setBorder(new EmptyBorder(12, 15, 12, 15));

        JButton backButton = new JButton("← Back");
        backButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        backButton.setForeground(Color.WHITE);
        backButton.setBorderPainted(false);
        backButton.setContentAreaFilled(false);
        backButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        backButton.addActionListener(e -> showProfileSelection());
        aiInfoPanel.add(backButton, BorderLayout.EAST);

        JPanel avatarInfo = new JPanel(new BorderLayout(10, 0));
        avatarInfo.setOpaque(false);

        JLabel avatarLabel = new JLabel(createCircularAvatar(profile.name, 40));
        avatarLabel.setBorder(BorderFactory.createLineBorder(Color.WHITE, 1));

        JPanel infoPanel = new JPanel(new GridLayout(2, 1, 0, 3));
        infoPanel.setOpaque(false);

        JLabel nameLabel = new JLabel(profile.name);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        nameLabel.setForeground(Color.WHITE);

        JLabel purposeLabel = new JLabel(
                "Purpose: " + (profile.description.length() > 60 ? profile.description.substring(0, 60) + "..."
                        : profile.description));
        purposeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        purposeLabel.setForeground(new Color(220, 235, 255));

        infoPanel.add(nameLabel);
        infoPanel.add(purposeLabel);

        avatarInfo.add(avatarLabel, BorderLayout.WEST);
        avatarInfo.add(infoPanel, BorderLayout.CENTER);

        aiInfoPanel.add(avatarInfo, BorderLayout.WEST);

        // Messages panel
        JPanel messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBackground(new Color(245, 247, 250));
        messagesPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Scroll pane for messages
        chatScroll = new JScrollPane(messagesPanel);
        chatScroll.setBorder(null);
        chatScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        chatScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        chatScroll.getViewport().addChangeListener(e -> SwingUtilities.invokeLater(this::updateMessageWidths));

        // Input area
        JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
        inputPanel.setBorder(new EmptyBorder(12, 15, 12, 15));
        inputPanel.setBackground(Color.WHITE);

        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 210, 230)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        sendButton = new JButton("Send");
        styleButton(sendButton, true);
        sendButton.setPreferredSize(new Dimension(80, 35));

        JPanel fieldPanel = new JPanel(new BorderLayout(8, 0));
        fieldPanel.setOpaque(false);
        fieldPanel.add(inputField, BorderLayout.CENTER);
        fieldPanel.add(sendButton, BorderLayout.EAST);

        inputPanel.add(fieldPanel, BorderLayout.CENTER);

        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        chatPanel.add(aiInfoPanel, BorderLayout.NORTH);
        chatPanel.add(chatScroll, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);
        mainFrame.add(chatPanel);
        mainFrame.revalidate();
        mainFrame.repaint();

        // Display existing messages
        for (ChatMessage msg : currentChatHistory) {
            addMessageToUI(msg);
        }
        SwingUtilities.invokeLater(this::updateMessageWidths);
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty())
            return;

        ChatMessage userMsg = new ChatMessage("user", text);
        currentChatHistory.add(userMsg);
        addMessageToUI(userMsg);
        inputField.setText("");

        // Disable send button while processing
        sendButton.setEnabled(false);
        sendButton.setText("Sending...");

        // Special-case: direct 'hi' greeting — respond locally without calling the API
        String lower = text.toLowerCase();
        if (lower.equals("hi") || lower.equals("hi!") || lower.equals("hi.")) {
            String profileDesc = (currentProfile != null && currentProfile.description != null
                    && !currentProfile.description.isBlank())
                            ? currentProfile.description
                            : "my defined area";
            String greet = String.format("Hi! I'm %s. I specialize in %s. How can I help you today?",
                    (currentProfile != null ? currentProfile.name : "this assistant"), profileDesc);
            String clean = cleanResponse(greet);
            ChatMessage aiMsg = new ChatMessage("assistant", clean);
            currentChatHistory.add(aiMsg);
            SwingUtilities.invokeLater(() -> {
                addMessageToUI(aiMsg);
                sendButton.setEnabled(true);
                sendButton.setText("Send");
            });
            saveChat(currentProfile, currentChatHistory);
            return;
        }

        new Thread(() -> {
            try {
                String response = callOpenRouterAPI(currentChatHistory);
                if (response != null && !response.isEmpty()) {
                    ChatMessage aiMsg = new ChatMessage("assistant", response);
                    currentChatHistory.add(aiMsg);
                    SwingUtilities.invokeLater(() -> {
                        addMessageToUI(aiMsg);
                        sendButton.setEnabled(true);
                        sendButton.setText("Send");
                    });
                    saveChat(currentProfile, currentChatHistory);
                } else {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(mainFrame,
                                "Failed to get response from AI. Please try again.",
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                        sendButton.setEnabled(true);
                        sendButton.setText("Send");
                    });
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(mainFrame,
                            "Error communicating with AI service: " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    sendButton.setEnabled(true);
                    sendButton.setText("Send");
                });
            }
        }).start();
    }

    private void addMessageToUI(ChatMessage msg) {
        JPanel view = (JPanel) chatScroll.getViewport().getView();
        if (msg.role.equals("user")) {
            String userText = msg.content.replace("\n", "<br>");
            JLabel userLabel = new JLabel(
                    String.format("<html><div style='padding: 8px;'><b>You:</b> %s</div></html>", userText));
            userLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            userLabel.setForeground(new Color(30, 30, 30));
            userLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            userLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            int vw = Math.max(200, chatScroll.getViewport().getWidth() - 20);
            userLabel.setMaximumSize(new Dimension(vw, Integer.MAX_VALUE));

            view.add(userLabel);
        } else {
            // For AI responses, preserve HTML formatting for math AIs
            String formattedResponse = msg.content;

            JLabel aiLabel = new JLabel(String.format(
                    "<html><div style='padding: 8px;'><b>%s:</b><br><div style='margin-top:4px'>%s</div></div></html>",
                    currentProfile.name, formattedResponse));
            aiLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            aiLabel.setOpaque(false);
            aiLabel.setBorder(BorderFactory.createEmptyBorder(6, 15, 6, 10));
            aiLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            int vw = Math.max(200, chatScroll.getViewport().getWidth() - 30);
            aiLabel.setMaximumSize(new Dimension(vw, Integer.MAX_VALUE));

            JPanel wrapper = new JPanel();
            wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
            wrapper.setOpaque(false);
            wrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
            aiLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            wrapper.add(aiLabel);

            view.add(wrapper);
        }

        JPanel messagesPanel = (JPanel) chatScroll.getViewport().getView();
        messagesPanel.revalidate();
        messagesPanel.repaint();

        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = chatScroll.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private String callOpenRouterAPI(List<ChatMessage> context) {
        try {
            JSONObject body = new JSONObject();
            JSONArray messageList = new JSONArray();

            boolean isMathProfile = isMathAI(currentProfile.description);

            String systemPrompt = buildSystemPrompt(currentProfile.description, isMathProfile);

            messageList.put(new JSONObject().put("role", "system").put("content", systemPrompt));

            int start = Math.max(0, context.size() - 3);
            for (int i = start; i < context.size(); i++) {
                ChatMessage msg = context.get(i);
                messageList.put(new JSONObject().put("role", msg.role).put("content", msg.content));
            }

            body.put("messages", messageList);
            body.put("temperature", 0.3);
            body.put("max_tokens", 1200); // Increased for better step-by-step math solutions
            body.put("top_p", 0.9);

            String[] modelsToTry = { MODEL_NAME, "gpt-3.5-turbo" };
            for (String m : modelsToTry) {
                try {
                    URL url = URI.create(OPENROUTER_URL).toURL();
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Authorization", "Bearer " + OPENROUTER_API_KEY);
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(30000); // 30 seconds
                    connection.setReadTimeout(30000); // 30 seconds
                    body.put("model", m);
                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    int code = connection.getResponseCode();
                    if (code == 200) {
                        try (BufferedReader br = new BufferedReader(
                                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                            StringBuilder response = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) {
                                response.append(line);
                            }
                            JSONObject json = new JSONObject(response.toString());
                            String content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                                    .getString("content");
                            return cleanResponse(content);
                        }
                    } else {
                        StringBuilder errorResponse = new StringBuilder();
                        try (BufferedReader br = new BufferedReader(
                                new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                errorResponse.append(line);
                            }
                        }
                        String err = errorResponse.toString().toLowerCase();
                        if (err.contains("not a valid model") || err.contains("invalid model")
                                || err.contains("model id")) {
                            continue;
                        } else {
                            System.err.println("API Error: " + code + " - " + errorResponse.toString());
                            break;
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "I cannot process that request right now. Please check your internet connection and try again.";
    }

    private String buildSystemPrompt(String description, boolean isMathProfile) {
        if (isMathProfile) {
            return """
                    You are a specialized Math AI assistant. Your purpose is strictly limited to: "%s"

                    MATH-SPECIFIC INSTRUCTIONS:
                    - You MUST solve math problems with clear, step-by-step explanations
                    - Format your responses like Gemini/Canvas with clean, structured layout
                    - For each math problem, follow this exact structure:

                    <div style='background-color: #f8f9fa; border-left: 4px solid #4285f4; padding: 12px; margin: 8px 0; border-radius: 4px;'>
                    <b>🧮 Problem Analysis:</b><br>
                    [Briefly restate and interpret the problem]

                    <b>📝 Solution Steps:</b><br>
                    <b>Step 1:</b> [First step with explanation]<br>
                    <b>Step 2:</b> [Second step with explanation]<br>
                    [Continue steps as needed...]<br><br>

                    <b>✅ Final Answer:</b> [Clear final answer]
                    </div>

                    - Use simple, clean mathematical notation
                    - Explain each step clearly and concisely
                    - Use HTML formatting for structure but avoid complex styling
                    - For multiple problems, label them clearly: <b>Problem 1</b>, <b>Problem 2</b>, etc.
                    - If a question is not math-related, reply exactly: "This question is outside my defined knowledge scope."

                    BEHAVIOR RULES:
                    1. Only answer math-related questions within your defined purpose
                    2. Be deterministic - if unsure about relevance, refuse the question
                    3. Never use general knowledge or assumptions outside your purpose
                    4. Always provide step-by-step solutions for math problems
                    5. Never apologize for refusing unrelated questions
                    """
                    .formatted(description == null ? "" : description);
        } else {
            return """
                    You are a specialized AI created by a user.
                    Your entire knowledge, purpose, and reasoning are strictly limited to the following description:
                    "%s"

                    Behavior Rules:
                    1. You must ONLY answer questions that have a **direct, clear, or logical connection** to the description.
                    2. If a question is **not related, partially related, vague, or outside** the description, reply EXACTLY with:
                       "This question is outside my defined knowledge scope."
                    3. Do NOT use general world knowledge, imagination, or assumptions.
                    4. Do NOT provide opinions, examples, or advice unrelated to the given description.
                    5. Always remain focused on the meaning and purpose of the description.
                    6. Your behavior must be deterministic — if unsure about relevance, refuse with the above message.

                    Answer Format:
                    - If related → provide helpful, focused answers
                    - If unrelated → reply exactly: "This question is outside my defined knowledge scope."
                    - Never apologize or justify refusals.
                    """
                    .formatted(description == null ? "" : description);
        }
    }

    private void updateMessageWidths() {
        if (chatScroll == null)
            return;
        Component view = chatScroll.getViewport().getView();
        if (!(view instanceof JPanel))
            return;
        JPanel panel = (JPanel) view;
        int vw = Math.max(200, chatScroll.getViewport().getWidth() - 20);
        for (Component c : panel.getComponents()) {
            if (c instanceof JLabel) {
                c.setMaximumSize(new Dimension(vw, Integer.MAX_VALUE));
            } else if (c instanceof JPanel) {
                c.setMaximumSize(new Dimension(vw, Integer.MAX_VALUE));
                for (Component sub : ((JPanel) c).getComponents()) {
                    if (sub instanceof JLabel) {
                        sub.setMaximumSize(new Dimension(vw, Integer.MAX_VALUE));
                    }
                }
            }
            c.revalidate();
        }
        panel.revalidate();
        panel.repaint();
    }

    private static class ChatMessage {
        String role;
        String content;

        ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private static class AIProfile {
        String name;
        String description;
        String imagePath;
        String creator;

        AIProfile(String name, String description, String imagePath, String creator) {
            this.name = name;
            this.description = description;
            this.imagePath = imagePath;
            this.creator = creator;
        }

        JSONObject toJson() {
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("description", description);
            obj.put("imagePath", imagePath);
            obj.put("creator", creator);
            return obj;
        }

        static AIProfile fromJson(JSONObject obj) {
            String name = obj.optString("name", "Unnamed");
            String description = obj.optString("description", "");
            String imagePath = obj.optString("imagePath", null);
            String creator = obj.optString("creator", null);
            return new AIProfile(name, description, imagePath, creator);
        }
    }

    private Icon createCircularAvatar(String name, int size) {
        BufferedImage bi = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bi.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(30, 100, 220));
        g2.fillOval(0, 0, size - 1, size - 1);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, size / 2));
        FontMetrics fm = g2.getFontMetrics();
        String initial = name.substring(0, 1).toUpperCase();
        int x = (size - fm.stringWidth(initial)) / 2;
        int y = (size + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(initial, x, y);

        g2.dispose();
        return new ImageIcon(bi);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            AIBuilderApp app = new AIBuilderApp();
            app.createAndShowUI();
        });
    }
}