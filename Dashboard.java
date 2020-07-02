import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import javax.swing.*;
import java.applet.*;

public class Dashboard extends JApplet implements Runnable {
	static final long serialVersionUID = 1L;

	// Connect status constants
	public final static int NULL = 0;
	public final static int DISCONNECTED = 1;
	public final static int DISCONNECTING = 2;
	public final static int BEGIN_CONNECT = 3;
	public final static int CONNECTED = 4;

	// Other constants
	public final static String statusMessages[] = {
		" Reg. failed!", " Disconnected",
		" Disconnecting...", " Registering...", " Registered"
	};
	public final static String configFile = "config.txt";
	boolean inBrowser = true;
	//    public final static Dashboard dashboard = new Dashboard();

	// Connection state info
	public static String hostIP = "127.0.0.1";
	public static InetAddress address;
	public static int port = 5070;
	public static int noticeCount = 0;
	public static int connectionStatus = DISCONNECTED;
	public static String statusString = statusMessages[connectionStatus];
	public static String domain = "";   // you should probably set this to your real domain
	public static int scroll = 1;
	public static int confirmed = 0;    // number of domains confirmed
	public volatile Thread receiveThread = null;
	private static AudioClip sound = null;
	private static boolean soundEnabled = false;

	// Various GUI components and info
	public static Container contentPane = null;
	public static JFrame mainFrame = null;
	public static JPanel noticePane = null;
	public static JScrollPane noticeTextPane = null;
	public static JScrollBar verticalBar = null;
	public static DefaultListModel<Object> noticeListModel = null;
	public static JList<Object> noticeList = null;
	public static JPanel statusBar = null;
	public static JLabel statusField = null;
	public static JTextField statusColor = null;
	public static JPanel statusPane = null;
	public static JLabel countField = null;
	public static JButton acceptButton = null;
	public static JButton rejectButton = null;
	public static JButton quitButton = null;
	public static JCheckBox soundButton = null;

	// TCP Components
	public static DatagramSocket socket = null;
	public static DatagramPacket sendPacket = null;
	public static DatagramPacket recvPacket = null;

	// Custom List Renderer since we use the array index as the List "value"
	public class ListBoxRenderer extends JLabel implements ListCellRenderer<Object> {
		static final long serialVersionUID = 1L;
		public ListBoxRenderer() {
			setOpaque(true);
		}

		public Component getListCellRendererComponent(
				JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus)
		{
			Notice notice = (Notice)value; 

			if ((notice.getDisabled() == true) || (notice.getInfoOnly() == true)) {
				if (notice.getDisabled() == true) {
					setForeground(Color.lightGray);
				} else {
					setForeground(list.getForeground());
				}
				setBackground(list.getBackground());
				isSelected = false;
			} else {
				if (isSelected) {
					setBackground(list.getSelectionBackground());
					setForeground(list.getSelectionForeground());
					acceptButton.setEnabled(true);
					rejectButton.setEnabled(true);
				} else {
					setBackground(list.getBackground());
					setForeground(list.getForeground());
				}
			}

			setText(notice.getMsg());

			return this;
		}
	}


	//////////////////////////////////////////////////////////////////
	public class Notice {
		private String msg;
		private int port;
		private InetAddress address;
		private boolean disabled = false;
		private boolean info = false;
		public String getMsg() {
			return this.msg;
		}
		public int getPort() {
			return this.port;
		}
		public InetAddress getAddr() {
			return this.address;
		}
		public boolean getDisabled() {
			return this.disabled;
		}
		public boolean getInfoOnly() {
			return this.info;
		}
		public void setMsg(String msg) {
			this.msg = msg;
		} 
		public void setPort(int port) {
			this.port = port;
		}    
		public void setAddr(InetAddress address) {
			this.address = address;
		}
		public void setDisabled(boolean disabled) {
			this.disabled = disabled;
		}
		public void setInfoOnly(boolean info) {
			this.info = info;
		}
	}


	/////////////////////////////////////////////////////////////////

	// Initialize all the GUI components and display the frame
	private void initGUI() {
		contentPane = getContentPane();

		// Set up the status bar
		statusField = new JLabel();
		statusField.setText(statusMessages[DISCONNECTED]);
		statusColor = new JTextField(1);
		statusColor.setBackground(Color.red);
		statusColor.setEditable(false);
		statusBar = new JPanel(new BorderLayout());
		statusBar.add(statusColor, BorderLayout.WEST);
		statusBar.add(statusField, BorderLayout.CENTER);
		ActionAdapter buttonListener = null;

		// Set up the notice pane
		noticePane = new JPanel(new BorderLayout());
		noticeListModel = new DefaultListModel<>();
		noticeList = new JList<>(noticeListModel);
		noticeList.setSelectionMode(DefaultListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		// Set up custom list renderer to handle proper tracking of notices
		noticeList.setCellRenderer(new ListBoxRenderer());
		// Set up scroll pane
		noticeTextPane = new JScrollPane(noticeList,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		verticalBar = noticeTextPane.getVerticalScrollBar();

		// Accept/Reject buttons
		JPanel buttonPane = new JPanel(new GridLayout(1,6));
		buttonListener = new ActionAdapter() {
			public void actionPerformed(ActionEvent e) {
				if (e.getActionCommand().equals("quit")) {
					changeStatusTS(DISCONNECTING, true);
					cleanUp();
					changeStatusTS(DISCONNECTED, true);
					System.exit(0);
				} else {
					if (noticeList.isSelectionEmpty()) {
						// nothing selected so do nothing
					} else {
						Object[] selectedItems = noticeList.getSelectedValues();
						noticeList.clearSelection();
						for (int i=0; i<selectedItems.length; i++) {
							Notice noticeObj = (Notice)selectedItems[i];

							// Perform requested action
							if (e.getActionCommand().equals("accept")) {
								sendIt("0", noticeObj.getPort(), noticeObj.getAddr());
							} else {
							// Reject
								sendIt("100", noticeObj.getPort(), noticeObj.getAddr());
							}
							noticeListModel.removeElement(selectedItems[i]);
						}
						// disable buttons
						acceptButton.setEnabled(false);
						rejectButton.setEnabled(false);
					}
				}
			}
		};

		acceptButton = new JButton("Accept");
		acceptButton.setMnemonic(KeyEvent.VK_A);
		acceptButton.setActionCommand("accept");
		acceptButton.addActionListener(buttonListener);
		acceptButton.setEnabled(false);
		int buttonHeight = acceptButton.getPreferredSize().height - 6;
		int buttonWidth = acceptButton.getPreferredSize().width;
		acceptButton.setPreferredSize(new Dimension(buttonWidth,buttonHeight));
		rejectButton = new JButton("Reject");
		rejectButton.setMnemonic(KeyEvent.VK_R);
		rejectButton.setActionCommand("reject");
		rejectButton.addActionListener(buttonListener);
		rejectButton.setEnabled(false);
		buttonHeight = rejectButton.getPreferredSize().height - 6;
		buttonWidth = rejectButton.getPreferredSize().width;
		rejectButton.setPreferredSize(new Dimension(buttonWidth,buttonHeight));
		if (!inBrowser) {
			quitButton = new JButton("Quit");
			quitButton.setMnemonic(KeyEvent.VK_Q);
			quitButton.setActionCommand("quit");
			quitButton.addActionListener(buttonListener);
			quitButton.setEnabled(true);
			buttonHeight = quitButton.getPreferredSize().height - 6;
			buttonWidth = quitButton.getPreferredSize().width;
			quitButton.setPreferredSize(new Dimension(buttonWidth,buttonHeight));
		}
		ItemListener soundListener = new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				Object source = e.getItemSelectable();

				if (source == soundButton) {
					if (e.getStateChange() == ItemEvent.DESELECTED) {
						soundEnabled = false;
					} else {
						soundEnabled = true;
					}
				}
			}
		};
		soundButton = new JCheckBox("sound");
		soundButton.setMnemonic(KeyEvent.VK_S);
		soundButton.setActionCommand("sound");
		soundButton.addItemListener(soundListener);
		soundButton.setEnabled(true);

		buttonPane.add(statusBar);
		buttonPane.add(soundButton);
		buttonPane.add(acceptButton);
		buttonPane.add(rejectButton);
		buttonPane.add(new JLabel());
		if (!inBrowser) {
			buttonPane.add(quitButton);
		} else {
			buttonPane.add(new JLabel());
		}

		countField = new JLabel();
		countField.setText("Notices received: " + noticeCount);

		noticePane.add(noticeTextPane, BorderLayout.CENTER);
		noticePane.setPreferredSize(new Dimension(600, 200));
		noticePane.add(countField, BorderLayout.SOUTH);

		// Set up the main pane
		JPanel mainPane = new JPanel(new BorderLayout());
		mainPane.add(buttonPane, BorderLayout.SOUTH);
		mainPane.add(noticePane, BorderLayout.CENTER);

		contentPane.add(mainPane);
	}


	/////////////////////////////////////////////////////////////////

	// The thread-safe way to change the GUI components while
	// changing state
	private void changeStatusTS(int newConnectStatus, boolean noError) {
		// Change state if valid state
		if (newConnectStatus != NULL) {
			connectionStatus = newConnectStatus;
		}

		// If there is no error, display the appropriate status message
		if (noError) {
			statusString = statusMessages[connectionStatus];
		} else {
			// Otherwise, display error message
			statusString = statusMessages[NULL];
		}

		// Call the run() routine (Runnable interface) on the
		// error-handling and GUI-update thread
		SwingUtilities.invokeLater(this);
	}

	/////////////////////////////////////////////////////////////////

	// The non-thread-safe way to change the GUI components while
	// changing state
	private void changeStatusNTS(int newConnectStatus, boolean noError) {
		// Change state if valid state
		if (newConnectStatus != NULL) {
			connectionStatus = newConnectStatus;
		}

		// If there is no error, display the appropriate status message
		if (noError) {
			statusString = statusMessages[connectionStatus];
		} else {
			// Otherwise, display error message
			statusString = statusMessages[NULL];
		}

		// Call the run() routine (Runnable interface) on the
		// current thread
		this.run();
	}


	/////////////////////////////////////////////////////////////////

	// Cleanup for disconnect
	private static void cleanUp() {
		if (socket != null) {
			socket.close();
			socket = null;
		}
	}

	/////////////////////////////////////////////////////////////////

	// Checks the current state and sets the enables/disables
	// accordingly
	public void run() {
		if (receiveThread == Thread.currentThread()) {
			byte[] recvBuf = new byte[256];
			DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
			int count;

			while(true) {
				try { // get response
					//[ifDEBUG]
					System.out.println("Applet about to call socket.receive().");
					//[endDEBUG]
					socket.receive(recvPacket);
					//[ifDEBUG]
					System.out.println("Applet returned from socket.receive().");
					//[endDEBUG]
				} catch (IOException e) {
					//[ifDEBUG]
					System.out.println("Applet socket.receive failed:");
					e.printStackTrace();
					//[endDEBUG]
					return;
				}

				byte[] received = recvPacket.getData();
				String msgText = new String(received, 0, recvPacket.getLength());
				//[ifDEBUG]
				System.out.println("Notice: " + msgText);
				//[endDEBUG]
				if (msgText.startsWith("timeout") || msgText.startsWith("accepted") || msgText.startsWith("rejected")) {
					// search for JList notice to disable
					Notice tempNotice = new Notice();
					for (count=0; count<noticeListModel.getSize(); count++) {
						tempNotice = (Notice)noticeListModel.getElementAt(count);
						if (tempNotice.getPort() == recvPacket.getPort()) {
							if (msgText.startsWith("timeout")) {
								tempNotice.setMsg("TIMEOUT - " + tempNotice.getMsg());
								//[ifDEBUG]
								System.out.println("WE TIMED OUT!!!");
								//[endDEBUG]
							} else if (msgText.startsWith("accepted")) {
								tempNotice.setMsg("ACCEPTED - " + tempNotice.getMsg());
								//[ifDEBUG]
								System.out.println("Other client ACCEPTED!!!");
								//[endDEBUG]
							} else {
								tempNotice.setMsg("REJECTED - " + tempNotice.getMsg());
								//[ifDEBUG]
								System.out.println("Other client REJECTED!!!");
								//[endDEBUG]
							}
							tempNotice.setDisabled(true);
							break;
						}
					}
				} else {
					Notice notice = new Notice();
					notice.setMsg(msgText);
					notice.setPort(recvPacket.getPort());
					notice.setAddr(recvPacket.getAddress());
					notice.setDisabled(false);
					if (noticeListModel.getSize() >= 1000) {

					}
					if (scroll == 1) {
						if (noticeListModel.getSize() >= 1000) noticeListModel.removeElementAt(0);
						noticeListModel.addElement(notice);
						noticeTextPane.invalidate();
						noticeTextPane.validate();
						verticalBar.setValue(verticalBar.getMaximum());
					} else {
						if (noticeListModel.getSize() >= 1000) noticeListModel.removeElementAt(noticeListModel.getSize()-1);
						noticeListModel.insertElementAt(notice,0);
						noticeTextPane.invalidate();
						noticeTextPane.validate();
						verticalBar.setValue(verticalBar.getMinimum());
					}
					if (soundEnabled) {
						//[ifDEBUG]
						System.out.println("Playing sound!!!");
						//[endDEBUG]
						sound.play();
					}
					noticeCount++;
				}
				changeStatusTS(NULL, true);
			}
		} else {
			switch (connectionStatus) {
				case DISCONNECTED:
					acceptButton.setEnabled(false);
					rejectButton.setEnabled(false);
					statusColor.setBackground(Color.red);
					break;

				case DISCONNECTING:
					acceptButton.setEnabled(false);
					rejectButton.setEnabled(false);
					statusColor.setBackground(Color.orange);
					break;

				case CONNECTED:
					statusColor.setBackground(Color.green);
					countField.setText("Notices received: " + noticeCount);
					break;

				case BEGIN_CONNECT:
					statusColor.setBackground(Color.orange);
					break;
			}

			// Make sure that the button/text field states are consistent
			// with the internal states
			statusField.setText(statusString);

			contentPane.repaint();
		}
	}


	public static void sendIt(String response, int responseport, InetAddress responseaddress) {
		byte [] responseBuf = response.getBytes();
		DatagramPacket sendpacket = new DatagramPacket(responseBuf, responseBuf.length, responseaddress, responseport);

		try { // send response
			//[ifDEBUG]
			System.out.println("Applet about to send response to address "
					+ responseaddress + " at port " + responseport);
			//[endDEBUG]
			socket.send(sendpacket);
			//[ifDEBUG]
			System.out.println("Applet sent packet.");
			//[endDEBUG]
		} catch (IOException e) {
			//[ifDEBUG]
			System.out.println("Applet socket.send failed:");
			e.printStackTrace();
			//[endDEBUG]
			return;
		}
	}

	public boolean registerDomain(String domain) {
		byte[] sendBuf = new byte[256];
		sendBuf = domain.getBytes();
		sendPacket = new DatagramPacket(sendBuf, sendBuf.length, address, port);

		try {
			// try to register with the server
			//[ifDEBUG]
			System.out.println("Applet about to send registration for domain "
					+ domain + " to address " + address + " at port " + port);
			//[endDEBUG]
			socket.send(sendPacket);
			//[ifDEBUG]
			System.out.println("Applet sent registration.");
			//[endDEBUG]
		}
		// If error, clean up and output an error message
		catch (IOException e) {
			return(false);
		}

		// get registration confirmation
		byte[] recvBuf = new byte[256];
		DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
		try {
			socket.setSoTimeout(2000);
		} catch (SocketException e) { return(false); }

		try { // get response
			//[ifDEBUG]
			System.out.println("Applet about to call socket.receive().");
			//[endDEBUG]
			socket.receive(recvPacket);
			//[ifDEBUG]
			System.out.println("Applet returned from socket.receive().");
			//[endDEBUG]
		} catch (SocketTimeoutException e) {
			//[ifDEBUG]
			System.out.println("Applet socket.receive timeout:");
			e.printStackTrace();
			//[endDEBUG]
			return(false);
		} catch (IOException e) {
			//[ifDEBUG]
			System.out.println("Applet socket.receive failed:");
			e.printStackTrace();
			//[endDEBUG]
			return(false);
		}

		byte[] received = recvPacket.getData();
		String msgText = new String(received, 0, recvPacket.getLength());
		if (domain.equals(msgText)) {
			return(true);
		} else {
			return(false);
		}
	}



	/////////////////////////////////////////////////////////////////

	// The main procedures
	public void init() {
		String curLine;
		int count = 0;
		String get_address=null, get_port=null, get_scroll=null, get_sound=null, get_domain=null;
		boolean done = false;
		String[] domains = null; 

		try {
			socket = new DatagramSocket();
		} catch (IOException e) {
			//[ifDEBUG]
			System.out.println("Couldn't create new DatagramSocket");
			//[endDEBUG]
			return;
		}

		noticeCount = 0;
		initGUI();
		changeStatusTS(BEGIN_CONNECT, true);

		//[ifDEBUG]
		System.out.println("GUI done");
		//[endDEBUG]

		try {
			InputStream config = null;
			if (!inBrowser) {
				//[ifDEBUG]
				System.out.println("Running as application");
				//[endDEBUG]
				config = getClass().getResourceAsStream(configFile);
				sound = Applet.newAudioClip(this.getClass().getResource("beep.wav"));
			} else {
				//[ifDEBUG]
				System.out.println("Running as applet");
				//[endDEBUG]
				ClassLoader cl = this.getClass().getClassLoader();
				config = cl.getResourceAsStream(configFile);
				sound = getAudioClip(getCodeBase(),"beep.wav");
				get_address = getCodeBase().getHost();
				get_port = getParameter("port");
				get_scroll = getParameter("scroll");
				get_sound = getParameter("sound");
				get_domain = getParameter("domain");
				if (get_domain != null) {
					domains = get_domain.split(","); 
				}
			}
			BufferedReader line = new BufferedReader(new InputStreamReader(config));
			while (((curLine = line.readLine()) != null) && !done) {
				if (curLine.startsWith("#")) continue;
				switch (count) {
					case 0:
						if (get_address == null) {
							try {
								address = InetAddress.getByName(curLine.trim());
							} catch (UnknownHostException e) {
								//[ifDEBUG]
								System.err.println("Counldn't get Internet address: Unknown host");
								//[endDEBUG]
							}
						} else {
							address = InetAddress.getByName(get_address);
						}
						break;
					case 1:
						if (get_port == null) {
							port = Integer.parseInt(curLine.trim());
						} else {
							port = Integer.parseInt(get_port);
						}
						break;
					case 2:
						if (get_scroll == null) {
							scroll = Integer.parseInt(curLine.trim());
						} else {
							scroll = Integer.parseInt(get_scroll);
						}
						break;
					case 3:
						if (get_sound == null) {
							if (curLine.trim().equals("0")) {
								//[ifDEBUG]
								System.err.println("Sound off");
								//[endDEBUG]
								soundEnabled = false;
							} else {
								//[ifDEBUG]
								System.err.println("Sound on");
								//[endDEBUG]
								soundEnabled = true;
							}
						} else {
							if (get_sound.trim().equals("0")) {
								//[ifDEBUG]
								System.err.println("Sound off");
								//[endDEBUG]
								soundEnabled = false;
							} else {
								//[ifDEBUG]
								System.err.println("Sound on");
								//[endDEBUG]
								soundEnabled = true;
							}
						}
						soundButton.setSelected(soundEnabled);
						break;
					default:
						if (get_domain == null) {
							domain = curLine.trim();
							if (domain.length() > 0) {
								if (!registerDomain(domain)) {
									Notice notice = new Notice();
									notice.setMsg("Registration failed for " + domain);
									notice.setPort(0);
									notice.setAddr(InetAddress.getByName("127.0.0.1"));
									notice.setDisabled(false);
									notice.setInfoOnly(true);
									if (scroll == 1) {
										noticeListModel.addElement(notice);
										noticeTextPane.invalidate();
										noticeTextPane.validate();
										noticeList.ensureIndexIsVisible(noticeListModel.getSize()-1);
									} else {
										noticeListModel.insertElementAt(notice,0);
										noticeTextPane.invalidate();
										noticeTextPane.validate();
										noticeList.ensureIndexIsVisible(0);
									}
								} else {
									confirmed++;
								}
							}
						} else {
							for (count=0; count<domains.length; count++) {
								if (domains[count].length() > 0) { 
									domain = domains[count];
									if (!registerDomain(domain)) {
										Notice notice = new Notice();
										notice.setMsg("Registration failed for " + domain);
										notice.setPort(0);
										notice.setAddr(InetAddress.getByName("127.0.0.1"));
										notice.setDisabled(false);
										notice.setInfoOnly(true);
										if (scroll == 1) {
											noticeListModel.addElement(notice);
											noticeTextPane.invalidate();
											noticeTextPane.validate();
											noticeList.ensureIndexIsVisible(noticeListModel.getSize()-1);
										} else {
											noticeListModel.insertElementAt(notice,0);
											noticeTextPane.invalidate();
											noticeTextPane.validate();
											noticeList.ensureIndexIsVisible(0);
										}
									} else {
										confirmed++;
									}
								} else {
									continue;
								}
							}
							done = true;
						}
						break;
				}
				count++;
			}
			config.close();
		}
		catch (Exception e) {}

		try {
			socket.setSoTimeout(0);
		} catch (SocketException e) {}
	}


	public void start() {
		if (confirmed > 0) {
			if (receiveThread == null) {
				receiveThread = new Thread(this, "Receiver");
				receiveThread.start();

				changeStatusTS(CONNECTED, true);
			}
			//            while (true) {
			//                try { // Poll every ~10 ms
			//                    Thread.sleep(10);
			//                 }
			//                catch (InterruptedException e) {}
			//            }
		} else {
			cleanUp();
			changeStatusNTS(DISCONNECTED, false);
		}
	}

	public static void main (String[] args) {

		// Create an instance of Dashboard and add to the frame.
		Dashboard applet = new Dashboard();
		applet.inBrowser = false;
		applet.init();

		// Set up the main frame
		mainFrame = new JFrame("Dashboard");
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainFrame.getContentPane().add(applet);
		mainFrame.setSize(mainFrame.getPreferredSize());
		mainFrame.setLocation(100, 100);
		mainFrame.pack();
		mainFrame.setVisible(true);

		applet.start();

	}
}



////////////////////////////////////////////////////////////////////

// Action adapter for easy event-listener coding
class ActionAdapter implements ActionListener {
	public void actionPerformed(ActionEvent e) {}
}

////////////////////////////////////////////////////////////////////

