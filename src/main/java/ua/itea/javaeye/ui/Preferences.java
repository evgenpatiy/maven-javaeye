package ua.itea.javaeye.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamDiscoveryEvent;
import com.github.sarxos.webcam.WebcamDiscoveryListener;
import com.github.sarxos.webcam.WebcamEvent;
import com.github.sarxos.webcam.WebcamListener;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamPicker;

import lombok.extern.slf4j.Slf4j;
import ua.itea.javaeye.stream.ServerStreamReceiver;
import ua.itea.javaeye.stream.VideoStreamServer;
import ua.itea.javaeye.utils.JavaEyeUtils;

@Slf4j
public class Preferences extends JFrame implements Runnable, WebcamListener, WebcamDiscoveryListener, ItemListener,
        WindowListener, UncaughtExceptionHandler {
    private static final long serialVersionUID = 6741762424371250099L;
    private final JavaEyeUtils utils = new JavaEyeUtils();
    private Webcam webcam = null;
    private JPanel webcamSetupPanel = new JPanel();
    private WebcamPanel webcamPreviewPanel = null;
    private WebcamPicker webcamPicker = null;
    private boolean webcamOK = false;
    private boolean networkOK = false;
    private ServerStreamReceiver streamReceiver = new ServerStreamReceiver();
    private InetAddress localAddress = null;

    @Override
    public void run() {
        addWindowListener(this);

        JButton sessionListButton = new JButton("Sessions");
        sessionListButton.addActionListener(event -> {
            sessionListButton.setEnabled(false);
            (new Thread(new SessionList(webcam, localAddress))).start();
        });

        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new GridLayout(2, 1));
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new FlowLayout());

        // network

        JLabel netHostName = new JLabel();
        JLabel netAddress = new JLabel();
        JLabel netNIFStatus = new JLabel();
        JLabel netMAC = new JLabel();

        JPanel networkSetupPanel = new JPanel();
        networkSetupPanel.setBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Network settings "));
        networkSetupPanel.setLayout(new GridLayout(7, 2));

        JComboBox<NetworkInterface> netNIF = new JComboBox<NetworkInterface>();
        netNIF.addActionListener(new ActionListener() {

            @SuppressWarnings("unchecked")
            @Override
            public void actionPerformed(ActionEvent e) {
                JComboBox<NetworkInterface> source = (JComboBox<NetworkInterface>) e.getSource();
                JComboBox<NetworkInterface> jb = source;
                NetworkInterface nif = (NetworkInterface) jb.getSelectedItem();
                try {
                    if (nif.isUp()) {
                        netNIFStatus.setText("connected");
                        netNIFStatus.setIcon(new ImageIcon(ImageIO.read(getClass().getResource("/img/green.png"))));
                        networkOK = true;
                        if (webcamOK && networkOK) {
                            sessionListButton.setEnabled(true);
                        }
                    } else {
                        netNIFStatus.setText("disconnected");
                        netNIFStatus.setIcon(new ImageIcon(ImageIO.read(getClass().getResource("/img/red.png"))));
                        networkOK = false;
                        if (webcamOK && networkOK) {
                            sessionListButton.setEnabled(true);
                        }
                    }
                } catch (SocketException ex) {
                    JOptionPane.showMessageDialog(null, "Network socket failure!", "error", JOptionPane.ERROR_MESSAGE);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

                Enumeration<InetAddress> inetAddresses = nif.getInetAddresses();
                try {
                    netHostName.setText(InetAddress.getLocalHost().getHostName());
                } catch (UnknownHostException ex) {
                }

                localAddress = Collections.list(inetAddresses).get(0);

                netAddress.setText(localAddress.getHostAddress());
                try {
                    netMAC.setText(
                            nif.isLoopback() ? "loopback, no MAC" : utils.getStringFromMAC(nif.getHardwareAddress()));
                } catch (SocketException e1) {
                    e1.printStackTrace();
                }
            }
        });

        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface nif : Collections.list(nets)) {
                if (!nif.isLoopback()) {
                    netNIF.addItem(nif);
                }
            }
        } catch (SocketException ex) {
            JOptionPane.showMessageDialog(null, "Network socket failure!", "Error", JOptionPane.ERROR_MESSAGE);
        }

        networkSetupPanel.add(new JLabel(" NIC:"));
        networkSetupPanel.add(netNIF);
        networkSetupPanel.add(new JLabel(" NIC status:"));
        networkSetupPanel.add(netNIFStatus);
        networkSetupPanel.add(new JLabel(" Hostname:"));
        networkSetupPanel.add(netHostName);
        networkSetupPanel.add(new JLabel(" IP address:"));
        networkSetupPanel.add(netAddress);
        networkSetupPanel.add(new JLabel(" MAC:"));
        networkSetupPanel.add(netMAC);
        networkSetupPanel.add(new JLabel(" Stream port:"));
        networkSetupPanel.add(new JLabel(String.valueOf(JavaEyeUtils.streamServerPort)));

        // webcam

        Webcam.addDiscoveryListener(this); // webcam connect-disconnect
        webcamPicker = new WebcamPicker();
        webcamPicker.addItemListener(this);
        webcam = webcamPicker.getSelectedWebcam();

        webcam.setViewSize(new Dimension(320, 240));

        webcamSetupPanel.setLayout(new BorderLayout());
        webcamSetupPanel.setBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Web-camera settings "));

        if (webcam != null) {

            // Start audio receiver!

            (new Thread(streamReceiver)).start();

            // Start video stream server!

            VideoStreamServer videoServer = new VideoStreamServer(webcam, JavaEyeUtils.dimension);
            InetSocketAddress myAddress = new InetSocketAddress(localAddress, JavaEyeUtils.streamServerPort);
            videoServer.start(myAddress);

            webcamOK = true;
            if (webcamOK && networkOK) {
                sessionListButton.setEnabled(true);
            }

            webcam.addWebcamListener(Preferences.this);
            webcamPreviewPanel = new WebcamPanel(webcam, false);
            webcamPreviewPanel.setFPSDisplayed(true);
            webcamPreviewPanel.setDoubleBuffered(true);
            webcamPreviewPanel.setImageSizeDisplayed(true);

            webcamSetupPanel.add(webcamPicker, BorderLayout.NORTH);
            webcamSetupPanel.add(webcamPreviewPanel, BorderLayout.CENTER);

            Thread webcamPanelThread = new Thread() {
                @Override
                public void run() {
                    log.info("Camera runs on " + Thread.currentThread().getName());
                    webcamPreviewPanel.start();
                }
            };
            webcamPanelThread.setDaemon(true);
            webcamPanelThread.setName("preview panel on preferences window");
            webcamPanelThread.setUncaughtExceptionHandler(this);
            webcamPanelThread.start();

        } else {
            webcamSetupPanel.add(new JLabel("Webcam fault", SwingConstants.CENTER), BorderLayout.CENTER);
        }

        sessionListButton.setEnabled(true);
        JButton exitButton = new JButton("Exit");
        exitButton.addActionListener(event -> {
            streamReceiver.stop();
            System.exit(1);
        });

        settingsPanel.add(webcamSetupPanel);
        settingsPanel.add(networkSetupPanel);

        buttonsPanel.add(sessionListButton);
        buttonsPanel.add(exitButton);

        setLayout(new BorderLayout());
        add(settingsPanel, BorderLayout.CENTER);
        add(buttonsPanel, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(320, 610));
        setTitle("JavaEye");

        URL iconURL = getClass().getResource("/img/eye.png");
        ImageIcon icon = new ImageIcon(iconURL);
        setIconImage(icon.getImage());

        setVisible(true);
        pack();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        log.info("Exception in thread " + t.getName());
    }

    @Override
    public void windowOpened(WindowEvent e) {

    }

    @Override
    public void windowClosing(WindowEvent e) {
        streamReceiver.stop();
    }

    @Override
    public void windowClosed(WindowEvent e) {

    }

    @Override
    public void windowIconified(WindowEvent e) {
        log.info("Camera " + webcam.getName() + " changed its state to pause");
        webcamPreviewPanel.pause();
    }

    @Override
    public void windowDeiconified(WindowEvent e) {
        log.info("Camera " + webcam.getName() + " changed its state to resume");
        webcamPreviewPanel.resume();
    }

    @Override
    public void windowActivated(WindowEvent e) {
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
    }

    @SuppressWarnings("unchecked")
    @Override
    public void webcamFound(WebcamDiscoveryEvent arg0) {
        if (webcamPicker != null) {
            webcamPicker.addItem(arg0.getWebcam());
        }

    }

    @Override
    public void webcamGone(WebcamDiscoveryEvent arg0) {
        if (webcamPicker != null) {
            webcamPicker.removeItem(arg0.getWebcam());
        }
    }

    @Override
    public void webcamClosed(WebcamEvent arg0) {
        log.info("Webcam " + webcam.getName() + " closed");

    }

    @Override
    public void webcamDisposed(WebcamEvent arg0) {
        log.info("Webcam " + webcam.getName() + " disposed");

    }

    @Override
    public void webcamImageObtained(WebcamEvent arg0) {
    }

    @Override
    public void webcamOpen(WebcamEvent arg0) {
        log.info("Webcam " + webcam.getName() + " opened");

    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        if (e.getItem() != webcam) {
            if (webcam != null) {
                webcamPreviewPanel.stop();
                webcamSetupPanel.remove(webcamPreviewPanel);
                webcam.removeWebcamListener(this);
                webcam.close();

                webcam = (Webcam) e.getItem();
                webcam.addWebcamListener(Preferences.this);
                log.info("Selected camera: " + webcam.getName());
                webcamPreviewPanel = new WebcamPanel(webcam, false);
                webcamPreviewPanel.setFPSDisplayed(true);
                webcamPreviewPanel.setDoubleBuffered(true);
                webcamPreviewPanel.setImageSizeDisplayed(true);

                webcamSetupPanel.add(webcamPreviewPanel, BorderLayout.CENTER);
                pack();

                Thread webcamPanelThread = new Thread() {
                    @Override
                    public void run() {
                        log.info("Camera runs on " + Thread.currentThread().getName());
                        webcamPreviewPanel.start();
                    }
                };
                webcamPanelThread.setDaemon(true);
                webcamPanelThread.setName("preview panel on preferences window");
                webcamPanelThread.setUncaughtExceptionHandler(this);
                webcamPanelThread.start();
            }
        }
    }
}
