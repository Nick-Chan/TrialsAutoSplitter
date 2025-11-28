package com.TrialsAutoSplitter;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.PrintWriter;
import java.net.Socket;

public class TrialsAutoSplitterPanel extends PluginPanel
{
    private final Client client;
    private final TrialsAutoSplitterConfig config;
    private final TrialsAutoSplitterPlugin splitter;
    private PrintWriter writer;
    private Socket socket;
    private JLabel status;

    @Inject
    TrialsAutoSplitterPanel(Client client, PrintWriter writer, TrialsAutoSplitterConfig config, TrialsAutoSplitterPlugin splitter){
        this.client = client;
        this.writer = writer;
        this.config = config;
        this.splitter = splitter;
    }

    private void connect(){
        try {
            socket = new Socket("localhost", config.port());
            writer = new PrintWriter(socket.getOutputStream());
            splitter.writer = writer;

            set_connected();

            if (client.getGameState() == GameState.LOGGED_IN) {
                String message = "Socket started at port <col=ff0000>" + config.port() + "</col>.";
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
            }

        } catch (Exception e) {
            if (client.getGameState() == GameState.LOGGED_IN) {
                String message = "Could not start socket, did you start the LiveSplit server?";
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
            }
        }
    }

    public void disconnect(){
        try {
            socket.close();
            set_disconnected();

            if (client.getGameState() == GameState.LOGGED_IN) {
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Socket closed.", null);
            }
        } catch (Exception ignored) {}
    }

    private void control(String cmd){
        try {
            writer.write(cmd + "\r\n");
            writer.flush();
        } catch (Exception ignored) { }
    }

    public void startPanel(){
        getParent().setLayout(new BorderLayout());
        getParent().add(this, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        final JPanel layout = new JPanel();
        BoxLayout boxLayout = new BoxLayout(layout, BoxLayout.Y_AXIS);
        layout.setLayout(boxLayout);
        add(layout, BorderLayout.NORTH);

        JPanel statusFrame = new JPanel();
        statusFrame.setLayout(new GridBagLayout());
        statusFrame.setBorder(BorderFactory.createTitledBorder(new LineBorder(Color.CYAN), "Status"));

        status = new JLabel("Not connected");
        status.setForeground(Color.RED);
        statusFrame.add(status);


        JPanel connectionFrame = new JPanel();
        connectionFrame.setLayout(new GridLayout(2, 1));
        connectionFrame.setBorder(BorderFactory.createTitledBorder(new LineBorder(Color.CYAN), "Connection"));

        JButton b_connect = new JButton("Connect");
        JButton b_disconnect = new JButton("Disconnect");
        b_connect.setFocusable(false);
        b_disconnect.setFocusable(false);

        b_connect.addActionListener(e -> connect());
        b_disconnect.addActionListener(e -> disconnect());

        connectionFrame.add(b_connect);
        connectionFrame.add(b_disconnect);


        JPanel controllerFrame = new JPanel();
        controllerFrame.setLayout(new GridLayout(6, 1));
        controllerFrame.setBorder(BorderFactory.createTitledBorder(new LineBorder(Color.CYAN), "LiveSplit Controller"));

        JButton[] controllerButtons = {new JButton("Split"), new JButton("Reset"), new JButton("Undo split"),
                new JButton("Skip split"), new JButton("Pause"), new JButton("Resume")};
        String[] controls = {"startorsplit", "reset", "unsplit", "skipsplit", "pause", "resume"};

        for (int i = 0; i < controllerButtons.length; i++){
            int finalI = i; // because lambda forces my hand
            controllerButtons[i].addActionListener(e -> control(controls[finalI]));

            controllerButtons[i].setFocusable(false);
            controllerFrame.add(controllerButtons[i], BorderLayout.CENTER);
        }

        layout.add(statusFrame);
        layout.add(Box.createRigidArea(new Dimension(0, 15)));
        layout.add(connectionFrame);
        layout.add(Box.createRigidArea(new Dimension(0, 15)));
        layout.add(controllerFrame);
    }

    public void set_connected(){
        status.setText("Connected");
        status.setForeground(Color.GREEN);
    }

    public void set_disconnected(){
        status.setText("Not connected");
        status.setForeground(Color.RED);
    }
}
